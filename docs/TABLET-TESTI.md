# Tablet test oturumu planı — 0.9.0

0.6.0 ile 0.9.0 arası tablet bağlı değilken kodlandı. Derleme, JVM birim testleri ve lint geçti; **cihaz testleri henüz çalıştırılmadı.** Bu plan, oturumda birlikte yapılacakları sırayla verir.

## 0. Hazırlık (5 dk)

1. Tableti USB ile bağla; `adb devices` içinde `TABLET-SERI-NO` görünmeli.
2. Gerçek notların yedeği (güvenlik için, yalnız okuma):
   `adb shell run-as com.gvn.notes.prototype tar -cf /sdcard/Download/gvnnotes-yedek.tar files/notebooks files/trash files/sync-index.json`
3. Paket kimliği değişmedi (`com.gvn.notes.prototype`); kurulum mevcut notların üzerine güncelleme olarak yapılır.

## 1. Otomatik cihaz testleri (~10 dk)

```
./build.ps1 -Offline
gradle :app:assembleDebugAndroidTest   (build.ps1 sonrası; komut DOGRULAMA'daki ile aynı)
./DEVICE-TEST.ps1
./LIBRARY-TEST.ps1
```

DEVICE-TEST.ps1 sırası: PageStore, Ink regresyonu, PDF, klasör eşitleme, not yönetimi, iki yönlü işlemler, **kalem araçları (0.6)**, **kement (0.7)**, **şablonlar (0.8)**, **parmakla çizim (0.9)**, kalıcılık (3 aşama). Testler yalnız `test-document`, `test-library` ve `test-*-src` klasörlerini kullanır; gerçek notlara dokunmaz.

Başarısız olan test için: `logs/<test>.txt` incelenir, kök neden bulunur, düzeltme + aynı testin yeniden çalıştırılması.

## 2. Elle deneme (S Pen ile, ~20 dk)

| # | Ne | Beklenen |
|---|----|----------|
| 1 | Uygulama adı ve simgesi | "GVNnotes", petrol mavisi sayfa+kalem simgesi |
| 2 | Not listesi açılışı (çok sayfalı PDF notları dahil) | Hızlı açılır; ikinci açılış daha hızlı (özet önbelleği) |
| 3 | Ayarlar → Kaynak klasör → **Yeniden bağla** | Yazma izni alınır, kart kaybolur |
| 4 | Klasöre taşı / yeni klasör / çöpe at / geri yükle | `Dosyalarım`da da aynı değişiklik; `.GVNnotes-çöp` |
| 5 | Hızlı el yazısı, zoom/kaydırma arası yazma | "Çiziliyor"da takılma yok |
| 6 | S Pen yan düğmesiyle silme | Basılıyken siler, bırakınca kalem |
| 7 | Alan silgisi (silgiye tekrar dokun → kapat) | Yalnız süpürülen kısım silinir |
| 8 | Akıllı şekil: kare/daire/çizgi çiz, uçta beklet | Düzgün şekle döner; bekletmezsen dönmez |
| 9 | Kement: seç, sürükle, rengi uygula, çoğalt, sil, geri al | Hepsi tek adımda geri alınır |
| 10 | Yeni not → Çizgili/Kareli/Noktalı; + ile sayfa ekle | Şablon devralınır; sıralayıcıdan değiştirilebilir |
| 11 | PDF olarak dışa aktar → Dosyalarım'da aç | Zemin + yazılar + şablon görünür |
| 12 | Ayarlar → Parmakla çizim aç/kapat | Açıkken parmak yazar, iki parmak kaydırır |
| 13 | Avucu ekrana koyarak yazma | Avuç kaydırma/yazı üretmez |
| 14 | Notu kapatıp açma | Son bırakılan sayfadan açılır |
| 15 | Ayarlar → Hata kaydı | Çökme yoksa "Kaydedilmiş çökme yok" |

## 3. Bilinen riskler (oturumda özellikle bak)

- **Akıllı şekil eşiği** (uçta 0,55 sn, ~1,5 mm): gerçek S Pen'de çok hassas/çok zor tetikleniyorsa `InkPageView.SHAPE_HOLD_MS/RADIUS` ayarlanır.
- **Avuç eşiği** (90 dp temas): parmakla çizim açıkken avuç yazı üretiyorsa ayarlanır.
- **Alan silgisi hızı:** çok yoğun sayfada silgi gecikirse ölçülür (sınır kutusu ön elemesi var).
- **Ink teslimi:** nadiren tamamlanmayan çizgi örneklerden yeniden kurulur; logcat'te `GVNnotes` etiketi sayılır.
- **Kayıt biçimi 3:** 0.8.0'dan sonra notlar biçim 3 yazılır; daha eski APK'ya dönülürse bu notlar açılmaz (veri silinmez).
