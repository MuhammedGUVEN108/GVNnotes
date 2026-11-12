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

## Capabilities and Constraints
- **Yazma tarafı GVNpen ile birebir aynı** (12 Kasım 2026 kullanıcı kararı): tüm özellikler, yazma hissi, geçişler, simgeler. Yalnız renkler GVNnotes temasına uyarlanır.
- **Arayüz "Petrol Ada"** (12 Kasım 2026 kullanıcı seçimi): kalıcı yüzen klasör ağacı + yüzen paneller. Bkz. DESIGN.md.
- Ayrı pencere/overlay yok. S Pen çizgileri sayfa koordinatında, zeminle birlikte hareket eder.
- PDF + JPEG/PNG/WebP; çoklu resim tek not (her resim bir sayfa). Boş sayfa şablonları: Düz, Çizgili, Kareli, Noktalı.
- İç içe ve boş klasörler; iki yönlü taşıma / klasör oluşturma / çöp. Yeni not doğrudan seçilen klasörde oluşturulabilir.
- Silme `.GVNnotes-çöp` klasörüne taşıma; uygulama kullanıcı dosyasını silmez. Aynı ad varsa reddet; iki klasörde aynı içerik ayrı not; kaynak çatışmada kazanır.
- Dosyalarım'dan silinen kaynağın notu çöp kutusuna gider (dosya geri gelirse döner); içerik değişen kaynak yeni not olur.
- Çöp kutusu 30 gün; sonra kalıcı silme. Klasör silme alt klasörleriyle birlikte.
- Dışa aktarma: zemin + şablon + yazılar, ~180 dpi görüntü PDF (seçilebilir metin yok).
- Arka planda anlık algılama yok; tarama uygulama açılınca / öne gelince yapılır.
- Veriler yalnız tablette, uygulamanın kendi alanında; internet ve bulut yok. Uygulamayı kaldırmak notları siler.

## Operating Context
Proje `D:/Sanal Desktop/GVNnotes`, Windows + PowerShell 5.1, yerel Gradle. Tablet USB ile bağlanır (seri `TABLET-SERI-NO`). Paket kimliği `com.gvn.notes.prototype` notları korumak için sabit. Güncel sürüm 0.11.0: tablette otomatik testler geçti, kullanıcı denemesi bekleniyor.

## Brand Commitments
Kalem tarafı GVNpen kimliğini taşır (şerit düzeni, simgeler, GVNpen markası). Uygulama çerçevesi Petrol Ada: açık mavi-gri masa, yüzen yumuşak gölgeli adalar, indigo seçim, petrol mavisi klasör. Samsung Notes yalnız davranış referansı; Samsung kodu/görselleri kopyalanmaz.

## Evidence on Hand
Tablet testleri ve kullanıcı onayları `docs/DOGRULAMA.md`; ekran görüntüleri `logs/ui-0.11.0/`; tasarım sheet'leri `D:/Sanal Desktop/GVNnotes Arayüz Sheetleri/`; Samsung Notes incelemesi `research/samsung-notes-4.3.831/RAPOR.md`. Sürümlerin doğrulama kapsamları birbirine genellenmez.
