package com.genesis.ai.app.utils

import android.content.Context
import android.os.Build
import com.genesis.ai.app.utils.AssetExtractor
import java.io.File

object RootInstaller {
    /**
     * Extracts the appropriate `su` binary for the device's CPU architecture from the app assets to the cache directory.
     *
     * Selects the correct binary based on the device's primary supported ABI, with a fallback to ARM64 if the architecture is unrecognized.
     *
     * @return A `File` referencing the extracted `su` binary in the cache directory.
     */
    fun extractSuBinary(context: Context): File {
        val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val assetName = when {
            arch.contains("arm64") -> "binaries/su-arm64-v8a"
            arch.contains("armeabi") -> "binaries/su-armeabi-v7a"
            arch.contains("x86_64") -> "binaries/su-x86_64"
            arch.contains("x86") -> "binaries/su-x86"
            else -> "binaries/su-arm64-v8a"
        }
        return AssetExtractor.extractAsset(context, assetName, "su", useCacheDir = true)
    }

    /**
     * Extracts the LSPosed framework ZIP file from the app's assets to the cache directory.
     *
     * @return The extracted LSPosed framework ZIP file.
     */
    fun extractLSPosedFramework(context: Context): File {
        return AssetExtractor.extractAsset(context, "lsposed/lsposed-framework.zip", "lsposed-framework.zip", useCacheDir = true)
    }

    /**
     * Extracts the sample LSPosed module files from assets to the cache directory.
     *
     * Extracts `module.prop`, `service.sh`, and `sample_module.jar` from the `modules/` asset folder and returns them as a `Triple` of `File` objects.
     *
     * @return A `Triple` containing the extracted property file, shell script, and JAR file, in that order.
     */
    fun extractSampleModule(context: Context): Triple<File, File, File> {
        val prop = AssetExtractor.extractAsset(context, "modules/module.prop", "module.prop", useCacheDir = true)
        val sh = AssetExtractor.extractAsset(context, "modules/service.sh", "service.sh", useCacheDir = true)
        val jar = AssetExtractor.extractAsset(context, "modules/sample_module.jar", "sample_module.jar", useCacheDir = true)
        return Triple(prop, sh, jar)
    }
}
