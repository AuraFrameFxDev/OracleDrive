package com.genesis.ai.app.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object AssetExtractor {
    /**
     * Copies a file from the app's assets to internal storage and returns the resulting file.
     *
     * The asset specified by [assetPath] is extracted to either the app's files directory or cache directory,
     * depending on the value of [useCacheDir]. The output file will be named [outputFileName].
     *
     * @param assetPath Path to the asset within the assets directory (e.g., "binaries/su-arm64-v8a").
     * @param outputFileName Name to assign to the extracted file in internal storage.
     * @param useCacheDir If true, the file is written to the cache directory; otherwise, to the files directory.
     * @return A [File] object referencing the extracted file in internal storage.
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
