package com.flashlaunch.system

import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Minimal `su` bridge. Used to freeze (pm disable-user) and unfreeze (pm enable)
 * apps; there is no non-root API for that on a normal device.
 */
object RootShell {

    private const val TAG = "FlashLaunch"

    fun isRooted(): Boolean = run("id", 6)?.contains("uid=0") == true

    fun freeze(pkg: String): Boolean = run("pm disable-user --user 0 $pkg", 15) != null

    fun unfreeze(pkg: String): Boolean = run("pm enable --user 0 $pkg", 15) != null

    private fun run(command: String, timeoutSeconds: Long): String? {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else {
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (process.exitValue() == 0) output else null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "root exec failed: $command", t)
            null
        }
    }
}
