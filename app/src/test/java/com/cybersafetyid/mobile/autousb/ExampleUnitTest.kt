package com.cybersafetyid.mobile.autousb

import com.cybersafetyid.mobile.autousb.shell.ShellExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun defaultAdbScript_containsExpectedCommands() {
        val script = ShellExecutor.DEFAULT_ADB_SCRIPT
        assertEquals(true, script.contains("service.adb.tcp.port 5555"))
        assertEquals(true, script.contains("stop adbd"))
        assertEquals(true, script.contains("start adbd"))
    }

    @Test
    fun isAdbPortActive_validations() {
        assertTrue(ShellExecutor.isAdbPortActive("5555"))
        assertTrue(ShellExecutor.isAdbPortActive("5555\n"))
        assertTrue(ShellExecutor.isAdbPortActive(" 4444 "))
        assertFalse(ShellExecutor.isAdbPortActive("-1"))
        assertFalse(ShellExecutor.isAdbPortActive("0"))
        assertFalse(ShellExecutor.isAdbPortActive("Disabled / None"))
        assertFalse(ShellExecutor.isAdbPortActive(""))
    }

    @Test
    fun formatAdbIpToast_singleIp() {
        val ips = listOf(Pair("wlan0", "192.168.1.100"))
        val toast = ShellExecutor.formatAdbIpToast(ips, "5555")
        assertTrue(toast.contains("ADB over TCP/IP Active"))
        assertTrue(toast.contains("adb connect 192.168.1.100:5555"))
    }

    @Test
    fun formatAdbIpToast_multipleIps() {
        val ips = listOf(Pair("wlan0", "192.168.1.100"), Pair("eth0", "192.168.1.105"))
        val toast = ShellExecutor.formatAdbIpToast(ips, "5555")
        assertTrue(toast.contains("ADB over TCP/IP Active"))
        assertTrue(toast.contains("adb connect 192.168.1.100:5555 (wlan0)"))
        assertTrue(toast.contains("adb connect 192.168.1.105:5555 (eth0)"))
    }

    @Test
    fun formatAdbIpToast_noIp() {
        val ips = emptyList<Pair<String, String>>()
        val toast = ShellExecutor.formatAdbIpToast(ips, "5555")
        assertTrue(toast.contains("Waiting for network connection..."))
    }

    @Test
    fun isLocalPortListening_invalidPortsReturnFalse() {
        assertFalse(ShellExecutor.isLocalPortListening(-1))
        assertFalse(ShellExecutor.isLocalPortListening(0))
        assertFalse(ShellExecutor.isLocalPortListening(70000))
    }

    @Test
    fun isValidIpv4_validations() {
        assertTrue(ShellExecutor.isValidIpv4("192.168.1.100"))
        assertTrue(ShellExecutor.isValidIpv4("10.0.0.1"))
        assertTrue(ShellExecutor.isValidIpv4("172.16.2.55"))
        assertFalse(ShellExecutor.isValidIpv4("127.0.0.1"))
        assertFalse(ShellExecutor.isValidIpv4("0.0.0.0"))
        assertFalse(ShellExecutor.isValidIpv4("169.254.1.20"))
        assertFalse(ShellExecutor.isValidIpv4("256.1.1.1"))
        assertFalse(ShellExecutor.isValidIpv4("fe80::1"))
        assertFalse(ShellExecutor.isValidIpv4(""))
    }

    @Test
    fun serviceConstants_verifyValues() {
        assertEquals("autousb_service_channel_v2", com.cybersafetyid.mobile.autousb.service.AutoUsbService.CHANNEL_ID)
        assertEquals(1001, com.cybersafetyid.mobile.autousb.service.AutoUsbService.NOTIFICATION_ID)
        assertEquals("com.cybersafetyid.mobile.autousb.STOP_SERVICE", com.cybersafetyid.mobile.autousb.service.AutoUsbService.ACTION_STOP_SERVICE)
    }
}
