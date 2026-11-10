# İlk prototip doğrulaması — 10 Kasım 2026

## 0.1.4 — "Çiziliyor" kilitlenmesi düzeltmesi, 11 Kasım 2026

Kullanıcı bildirimi: editör "Çiziliyor…" durumunda takılı kalıyor; araçlar ve Notlara dön kilitleniyor.

Kök neden (Ink 1.0.0 kaynağıyla doğrulandı): Uygulama zoom/kaydırmada canlı `InProgressStrokesView` boyutunu kâğıt sınırına göre değiştiriyordu. Ink, çizgi tesliminden sonra eski tamponu ~500 ms içinde temizleyip teslimleri yeniden açar; bu sürede yüzey yeniden boyutlanırsa eski viewport atılır ve `cohortHandoffPaused` hiç kapanmaz. Sonraki çizgiler teslim edilmez, `pending` boşalmaz, `busy` kalıcı olarak açık kalır.

Düzeltme (`InkPageView`):
- Canlı yüzey artık hiç yeniden boyutlanmaz, tüm alanı kaplar. Masa alanında ıslak mürekkep görünmemesi için kâğıt dışı `maskPath` ile gizlenir.
- Güvenlik ağı: kalem kalktıktan 700 ms sonra teslim gelmemişse bitmiş çizgiler genel `getFinishedStrokes()` API ile alınır ve `GVNnotes` etiketiyle loglanır.
- Silgi, teslim bekleyen çizgi varken artık sessizce yok sayılmaz; bitmiş çizgileri önce sayfaya alır.
- Ekran kapanırken kalemi kalkmış ama teslim edilmemiş çizgiler iptal edilmeden önce sayfaya alınır (olası son çizgi kaybı).

Doğrulama (gerçek SM-X810):
- Yeni test `strokeAfterQuickZoomDoesNotStayBusy`: dört çizginin her birinden hemen sonra zoom/kaydırma, ardından son çizgi. **Eski kodda başarısız** ("Ink ownership transfer timed out"), **yeni kodda OK**. Güvenlik ağı logu 0 kez tetiklendi; düzeltme kök nedeni gideriyor.
- Tüm cihaz testleri yeniden geçti: InkPageRegressionTest (2), PageStore (3), kalıcılık seed/reopen/deleted, kütüphane seed/verify.
- `build.ps1 -Offline`: BUILD SUCCESSFUL, JVM testleri, lint 0 hata / 11 uyarı.
- 0.1.4 tablete kuruldu; kullanıcının mevcut notları (İlk sayfam, arda) listede açıldı.

APK: `dist/GVNnotes-0.1.4-prototype.apk`, SHA256 `986015d254538577d250f5b2d076cc0fcfa885e5ae409f4b6b50829bbaea109a`. Fiziksel S Pen ile kullanıcı denemesi bekleniyor.

## 0.1.3 — Notlar arayüzü ve bağımsız notlar, 11 Kasım 2026

Yeni normal açılış Notlar ekranıdır: gerçek sayfa küçük önizlemeleri, ad vererek Yeni not/Oluştur, not açma, editörde Notlara dön ve sistem Back. Her not ayrı UUID dizini ve EditorState/geçmiş/kayıt dosyası kullanır. Notlara dönüş tamamlanan çizgi ve kayıt sonuçlanana kadar bekler; kayıt hatasında notta kalma/yeniden deneme sunulur. Çizim motoru ve onaylı araç şeridi korunur.

Önceki `notebooks/main` doğrudan listelenir; taşınmadı veya yeniden yazılmadı. Gerçek kullanıcıdaki iki `.gvn` kaydının öncesi/sonrası SHA256 değerleri aynı: logs/library-existing-note-before.txt ve after.txt. Yeni küçük önizleme cache dosyaları kaynak kayıttan ayrıdır.

