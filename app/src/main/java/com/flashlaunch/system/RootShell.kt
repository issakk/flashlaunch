package com.flashlaunch.system

import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Minimal `su` bridge, used when Shizuku is not available.
 * The probe result is cached so we do not pop a root prompt on every resume.
 */
object RootShell {

    private const val TAG = "FlashLaunch"

    @Volatile
    private var cached: Boolean? = null

    /** Probes (or returns the cached answer) and caches it. */
    fun isRooted(): Boolean {
        cached?.let { return it }
        return run("id")?.contains("uid=0") == true
    }

    /** Cached answer only; never spawns `su`. */
    fun isRootedCached(): Boolean = cached ?: false

    fun resetCache() {
        cached = null
    }

    fun freeze(pkg: String): Boolean = run("pm disable-user --user 0 $pkg") != null

    fun unfreeze(pkg: String): Boolean = run("pm enable --user 0 $pkg") != null

    private fun run(command: String, timeoutSeconds: Long = 15): String? {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                cached = false
                null
            } else {
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (process.exitValue() == 0) {
                    output
                } else {
                    null
                }
            }
        } catch (t: Throwable) {
            // no su binary at all: remember that
            if (command == "id") cached = false
            Log.w(TAG, "root exec failed: $command", t)
            null
        }
    }
}
