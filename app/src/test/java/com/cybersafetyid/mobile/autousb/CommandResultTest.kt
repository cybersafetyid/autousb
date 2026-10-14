package com.cybersafetyid.mobile.autousb

import com.cybersafetyid.mobile.autousb.shell.CommandResult
import com.cybersafetyid.mobile.autousb.shell.ExecutionMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandResultTest {

    @Test
    fun commandResult_successFlag() {
        val result = CommandResult(
            command = "setprop service.adb.tcp.port 5555",
            exitCode = 0,
            stdout = "success",
            stderr = "",
            executionMethodUsed = ExecutionMethod.ROOT
        )
        assertTrue(result.isSuccess)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun executionMethod_fromString() {
        assertEquals(ExecutionMethod.ROOT, ExecutionMethod.fromString("ROOT"))
        assertEquals(ExecutionMethod.SHIZUKU, ExecutionMethod.fromString("SHIZUKU"))
        assertEquals(ExecutionMethod.NON_ROOT, ExecutionMethod.fromString("NON_ROOT"))
        assertEquals(ExecutionMethod.AUTO, ExecutionMethod.fromString("UNKNOWN"))
    }
}
