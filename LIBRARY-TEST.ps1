param([string]$Serial,[switch]$Hepsi)
# Kutuphane testleri artik DEVICE-TEST.ps1 icinde 'library' birimi; bu betik yalniz onu calistirir.
& "$PSScriptRoot/DEVICE-TEST.ps1" -Serial $Serial -Sadece library
