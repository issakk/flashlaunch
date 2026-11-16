package com.flashlaunch.system

import android.content.Context

enum class PrivilegeMode { NONE, SHIZUKU, ROOT }

data class PrivilegeStatus(
    val mode: PrivilegeMode = PrivilegeMode.NONE,
    val rootAvailable: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
) {
    val canFreeze: Boolean get() = mode != PrivilegeMode.NONE
}

/**
 * Picks the best available way to run `pm`: Shizuku first (no root, survives
 * reboots of the root daemon), then `su`.
 */
object Privileged {

    fun status(context: Context): PrivilegeStatus {
        val shizukuRunning = ShizukuShell.isBinderAlive()
        val shizukuGranted = ShizukuShell.hasPermission()
        val root = RootShell.isRooted()
        val mode = when {
            shizukuGranted -> PrivilegeMode.SHIZUKU
            root -> PrivilegeMode.ROOT
            else -> PrivilegeMode.NONE
        }
        return PrivilegeStatus(
            mode = mode,
            rootAvailable = root,
            shizukuInstalled = ShizukuShell.isInstalled(context),
            shizukuRunning = shizukuRunning,
            shizukuGranted = shizukuGranted,
        )
    }

    fun freeze(pkg: String): Boolean =
        if (ShizukuShell.hasPermission()) ShizukuShell.freeze(pkg) else RootShell.freeze(pkg)

    fun unfreeze(pkg: String): Boolean =
        if (ShizukuShell.hasPermission()) ShizukuShell.unfreeze(pkg) else RootShell.unfreeze(pkg)
}
