param([string]$Serial='TABLET-SERI-NO')
$ErrorActionPreference='Stop'
$adb='C:/Users/Muhammed/AppData/Local/Ajan-Araclari/android/sdk/platform-tools/adb.exe'
function Adb([string[]]$Arguments){
    $result=& $adb -s $Serial @Arguments 2>&1
    if($LASTEXITCODE -ne 0){throw ($result -join "`n")}
    return $result
}
Adb @('install','-r',"$PSScriptRoot/app/build/outputs/apk/debug/app-debug.apk") | Out-Null
Adb @('install','-r',"$PSScriptRoot/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk") | Out-Null
foreach($phase in @('seed','verify')) {
    $result=Adb @('shell','am','instrument','-w','-r','-e','class','com.gvn.notes.LibraryDeviceTest','-e','phase',$phase,
        'com.gvn.notes.prototype.test/androidx.test.runner.AndroidJUnitRunner')
    $result | Out-File -Encoding UTF8 "$PSScriptRoot/logs/library-$phase.txt"
    if(($result -join "`n") -notmatch 'OK \(1 test\)'){throw "Library test failed: $phase"}
    Write-Output "library-$phase passed"
    Adb @('shell','am','force-stop','com.gvn.notes.prototype') | Out-Null
}
Adb @('uninstall','com.gvn.notes.prototype.test') | Out-Null
Adb @('shell','am','start','-W','-n','com.gvn.notes.prototype/com.gvn.notes.MainActivity')
