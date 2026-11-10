# Prototip kodunu okuma rehberi

- **Model.kt:** Sayfa, not, kaynak ve çizgi tipleri. `data class`, yalnızca veriyi taşıyan Kotlin sınıfıdır. UUID kimlik kullanmamızın nedeni aynı içerikli iki dosyanın iki ayrı not olabilmesi.
- **Viewport:** Sayfa ile ekran arasında dönüşüm. Bir haritayı büyütmek gibi: içerik aynı koordinatta kalır, yalnız bakış değişir. Girdi ters dönüşümle sayfaya taşınır; çizici aynı dönüşümün düz hâlini kullanır.
- **History:** Geri alma ve yineleme. İlk prototipte immutable çizgi listelerinin geçmişini tutar; yeni değişiklikten sonra eski ileri alma yolu kapanır. Kalıcı kayıt değildir.
- **EditorState:** ViewModel, Android ekranı yeniden oluşturduğunda çalışma verisini RAM'de koruyan sınıftır. Uygulama süreci kapanınca kalıcı kayıt sağlamaz.
- **InkPageView:** Compose içindeki normal Android View. MotionEvent, parmak/kalem hareket paketidir. Stylus yazı üretir; iki parmak görünümü değiştirir. Ink, basınçlı çizgiyi düşük gecikme yoluyla oluşturur.
- **Pending:** Çizim hâlindeki gerçek örnekler. Ink tamamlandığında sayfa çizgisine dönüşür. Tahmin edilen noktalar yalnız geçici görüntüyü iyileştirir, veriye kaydedilmez.
- **cancelGesture:** İşletim sistemi teması iptal ederse yarım çizgiyi ve geçici silmeyi bırakır. İptal, tamamla anlamına gelmez.
- **MainActivity:** Menü/araçlar Compose ile; yüksek frekanslı kalem girişini Compose yeniden çizimine bağlamamak için yüzey AndroidView içine gömülür.

0.1.2'de **PageStore** ve **SaveEnvelopeCodec** eklendi: sürümlü/checksum kontrollü çift slot kaydı, arka plan yazıcısı ve açılışta geri yükleme. EditorState değişiklikleri otomatik kayda gönderir; ViewModel yalnız RAM koruması olarak kalmaz. [Kayıt mimarisi](KAYIT-MIMARISI.md).

Dosya/SAF senkronu, PDF/resim ve tam GVNpen araçları henüz yok. Kalem motoru GVNpen'den kopyalanmadı; Jetpack Ink kullanılıyor.

0.1.3: **NoteLibrary** not dizinlerini listeler, yeni nota rastgele UUID dizini ve ilk boş kayıt oluşturur. Eski main dizinini taşımadan okur. Önizleme kaydın kendisi değil, silinse yeniden üretilebilen bir PNG cache'idir. **LibraryState** liste/yüklenme/seçili not durumunu taşır. **LibraryScreen** Material not ızgarası ve ad dialogudur. **MainActivity** her notu ayrı ViewModel anahtarıyla açar; böylece undo geçmişi ve çizimler birbirine karışmaz. **EditorDestination**, Geri'ye basıldığında aktif çizgi ve kalıcı kayıt tamamlanana kadar bekler. Bu bekleme, son yazının nottan çıkarken kaybolmasını önler.
