# Tablet test oturumu — 0.12.0

Son tam çalıştırma: 12 Kasım 2026, SM-X810 — 13 test biriminin hepsi geçti. Güncel durum: [TEST-DURUMU.md](TEST-DURUMU.md).

**Atlama kuralı (kullanıcı isteği):** bir test birimi daha önce geçtiyse ve ilgili kaynak dosyaları o zamandan beri değişmediyse yeniden çalıştırılmaz; tabloda ✅ kalır. İlgili dosyalar değişince ⬜ olur ve sıradaki çalıştırmada test edilir. `-Hepsi` hepsini zorlar.

## 0. Hazırlık

1. Tableti USB ile bağla; `adb devices` içinde `TABLET-SERI-NO` görünmeli. adb: `C:/Users/Muhammed/AppData/Local/Ajan-Araclari/android/sdk/platform-tools/adb.exe`.
2. İsteğe bağlı gerçek not yedeği (yalnız okuma):
   `adb shell run-as com.gvn.notes.prototype tar -cf /sdcard/Download/gvnnotes-yedek.tar files/notebooks files/trash files/sync-index.json`
3. Paket kimliği `com.gvn.notes.prototype`; kurulum mevcut notların üzerine güncellemedir.

## 1. Otomatik testler

```powershell
./build.ps1 -Offline
# Test APK'sı (build.ps1'deki JAVA_HOME, ANDROID_HOME ve gradle.bat yoluyla):
gradle -p . :app:assembleDebugAndroidTest --offline --console plain --no-daemon
./DEVICE-TEST.ps1                 # yalnız değişenler
./DEVICE-TEST.ps1 -Hepsi          # hepsi
./DEVICE-TEST.ps1 -Sadece lasso   # tek birim
```

Birimler: persistence-codec-device, persistence-ink-regression, pdf-note, folder-sync, note-management, two-way, pen-tools, lasso, folder-delete, templates, finger-draw, persistence-restart (seed / verify / deleted), library (seed / verify). `LIBRARY-TEST.ps1` yalnız `library` birimini çalıştırır.

Testler yalnız `test-document`, `test-library` ve test kaynak klasörlerini kullanır; gerçek notlara ve kaydedilmiş GVNpen ayarlarına dokunmaz. Başarısız aşamada `logs/<aşama>.txt` incelenir, kök neden düzeltilir, aynı aşama yeniden çalıştırılır.

## 2. Görsel kontrol

Arayüz değiştiyse ekran görüntüsü al ve bak:

```bash
adb exec-out screencap -p > logs/ui-<sürüm>/01-kutuphane.png   # bash/Git Bash; PowerShell 5.1 '>' PNG'yi bozar
```

Kontrol listesi: kütüphane (klasör ağacı, kartlar), düzenleyici (konum çipi, sağ üst ada, alt orta ada, GVNpen şeridiyle çakışma yok), Klasör adası, sayfa sıralayıcı adası, ayarlar, yeni not penceresi, GVNpen menüsü ve ayar ekranı. 0.11.0 görüntüleri: `logs/ui-0.11.0/`.

## 3. Elle deneme (S Pen ile, kullanıcıyla)

| # | Ne | Beklenen |
|---|----|----------|
| 1 | Hızlı el yazısı, zoom/kaydırma arası yazma | Takılma yok; GVNpen'deki his |
| 2 | Kalem yaklaşınca kalınlık/şekil menüsü | 350 ms'de açılır, uzaklaşınca kapanır |
| 3 | S Pen yan düğmesiyle silme | Basılıyken siler, bırakınca kalem |
| 4 | Akıllı şekil: kare/daire/çizgi çiz, uçta beklet | Düzgün şekle döner |
| 5 | 2B/3B şekiller | Sürükleyerek çizilir |
| 6 | Kement: seç, sürükle, rengi uygula, çoğalt, sil, geri al | Hepsi tek adımda geri alınır |
| 7 | Konum çipi → başka nota geç | Önceki not kaydedilir, yeni not açılır |
| 8 | Klasöre git / Bu klasörde yeni | Kütüphanede doğru klasör; yeni not o klasörde |
| 9 | Klasöre taşı / yeni klasör / çöpe at / geri yükle | `Dosyalarım`da da aynı değişiklik; `.GVNnotes-çöp` |
| 10 | Yeni not → şablonlar; + ile sayfa ekle | Şablon devralınır; sıralayıcıdan değiştirilebilir |
| 11 | PDF olarak dışa aktar → Dosyalarım'da aç | Zemin + yazılar + şablon görünür |
| 12 | Ayarlar → Parmakla çizim aç/kapat | Açıkken parmak yazar, iki parmak kaydırır |
| 13 | GVNpen Görünüm → Açık tema kapat | Şerit koyu temaya geçer, okunur kalır |
| 14 | Notu kapatıp açma | Son bırakılan sayfadan açılır |
| 15 | Klasör kartı ⋯ → Klasörü sil | Alt klasörleriyle gider; notlar çöpte, dosyalar `.GVNnotes-çöp`'te |
| 16 | Dosyalarım'dan bir PDF sil, uygulamaya dön | Notu çöp kutusunda; PDF'i geri koyunca not geri gelir |
| 17 | Çöp kutusu | Her notta "N gün sonra kalıcı silinecek" |
