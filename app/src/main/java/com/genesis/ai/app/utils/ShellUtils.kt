package com.genesis.ai.app.utils

import java.io.BufferedReader
import java.io.InputStreamReader

object ShellUtils {
    /**
     * Executes a list of shell commands with root privileges.
     *
     * Attempts to run each command in the provided list using the `su` binary. Returns `true` if all commands execute successfully (exit code 0), or `false` if an error occurs or root access is denied.
     *
     * @param commands The shell commands to execute as root.
     * @return `true` if all commands complete successfully; `false` otherwise.
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
     * Executes a shell command without root privileges and returns its standard output.
     *
     * @param command The shell command to execute.
     * @return The standard output produced by the command, or an empty string if an error occurs.
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
