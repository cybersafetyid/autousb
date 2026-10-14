package com.cybersafetyid.mobile.autousb.shell

enum class ExecutionMethod(val displayName: String, val commandRunner: String) {
    AUTO("Auto-Detect", "auto"),
    ROOT("Root (su)", "su"),
    LOCAL_ADB("Built-in Wireless ADB", "local_adb"),
    SHIZUKU("Shizuku (ADB Shell)", "shizuku"),
    NON_ROOT("Non-Root (sh)", "sh");

    companion object {
        fun fromString(value: String): ExecutionMethod {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: AUTO
        }
    }
}
