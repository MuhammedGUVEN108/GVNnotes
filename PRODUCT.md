# GVNnotes
<!-- impeccable:product-schema 1 -->

## Platform
android

## Stack
Kotlin + Jetpack Compose (kütüphane, düzenleyici çerçevesi, ayarlar) + Android View yazma yüzeyi (InkPageView). Kalem tarafı kullanıcının GVNpen uygulamasından kopyalanan Java kodu (`com.gvn.notes.pen`): araç şeridi, çizim/silgi geometrisi, akıllı şekil, ayar ekranı. Jetpack Ink 0.10.0'da kaldırıldı. minSdk 28, targetSdk 35.

## Users
Muhammed; Galaxy Tab S9+ (SM-X810, Android 16) ve S Pen ile kendi ders PDF'leri ve resimleri üzerinde çalışan tek kullanıcı.

## Product Purpose
Dosyalarım'daki `Documents/GVNnotes` klasörüne eklenen PDF/resimleri klasör yapısıyla otomatik not yapmak ve S Pen ile doğrudan sayfaya, GVNpen'deki gibi yazmak.

## Yoğun not davranışı
100 sayfalık ders dosyasında yıl boyu mürekkep birikmesi temel senaryodur. 0.16.0 sayfa/çizgi bazlı kalıcı SQLite kayıt, noktaları gerektiğinde yükleme ve komşu sayfa hazırlığı kullanır. Zoom sırasında mevcut görüntü ölçeklenir; net karolar arka planda hazırlanır. Soğuk yoğun sayfanın hazırlık süresi olabilir; sınırsız kapasite veya sıfır gecikme garantisi verilmez.

## Capabilities and Constraints
- **Yazma tarafı GVNpen ile birebir aynı** (12 Kasım 2026 kullanıcı kararı): tüm özellikler, yazma hissi, geçişler, simgeler. Yalnız renkler GVNnotes temasına uyarlanır.
- **Arayüz "Petrol Ada"** (12 Kasım 2026 kullanıcı seçimi): kalıcı yüzen klasör ağacı + yüzen paneller. Bkz. DESIGN.md.
- Ayrı pencere/overlay yok. S Pen çizgileri sayfa koordinatında, zeminle birlikte hareket eder.
- PDF + JPEG/PNG/WebP; çoklu resim tek not (her resim bir sayfa). Boş sayfa şablonları: Düz, Çizgili, Kareli, Noktalı.
- İç içe ve boş klasörler; iki yönlü taşıma / klasör oluşturma / çöp. Yeni not doğrudan seçilen klasörde oluşturulabilir.
- Silme `.GVNnotes-çöp` klasörüne taşıma; uygulama kullanıcı dosyasını silmez. Aynı ad varsa reddet; iki klasörde aynı içerik ayrı not; kaynak çatışmada kazanır.
- Dosyalarım'dan silinen kaynağın notu çöp kutusuna gider (dosya geri gelirse döner); içerik değişen kaynak yeni not olur.
- Çöp kutusu 30 gün; sonra kalıcı silme. Klasör silme alt klasörleriyle birlikte.
- Dışa aktarma: özgün PDF sayfaları/metin/görseller korunur; yazılar ve boş sayfa şablonları vektör eklenir. Kaynakta metin yoksa OCR üretilmez.
- Arka planda anlık algılama yok; tarama uygulama açılınca / öne gelince yapılır.
- Veriler yalnız tablette, uygulamanın kendi alanında; internet ve bulut yok. Uygulamayı kaldırmak notları siler.

## Operating Context
Proje `D:/Sanal Desktop/GVNnotes`, Windows + PowerShell 5.1, yerel Gradle. Tablet USB ile bağlanır (seri `TABLET-SERI-NO`). Paket kimliği `com.gvn.notes.prototype` notları korumak için sabit. Güncel sürüm 0.16.1: sık liste, belgeyi örtmeyen yan panel ve üst satırda kontroller; 17 tablet test birimi ve görsel doğrulama geçti; tablette kurulu.

## Brand Commitments
Kalem tarafı GVNpen kimliğini taşır (şerit düzeni, simgeler, GVNpen markası). Uygulama çerçevesi Petrol Ada: açık mavi-gri masa, yüzen yumuşak gölgeli adalar, indigo seçim, petrol mavisi klasör. Samsung Notes yalnız davranış referansı; Samsung kodu/görselleri kopyalanmaz.

## Evidence on Hand
Tablet testleri ve kullanıcı onayları `docs/DOGRULAMA.md`; ekran görüntüleri `logs/ui-0.11.0/`; tasarım sheet'leri `D:/Sanal Desktop/GVNnotes Arayüz Sheetleri/`; Samsung Notes incelemesi `research/samsung-notes-4.3.831/RAPOR.md`. Sürümlerin doğrulama kapsamları birbirine genellenmez.

## 14 Kasım 2026 kapsamı

Kullanıcı kütüphanede alan tekrarını ve düzenleyicide panellerin sayfayı örtmesini azaltmayı, sık listeyi ve kaliteli PDF çıktısını onayladı. Yedekleme öncelikli değil, koyu tema kapsam dışında.

## Kalıcı birikim ve performans

Hedef kullanım: aynı 100 sayfalık ders dosyası aylar/yıl boyunca mürekkeple dolar. Görünmeyen sayfalar için bitmap ayrılmaz; görünen karolar 64 MiB bütçede tutulur. 100 sayfa/952.320 nokta senaryosu kaydetme/yeniden açmayla doğrulandı. Bu gerçek bir yıl boyunca saha testi değildir; yeni şema-6 SQLite biçiminde eski toplam çizgi/nokta/bayt sınırları uygulanmaz. 2000 sayfa sınırı ve cihaz/disk/tek sayfa yoğunluğu pratik sınırları sürer. 100 sayfa / 30.000 çizgi / 2.880.000 nokta ile doğrulandı.
