$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Resolve-AndroidTools.ps1')
$projectRoot=Split-Path -Parent $PSScriptRoot
$javaBin=Resolve-FoldSpaceJavaBin
$testStage=Join-Path $env:TEMP ('foldspace-controls-test-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testStage | Out-Null
$files=@('foldspace\src\com\mkf\foldspace\FoldPolicy.java','foldspace\src\com\mkf\foldspace\ControlGeometry.java','foldspace\src\com\mkf\foldspace\PlaybackContinuity.java','foldspace\tests\FoldPolicyTest.java','foldspace\tests\ControlGeometryTest.java','foldspace\tests\PlaybackContinuityTest.java','foldspace\src\com\mkf\foldspace\WorkspacePolicy.java','foldspace\src\com\mkf\foldspace\SessionRegistry.java','foldspace\tests\WorkspacePolicyTest.java','foldspace\tests\SessionRegistryTest.java','foldspace\src\com\mkf\foldspace\OrientationSession.java','foldspace\tests\OrientationSessionTest.java','foldspace\src\com\mkf\foldspace\UpperAppSwipe.java','foldspace\tests\UpperAppSwipeTest.java','foldspace\src\com\mkf\foldspace\PhysicalOrientation.java','foldspace\tests\PhysicalOrientationTest.java','foldspace\src\com\mkf\foldspace\BackgroundReturn.java','foldspace\tests\BackgroundReturnTest.java') | ForEach-Object { Join-Path $projectRoot $_ }
& "$javaBin\javac.exe" -encoding UTF-8 -d $testStage $files
if($LASTEXITCODE){throw 'Test compilation failed'}
foreach($test in @('com.mkf.foldspace.FoldPolicyTest','com.mkf.foldspace.ControlGeometryTest','com.mkf.foldspace.PlaybackContinuityTest','com.mkf.foldspace.WorkspacePolicyTest','com.mkf.foldspace.SessionRegistryTest','com.mkf.foldspace.OrientationSessionTest','com.mkf.foldspace.UpperAppSwipeTest','com.mkf.foldspace.PhysicalOrientationTest','com.mkf.foldspace.BackgroundReturnTest')){
    & "$javaBin\java.exe" -cp $testStage $test
    if($LASTEXITCODE){throw "Test failed: $test"}
}
