package com.genesis.ai.app.utils

import android.content.Context
import java.io.File

object ModuleDeployer {
    /**
     * Deploys a sample LSPosed module to the target modules directory on a rooted device.
     *
     * Extracts the sample module files and copies them to `/data/adb/modules/{moduleName}`, setting appropriate permissions and ownership.
     *
     * @param context The Android context used for resource access.
     * @param moduleName The name of the module directory to deploy to. Defaults to "oracledrive_sample".
     * @return `true` if the deployment succeeds, `false` otherwise.
     */
    fun deploySampleModule(context: Context, moduleName: String = "oracledrive_sample"): Boolean {
        val (prop, sh, jar) = RootInstaller.extractSampleModule(context)
        val targetDir = File("/data/adb/modules/$moduleName")
        val commands = listOf(
            "mkdir -p ${targetDir.absolutePath}",
            "cp ${prop.absolutePath} ${targetDir.absolutePath}/module.prop",
            "cp ${sh.absolutePath} ${targetDir.absolutePath}/service.sh",
            "cp ${jar.absolutePath} ${targetDir.absolutePath}/sample_module.jar",
            "chmod 0644 ${targetDir.absolutePath}/module.prop",
            "chmod 0755 ${targetDir.absolutePath}/service.sh",
            "chmod 0644 ${targetDir.absolutePath}/sample_module.jar",
            "chown 0:0 ${targetDir.absolutePath}/*"
        )
        return ShellUtils.runAsRoot(commands)
    }

    /**
     * Removes extraneous files from all LSPosed module directories under `/data/adb/modules`.
     *
     * Only files named "module.prop", "service.sh", "sample_module.jar", and "enable" are retained in each module directory; all others are deleted.
     *
     * @return The total number of files deleted across all module directories.
     */
    fun cleanUnusedModuleFiles(): Int {
        val modulesDir = java.io.File("/data/adb/modules")
        val modules = modulesDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        var deleted = 0
        for (dir in modules) {
            val files = dir.listFiles()?.filterNot { it.name in setOf("module.prop", "service.sh", "sample_module.jar", "enable") } ?: emptyList()
            for (file in files) {
                if (file.delete()) deleted++
            }
        }
        return deleted
    }
}
