param([switch]$Offline)
$ErrorActionPreference='Stop'
$env:JAVA_HOME='C:/Users/Muhammed/AppData/Local/Ajan-Araclari/java/jdk-21.0.13+9'
$env:ANDROID_HOME='C:/Users/Muhammed/AppData/Local/Ajan-Araclari/android/sdk'
$gradle='D:/Sanal Desktop/yerel-araclar/araclar/gradle-8.11.1/bin/gradle.bat'
if(!(Test-Path -LiteralPath $gradle)){$gradle=Join-Path $PSScriptRoot 'gradlew.bat'}
if(!(Test-Path -LiteralPath $gradle)){throw 'Yerel Gradle veya proje wrapper bulunamadi.'}
New-Item -ItemType Directory -Force "$PSScriptRoot/logs","$PSScriptRoot/dist" | Out-Null
$arguments=@('-p',$PSScriptRoot,':core:test',':app:testDebugUnitTest',':app:assembleDebug',':app:lintDebug','--console','plain','--no-daemon')
if($Offline){$arguments+='--offline'}
$ErrorActionPreference='Continue'
& $gradle @arguments *> "$PSScriptRoot/logs/build.log"
$buildExit=$LASTEXITCODE
$ErrorActionPreference='Stop'
if($buildExit -ne 0){Get-Content "$PSScriptRoot/logs/build.log" -Tail 65; throw 'Derleme/test basarisiz. logs/build.log dosyasina bakin.'}
$meta=Get-Content -Raw -LiteralPath "$PSScriptRoot/app/build/outputs/apk/debug/output-metadata.json" | ConvertFrom-Json
$apk=Join-Path "$PSScriptRoot/dist" "GVNnotes-$($meta.elements[0].versionName).apk"
Copy-Item -LiteralPath "$PSScriptRoot/app/build/outputs/apk/debug/app-debug.apk" -Destination $apk -Force
Get-FileHash -LiteralPath $apk | Format-List




