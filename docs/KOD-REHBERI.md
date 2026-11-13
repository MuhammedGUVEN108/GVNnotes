# Kodun neyi neden yaptığı — 0.11.0

Güncel genel kurallar: [AGENTS.md](../AGENTS.md). Bu belge, kodu ilk kez okuyan biri için parçaların nasıl birleştiğini anlatır.

## Katmanlar

1. **core (saf Kotlin):** Android'e bağlı olmayan veri tipleri ve hesaplar; JVM'de test edilir.
   - `Model.kt`: Folder, Note, Page, PageStroke, Sample, Viewport, History. Kimlikler UUID'dir; aynı içerikli iki dosya iki ayrı not olabilsin diye hash kimlik olarak kullanılmaz.
   - `PenGeometry.kt`: kement ve silgi için çizgi–nokta geometrisi.
   - `SaveEnvelope.kt`: kayıt zarfı (sihirli sayı, sürüm, revizyon, SHA256). Bozuk ya da yarım dosya sağlam veri gibi açılmaz.
2. **pen (GVNpen kopyası, Java):** kullanıcının GVNpen uygulamasından alınan kalem tarafı. Paket adı dışında GVNpen davranışı korunur.
   - `PenToolbar`: notun üstünde şeffaf katmanda yüzen araç şeridi. Boş alana dokunuş alttaki sayfaya geçer. Menüler kalem yaklaşınca 350 ms'de açılır, 400 ms'de kapanır; şerit 150 ms'de katlanır.
   - `Prefs`: GVNpen ayarları; `gvnpen` SharedPreferences içinde GVNpen profil JSON'u. `DefaultsVersion` ile tek seferlik varsayılan göçleri (5: Petrol Ada açık tema + indigo vurgu, yalnız eski varsayılandaysa).
   - `PenSettingsActivity`: GVNpen ayar ekranı. `Geometry`, `SmartShape`, `PenRenderer`, `SizeScale`: çizgi, silgi, akıllı şekil, çizim.
3. **app (Kotlin, Compose + View):**
   - `InkPageView`: yazma yüzeyi. GVNpen InkView akışı sayfa koordinatına uyarlanmıştır: basınç, yumuşatma, fosforlu, akıllı şekil, 2B/3B şekiller, kapsül silgi (tüm çizgi / alan), S Pen yan düğmesi, kalem imleci, Android 14+ tahmin. Üstüne zoom/kaydırma, çok sayfa, PDF/resim/şablon zemini, kement ve parmak davranışı eklenir. Yüksek frekanslı kalem girdisi Compose yeniden çizimine bağlanmasın diye normal Android View'dır.
   - `EditorState` (ViewModel): açık notun sayfaları, çizgileri, geri al/yinele geçmişi ve kayıt durumu. Her kalem teması tek geri alma adımıdır. Değişiklikler otomatik kayda gönderilir.
   - `StrokeBuild`: kayıtlı `PageStroke` ↔ GVNpen çizgisi dönüşümü.
   - `PageStore`: not kaydı (bkz. [KAYIT-MIMARISI.md](KAYIT-MIMARISI.md)).
   - `NoteLibrary`: not dizinleri, kart önbelleği (`card.json`), önizleme PNG'si, oluşturma (klasör seçerek), PDF/resim içe aktarma, taşıma, çöp. `LibraryState` (ViewModel) kütüphane ekranının durumu ve eşitleme tetikleyicisidir; düzenleyiciden gelen "klasöre git / bu klasörde yeni" isteklerini (`requestedFolder`, `requestedNewIn`) taşır.
   - `FolderSync`: `Documents/GVNnotes` SAF taraması. `SyncIndex` kaynak dosya ↔ not eşlemesini tutar. Eksik tamamlanan tarama hiçbir notu "kayıp" saymaz. Kaynak kazanır; hedefte aynı ad varsa `NameTakenException`.
   - `MainActivity`: tema (`AdaColors`), kütüphane ↔ düzenleyici geçişi ve düzenleyici ekranı. Her açık notun düzenleyicisi kendi `ViewModelStore`'unda yaşar; not kapanınca temizlenir, böylece kapalı notun eski durumu diske geri yazılamaz. `EditorDestination`, geri basıldığında ya da Klasör adasından başka nota geçilirken etkin çizgi ve kayıt bitene kadar bekler (`AfterClose`).
   - `LibraryScreen`: klasör ağacı adası, not kartları, çöp, ayarlar ve yeni not penceresi.
   - `Theme.kt`: Petrol Ada renkleri ve `Island` bileşeni.
   - `PdfPages`, `ImageImport`, `PagePattern`, `Export`, `AppSettings` (+ `CrashLog`).

## Akışlar

- **Not açma:** kart → `LibraryState.open(key)` → eşitleme sürüyorsa bitince açılır → `EditorState` kaydı yükler → `InkPageView` + `PenToolbar` oluşturulur.
- **Yazma:** S Pen olayı → `InkPageView` (GVNpen akışı) → kalem kalkınca çizgi `EditorState`'e → geçmiş + sessiz otomatik kayıt → dosyayı geri okuyarak doğrulama (sürekli durum etiketi yok).
- **Klasör adasından başka nota geçiş:** `AfterClose.Open(key)` → kayıt bitince düzenleyici kapanır → `libraryState.open(key)`.
- **Eşitleme:** uygulama açılınca/öne gelince ve not kapanınca (`LibraryState.sync`); not açıkken çalışmaz, açık notun kaydıyla yarışmasın diye.

## Testler

- JVM: `core/src/test` (model, geometri, kayıt zarfı) ve `app/src/test/java/.../pen` (GVNpen'in kendi GeometryTests ve EraseTests'i).
- Tablet: `app/src/androidTest` → `DEVICE-TEST.ps1`, `LIBRARY-TEST.ps1` (bkz. [TABLET-TESTI.md](TABLET-TESTI.md)).

## 0.15.0 yoğun ve kalıcı mürekkep

InkTileCache yalnız görünen sayfanın 512 piksel parçalarını ölçek kademeleriyle üretir; aynı görüntüyü tekrar çizmez, yeni çizgiyi mevcut parçalara ekler. Silgi/geri alma/renk değişikliği etkilenen parçaları geçersiz kılar. LRU bütçesi 64 MiB; atılan parça modelden yeniden üretilir. ActiveInkCache uzun kalemin kesinleşmiş başını ekran bitmap’ine ekler; canlı uç en çok 129 segmenttir. Fosforlu tek yol kalır, yarı saydam ek yeri oluşturulmaz. Geometri önbelleği 300.000 noktayla sınırlıdır. WeakIdentityCache nesne kimliği kullanır; PageStroke’un binlerce noktayı dolaşan hashCode’unu çağırmaz. Küçük resimler artık başka sayfadaki yeni çizgi yüzünden tekrar üretilmez.
