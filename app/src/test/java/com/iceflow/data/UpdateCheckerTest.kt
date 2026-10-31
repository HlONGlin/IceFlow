package com.iceflow.data

import com.iceflow.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test fun comparesSemanticVersionsAndIgnoresTagPrefixes() {
        assertTrue(UpdateChecker.isNewer("v0.2.0", "0.1.9"))
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.99.99"))
        assertFalse(UpdateChecker.isNewer("0.1.0", "v0.1.0"))
        assertFalse(UpdateChecker.isNewer("0.1.0-beta.1", "0.1.0"))
    }

    private fun asset(variant: String, abi: String): Pair<String, String> {
        val name = "IceFlow-0.1.4.6-$variant-$abi-release.apk"
        return name to "https://example.com/download/$name"
    }

    private val releaseAssets = listOf("dual", "single").flatMap { variant ->
        listOf("arm64-v8a", "armeabi-v7a", "x86_64", "universal").map { asset(variant, it) }
    }

    @Test fun selectsUniversalForTheInstalledVariantRegardlessOfAssetOrder() {
        for (variant in listOf("single", "dual")) {
            for (assets in listOf(releaseAssets, releaseAssets.reversed())) {
                assertEquals(asset(variant, "universal").second,
                    UpdateChecker.selectDownloadUrl(assets, listOf("x86_64"), variant))
            }
        }
    }

    @Test fun defaultVariantMatchesTheCurrentBuild() {
        assertEquals(asset(BuildConfig.CORE_VARIANT, "universal").second,
            UpdateChecker.selectDownloadUrl(releaseAssets, listOf("arm64-v8a")))
    }

    @Test fun selectsCompatibleAbiInDeviceOrderWhenSameVariantUniversalIsMissing() {
        for (variant in listOf("single", "dual")) {
            val other = if (variant == "single") "dual" else "single"
            val assets = listOf(asset(other, "universal"), asset(variant, "armeabi-v7a"), asset(variant, "arm64-v8a"))
            assertEquals(asset(variant, "arm64-v8a").second,
                UpdateChecker.selectDownloadUrl(assets, listOf("arm64-v8a", "armeabi-v7a"), variant))
            assertEquals(asset(variant, "armeabi-v7a").second,
                UpdateChecker.selectDownloadUrl(assets, listOf("armeabi-v7a"), variant))
        }
    }

    @Test fun missingVariantDoesNotDownloadTheOtherCore() {
        for (variant in listOf("single", "dual")) {
            val others = releaseAssets.filterNot { it.first.contains("-$variant-") }
            assertNull(UpdateChecker.selectDownloadUrl(others, listOf("x86_64"), variant))
        }
    }

    @Test fun incompatibleAbiDoesNotFallBackToAnArbitraryApk() {
        val assets = listOf(asset("single", "arm64-v8a"), asset("dual", "x86_64"))
        assertNull(UpdateChecker.selectDownloadUrl(assets, listOf("x86_64"), "single"))
        assertNull(UpdateChecker.selectDownloadUrl(emptyList(), listOf("arm64-v8a"), "dual"))
    }

    @Test fun skipsUnsignedFilesAndEmptyDownloadUrls() {
        val universal = asset("single", "universal")
        val assets = listOf(
            universal.first.replace("IceFlow-", "IceFlow-unsigned-") to "https://example.com/unsigned.apk",
            universal.first to " ",
            asset("single", "x86_64"),
        )
        assertEquals(asset("single", "x86_64").second,
            UpdateChecker.selectDownloadUrl(assets, listOf("x86_64"), "single"))
        assertNull(UpdateChecker.selectDownloadUrl(assets.take(2), listOf("x86_64"), "single"))
    }

    @Test fun acceptsCaseInsensitiveApkNames() {
        val original = asset("dual", "universal")
        assertEquals(original.second,
            UpdateChecker.selectDownloadUrl(listOf(original.first.uppercase() to original.second), emptyList(), "dual"))
    }

    @Test fun unlabelledAssetsAndUnknownVariantsAreNotSelected() {
        val assets = listOf("IceFlow-universal-release.apk" to "https://example.com/legacy.apk")
        assertNull(UpdateChecker.selectDownloadUrl(assets, listOf("arm64-v8a"), "single"))
        assertNull(UpdateChecker.selectDownloadUrl(releaseAssets, listOf("arm64-v8a"), "unknown"))
    }
}