Doğrulama:
- 8 JVM testi, build ve lint başarılı; lint 0 hata / 11 uyarı (sürüm/yazım ve prototip manifest önerileri).
- Native Compose UI testi: eski tek notu aç/geri; Anatomi ve Fizyoloji adlı ayrı boş notlar oluştur; her birine farklı konumda çiz; kaydı ayrıca beklemeden Geri'ye bas. Kayıt tamamlanıp liste açıldı; not kimlikleri ve çizgi konumları ayrı kaldı.
- Force-stop sonrası aynı üç test notu listelendi, her not kendi çizgisini yükledi, diğer notun geçmişi taşınmadı. Eski `main` fixture dosyaları değişmedi.
- Önizlemeler ve ad dialogu; yeniden açılan editör/geri düğmesi; yatay notlar ızgarası gerçek tablet görüntülerinde incelendi. Compose test saatinin IO'dan gelen bitmap karelerini bazen geç göstermesi nedeniyle tamamlanmış portre render ayrıca test runner dışında doğrulandı (`library-preview-live.png`).
- Gerçek sistem fontu geçici 1.3'e getirilip uygulama yeniden açılarak kontrol edildi; başlık/kart/FAB kırpılmadı. Ayar `finally` ile önceki 1.0 değerine geri alındı ve uygulama normal açıldı. Kanıt: library-font130.png ve library-font-scale-before.txt.
- Önceki kayıt/Ink testleri tekrar başarılı: 3 depolama/kurtarma/commit, çizgi-konum/zoom/silgi/undo/iptal, autosave → force-stop → yeniden aç ve silinmenin kalıcılığı fazları. Test APK kaldırıldı.
- Kaynak ve ekran görüntülerine dayalı bağımsız Impeccable bitiş incelemesi onaylı aşama için engelleyici maddi arayüz kusuru bulmadı. Bu sonuç kullanıcının fiziksel yeni arayüz onayı yerine geçmez.

Güncel APK `dist/GVNnotes-0.1.3-prototype.apk`; SHA256 `160f0c27e38b42b12bc9e80ed38fe953e416059cedf99f88d857e79e08c0e472`. v2 imzası doğrulandı; tablet kurulu. Onaylı 0.1.2 APK korunur. Klasör gezintisi, PDF/resim ve kaynak klasör senkronu henüz yoktur.

Kanıt: logs/library-build.log, library-seed.txt, library-verify.txt, library-screenshots/, library-preview-live.png, library-font130.png, persistence-*.txt, apk-signature-0.1.3.log. UI testleri yalnız `test-library/notebooks` altında, kayıt testleri `test-document` altında çalışır; normal kullanıcı notlarıyla karışmaz.

## 0.1.2 — Otomatik/kalıcı kayıt

**11 Kasım 2026 kullanıcı onayı:** Kapatıp yeniden açma denemesinden sonra “evet kayıtlı” dedi. Otomatik/kalıcı kayıt kullanıcı tarafından doğrulandı. 0.1.2 yeni çalışan referans sürümdür; önceki APK'lar korunur.

Tek boş sayfa, çizgiler ve gerçek Ink girdileri/fırça ailesi sürümlü ikili dosyaya otomatik kaydediliyor. Çizim/silgi/undo/redo tetikler; tek IO yazıcısı, conflated son durum kuyruğu, çift AtomicFile slot, SHA256 ve yazma sonrası geri okuma kullanılır. Hata durumunda açık mesaj/yeniden deneme; açılış hatasında yazma engeli var.

