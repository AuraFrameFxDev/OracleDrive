package com.genesis.ai.app.utils

import android.content.Context
import android.os.Build
import com.genesis.ai.app.utils.AssetExtractor
import java.io.File

object RootInstaller {
    /**
     * Extracts and installs the correct su binary for the current architecture.
     * @param context Context
     * @return File pointing to the extracted su binary
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
     * Extracts the LSPosed framework ZIP to cacheDir
     * @param context Context
     * @return File pointing to the extracted LSPosed ZIP
     */
    fun extractLSPosedFramework(context: Context): File {
        return AssetExtractor.extractAsset(context, "lsposed/lsposed-framework.zip", "lsposed-framework.zip", useCacheDir = true)
    }

    /**
     * Extracts the sample LSPosed module to cacheDir
     * @param context Context
     * @return Triple<File, File, File> for (module.prop, service.sh, sample_module.jar)
     */
    fun extractSampleModule(context: Context): Triple<File, File, File> {
        val prop = AssetExtractor.extractAsset(context, "modules/module.prop", "module.prop", useCacheDir = true)
        val sh = AssetExtractor.extractAsset(context, "modules/service.sh", "service.sh", useCacheDir = true)
        val jar = AssetExtractor.extractAsset(context, "modules/sample_module.jar", "sample_module.jar", useCacheDir = true)
        return Triple(prop, sh, jar)
    }
}
