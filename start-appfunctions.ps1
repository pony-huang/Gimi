<#
.SYNOPSIS
Starts an installed Gimi APK with temporary Shell AppFunction permission.
.DESCRIPTION
Build/install the selected variant first. Release is the default; use -Variant Debug
for the debug APK. Keep this command running during testing. Stop with
Ctrl+C; to end the device session explicitly, run adb -s <serial> shell am
force-stop <package>. No permanent permission is granted.
.EXAMPLE
.\start-appfunctions.ps1
.EXAMPLE
.\start-appfunctions.ps1 -Variant Debug -Serial <device-serial>
#>
param(
    [string]$Serial,
    [ValidateSet('Release', 'Debug')]
    [string]$Variant = 'Release'
)

$ErrorActionPreference = 'Stop'
$packageName = if ($Variant -eq 'Debug') { 'github.ponyhuang.gimi.debug' } else { 'github.ponyhuang.gimi' }
if (-not $Serial) {
    $deviceLines = @(adb devices -l | Where-Object { $_ -match '^\S+\s+device\s' })
    $usbDevices = @($deviceLines | Where-Object { $_ -notmatch 'adb-tls' })
    if ($usbDevices.Count -eq 1) { $deviceLines = $usbDevices }
    if ($deviceLines.Count -ne 1) { throw 'Specify -Serial when no unique device is connected.' }
    $Serial = ($deviceLines[0] -split '\s+')[0]
}
$installed = adb -s $Serial shell pm path --user 0 $packageName
if ($LASTEXITCODE -ne 0 -or -not ($installed -match '^package:')) {
    throw "Install the Gimi $Variant APK before starting this session."
}
$registered = @(adb -s $Serial shell pm list instrumentation | ForEach-Object {
    if ($_ -match '^instrumentation:(\S+)\s') { $Matches[1] }
})
$fullInstrumentationName = "$packageName/github.ponyhuang.gimi.appfunction.AppFunctionShellInstrumentation"
$shortInstrumentationName = "$packageName/.appfunction.AppFunctionShellInstrumentation"
$instrumentationName = $registered | Where-Object {
    $_ -eq $fullInstrumentationName -or $_ -eq $shortInstrumentationName
} | Select-Object -First 1
if ($LASTEXITCODE -ne 0 -or -not $instrumentationName) {
    throw "The installed Gimi $Variant APK lacks the AppFunction session entry. Install a current build."
}
adb -s $Serial shell am force-stop $packageName
if ($LASTEXITCODE -ne 0) { throw 'Could not stop the previous Gimi session.' }
Write-Host 'Starting temporary AppFunction session. Keep this terminal open.'
try {
    $sessionFailed = $false
    adb -s $Serial shell am instrument -w $instrumentationName | ForEach-Object {
        Write-Output $_
        if ($_ -match 'INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT: (shortMsg|Error)=') {
            $sessionFailed = $true
        }
    }
    if ($LASTEXITCODE -ne 0) { throw 'Instrumentation failed to start.' }
    if ($sessionFailed) { throw 'The AppFunction instrumentation session failed; see output above.' }
} finally {
    adb -s $Serial shell am force-stop $packageName
}