Doğrulama:
- 8 JVM birim testi başarılı.
- Gerçek Tab S9+ Android16'da 3 PageStore testi: ham metadata ve Ink girdilerinin korunması; bozuk son slottan kurtarma/iki slotta hatada üzerine yazmama; gerçek commit başarısızlığında başarı bildirmeme ve eski kaydı koruma.
- Önceki konum/zoom/silgi/undo/iptal testi otomatik kaydın diske ulaşması kontrolüyle yeniden geçti.
- Kayıt testi **uygulama içinde sentetik stylus ile iki çizgi çizdi**, Kaydedildi durumunu bekledi; shell `am force-stop` sonrası ayrı instrumentation sürecinde yeniden açıldı. Piksel ve veri kontrolleri geçti. Silme → undo → redo kaydedildi; ikinci force-stop/yeniden açma sonrası silinmiş çizgi geri gelmedi.
- Testler yalnız test-document ve test cache klasörlerinde; kullanıcı notebooks/main dosyasını değiştirmez. Test APK'sı kaldırıldı, normal uygulama yeniden açıldı.
- Lint: 0 hata / 9 uyarı. Kalanlar sürüm önerileri, backup yapılandırması/launcher simgesi ve iki KTX yazım önerisi.
- APK imzası doğrulandı; 0.1.2 tablete kuruldu. SHA256 `78e9b78a8f6d40e4595f457637032331c5838cc29ea087a2289c4ae92883403d`.
- Gerçek ekran görüntülerinde yeniden yüklenmiş kalem/fosforlu çizgileri ve normal not ekranındaki otomatik kayıt durumu incelendi.

