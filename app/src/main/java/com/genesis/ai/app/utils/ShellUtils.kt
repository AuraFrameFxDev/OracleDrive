package com.genesis.ai.app.utils

import java.io.BufferedReader
import java.io.InputStreamReader

object ShellUtils {
    /**
     * Runs a list of shell commands as root (su)
     * @param commands List of commands to run
     * @return true if all commands succeed
     */
    fun runAsRoot(commands: List<String>): Boolean {
        try {
            val process = Runtime.getRuntime().exec("su")
            val output = process.outputStream
            val writer = output.bufferedWriter()
            for (cmd in commands) {
                writer.write(cmd)
                writer.newLine()
            }
            writer.write("exit")
            writer.newLine()
            writer.flush()
            writer.close()
            val result = process.waitFor()
            return result == 0
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Runs a single shell command and returns output
     */
    fun runCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                output.append(line).append("\n")
                line = reader.readLine()
            }
            reader.close()
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
