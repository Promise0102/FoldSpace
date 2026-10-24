[CmdletBinding()]
param(
    [string]$Version = '0.3.1',
    [string]$BuildToolsVersion = '36.0.0',
    [string]$AndroidPlatform,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Resolve-AndroidTools.ps1')
$projectRoot = Split-Path -Parent $PSScriptRoot
[xml]$sourceManifest = Get-Content -LiteralPath (Join-Path $projectRoot 'foldspace\AndroidManifest.xml') -Raw
$manifestVersion = $sourceManifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
if ($Version -ne $manifestVersion) { throw 'Requested output version does not match the source manifest.' }
$dependencyRecords = Get-Content -LiteralPath (Join-Path $projectRoot 'foldspace\libs\dependencies.json') -Raw | ConvertFrom-Json
foreach ($dependency in $dependencyRecords) {
    $actual = (Get-FileHash -LiteralPath (Join-Path $projectRoot ('foldspace\libs\' + $dependency.file)) -Algorithm SHA256).Hash
    if ($actual -ne $dependency.sha256) { throw ('Dependency hash mismatch: ' + $dependency.file) }
}
$sdkPath = Resolve-FoldSpaceAndroidSdk
$buildTools = Join-Path $sdkPath ('build-tools/' + $BuildToolsVersion)
if (!(Test-Path -LiteralPath (Join-Path $buildTools 'aapt2.exe'))) { throw 'Requested Android Build Tools version not found.' }
$androidJar = Resolve-FoldSpaceAndroidJar $sdkPath $AndroidPlatform
$javaBin = Resolve-FoldSpaceJavaBin
$env:JAVA_HOME = Split-Path -Parent $javaBin
$env:PATH = $javaBin + ';' + $env:PATH
if (!$OutputDirectory) { $OutputDirectory = Join-Path $projectRoot 'output' }
elseif (![IO.Path]::IsPathRooted($OutputDirectory)) { $OutputDirectory = Join-Path $projectRoot $OutputDirectory }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$outputPath = Join-Path $OutputDirectory ('foldspace-' + $Version + '-signed.apk')
if (Test-Path -LiteralPath $outputPath) { throw 'Signed output already exists.' }
$stagePath = Join-Path $env:TEMP ('foldspace-build-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stagePath | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'foldspace\res') -Destination $stagePath -Recurse
Copy-Item -LiteralPath (Join-Path $projectRoot 'foldspace\src') -Destination $stagePath -Recurse
Copy-Item -LiteralPath (Join-Path $projectRoot 'foldspace\AndroidManifest.xml') -Destination $stagePath
New-Item -ItemType Directory -Path (Join-Path $stagePath 'libs'),(Join-Path $stagePath 'assets') | Out-Null
Get-ChildItem -LiteralPath (Join-Path $projectRoot 'foldspace\libs') -Filter *.jar | Copy-Item -Destination (Join-Path $stagePath 'libs')
Get-ChildItem -LiteralPath (Join-Path $projectRoot 'foldspace\libs') -Filter *.txt | Copy-Item -Destination (Join-Path $stagePath 'assets')
Push-Location $stagePath
try {
    & "$buildTools\aapt2.exe" compile --dir res -o compiled.zip
    if ($LASTEXITCODE) { throw 'Resource compilation failed' }
    & "$buildTools\aapt2.exe" link -I $androidJar --manifest AndroidManifest.xml --java gen -A assets --min-sdk-version 30 --target-sdk-version 35 -o resources.apk compiled.zip
    if ($LASTEXITCODE) { throw 'Resource link failed' }
    New-Item -ItemType Directory -Path classes,dex | Out-Null
    $sourceFiles = @(Get-ChildItem -Path src,gen -Filter *.java -Recurse | ForEach-Object { $_.FullName })
    $dependencies = @(Get-ChildItem -LiteralPath (Join-Path $stagePath 'libs') -Filter *.jar | ForEach-Object { $_.FullName })
    $compilePath = (@($androidJar) + $dependencies) -join ';'
    & "$javaBin\javac.exe" -encoding UTF-8 -source 8 -target 8 -Xlint:-options -classpath $compilePath -d classes $sourceFiles
    if ($LASTEXITCODE) { throw 'Java compilation failed' }
    & "$javaBin\jar.exe" cf classes.jar -C classes .
    if ($LASTEXITCODE) { throw 'Class archive failed' }
    & "$buildTools\d8.bat" classes.jar $dependencies --lib $androidJar --min-api 30 --output dex
    if ($LASTEXITCODE) { throw 'Dex compilation failed' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::Open((Join-Path $stagePath 'resources.apk'),[IO.Compression.ZipArchiveMode]::Update)
    try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $stagePath 'dex\classes.dex'),'classes.dex') | Out-Null } finally { $zip.Dispose() }
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    & (Join-Path $PSScriptRoot 'Sign-Apk.ps1') -InputApk (Join-Path $stagePath 'resources.apk') -OutputApk $outputPath -BuildTools $buildTools
    if (!(Test-Path -LiteralPath $outputPath)) { throw 'Signed APK missing' }
    [ordered]@{
        package='com.mkf.foldspace'; version=$manifestVersion
        versionCode=$sourceManifest.manifest.GetAttribute('versionCode','http://schemas.android.com/apk/res/android')
        sha256=(Get-FileHash -LiteralPath $outputPath -Algorithm SHA256).Hash
        source='foldspace native project'; requires='User-authorized running Shizuku for upper-screen app hosting'
        deviceValidation='Not performed by this build script; fullscreen UI and upper-host interactions require device validation.'
        dependencies=$dependencyRecords
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath ($outputPath + '.build.json') -Encoding utf8
    Write-Output "Native project build complete: $outputPath"
    Write-Output "Build staging directory retained: $stagePath"
} finally { Pop-Location }
