package com.net2software.mobile.autousb.shell

enum class ExecutionMethod(val displayName: String, val commandRunner: String) {
    AUTO("Auto-Detect", "auto"),
    ROOT("Root (su)", "su"),
    NON_ROOT("Non-Root (sh)", "sh");

    companion object {
        fun fromString(value: String): ExecutionMethod {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: AUTO
        }
    }
}
