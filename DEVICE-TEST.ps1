param([string]$Serial='TABLET-SERI-NO',[switch]$Hepsi,[string[]]$Sadece,[switch]$Durum)
# Tablet testleri. Her test biriminin ilgili kaynak dosyalari vardir; o dosyalar son basarili calistirmadan beri
# degismediyse birim atlanir (docs/TEST-DURUMU.md'de tikli). -Hepsi her seyi calistirir; -Sadece ad1,ad2 yalniz onlari.
$ErrorActionPreference='Stop'
$adb='C:/Users/Muhammed/AppData/Local/Ajan-Araclari/android/sdk/platform-tools/adb.exe'
$root=$PSScriptRoot
$ledgerJson="$root/docs/test-durumu.json"
$ledgerMd="$root/docs/TEST-DURUMU.md"
function Adb([string[]]$Arguments){
    $result=& $adb -s $Serial @Arguments 2>&1
    if($LASTEXITCODE -ne 0){throw ($result -join "`n")}
    return $result
}
$K='app/src/main/kotlin/com/gvn/notes/'
$T='app/src/androidTest/kotlin/com/gvn/notes/'
# Her birimde ortak: manifest, gradle (surum satirlari haric), core modeli ve test yardimcisi.
$common=@('app/src/main/AndroidManifest.xml','app/build.gradle.kts','core/src/main/kotlin/com/gvn/notes/core/Model.kt',"${T}TestPen.kt", "${K}WeakIdentityCache.kt","${K}SampleHandle.kt","${K}PageHistory.kt","${K}PageInkDatabase.kt","${K}LegacyPageStore.kt")
$units=[ordered]@{
  'selection'=@{ tests=,@('SelectionDeviceTest',''); files=@("${K}LibraryScreen.kt","${K}NoteLibrary.kt","${K}FolderSync.kt","${K}MainActivity.kt","${K}Theme.kt","${T}SelectionDeviceTest.kt") }
  'zoom-idle'=@{ tests=,@('ZoomIdleDeviceTest',''); files=@("${K}InkPageView.kt","${K}InkTileCache.kt","${K}PdfPages.kt","${K}EditorState.kt","${K}SampleHandle.kt","${T}ZoomIdleDeviceTest.kt") }
  'viewport'=@{ tests=,@('ViewportDeviceTest',''); files=@("${K}InkPageView.kt","${K}InkTileCache.kt","${K}EditorState.kt","${K}PdfPages.kt","${K}NoteLibrary.kt","${T}ViewportDeviceTest.kt") }
  'ink-cache'=@{ tests=,@('InkCacheDeviceTest',''); files=@("${K}InkTileCache.kt","${K}ActiveInkCache.kt","${K}StrokeBuild.kt","${K}EditorState.kt",'app/src/main/java/com/gvn/notes/pen/PenRenderer.java',"${T}InkCacheDeviceTest.kt") }
  'ink-performance'=@{ tests=,@('InkPerformanceDeviceTest',''); files=@("${K}InkTileCache.kt","${K}ActiveInkCache.kt","${K}InkPageView.kt","${K}InkTileCache.kt","${K}ActiveInkCache.kt","${K}PageStore.kt","${K}EditorState.kt","${K}StrokeBuild.kt",'core/src/main/kotlin/com/gvn/notes/core/SaveEnvelope.kt',"${T}InkPerformanceDeviceTest.kt") }
  'persistence-codec-device'=@{ tests=,@('PageStoreDeviceTest',''); files=@("${K}PageStore.kt",'core/src/main/kotlin/com/gvn/notes/core/SaveEnvelope.kt',"${T}PageStoreDeviceTest.kt") }
  'persistence-ink-regression'=@{ tests=,@('InkPageRegressionTest',''); files=@("${K}InkPageView.kt","${K}InkTileCache.kt","${K}ActiveInkCache.kt","${K}EditorState.kt","${K}StrokeBuild.kt","${K}PageStore.kt",'app/src/main/java/com/gvn/notes/pen/',"${T}InkPageRegressionTest.kt") }
  'vector-export'=@{ tests=,@('VectorExportDeviceTest',''); files=@("${K}Export.kt","${K}PagePattern.kt","${K}StrokeBuild.kt",'app/src/main/java/com/gvn/notes/pen/PenRenderer.java',"${K}PdfPages.kt","${T}VectorExportDeviceTest.kt",'app/libs/') }
  'pdf-note'=@{ tests=,@('PdfNoteDeviceTest',''); files=@("${K}PdfPages.kt","${K}NoteLibrary.kt","${K}PageStore.kt","${K}EditorState.kt","${K}InkPageView.kt","${K}InkTileCache.kt","${K}ActiveInkCache.kt","${K}MainActivity.kt","${K}LibraryScreen.kt","${K}Export.kt","${T}PdfNoteDeviceTest.kt") }
  'folder-sync'=@{ tests=,@('FolderSyncDeviceTest',''); files=@("${K}FolderSync.kt","${K}NoteLibrary.kt","${K}LibraryScreen.kt","${K}MainActivity.kt","${K}PdfPages.kt","${T}FolderSyncDeviceTest.kt") }
  'note-management'=@{ tests=,@('NoteManagementDeviceTest',''); files=@("${K}NoteLibrary.kt","${K}LibraryScreen.kt","${K}MainActivity.kt","${K}EditorState.kt","${K}Export.kt","${K}FolderSync.kt","${K}PageStore.kt","${T}NoteManagementDeviceTest.kt") }
  'two-way'=@{ tests=,@('TwoWayDeviceTest',''); files=@("${K}FolderSync.kt","${K}NoteLibrary.kt","${K}ImageImport.kt","${K}EditorState.kt","${K}PageStore.kt","${T}TwoWayDeviceTest.kt") }
  'pen-tools'=@{ tests=,@('PenToolsDeviceTest',''); files=@('app/src/main/java/com/gvn/notes/pen/',"${K}InkPageView.kt","${K}InkTileCache.kt","${K}ActiveInkCache.kt","${K}StrokeBuild.kt","${K}EditorState.kt",'core/src/main/kotlin/com/gvn/notes/core/PenGeometry.kt',"${T}PenToolsDeviceTest.kt") }
  'lasso'=@{ tests=,@('LassoDeviceTest',''); files=@("${K}InkPageView.kt","${K}InkTileCache.kt","${K}ActiveInkCache.kt","${K}EditorState.kt","${K}StrokeBuild.kt","${K}MainActivity.kt",'core/src/main/kotlin/com/gvn/notes/core/PenGeometry.kt',"${T}LassoDeviceTest.kt") }
  'folder-delete'=@{ tests=,@('FolderDeleteDeviceTest',''); files=@("${K}FolderSync.kt","${K}NoteLibrary.kt","${K}PageStore.kt","${T}FolderDeleteDeviceTest.kt") }
  'cursor-size'=@{ tests=,@('CursorSizeDeviceTest',''); files=@("${K}InkPageView.kt","${K}InkTileCache.kt","${K}ActiveInkCache.kt",'app/src/main/res/drawable/pencil_cursor.png',"${T}CursorSizeDeviceTest.kt") }
  'templates'=@{ tests=,@('TemplateDeviceTest',''); files=@("${K}PagePattern.kt","${K}NoteLibrary.kt","${K}EditorState.kt","${K}InkPageView.kt","${K}InkTileCache.kt","${K}ActiveInkCache.kt","${K}PageStore.kt","${T}TemplateDeviceTest.kt") }
  'finger-draw'=@{ tests=,@('FingerDrawDeviceTest',''); files=@("${K}InkPageView.kt","${K}InkTileCache.kt","${K}ActiveInkCache.kt","${K}AppSettings.kt",'app/src/main/java/com/gvn/notes/pen/Prefs.java',"${T}FingerDrawDeviceTest.kt") }
  'persistence-restart'=@{ tests=@(@('PersistenceRestartTest','seed'),@('PersistenceRestartTest','verify'),@('PersistenceRestartTest','deleted')); files=@("${K}PageStore.kt","${K}EditorState.kt","${K}InkPageView.kt","${K}InkTileCache.kt","${K}ActiveInkCache.kt","${K}MainActivity.kt",'core/src/main/kotlin/com/gvn/notes/core/SaveEnvelope.kt',"${T}PersistenceRestartTest.kt") }
  'library'=@{ tests=@(@('LibraryDeviceTest','seed'),@('LibraryDeviceTest','verify')); files=@("${K}LibraryScreen.kt","${K}MainActivity.kt","${K}NoteLibrary.kt","${K}PageStore.kt","${K}EditorState.kt","${K}Theme.kt","${T}LibraryDeviceTest.kt") }
}
function FilesOf([string[]]$paths){
    $list=@()
    foreach($p in $paths){
        $full=Join-Path $root $p
        if(Test-Path -LiteralPath $full -PathType Container){$list+=Get-ChildItem -LiteralPath $full -Recurse -File | ForEach-Object { $_.FullName }}
        elseif(Test-Path -LiteralPath $full){$list+=(Resolve-Path -LiteralPath $full).Path}
        else{$list+="YOK:$p"}
    }
    return $list | Sort-Object
}
function Fingerprint($unit){
    $sha=[System.Security.Cryptography.SHA256]::Create();$sb=New-Object System.Text.StringBuilder
    foreach($f in (FilesOf ($common+$unit.files))){
        if($f.StartsWith('YOK:')){[void]$sb.Append($f+"`n");continue}
        $bytes=[System.IO.File]::ReadAllBytes($f)
        if($f.EndsWith('build.gradle.kts')){ # Surum numarasi degisikligi testleri yeniden calistirmasin.
            $text=[System.Text.Encoding]::UTF8.GetString($bytes) -split "`n" | Where-Object { $_ -notmatch 'versionCode|versionName' }
            $bytes=[System.Text.Encoding]::UTF8.GetBytes(($text -join "`n"))}
        $rel=$f.Substring($root.Length).Replace('\','/')
        [void]$sb.Append($rel+':'+[BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-','')+"`n")
    }
    return [BitConverter]::ToString($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($sb.ToString()))).Replace('-','').Substring(0,16).ToLower()
}
$ledger=@{}
if(Test-Path -LiteralPath $ledgerJson){(Get-Content -Raw -Encoding UTF8 -LiteralPath $ledgerJson | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $ledger[$_.Name]=$_.Value }}
$version=((Get-Content "$root/app/build.gradle.kts") -match 'versionName' | Select-Object -First 1) -replace '.*"(.*)".*','$1'
function SaveLedger{
    $ordered=[ordered]@{};foreach($n in $units.Keys){if($ledger.ContainsKey($n)){$ordered[$n]=$ledger[$n]}}
    [System.IO.File]::WriteAllText($ledgerJson,($ordered | ConvertTo-Json -Depth 4),(New-Object System.Text.UTF8Encoding $false))
    $c=[char]0x2705;$x=[char]0x274C;$w=[char]0x2B1C
    $lines=@("# Tablet test durumu","","``DEVICE-TEST.ps1`` bu tabloyu kendisi g$([char]0xFC)nceller. $c = son $([char]0xE7)al$([char]0x131)$([char]0x15F)t$([char]0x131)rmada ge$([char]0xE7)ti ve ilgili kod o zamandan beri de$([char]0x11F)i$([char]0x15F)medi (atlan$([char]0x131)r). $w = ilgili kod de$([char]0x11F)i$([char]0x15F)ti, s$([char]0x131)radaki $([char]0xE7)al$([char]0x131)$([char]0x15F)t$([char]0x131)rmada test edilecek. $x = son $([char]0xE7)al$([char]0x131)$([char]0x15F)t$([char]0x131)rmada kald$([char]0x131). Hepsini zorla: ``./DEVICE-TEST.ps1 -Hepsi``.","","| Durum | Test | Son ge$([char]0xE7)ti$([char]0x11F)i | S$([char]0xFC)r$([char]0xFC)m | Parmak izi |","|---|---|---|---|---|")
    foreach($n in $units.Keys){
        $e=$ledger[$n];$now=Fingerprint $units[$n]
        $mark=if($null -eq $e){$w}elseif($e.result -ne 'passed'){$x}elseif($e.fingerprint -eq $now){$c}else{$w}
        $date=if($e){$e.date}else{'-'};$ver=if($e){$e.version}else{'-'}
        $lines+="| $mark | $n | $date | $ver | ``$now`` |"
    }
    [System.IO.File]::WriteAllText($ledgerMd,(($lines -join "`n")+"`n"),(New-Object System.Text.UTF8Encoding $false))
}
New-Item -ItemType Directory -Force "$root/logs" | Out-Null
$todo=@()
foreach($n in $units.Keys){
    if($Sadece -and ($Sadece -notcontains $n)){continue}
    $fp=Fingerprint $units[$n];$e=$ledger[$n]
    if(-not $Hepsi -and -not $Sadece -and $e -and $e.result -eq 'passed' -and $e.fingerprint -eq $fp){Write-Output "$n atlandi (degisiklik yok, $($e.date) tarihinde gecti)";continue}
    $todo+=$n
}
if($todo.Count -eq 0){SaveLedger;Write-Output 'Calistirilacak test yok: hepsi guncel. Zorlamak icin -Hepsi.';return}
SaveLedger
if($Durum){Write-Output 'Yalniz durum tablosu guncellendi; cihaz testi calistirilmadi.';return}
Adb @('install','-r',"$root/app/build/outputs/apk/debug/app-debug.apk") | Out-Null
Adb @('install','-r',"$root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk") | Out-Null
$failed=$null
foreach($n in $todo){
    $ok=$true
    foreach($t in $units[$n].tests){
        $arguments=@('shell','am','instrument','-w','-r','-e','class',"com.gvn.notes.$($t[0])")
        if($t[1]){$arguments+=@('-e','phase',$t[1])}
        $arguments+='com.gvn.notes.prototype.test/androidx.test.runner.AndroidJUnitRunner'
        $name=if($t[1]){"$n-$($t[1])"}else{$n}
        $result=Adb $arguments
        $result | Out-File -Encoding UTF8 -LiteralPath "$root/logs/$name.txt"
        if(($result -join "`n") -notmatch 'OK \([0-9]+ tests?\)'){$ok=$false;break}
        if($t[1]){Adb @('shell','am','force-stop','com.gvn.notes.prototype') | Out-Null}
    }
    $ledger[$n]=[ordered]@{result=if($ok){'passed'}else{'failed'};fingerprint=(Fingerprint $units[$n]);date=(Get-Date -Format 'yyyy-MM-dd HH:mm');version=$version}
    SaveLedger
    if(-not $ok){$failed=$n;break}
    Write-Output "$n passed"
}
Adb @('uninstall','com.gvn.notes.prototype.test') | Out-Null
# Testler files/test-* klasorlerini kullanir. Normal uygulama kullanicinin notlarini acar.
Adb @('shell','am','start','-W','-n','com.gvn.notes.prototype/com.gvn.notes.MainActivity') | Out-Null
if($failed){throw "Cihaz testi basarisiz: $failed. logs/$failed*.txt dosyasina bakin."}
