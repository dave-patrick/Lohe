param(
    [Parameter(Mandatory)][string]$BundlePath,
    [Parameter(Mandatory)][string]$BundletoolPath,
    [string]$ApkPath,
    [string]$ZipalignPath,
    [switch]$RequireSigned
)
$ErrorActionPreference = 'Stop'
$bundle = (Resolve-Path -LiteralPath $BundlePath).Path
$bundletool = (Resolve-Path -LiteralPath $BundletoolPath).Path
$validationOutput = & java -jar $bundletool validate ("--bundle=" + $bundle) 2>&1
if ($LASTEXITCODE -ne 0) { $validationOutput | Write-Output; throw 'bundletool validation failed.' }
$manifestText = & java -jar $bundletool dump manifest ("--bundle=" + $bundle) --module=base
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the bundle manifest.' }
[xml]$manifest = $manifestText -join "`n"
$ns = 'http://schemas.android.com/apk/res/android'
if ($manifest.manifest.package -ne 'com.lohe.app') { throw 'Unexpected production package ID.' }
$application = $manifest.SelectSingleNode('/manifest/application')
if ($application.GetAttribute('debuggable', $ns) -eq 'true') { throw 'Play bundle is debuggable.' }
$sdk = $manifest.SelectSingleNode('/manifest/uses-sdk')
if ([int]$sdk.GetAttribute('targetSdkVersion', $ns) -lt 36) { throw 'Target SDK is below the verified Play requirement.' }
$carMarkers = 'com.google.android.gms.car.application|com.android.automotive|CarAppActivity|CarPlaybackService|MEDIA_TEMPLATES|CalMediaActivityComponent|android.hardware.type.automotive|templates_host.media'
if (($manifestText -join "`n") -match $carMarkers) { throw 'Phone-only Play bundle still declares car support.' }
$permissions = @($manifest.SelectNodes('/manifest/uses-permission') | ForEach-Object { $_.GetAttribute('name', $ns) })
foreach ($required in @('android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK', 'android.permission.FOREGROUND_SERVICE_SPECIAL_USE', 'android.permission.SYSTEM_ALERT_WINDOW')) {
    if ($required -notin $permissions) { throw "Expected playback permission missing: $required" }
}
foreach ($forbidden in @('android.permission.REQUEST_INSTALL_PACKAGES', 'android.permission.QUERY_ALL_PACKAGES', 'android.permission.MANAGE_EXTERNAL_STORAGE', 'com.google.android.gms.permission.AD_ID')) {
    if ($forbidden -in $permissions) { throw "Unexpected restricted/advertising permission: $forbidden" }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($bundle)
$native = @()
try {
    $signed = @($zip.Entries | Where-Object FullName -match '^META-INF/[^/]+\.(RSA|DSA|EC)$').Count -gt 0
    if ($RequireSigned -and !$signed) { throw 'Submission bundle is unsigned.' }
    foreach ($entry in @($zip.Entries | Where-Object FullName -like '*.so')) {
        $stream = $entry.Open()
        $memory = [IO.MemoryStream]::new()
        try { $stream.CopyTo($memory); $bytes = $memory.ToArray() } finally { $stream.Dispose(); $memory.Dispose() }
        if ($bytes.Length -lt 64 -or $bytes[0] -ne 127 -or $bytes[1] -ne 69 -or $bytes[2] -ne 76 -or $bytes[3] -ne 70 -or $bytes[5] -ne 1) {
            throw "Unsupported native library format: $($entry.FullName)"
        }
        $minimumAlignment = $null
        if ($entry.FullName -match '/(arm64-v8a|x86_64)/') {
            if ($bytes[4] -ne 2) { throw 'A 64-bit ABI contains a non-64-bit ELF.' }
            $offset = [BitConverter]::ToUInt64($bytes, 32)
            $size = [BitConverter]::ToUInt16($bytes, 54)
            $count = [BitConverter]::ToUInt16($bytes, 56)
            for ($index = 0; $index -lt $count; $index++) {
                $header = [int]($offset + $index * $size)
                if ([BitConverter]::ToUInt32($bytes, $header) -eq 1) {
                    $alignment = [BitConverter]::ToUInt64($bytes, $header + 48)
                    if ($null -eq $minimumAlignment -or $alignment -lt $minimumAlignment) { $minimumAlignment = $alignment }
                    if ($alignment -lt 16384) { throw "Native library is not 16 KB aligned: $($entry.FullName)" }
                }
            }
            if ($null -eq $minimumAlignment) { throw 'Native library has no loadable segments.' }
        }
        $native += [pscustomobject]@{library=$entry.FullName; minimumLoadAlignment=$minimumAlignment}
    }
} finally { $zip.Dispose() }
if ($signed) {
    $signatureOutput = & jarsigner -verify $bundle 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Bundle signature verification failed.' }
}
if ($ApkPath) {
    if (!$ZipalignPath) { throw 'Supply ZipalignPath when checking an APK.' }
    & $ZipalignPath -c -P 16 4 ((Resolve-Path -LiteralPath $ApkPath).Path)
    if ($LASTEXITCODE -ne 0) { throw 'APK 16 KB ZIP alignment failed.' }
}
[pscustomobject]@{
    package='com.lohe.app'; targetSdk=[int]$sdk.GetAttribute('targetSdkVersion', $ns)
    versionCode=$manifest.manifest.GetAttribute('versionCode', $ns)
    versionName=$manifest.manifest.GetAttribute('versionName', $ns)
    signed=$signed; carDeclarations=$false; permissions=$permissions; nativeLibraries=$native
    apkZipAlignmentChecked=[bool]$ApkPath
} | ConvertTo-Json -Depth 5
