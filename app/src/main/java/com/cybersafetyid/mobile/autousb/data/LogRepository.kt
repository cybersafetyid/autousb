package com.cybersafetyid.mobile.autousb.data

import com.cybersafetyid.mobile.autousb.shell.CommandResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LogRepository {
    private val _logs = MutableStateFlow<List<CommandResult>>(emptyList())
    val logs: StateFlow<List<CommandResult>> = _logs.asStateFlow()

    fun addLog(result: CommandResult) {
        val currentList = _logs.value.toMutableList()
        currentList.add(0, result) // Newest first
        if (currentList.size > 100) {
            currentList.removeAt(currentList.lastIndex)
        }
        _logs.value = currentList
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
