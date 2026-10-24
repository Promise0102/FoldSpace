[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$InputApk,
    [Parameter(Mandatory)][string]$OutputApk,
    [string]$BuildTools = "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0"
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$signingDirectory = Join-Path $projectRoot 'signing'
New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
$keystore = Join-Path $signingDirectory 'hover-space-local.p12'
$passwordFile = Join-Path $signingDirectory 'keystore-password.txt'
if ((Test-Path -LiteralPath $keystore) -ne (Test-Path -LiteralPath $passwordFile)) { throw 'Signing key/password file pair is incomplete.' }
if (!(Test-Path -LiteralPath $keystore)) {
    $password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    [IO.File]::WriteAllText($passwordFile, $password, [Text.UTF8Encoding]::new($false))
    & keytool -genkeypair -storetype PKCS12 -keystore $keystore -storepass:file $passwordFile -keypass:file $passwordFile -alias hover-space-local -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10000 -dname 'CN=MKF Hover Space Local Test,O=Local Test,C=CN'
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
}
if (Test-Path -LiteralPath $OutputApk) { throw 'Signed output already exists.' }
$alignedApk = $InputApk + '.aligned.apk'
& (Join-Path $BuildTools 'zipalign.exe') -P 16 -f 4 $InputApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed.' }
& (Join-Path $BuildTools 'apksigner.bat') sign --ks $keystore --ks-key-alias hover-space-local --ks-pass ('file:' + $passwordFile) --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out $OutputApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
$verification = & (Join-Path $BuildTools 'apksigner.bat') verify --verbose --print-certs $OutputApk
if ($LASTEXITCODE -ne 0) { throw 'Signature verification failed.' }
$verification | Set-Content -LiteralPath ($OutputApk + '.signature.txt') -Encoding utf8
& (Join-Path $BuildTools 'zipalign.exe') -c -P 16 4 $OutputApk
if ($LASTEXITCODE -ne 0) { throw 'Signed APK alignment verification failed.' }
Write-Output $verification
Write-Output ([pscustomobject]@{path=[IO.Path]::GetFullPath($OutputApk);sha256=(Get-FileHash -LiteralPath $OutputApk -Algorithm SHA256).Hash})
