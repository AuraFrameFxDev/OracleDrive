package com.genesis.ai.app.utils

import android.content.Context
import java.io.File

object ModuleDeployer {
    /**
     * Deploys a module to the target LSPosed modules directory (requires root)
     * @param context Context
     * @param moduleName Name of the module directory
     * @return true if deployment was successful
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
}
