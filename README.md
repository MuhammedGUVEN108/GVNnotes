# GVNnotes

Galaxy Tab S9+ ve S Pen için PDF, resim ve boş sayfalar üzerine el yazısıyla not uygulaması. Proje merkezi: `D:/Sanal Desktop/GVNnotes`.

Yapay zekâ ajanları önce [AGENTS.md](AGENTS.md) dosyasını okumalı.

## Mevcut durum — 0.11.0 (12 Kasım 2026)

- **Yazma:** GVNpen ile aynı. Yüzen, sürüklenebilir araç şeridi: kalem, fosforlu, silgi (tüm çizgi / alan), 2B ve 3B şekiller, akıllı şekil, kalınlık, geri al / yinele, sayfayı temizle, hızlı renkler, renk paleti, gezinme ve okuma modu. S Pen yan düğmesi, kalem yaklaşınca açılan menüler, kalem imleci. GVNpen ayar ekranı.
- **Arayüz: "Petrol Ada".** Kütüphanede solda kalıcı, yüzen klasör ağacı; önizlemeli not kartları; klasör kısayolları. Düzenleyicide başlıkta konum çipi → Klasör adası (aynı klasördeki notlar, klasöre git, bu klasörde yeni not); sağda kapatılabilir sayfa sıralayıcı; sağ üstte yakınlaştırma / tam ekran / dışa aktar; altta kayıt durumu ve sayfa sayacı. GVNpen şeridi de aynı renklerde.
- **Notlar:** PDF, resim (çoklu resim tek not) ve boş sayfa (Düz, Çizgili, Kareli, Noktalı); çok sayfa; kement seçimi; otomatik ve doğrulanmış kayıt; çöp kutusu; PDF olarak dışa aktarma.
- **Klasör eşitleme:** `Documents/GVNnotes` alt klasörleriyle otomatik not olur; taşıma, yeni klasör ve çöp iki yönlü.

Doğrulama: tablette 13 cihaz + 2 kütüphane testi geçti, ekranlar görüntüyle kontrol edildi. Kullanıcının S Pen ile elle denemesi bekleniyor. Geçmiş: [docs/DOGRULAMA.md](docs/DOGRULAMA.md).

## Sonraki adım

Kullanıcının 0.11.0 denemesi ve geri bildirimi; ardından 1.0.0.

## Belgeler

- [AGENTS.md](AGENTS.md) — ajanlar için güncel kurallar ve kod haritası
- [Kullanım kılavuzu](docs/KULLANIM.md)
- [Ürün tanımı](PRODUCT.md) · [Tasarım sistemi](DESIGN.md)
- [Kodun neyi neden yaptığı](docs/KOD-REHBERI.md) · [Kayıt ve kurtarma](docs/KAYIT-MIMARISI.md)
- [Tablet test oturumu](docs/TABLET-TESTI.md) · [Doğrulama geçmişi](docs/DOGRULAMA.md)
- Tarihsel: [Eşitleme ve veri modeli gerekçeleri](docs/TASARIM.md) · [GVNpen kaynak incelemesi](docs/GVNPEN-INCELEME.md) · [Samsung Notes incelemesi](research/samsung-notes-4.3.831/RAPOR.md)
- Arayüz tasarım sheet'leri: `D:/Sanal Desktop/GVNnotes Arayüz Sheetleri/` (seçilen: `6-petrol-ada.html`)

## Derleme

`./build.ps1 -Offline` JVM testlerini, debug APK'yı ve Android lint'i çalıştırır; APK `dist/GVNnotes-<sürüm>.apk` olarak kopyalanır. JDK 21, Android SDK 35 ve yerel Gradle 8.11.1 kullanılır. Tablet testleri: [docs/TABLET-TESTI.md](docs/TABLET-TESTI.md).

APK debug imzalıdır, yerel kurulum içindir. Paket kimliği `com.gvn.notes.prototype` mevcut notları korumak için değiştirilmez.
