# GVNnotes — düzeltilmiş tasarım, 10 Kasım 2026

> **Tarihsel belge (10 Kasım 2026).** Klasör eşitleme kuralları ve veri modeli gerekçeleri (kimlik UUID, kaynak kazanır, çöp, izin kaybı ≠ silinme vb.) hâlâ geçerlidir. **Geçersiz kısımlar:** "GVNpen yalnız arayüz referansı, kod/motor aktarılmaz" (0.10.0'da kaldırıldı: yazma tarafı GVNpen kodudur), Jetpack Ink/motion prediction notları (Ink kaldırıldı), Room ve aşama planı (uygulanmadı; kayıt PageStore ile yapılıyor). Güncel durum: [AGENTS.md](../AGENTS.md).

## Kararlar ve son kullanıcı düzeltmesi

Tab S9+, S Pen, PDF/resimden otomatik not, alt klasörler ve boş klasörler, iki yönlü dosya yönetimi, .GVNnotes-çöp, kaynak kazanır, ad çakışmasını reddet, aynı içerik iki klasörde ayrı not, kayıp kaynakta notu koru, çoklu resim ve dışa aktarma kararları korunur.

**Son kullanıcı mesajı önceki entegrasyon planını değiştirir: GVNpen yalnız arayüz referansıdır. Kod ve motor aktarılmaz.** Yeni kalem motoru GVNnotes'a aittir. Uygulama içinde çizim için View kullanmak, sistem üstü overlay kullanmak anlamına gelmez.

Bulutun ilk sürümde bulunmaması kapsam önerisidir, kalıcı kullanıcı yasağı değildir. Birleştirme akışı, düğme/hover eşlemesi, ilk tam araç listesi ve yedek konumu açık karardır.

## Taslaktaki çelişkiler ve düzeltmeler

1. **Not kimliği hash olamaz.** Aynı içerik farklı klasörlerde ayrı not olmalı. Folder/Note/Page/SourceEntry kimlikleri UUID; hash yalnız SourceBlob içerik kimliği ve tekilleştirme içindir. Aynı blob iki ayrı notta ortak kullanılabilir; yazılar ortak olmaz.
2. **Herhangi bir sayfada hash varsa atla kuralı kaldırıldı.** Aynı kaynak girdisi yeniden görülürse tekrar oluşturma; yeni URI/konumda aynı hash görülmesi kopya mı taşıma mı ayrıca uzlaştırılır. Eski ve yeni konum aynı taramada varsa ayrı not. Eski konum kayıp ve tek aday varsa taşıma adayı; birden çok aday varsa otomatik birleştirme yok.
3. **Kaynak ve not ayrılmalı.** Birleştirilen çoklu resim notunda birden fazla SourceEntry olur. Page ilgili kaynak sürümüne bağlanır; tek note.sourceHash yetmez. Kaynak değişince yazılar sessizce başka zemine bindirilmez; eski sürüm ve not korunur, yeni kaynak ayrı not olur.
4. **Kaynak kazanır kuralı çizgileri silmez.** Dosya adı/konumu gibi iki yönlü kaynak metadata çatışmasında uygulamanın bekleyen işlemi iptal edilir ve bildirilir. El yazıları uygulamaya ait içeriktir; dosya sistemi çatışması bunları yok etmez.
5. **Orijinale dokunulmaz ifadesi daraltıldı.** Kaynak dosyanın içeriği el yazısıyla üzerine yazılmaz; kullanıcı adlandırma/taşıma/çöpe alma işlemlerinin kaynağa yansımasını açıkça istedi.
6. **İzin kaybı veya eksik tarama silinme değildir.** Yalnız başarıyla tamamlanan ilgili alt ağaçta yokluk tespit edilir. Hata/iptal/derinlik sınırında kaynak UNKNOWN kalır. Derinlik sınırı varsa atlanan klasör kullanıcıya gösterilir.
7. **Çöp ve dışa aktarma taramadan dışlanır.** .GVNnotes-çöp içeriği yeni nota dönüşmez. Çöpe taşımada eski URI/yol ve işlem kaydı tutulur. Hedefte aynı isim varsa reddetme kuralı geçerlidir; sessiz üzerine yazma yok. Boş klasörlerin de kalıcı yerel kimliği gerekir.
8. **Boyut/zaman içerik kanıtı değildir.** Sağlayıcı sıfır/null veya değişmeyen zaman döndürebilir. Metadata yalnız hız ipucudur; gerektiğinde yeniden hash, elle tam doğrulama ve değişim şüphesinde yeniden okuma gerekir. Kopyalanırken değişen dosyada hash ve snapshot aynı akıştan üretilir, değişim tespit edilirse yeniden denenir.
9. **Journal iki sistem arasında transaction değildir.** SAF başarılı olup Room güncellemesi yarıda kalabilir. PREPARED/APPLIED/COMMITTED/FAILED/NEEDS_RECONCILIATION durumları, eski/yeni URI ve sonuç kontrolü gerekir. Açılışta körlemesine tekrar taşıma veya geri alma yapılmaz; önce gerçek kaynak durumu uzlaştırılır.
10. **Ekran yenilenmesi çizgi kaybı olmamalı.** ViewModel çalışma durumunu taşır; kalıcı kayıt Aşama 2'de gelir. ACTION_CANCEL/FLAG_CANCELED tamamlanmamış çizgiyi iptal eder; tahmini noktalar kalıcı çizgi listesine yazılmaz.
11. **Koordinatlar ve kalınlık açık olmalı.** PDF nokta birimi, resim normalize edilmiş sayfa birimi; araç kalınlığı sayfa/ekran tercihine göre dönüştürülür. Piksel, dp ve sayfa birimi karıştırılmaz. Görünüm matrisi tek yetkili kaynaktır.
12. **Overlay var diye motor atılmaz.** Kaynak incelemesi GVNpen'in hem overlay hem kendi motorunu içerdiğini gösterdi; eski A/B sınıflaması birbirini dışlamıyor. Ancak son kullanıcı düzeltmesiyle hiçbir motor kodu taşınmayacak.

## Veri modeli önerisi

Folder(id, parentId, name, sourceEntryId?, relativePath, sourceStatus)
Note(id, folderId, title, createdAt, status)
SourceEntry(id, rootId, providerAuthority, documentId, uri, parentId, displayName, relativePath, blobHash, size?, modified?, sourceStatus)
SourceBlob(hash, privatePath, mediaType, byteLength)
Page(id, noteId, orderKey, sourceEntryId?, sourceBlobHash?, sourcePageIndex?, width, height, backgroundType)
Stroke(id, pageId, formatVersion, tool, color, size, samples)
Operation(id, entryId, kind, expectedSourceVersion, oldUri, resultUri?, destination, state)

SourceBlob depolama paylaşımı notları birleştirmez. Birleştirme mevcut sayfaları yeni notta sıralar; kaynak klasörleri kendiliğinden değişmez. Birleştirilmiş notu taşırken birden çok kaynağa ne yapılacağı **açık karar**, uygulanmadan önce belirlenmeli.

## Kütüphane ve platform doğrulaması

- Jetpack Ink resmi sürüm sayfası: stable 1.0.0, alpha 1.1.0-alpha11 (10 Kasım 2026 kontrolü). İlk aday stable; derlenebilir uyumlu sürüm gerçek Maven artifactinden de kontrol edilir.
- Motion prediction: androidx.input:input-motionprediction 1.0.1; record/predict ve tahmini event'i recycle etme. Ink'in işlediği MotionEvent geçmişini ayrıca yeniden ekleyip noktaları çoğaltma.
- SAF: moveDocument için kaynakta FLAG_SUPPORTS_MOVE; renameDocument için FLAG_SUPPORTS_RENAME; oluşturma için ebeveynde FLAG_DIR_SUPPORTS_CREATE. İzin ve bayraklar başarı garantisi değil; sonuç URI'si kaydedilir. move API24+, rename/create API21+.
- Aynı sağlayıcı şartı aynı depolama birimi garantisi değildir. Farklı birime taşıma ürün kuralıyla reddedilir; sağlayıcı/birim tespiti belirsizse güvenli hata gösterilir. Kopyala+sil geri dönüşü yok.
- WorkManager periyodik minimum 15 dakika; tam zamanlı algılama değildir. Android force-stop ve izin iptali ayrıca ele alınır.
- SAF erişimi tek seferlik seçilir ama kullanıcı izni iptal edebilir; yeniden bağlama akışı gerekir. Android 11+ kısıtlı kökler yerine uygun alt klasör seçilir.

Resmî kaynaklar:
- https://developer.android.com/jetpack/androidx/releases/ink
- https://developer.android.com/jetpack/androidx/releases/input
- https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/ink-api-draw-stroke
- https://developer.android.com/reference/android/provider/DocumentsContract
- https://developer.android.com/reference/android/provider/DocumentsContract.Document
- https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work

## Aşamalar

1. Bağımsız Ink tabanlı boş sayfa, kalem/fosforlu/tüm çizgi silgisi, undo/redo, iki parmak zoom/pan; gerçek tablet hissi henüz doğrulanmadan kabul edilmiş sayılmaz.
2. Sürümlü çizgi saklama, otomatik kayıt, kapat-aç ve çökme kurtarma.
3. Tek resim + EXIF, tek PDF + sayfa dönüşümü.
4. SAF düz ve rekürsif salt okunur tarama; sonra kaynak değişikliklerini uzlaştırma; en son iki yönlü journal ve çöp.
5. Çoklu kaynak notlar/birleştirme ve sayfa yönetimi.
6. PNG/JPEG ve raster PDF dışa aktarma; sonra vektör PDF.

Model baştan ağaç ve çoklu kaynak ilişkisine uygun kurulur. Room'u ilk kalem denemesine zorunlu bağlamamak mümkündür; bu prototipte metadata tipleri hazır, kalıcı DB sonraki aşamadır.
