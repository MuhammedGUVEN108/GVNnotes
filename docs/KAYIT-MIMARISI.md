# Sayfa bazlı kayıt — 0.16.0

## Kullanıcı akışı

Kalem kalkınca, silince, geri alınca veya sayfa değişikliğinde kayıt arka planda otomatik yapılır. Sürekli Kaydedildi/Çiziliyor etiketi ve alt çubuk yoktur. Hata görünür; bellekteki değişiklikler korunur. Nottan çıkışta son sürümün kaydı beklenir. Geri alma geçmişi oturum içindir.

## Veri modeli

`notebooks/<uuid>/notebook.db` SQLite şema 6'dır. `pages` sayfa metadata'sını, `strokes` çizgi metadata'sını ve değişmez nokta kümesi kimliğini tutar. `sample_sets` / `chunks` noktaları 256 örneklik küçük BLOB parçalarında saklar. Her parça SHA256 ile doğrulanır; eksik parça/sıra/nokta sayısı hata verir. Kaynak PDF `source.pdf` olarak kalır. Önizleme, liste kartı, görünüm konumu ve eşitleme dosyaları önceki yerindedir.

- WAL ve FULL synchronous; değişen sayfa/çizgi ile not metadata'sı tek transaction içinde yazılır. Başarısız transaction önceki revizyonu korur. Varsayılan bozulma işleyicisinin dosyayı otomatik silmesi engellenir.
- Değişmeyen sayfanın nokta verisi okunmaz/yazılmaz. Yeni çizginin noktaları yalnızca ilk kayıt sırasında yazılır; renk, sıra, silme ve geri almada mevcut değişmez kümeler tekrar kullanılır.
- Ana IO yazıcısı sırayla çalışır; biriken isteklerde son tam mantıksal durum seçilir. Snapshot sayfa listelerine referans verir; bütün belgenin çizgi/nokta listesi kopyalanmaz.
- Açılışta sayfalar ve hafif çizgi metadata'sı okunur. Noktalar gereken sayfalar için IO'da yüklenir. Aktif sayfa ve bütçeye sığan komşuları önceden hazırlanır. Soğuk sayfa hazır olmadan çizim kabul edilmez.
- Nokta LRU bütçesi 750.000 örnek; aktif sayfa ve uygun komşular sabitlenir (komşularla hedef 600.000). Tek aktif sayfa daha büyükse sabitlenen veri bu yumuşak bütçeyi aşabilir. Bu, toplam RAM garantisi değildir.
- Geri alma yalnız değişen sayfanın listelerini tutar (80 adım). Geçmiş/export tarafından kullanılan eski nokta kümeleri canlı handle ile korunur. Her 128 revizyonda kullanılmayan kümelerin sınırlı bir bölümü transaction ile temizlenir.

## Uyumluluk ve sınırlar

Eski şema 1–4 kayıtlarını LegacyPageStore okur; başarılı dönüşümden sonra yeni DB kullanılır. `document-a.gvn` / `document-b.gvn` artık eski APK'nın yeni biçimi yanlışlıkla açmasını önleyen küçük sürüm işaretleridir; yeni biçimin yedek slotu değildir. Daha eski APK'ya dönerek yeni notlar açılamaz.

Eski belge başına 64 MiB / 20.000 çizgi / 2.000.000 nokta sınırları yeni SQLite biçiminde uygulanmaz. Sayfa sayısı 2000 ile sınırlıdır. Disk alanı, tek sayfa yoğunluğu, metadata boyutu ve cihaz belleği pratik sınır olmaya devam eder; sınırsız kapasite iddiası yoktur. Kullanıcı mevcut notların test verisi olduğunu belirterek biçim değişimini onayladı (14 Kasım 2026).

## Doğrulama

PageStoreDeviceTest: metadata/nokta eşitliği, gerçek SQL trigger ile başarısız transaction ve önceki revizyon, bozuk DB'nin silinmemesi; 100 sayfa / 30.000 çizgi / 2.880.000 nokta kaydet-aç, noktaların başlangıçta yüklenmemesi, tek sayfaya 3 yeni nokta için yalnız o sayfanın yazılması, yalnız başlık değişince 0 nokta yazılması. PersistenceRestartTest gerçek force-stop sonrasında kalemi/silgiyi/geri almayı doğrular. Ekran karoları kayıt veya PDF kaynağı değildir; PDF çıktı vektör kalır.
