import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.iceflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.iceflow"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("iceflow.versionCode").getOrElse("103").toInt().also { require(it > 0) }
        versionName = providers.gradleProperty("iceflow.versionName").getOrElse("0.1.2.1")

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

val verifyReleaseNativeLibraries by tasks.registering {
    doLast {
        val aar = file("libs/xray.aar")
        check(aar.isFile) { "Missing app/libs/xray.aar; run xray-wrapper/build-aar.ps1 before building or testing." }
        ZipFile(aar).use { archive ->
            listOf("arm64-v8a", "armeabi-v7a", "x86_64").forEach { abi ->
                check(file("src/main/jniLibs/$abi/libiceflow_core.so").isFile) {
                    "Missing app/src/main/jniLibs/$abi/libiceflow_core.so; run rust-core/build-android.ps1."
                }
                check(archive.getEntry("jni/$abi/libgojni.so") != null) {
                    "Missing jni/$abi/libgojni.so in app/libs/xray.aar; run xray-wrapper/build-aar.ps1."
                }
            }
        }
    }
}
tasks.matching { it.name in setOf("preReleaseBuild", "preDebugBuild", "testDebugUnitTest") }
    .configureEach { dependsOn(verifyReleaseNativeLibraries) }

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // 原生产物：xray.aar 由 xray-wrapper 生成；不存在时应用仍可编译，运行时会提示缺失
    // 只引入 aar；gomobile 同时生成的 xray-sources.jar 仅含源码，不应进入 classpath
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

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
