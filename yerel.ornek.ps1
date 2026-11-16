# Yerel ayarlar sablonu. Bu dosyayi yerel.ps1 adiyla kopyalayip kendi yollarinizi yazin; yerel.ps1 git'e girmez.
# Dosya UTF-8 BOM'lu kalmali (PowerShell 5.1 Turkce karakterli yollari BOM'suz dosyada bozar).
$env:JAVA_HOME='C:/yol/jdk-21'
$env:ANDROID_HOME='C:/yol/android/sdk'
# Istege bagli: yerel Gradle 8.11.1. Bos birakilirsa proje wrapper'i (gradlew.bat) kullanilir.
$GvnGradle=''
# Tablet testleri icin (adb devices ciktisindaki seri numarasi).
$GvnSerial=''
# Istege bagli: adb yolu. Bos birakilirsa ANDROID_HOME/platform-tools/adb.exe kullanilir.
$GvnAdb=''
