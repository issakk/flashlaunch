package com.flashlaunch.data

/** A named set of apps whose visibility can be toggled as one unit. */
data class AppGroup(
    val name: String,
    val enabled: Boolean = true,
    val packages: Set<String> = emptySet(),
)
