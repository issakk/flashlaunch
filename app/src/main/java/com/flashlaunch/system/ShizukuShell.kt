package com.flashlaunch.system

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Shizuku bridge: runs `pm` as the shell user (uid 2000), no root needed.
 * Requires the Shizuku app to be running and this app to be granted.
 */
object ShizukuShell {

    private const val TAG = "FlashLaunch"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    const val PERMISSION_REQUEST_CODE = 0x5348 // "SH"

    fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        isBinderAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (t: Throwable) {
        false
    }

    fun requestPermission() {
        try {
            if (isBinderAlive()) Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (t: Throwable) {
            Log.w(TAG, "shizuku permission request failed", t)
        }
    }

    fun freeze(pkg: String): Boolean = exec("pm disable-user --user 0 $pkg")

    fun unfreeze(pkg: String): Boolean = exec("pm enable --user 0 $pkg")

    private fun exec(command: String, timeoutSeconds: Long = 20): Boolean {
        return try {
            // output is discarded so a chatty command can never fill the pipe
            val process = Shizuku.newProcess(
                arrayOf("sh", "-c", "$command >/dev/null 2>&1"),
                null,
                null,
            )
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (t: Throwable) {
            Log.w(TAG, "shizuku exec failed: $command", t)
            false
        }
    }
}
