package com.genesis.ai.app.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object AssetExtractor {
    /**
     * Extracts a file from the app's assets folder and writes it to internal storage.
     *
     * Copies the specified asset to either the app's files directory or cache directory, returning a File object referencing the extracted file.
     *
     * @param assetPath Relative path of the asset within the assets folder.
     * @param outputFileName Name to assign to the extracted file.
     * @param useCacheDir If true, the file is written to the cache directory; otherwise, to the files directory.
     * @return A File referencing the extracted asset in internal storage.
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