Kanıt: logs/persistence-build.log, persistence-codec-device.txt, persistence-ink-regression.txt, persistence-seed.txt, persistence-reopen.txt, persistence-deletion-reopen.txt, persistence-screenshots/*.png, persistence-main-open.png, apk-signature-0.1.2.log. Bu testler fiziksel S Pen hissinin yeni kullanıcı onayı değildir. Son kamera konumu, araç seçimi ve süreçler arası undo geçmişi henüz saklanmaz. PDF/resim/klasör senkronu sonraki aşamadır.

## 0.1.1 — Yukarı sıçrama ve silgi düzeltmesi

**Kullanıcı fiziksel tablet onayı:** Düzeltmeden sonra “evet ekran kusursuz şuanki özelliklerle” dedi. Mevcut özelliklerle ekran/yazma deneyimi kullanıcı tarafından kabul edildi. Bu onay henüz bulunmayan PDF, kalıcı kayıt veya klasör senkronu özelliklerini kapsamaz. 0.1.1 çalışan referans sürümdür.

Kullanıcı yazının yukarı fırladığını ve silginin çalışmadığını bildirdi. Kök neden: CanvasStrokeRenderer.draw içindeki matris parametresi Canvas'a dönüşüm uygulamıyor; geometri/çizim kalitesi için kullanılıyor. Tamamlanmış çizgiler sayfa koordinatlarında doğrudan ekran pikseli gibi çiziliyordu. Canlı yüzey ve silgi ise doğru sayfa dönüşümünü kullanıyordu. InkPageView.onDraw içindeki çizgi döngüsünden önce `canvas.concat(matrix)` eklendi.

Gerçek SM-X810 üzerinde, **sentetik TOOL_TYPE_STYLUS olayları ve UiAutomation ekran pikseli kontrolüyle** regresyon:
- Eski kod aynı testte `Finished ink jumped away from its page coordinates` hatasıyla başarısız oldu (`logs/regression-before.txt`).
- Düzeltilen kod: **OK (1 test)** (`logs/regression-after.txt`). Test çizginin tamamlandıktan sonraki konumunu, zoom/pan hizasını, tüm çizgi silgisini, undo ile aynı konuma geri gelmesini ve iptal edilen çizginin saklanmamasını denetliyor.
- Dört JVM testi ve Android lint yeniden başarılı: 0 hata / 7 uyarı.
- Dört gerçek tablet ekran görüntüsü `logs/regression-after/` içine alındı; bitmiş çizgi ve silinmiş sayfa görsel olarak da incelendi.
- 0.1.1 APK v2 imzası doğrulandı; tablete kuruldu ve test paketini kaldırdıktan sonra uygulama yeniden açıldı. Güncel APK: `dist/GVNnotes-0.1.1-prototype.apk`, SHA256 `d446c4d4e338f20b887000285a27662ec3c8931801924e4562938336beb6c3bb`.

Bu test fiziksel S Pen basınç/gecikme/avuç onayı değildir; kullanıcının yeniden denemesi bekleniyor. Kalıcı kayıt henüz yok.

## Tablet kurulumu — aynı gün sonraki adım

Kullanıcı tableti bağladığını bildirdi. ADB ile SM-X810 (Tab S9+), Android16/API36 doğrulandı. Aynı SHA256 APK `install -r` ile başarıyla kuruldu; MainActivity soğuk açılışı Status ok (686 ms). Uygulama süreci çalışıyor; açılış logunda FATAL EXCEPTION/AndroidRuntime görülmedi. `logs/tablet-first-open.png` gerçek ekran görüntüsü incelendi: sayfa, araçlar ve alt kontroller görünüyor. Bu açılış gözlemi tüm arayüz/S Pen testlerini tamamlamaz; yazma hissi, avuç ve zoom kullanıcı testini bekliyor. Ekranda eski GVNpen'e ait olabilecek küçük kaplama izleri var; bağımsız kalem testi öncesi eski overlay kapatılmalı.

## Tamamlananlar

- Son kaynakla `build.ps1 -Offline`: **BUILD SUCCESSFUL**, debug APK + Android lint.
- JVM birim testleri: **4 test, 0 hata, 0 başarısızlık**. Koordinat dönüşümü/zoom odağı, ayrı not kimlikleri, undo/redo ve hızlı silgi segment kesişimi.
- Android lint: **0 hata, 7 uyarı**. Kalanlar: daha yeni Compose/Activity/Lifecycle sürümü önerileri, backup yapılandırması, henüz özel launcher simgesi olmaması ve iki KTX yazım önerisi. Bunlar fiziksel kalem testi değildir.
- Son APK imzası: apksigner verify başarılı, v2.
- Gradle wrapper 8.11.1 oluşturuldu; Android Studio açılışı için proje standart Gradle yapısında.
- Kaynak incelemesinde aktif/tamamlanan çizgi sayfa sınırı, etkin araç semantiği, 48 dp hedef/8 dp aralık düzeltildi. Hızlı ardışık kalem çizgileri önceki async teslimi beklemeden başlatılır; her çizgi ayrı undo adımıdır.
- Ink 1.0.0 resmî kaynak paketindeki InProgressStrokesFinishedListener dokümanı callback Map sırasının çizgilerin başlatılma sırası olduğunu açıkça bildiriyor; undo için bu sözleşme kullanılıyor.

## Çıktı

`dist/GVNnotes-0.1.0-prototype.apk`

SHA256: `9afa3e1b80d2a65ac1ba43cfba2e71d1d853beee5f532eb9e09800c5d6f8ce25`

Paket: `com.gvn.notes.prototype`; sürüm `0.1.0-prototype`. Yerel debug APK; yayımlanmadı. Yukarıdaki sonraki adımda tablete kuruldu. Mevcut GVNpen değiştirilmedi.

## Açık kalanlar

ADB kontrolünde bağlı cihaz yok; emülatör de yok. Bu nedenle ekran görüntüsü, fiziksel kalem/avuç/zoom akışı ve gecikme hissi doğrulanamadı. Kaynak incelemesi görsel kabul sayılmadı.

Kalıcı kayıt, PDF/resim, klasör taraması ve iki yönlü senkron bu prototipte henüz yok. Çizimler süreç kapanınca kaybolur; ekran dönüşündeki RAM korunumu ViewModel'e dayanır ve cihazda ayrıca denenmeli.

Sonraki adım: [Tab S9+ kabul kontrolü](TABLET-TESTI.md). Yazma hissi kabul edilince Aşama 2 kalıcı kayıt.

Kanıt: `logs/build.log`, `logs/apk-signature.log`, `core/build/test-results/test/TEST-com.gvn.notes.core.ModelTest.xml`, `app/build/reports/lint-results-debug.*`. Resmî API kaynakları `research/ink-api/` altında.
