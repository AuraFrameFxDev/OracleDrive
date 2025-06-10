package com.genesis.ai.app.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object AssetExtractor {
    /**
     * Extracts an asset file to the app's filesDir or cacheDir
     * @param context Context
     * @param assetPath Path inside assets (e.g. "binaries/su-arm64-v8a")
     * @param outputFileName Name for the output file
     * @param useCacheDir If true, use cacheDir, else filesDir
     * @return File pointing to the extracted file
     */
    fun extractAsset(context: Context, assetPath: String, outputFileName: String, useCacheDir: Boolean = false): File {
        val dir = if (useCacheDir) context.cacheDir else context.filesDir
        val outFile = File(dir, outputFileName)
        context.assets.open(assetPath).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }
}
