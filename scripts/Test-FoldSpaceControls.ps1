$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Resolve-AndroidTools.ps1')
$projectRoot=Split-Path -Parent $PSScriptRoot
$javaBin=Resolve-FoldSpaceJavaBin
$testStage=Join-Path $env:TEMP ('foldspace-controls-test-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testStage | Out-Null
$files=@('foldspace\src\com\mkf\foldspace\FoldPolicy.java','foldspace\src\com\mkf\foldspace\ControlGeometry.java','foldspace\src\com\mkf\foldspace\PlaybackContinuity.java','foldspace\tests\FoldPolicyTest.java','foldspace\tests\ControlGeometryTest.java','foldspace\tests\PlaybackContinuityTest.java') | ForEach-Object { Join-Path $projectRoot $_ }
& "$javaBin\javac.exe" -encoding UTF-8 -d $testStage $files
if($LASTEXITCODE){throw 'Test compilation failed'}
foreach($test in @('com.mkf.foldspace.FoldPolicyTest','com.mkf.foldspace.ControlGeometryTest','com.mkf.foldspace.PlaybackContinuityTest')){
    & "$javaBin\java.exe" -cp $testStage $test
    if($LASTEXITCODE){throw "Test failed: $test"}
}
