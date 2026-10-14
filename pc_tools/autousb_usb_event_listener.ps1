# ======================================================================
#   AutoUSB - PowerShell Hardware USB Event Listener
#   Triggers 'adb tcpip 5555' instantly when a USB Android device is plugged in!
# ======================================================================

Write-Host "AutoUSB PC Hardware Listener Active. Waiting for USB device insertion..." -ForegroundColor Green

$query = "SELECT * FROM __InstanceCreationEvent WITHIN 2 WHERE TargetInstance ISA 'Win32_USBControllerDevice'"

Register-WmiEvent -Query $query -SourceIdentifier "AutoUsbDevicePlugin" -Action {
    Start-Sleep -Seconds 1
    $devices = adb devices | Select-String -Pattern "\sdevice$"
    foreach ($line in $devices) {
        $devId = $line.ToString().Split("`t")[0].Trim()
        if ($devId) {
            Write-Host "[AutoUSB PC] USB Device Plugged In: $devId" -ForegroundColor Yellow
            Write-Host "[AutoUSB PC] Activating ADB TCP Port 5555 on $devId..." -ForegroundColor Cyan
            adb -s $devId tcpip 5555
            Write-Host "[AutoUSB PC] Success! ADB TCP Port 5555 activated on $devId." -ForegroundColor Green
        }
    }
}

while ($true) {
    Start-Sleep -Seconds 10
}
