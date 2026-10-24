function Resolve-FoldSpaceJavaBin {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
        return (Join-Path $env:JAVA_HOME 'bin')
    }
    $compiler = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($compiler) { return (Split-Path -Parent $compiler.Source) }
    throw 'JDK not found. Set JAVA_HOME to a JDK 17+ installation or add its bin directory to PATH.'
}

function Resolve-FoldSpaceAndroidSdk {
    foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android/Sdk'))) {
        if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'build-tools'))) { return $candidate }
    }
    throw 'Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT.'
}

function Resolve-FoldSpaceAndroidJar([string]$SdkPath, [string]$Platform) {
    $platformNames = if ($Platform) { @($Platform) } else { @('android-37', 'android-37.0') }
    foreach ($name in $platformNames) {
        $jar = Join-Path $SdkPath ('platforms/' + $name + '/android.jar')
        if (Test-Path -LiteralPath $jar) { return $jar }
    }
    throw 'Android API 37 platform not found. Install it or pass -AndroidPlatform with an installed compatible platform directory name.'
}
