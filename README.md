# GVNnotes

Proje merkezi: `D:/Sanal Desktop/GVNnotes`.

## Kesinleşen kararlar

- Hedef cihaz: Samsung Galaxy Tab S9+ ve S Pen.
- **Son kullanıcı kararı:** GVNpen yalnız arayüz referansıdır; mevcut kodu ve çizim motoru taşınmaz. Yeni motor Jetpack Ink tabanlı prototiple değerlendiriliyor.
- Ayrı ekran kaplaması olmayacak; çizgiler doğrudan ilgili sayfanın verisi olacak.
- Samsung Notes arayüz ve işleyiş referansı olarak inceleniyor.
- Kullanıcı talebi (10 Kasım 2026): Tablette Dosyalarım üzerinden erişilen belirli bir klasöre eklenen PDF'ler GVNnotes'ta otomatik not olacak; uygulama içinden tek tek PDF seçmek gerekmeyecek. Kesin klasör yolu henüz belirlenmedi. Arka plan algılama zamanlaması uygulama aşamasında cihazda doğrulanacak.

## Mevcut durum

**0.11.0 — "Petrol Ada" arayüzü (12 Kasım 2026).** Kütüphanede kalıcı yüzen klasör ağacı, düzenleyicide konum çipi + Klasör adası, ada biçiminde ayarlar ve yeni not penceresi. Yazma tarafı GVNpen ile aynı kaldı. Tasarım: `D:/Sanal Desktop/GVNnotes Arayüz Sheetleri/6-petrol-ada.html`. Cihazda henüz denenmedi.

**0.9.0 — ilk kullanılabilir sürüm adayı.** PDF/resim/boş sayfa notları, çok sayfa, sayfa sıralayıcı ve şablonlar; Samsung Notes düzeninde arayüz; S Pen kalem/fosforlu, tüm çizgi ve alan silgisi, S Pen yan düğmesi, akıllı şekil, kement seçimi; otomatik kayıt; `Documents/GVNnotes` iki yönlü klasör eşitlemesi; çöp kutusu; PDF olarak dışa aktarma; ayarlar ve hata kaydı.

Tablette denenenler: 0.5.0'a kadar (tam cihaz test seti geçti; kullanıcı 0.2.0 ve 0.2.1'i onayladı). 12 Kasım 2026 tablet oturumunda **tüm otomatik cihaz testleri geçti** (0.9.1; kement bırakma hatası düzeltildi). Elle S Pen denemesi sürüyor. [Tablet test oturumu planı](docs/TABLET-TESTI.md) · [Kullanım kılavuzu](docs/KULLANIM.md)

[Samsung Notes inceleme raporu](research/samsung-notes-4.3.831/RAPOR.md)

## Sonraki adım

Kullanıcıyla tablette 0.9.0 test oturumu (DEVICE-TEST.ps1 + elle deneme listesi), bulunan hataların düzeltilmesi ve 1.0.0.

## Belgeler

- [Kullanım kılavuzu](docs/KULLANIM.md)
- [Tablet test oturumu planı](docs/TABLET-TESTI.md)
- [Doğrulama geçmişi](docs/DOGRULAMA.md)
- [Düzeltilmiş tasarım ve açık kararlar](docs/TASARIM.md)
- [GVNpen kaynak incelemesi](docs/GVNPEN-INCELEME.md)
- [Kodun neyi neden yaptığı](docs/KOD-REHBERI.md)
- [Otomatik kayıt ve kurtarma yapısı](docs/KAYIT-MIMARISI.md)

## Derleme

`./build.ps1` birim testleri, debug APK ve Android lint çalıştırır. Mevcut JDK21/SDK35 ve yerel Gradle8.11.1 kullanılır. İlk çalışmada resmî Maven depolarından bağımlılıklar indirilebilir. Sonraki çalışmada `./build.ps1 -Offline` kullanılabilir. Android Studio bu klasörü Gradle projesi olarak açabilir.

Güncel çıktı: `dist/GVNnotes-0.9.1.apk` (SHA256 `1c6d37ab481efd5a5b6668a812fac12e8d16ccebf1fd60a81ed493e51dcb0174`). Debug imzalı, yerel kurulum içindir; paket kimliği `com.gvn.notes.prototype` mevcut notları korumak için değiştirilmedi.

0.1.1: Tamamlanan çizgilerin Canvas sayfa dönüşümü düzeltildi; yukarı sıçrama ve görünen yazıyla silgi konumunun ayrılması aynı kök nedene bağlıydı. Gerçek tablet üzerinde sentetik stylus olayı + ekran pikseli kullanan regresyon testi eklendi. Test sonucu docs/DOGRULAMA.md içinde.
