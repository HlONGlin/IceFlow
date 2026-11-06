import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.iceflow"
    compileSdk = 36

    flavorDimensions += "core"
    productFlavors {
        create("dual") {
            dimension = "core"
            buildConfigField("String", "CORE_VARIANT", "\"dual\"")
        }
        create("single") {
            dimension = "core"
            buildConfigField("String", "CORE_VARIANT", "\"single\"")
        }
    }

    sourceSets {
        getByName("main").jniLibs.setSrcDirs(emptyList<String>())
        getByName("dual").jniLibs.srcDir("src/dual/jniLibs")
    }

    defaultConfig {
        applicationId = "com.iceflow"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("iceflow.versionCode").getOrElse("105").toInt().also { require(it > 0) }
        versionName = providers.gradleProperty("iceflow.versionName").getOrElse("0.1.4.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // Generate installable ABI-specific APKs plus one universal APK for Releases.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    val signingValues = listOf("ICEFLOW_KEYSTORE_PATH", "ICEFLOW_KEYSTORE_PASSWORD", "ICEFLOW_KEY_ALIAS", "ICEFLOW_KEY_PASSWORD")
        .map { providers.environmentVariable(it).orNull }
    require(signingValues.all { it == null } || signingValues.all { !it.isNullOrBlank() }) {
        "Set all four ICEFLOW signing environment variables, or leave all unset."
    }
    if (signingValues.all { it != null }) {
        signingConfigs.create("release") {
            storeFile = file(signingValues[0]!!)
            storePassword = signingValues[1]
            keyAlias = signingValues[2]
            keyPassword = signingValues[3]
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // VpnService 需要直接加载 .so，保留传统解压方式更稳妥
        jniLibs.useLegacyPackaging = true
    }
}

val nativeAbis = { archive: ZipFile, root: String, library: String ->
    val result = mutableSetOf<String>()
    val entries = archive.entries()
    while (entries.hasMoreElements()) {
        val name = entries.nextElement().name
        val prefix = "$root/"
        if (name.startsWith(prefix) && name.endsWith("/$library")) {
            result += name.removePrefix(prefix).substringBefore('/')
        }
    }
    result
}

val releaseAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
listOf("dual", "single").forEach { flavor ->
    val flavorTitle = flavor.replaceFirstChar { it.uppercase() }
    val verifyTask = tasks.register("verify${flavorTitle}ReleaseNativeLibraries") {
        doLast {
            val apkDirectory = layout.buildDirectory.dir("outputs/apk/$flavor/release").get().asFile
            val apks = apkDirectory.listFiles()
                ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                ?.sortedBy { it.name }
                .orEmpty()
            check(apks.size == 4) {
                "Expected three ABI APKs and one universal APK for $flavor, found ${apks.size} in $apkDirectory"
            }

            if (flavor == "dual") {
                val aar = file("libs/xray.aar")
                check(aar.isFile) { "Missing app/libs/xray.aar; run native/xray-wrapper/build-aar.ps1." }
                ZipFile(aar).use { archive ->
                    val goAbis = nativeAbis(archive, "jni", "libgojni.so")
                    check(goAbis == releaseAbis.toSet()) {
                        "xray.aar libgojni.so ABIs are $goAbis, expected $releaseAbis"
                    }
                    releaseAbis.forEach { abi ->
                        check(archive.getEntry("jni/$abi/libgojni.so")?.size?.let { it > 0 } == true) {
                            "Missing or empty jni/$abi/libgojni.so in app/libs/xray.aar"
                        }
                    }
                }

                val actualAbiSets = apks.map { apk ->
                    val expected = if (apk.name.contains("universal", ignoreCase = true)) {
                        releaseAbis.toSet()
                    } else {
                        releaseAbis.filter { apk.name.contains(it) }.toSet()
                    }
                    check(expected.isNotEmpty()) { "Cannot determine ABI split from ${apk.name}" }
                    ZipFile(apk).use { archive ->
                        val goAbis = nativeAbis(archive, "lib", "libgojni.so")
                        val rustAbis = nativeAbis(archive, "lib", "libiceflow_core.so")
                        check(goAbis == expected) {
                            "${apk.name} libgojni.so ABIs are $goAbis, expected $expected"
                        }
                        check(rustAbis == expected) {
                            "${apk.name} libiceflow_core.so ABIs are $rustAbis, expected $expected"
                        }
                        expected.forEach { abi ->
                            listOf("libgojni.so", "libiceflow_core.so").forEach { library ->
                                check(archive.getEntry("lib/$abi/$library")?.size?.let { it > 0 } == true) {
                                    "Missing or empty lib/$abi/$library in ${apk.name}"
                                }
                            }
                        }
                    }
                    expected
                }.toSet()
                val expectedAbiSets = releaseAbis.map { setOf(it) }.toSet() + setOf(releaseAbis.toSet())
                check(actualAbiSets == expectedAbiSets) {
                    "$flavor APK ABI sets are $actualAbiSets, expected $expectedAbiSets"
                }
            } else {
                // Both AARs use libgojni.so: identify provenance by exact SHA-256, not basename.
                val mihomo = file("libs/single/mihomo.aar")
                val xray = file("libs/xray.aar")
                check(mihomo.isFile && xray.isFile) { "Build both audited core AARs before verification" }
                fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
                    .digest(bytes).joinToString("") { "%02x".format(it) }
                ZipFile(mihomo).use { singleAar ->
                    check(nativeAbis(singleAar, "jni", "libgojni.so") == releaseAbis.toSet())
                    ZipFile(xray).use { dualAar ->
                        apks.forEach { apk ->
                            val expected = if (apk.name.contains("universal", ignoreCase = true)) releaseAbis.toSet()
                                else releaseAbis.filter { apk.name.contains(it) }.toSet()
                            check(expected.isNotEmpty()) { "Unknown ABI for " + apk.name }
                            ZipFile(apk).use { archive ->
                                check(nativeAbis(archive, "lib", "libgojni.so") == expected)
                                check(nativeAbis(archive, "lib", "libiceflow_core.so").isEmpty()) {
                                    apk.name + " leaks the dual Rust library"
                                }
                                expected.forEach { abi ->
                                    fun bytes(zip: ZipFile, name: String): ByteArray {
                                        val entry = checkNotNull(zip.getEntry(name)) { "Missing " + name }
                                        return zip.getInputStream(entry).use { it.readBytes() }.also { check(it.isNotEmpty()) }
                                    }
                                    val source = digest(bytes(singleAar, "jni/$abi/libgojni.so"))
                                    val packaged = digest(bytes(archive, "lib/$abi/libgojni.so"))
                                    val dual = digest(bytes(dualAar, "jni/$abi/libgojni.so"))
                                    check(packaged == source && packaged != dual) {
                                        apk.name + " " + abi + " libgojni.so does not match the single mihomo AAR"
                                    }
                                    logger.lifecycle(apk.name + " " + abi + " mihomo SHA-256=" + packaged + "; Rust absent")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    tasks.matching { it.name == "assemble${flavorTitle}Release" }
        .configureEach { finalizedBy(verifyTask) }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Xray is only part of the dual core variant. Bridges remain reflection based.
    // 只引入 aar；gomobile 同时生成的 xray-sources.jar 仅含源码，不应进入 classpath
    add("dualImplementation", fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    add("singleImplementation", files("libs/single/mihomo.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    // 二维码扫描（扫码导入节点）
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }
    implementation("com.google.zxing:core:3.5.3")

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("org.yaml:snakeyaml:2.5")

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
