param([string]$Serial='TABLET-SERI-NO')
$ErrorActionPreference='Stop'
$adb='C:/Users/Muhammed/AppData/Local/Ajan-Araclari/android/sdk/platform-tools/adb.exe'
$root=$PSScriptRoot
function Adb([string[]]$Arguments){
    $result=& $adb -s $Serial @Arguments 2>&1
    if($LASTEXITCODE -ne 0){throw ($result -join "`n")}
    return $result
}
function TestPhase([string]$Name,[string]$Class,[string]$Phase=''){
    $arguments=@('shell','am','instrument','-w','-r','-e','class',$Class)
    if($Phase){$arguments+=@('-e','phase',$Phase)}
    $arguments+='com.gvn.notes.prototype.test/androidx.test.runner.AndroidJUnitRunner'
    $result=Adb $arguments
    $result | Out-File -Encoding UTF8 -LiteralPath "$root/logs/$Name.txt"
    if(($result -join "`n") -notmatch 'OK \([0-9]+ tests?\)'){throw "Cihaz testi basarisiz: $Name. Logu inceleyin."}
    Write-Output "$Name passed"
}
New-Item -ItemType Directory -Force "$root/logs" | Out-Null
Adb @('install','-r',"$root/app/build/outputs/apk/debug/app-debug.apk") | Out-Null
Adb @('install','-r',"$root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk") | Out-Null
TestPhase 'persistence-codec-device' 'com.gvn.notes.PageStoreDeviceTest'
TestPhase 'persistence-ink-regression' 'com.gvn.notes.InkPageRegressionTest'
TestPhase 'pdf-note' 'com.gvn.notes.PdfNoteDeviceTest'
TestPhase 'folder-sync' 'com.gvn.notes.FolderSyncDeviceTest'
TestPhase 'note-management' 'com.gvn.notes.NoteManagementDeviceTest'
TestPhase 'two-way' 'com.gvn.notes.TwoWayDeviceTest'
TestPhase 'pen-tools' 'com.gvn.notes.PenToolsDeviceTest'
TestPhase 'lasso' 'com.gvn.notes.LassoDeviceTest'
TestPhase 'templates' 'com.gvn.notes.TemplateDeviceTest'
TestPhase 'finger-draw' 'com.gvn.notes.FingerDrawDeviceTest'
TestPhase 'persistence-seed' 'com.gvn.notes.PersistenceRestartTest' 'seed'
Adb @('shell','am','force-stop','com.gvn.notes.prototype') | Out-Null
TestPhase 'persistence-reopen' 'com.gvn.notes.PersistenceRestartTest' 'verify'
Adb @('shell','am','force-stop','com.gvn.notes.prototype') | Out-Null
TestPhase 'persistence-deletion-reopen' 'com.gvn.notes.PersistenceRestartTest' 'deleted'
Adb @('uninstall','com.gvn.notes.prototype.test') | Out-Null
# Testler files/test-document kullanir. Normal uygulama kullanicinin notebooks/main klasorunu acar.
Adb @('shell','am','start','-W','-n','com.gvn.notes.prototype/com.gvn.notes.MainActivity')
