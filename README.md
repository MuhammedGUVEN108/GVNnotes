# GVNnotes

0.16.2: silgi, geri al/yinele ve kement değişikliklerinde yeni mürekkep karosu hazırlanırken eski görüntü korunur; yoğun sayfanın bölgesi boş çizilmez.

0.16.1: yeni sayfanın ilk kısa çizgisinde canlı → kalıcı katman geçişindeki boş kare giderildi. Hafif mürekkep aynı karede hazırlanır; yoğun sayfalar arka planda kalır.

0.16.0: sayfa bazlı SQLite kayıt, gerektiğinde nokta yükleme, komşu sayfa hazırlığı ve arka planda mürekkep karoları. Yakınlaştırmada hazır görüntü korunur; yeni netlik arka planda tamamlanır. [Kayıt mimarisi](docs/KAYIT-MIMARISI.md).

Galaxy Tab S9+ ve S Pen için PDF, resim ve boş sayfalar üzerine el yazısıyla not uygulaması. Proje merkezi: `D:/Sanal Desktop/GVNnotes`.

Yapay zekâ ajanları önce [AGENTS.md](AGENTS.md) dosyasını okumalı.

## Mevcut durum — 0.16.2 (14 Kasım 2026)

- **0.12.0:** klasör silme (alt klasörleriyle), çöp kutusu 30 gün sonra kendiliğinden boşalır, Dosyalarım'dan silinen dosyanın notu çöpe gider (dosya geri gelirse döner). Tablet testleri yalnız ilgili kodu değişenleri çalıştırır ([durum tablosu](docs/TEST-DURUMU.md)).

- **Yazma:** GVNpen ile aynı. Yüzen, sürüklenebilir araç şeridi: kalem, fosforlu, silgi (tüm çizgi / alan), 2B ve 3B şekiller, akıllı şekil, kalınlık, geri al / yinele, sayfayı temizle, hızlı renkler, renk paleti, gezinme ve okuma modu. S Pen yan düğmesi, kalem yaklaşınca açılan menüler, kalem imleci. GVNpen ayar ekranı.
- **Arayüz: "Petrol Ada".** Kütüphanede solda kalıcı, yüzen klasör ağacı; önizlemeli not kartları; klasör kısayolları. Düzenleyicide başlıkta konum çipi → Klasör adası (aynı klasördeki notlar, klasöre git, bu klasörde yeni not); sağda kapatılabilir sayfa sıralayıcı; üst satırda sayfa/yakınlaştırma ve araçlar; PDF dışa aktarma üst menüde. GVNpen şeridi de aynı renklerde.
- **Notlar:** PDF, resim (çoklu resim tek not) ve boş sayfa (Düz, Çizgili, Kareli, Noktalı); çok sayfa; kement seçimi; otomatik ve doğrulanmış kayıt; çöp kutusu; PDF olarak dışa aktarma.
- **Klasör eşitleme:** `Documents/GVNnotes` alt klasörleriyle otomatik not olur; taşıma, yeni klasör ve çöp iki yönlü.

**0.14.0:** varsayılan sık liste ve kalıcı kart/liste seçimi; geniş ekranda tekrar eden klasör kartları kaldırıldı, dar ekranda kompakt klasör satırları. Sayfa sıralayıcı belgeyi örtmeyen ayrı yan panel; sayfa/yakınlaştırma bilgisi ve araçlar mevcut üst satırda; alt çubuk ve sürekli kayıt yazıları kaldırıldı. PDF çıktısı özgün PDF sayfalarını korur ve yazıları/şablonları vektör olarak ekler; tam sayfa rasterleştirme kaldırıldı.

Doğrulama: çevrimdışı derleme, 20 JVM testi, lint (0 hata), 17 tablet test birimi geçti. Tablette kurulu; alt çubuksuz üst satır, ayrı sayfa paneli ve sık liste gerçek ekran görüntüleriyle kontrol edildi. Cihazdan alınan PDF çıktısında metin/sayfa sırası ve vektör çizimler PyMuPDF ile de doğrulandı. Ayrıntı: [doğrulama günlüğü](docs/DOGRULAMA.md).

## Sonraki adım

Kullanıcının sadeleştirilmiş kütüphane ve üst satır düzenini günlük ders kullanımında denemesi. `./DEVICE-TEST.ps1 -Durum` yalnız test durum tablosunu yeniler, cihaza bağlanmaz.

## Belgeler

- [AGENTS.md](AGENTS.md) — ajanlar için güncel kurallar ve kod haritası
- [Kullanım kılavuzu](docs/KULLANIM.md)
- [Ürün tanımı](PRODUCT.md) · [Tasarım sistemi](DESIGN.md)
- [Kodun neyi neden yaptığı](docs/KOD-REHBERI.md) · [Kayıt ve kurtarma](docs/KAYIT-MIMARISI.md)
- [Tablet test oturumu](docs/TABLET-TESTI.md) · [Test durumu ✅](docs/TEST-DURUMU.md) · [Doğrulama geçmişi](docs/DOGRULAMA.md)
- Tarihsel: [Eşitleme ve veri modeli gerekçeleri](docs/TASARIM.md) · [GVNpen kaynak incelemesi](docs/GVNPEN-INCELEME.md) · [Samsung Notes incelemesi](research/samsung-notes-4.3.831/RAPOR.md)
- Arayüz tasarım sheet'leri: `D:/Sanal Desktop/GVNnotes Arayüz Sheetleri/` (seçilen: `6-petrol-ada.html`)

## Derleme

`./build.ps1 -Offline` JVM testlerini, debug APK'yı ve Android lint'i çalıştırır; APK `dist/GVNnotes-<sürüm>.apk` olarak kopyalanır. JDK 21, Android SDK 35 ve yerel Gradle 8.11.1 kullanılır. Tablet testleri: [docs/TABLET-TESTI.md](docs/TABLET-TESTI.md).

APK debug imzalıdır, yerel kurulum içindir. Paket kimliği `com.gvn.notes.prototype` mevcut notları korumak için değiştirilmez.

**Önceki 0.15.0 — yoğun mürekkep:** görünen sayfa parçaları için 64 MiB bütçeli önbellek, uzun kalem çizgisinin 128 noktalık canlı ucu, kimlik tabanlı önbellekler ve büyük notlarda akışlı kayıt. Aynı tablette 100 sayfa / 952.320 nokta ile ölçüldü; yoğun çizim 365 → 11 ms, uzun canlı çizgi 491 → 12 ms. [Ölçüm ve sınırlar](docs/PERFORMANS.md).
