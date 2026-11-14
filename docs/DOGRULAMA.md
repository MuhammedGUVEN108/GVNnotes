# İlk prototip doğrulaması — 10 Kasım 2026

## 0.18.0 — kütüphanede toplu seçim ve yumuşak geçişler, 15 Kasım 2026

Kullanıcı kütüphane ekranı için toplu işlemleri ("3 numara kesin gelsin") ve "her şey çok sert çalışıyor" diyerek küçük, yumuşak animasyonları istedi.

- **Toplu seçim:** nota uzun basınca seçim modu. Seçimdeyken dokunma notu açmaz, seçime ekler/çıkarır. Başlık satırı seçim çubuğuna döner (ayrı alt çubuk yok): N not seçili, Tümünü seç / Seçimi kaldır, Taşı, Çöpe at, Kapat. Geri tuşu önce seçimi bırakır; klasör/arama/çöp/ayarlar değişince ve eşitleme notu kaldırınca seçim güncellenir. Tek notun ⋯ menüsü seçimde gizlenir.
- **Toplu işlemler (LibraryState.moveNotes / trashNotes):** tek işlem, tek liste yenileme, tek bildirim. Ad çakışması yalnız o notu atlar ve sayıyla bildirilir; kaynağı klasörde olan not varsa yazma izni işe başlamadan denetlenir (izin yoksa hiçbir not taşınmaz). Toplu çöpe atma onay ister; notlar 30 gün çöpte kalır, kaynak dosyalar .GVNnotes-çöp'e taşınır. Tek not işlemleri ve bildirimleri değişmedi.
- **Yumuşak geçişler (yalnız kütüphane):** liste ↔ çöp ↔ ayarlar ve başlık ↔ seçim çubuğu solarak geçer; not/klasör satırları liste değişince yerine kayar ve solarak girer/çıkar; seçili satır ve klasör ağacı vurgusu renk geçişiyle; bildirim ve içe aktarma balonu yumuşak girer. Süreler 150–220 ms, yaylanma ve tekrar yok. Düzenleyiciye geçiş ve GVNpen şeridi değişmedi (not kapanırken bellekteki durumun yeniden oluşmaması için).

Doğrulama: yeni SelectionDeviceTest — toplu taşımada bir ad çakışması (2 taşındı/1 atlandı), çöp + geri yükleme, kaynak dosyanın silinmeyip .GVNnotes-çöp'e gitmesi, yazma izni yokken hiçbir şeyin değişmemesi; arayüzde uzun bas, seçimde dokunmanın not açmaması, tümünü seç, geri tuşu ve kapat. Satır sonu değişikliği yüzünden 20 tablet biriminin hepsi yeniden çalıştı ve hepsi geçti. Gerçek kütüphanede yalnız seçim moduna girildi (taşı/çöpe at basılmadı): logs/ui-0.18.0/01-secim.png, kısa kayıt logs/ui-0.18.0/secim-kaydi.mp4. Animasyonların "yumuşak" hissi testle ölçülemez; kullanıcı onayı bekleniyor.

APK: dist/GVNnotes-0.18.0.apk, SHA256 `29e244398e642b6400b782c52b98c619bf0588129abf3a3dd2d8411158f7beaf`. Tablette kurulu.

## 0.17.3 — yazarken üst düğmeler soluklaşmıyor, 14 Kasım 2026

Kullanıcı: kalem kâğıda değince üst satırdaki **+** ve **Ekrana sığdır** siyahtan soluk siyaha geçiyor, kalkınca geri dönüyordu; "özellik o an pasifleşebilir ama bunu kullanıcıya göstermesin." MainActivity'deki `canEdit` artık yazma durumunu (`busy`) içermez; düğmeler, başlık, sayfa sıralayıcı menüleri ve seçim çubuğu görünüşte hep etkin. Yazarken basılan işlem EditorState/InkPageView içindeki mevcut `busy` korumasıyla sessizce yok sayılır (sayfa ekleme/silme/taşıma, şablon, seçim işlemleri ve sığdırma bu korumaya sahip).

Doğrulama: PdfNoteDeviceTest kalem kâğıttayken iki düğmenin etkin olduğunu ve o an basılan + düğmesinin sayfa eklemediğini denetler. Etkilenen 6 tablet birimi geçti (pdf-note, folder-sync, note-management, lasso, persistence-restart, library); diğerleri değişmedi. Derleme, JVM testleri ve lint geçti.

APK: dist/GVNnotes-0.17.3.apk, SHA256 `a15d82700e4322c469c1d70dc647fa9ac4ec8caab011fbf6fce20b454bba4272`. Tablette kurulu.

## 0.17.2 — belirli ölçekte sürekli yanıp sönen mürekkep, 14 Kasım 2026

Kullanıcı tablette (yatay, ölçek %150'nin üstünde) mürekkebin bir anda sürekli yanıp sönmeye başladığını, ölçek değişince geçip aynı ölçeğe dönünce geri geldiğini bildirdi.

**Kök neden:** InkTileCache 512 px'lik karoları 64 MiB bütçeyle (≈63 karo) tutuyor. Karo çözünürlüğü `2^ceil(log2(ölçek))` olduğundan bazı ölçeklerde görünen karo sayısı bütçeyi aşıyordu. Her tamamlanan karo `put()` sırasında ekranda görünen başka bir karoyu atıyor, atılan karo yeniden planlanıyor, `onReady` yeniden çizdiriyordu: sonsuz döngü. Arka plan yoluna (sayfa >4096 nokta) geçince görünür oldu; "bir anda" başlaması buna uyar.

**Neden testler kaçırdı:** önceki önbellek testleri 1000×800 alan çiziyordu; gerçek ekran (2800×1568) boyutunda görünen karo sayısı hiç sınanmamıştı.

**Düzeltme (InkTileCache):** bu ve önceki karede istenen karolar bütçe yüzünden atılmaz; yer yalnız ekranda olmayan karolardan açılır. Görünen alanın gerektirdiği kadar geçici aşım olur. Yalnız arka plan modunda (üretim); senkron kullanım önceki gibi sıkı bütçeli.

**Doğrulama:** yeni `realScreenZoomBandSettlesWithoutEvictingVisibleTiles` testi gerçek ekran boyunda iki yoğun sayfanın sınırını %160–%800 arası 8 ölçekte çizer; oturduktan sonra 40 karede durağanlık (yeniden çizim yok) ve bellek sınırı denetlenir. **Düzeltmesiz kodla %410'da kaldı** ("karolar hiç oturmadı"; logs/zoomband-old.txt), düzeltmeyle 8 önbellek testinin hepsi geçti (logs/zoomband-new.txt). Ölçülen karo belleği: %205/%230'da 56 MiB, %410'da 85 MiB, %460'ta 71 MiB. DEVICE-TEST: ilgili 11 birim geçti, değişmeyen 7 birim atlandı. Lint ve JVM testleri geçti.

**Gerçek uygulama ekranında ölçülen bant (yeni ZoomIdleDeviceTest):** yatay tam ekran InkPageView, yoğun mürekkepli yatay PDF (3 sayfa, sayfa sınırı ekranın ortasında), %150–%500 arası 0,1 adımlı 36 ölçek; her ölçekte durulduktan sonra 1 s'de çizim sayısı sayılır. **Düzeltmesiz kodla** sürekli yeniden çizim: %200, %210, %240 ve %410–%500 (saniyede 43–61 çizim; logs/zoomidle-old.txt). **Düzeltmeyle** 36 ölçeğin hepsi durgun (0 çizim; logs/zoomidle-new.txt). Test PDF arka planı ve sayfa hazırlama yeniden çizimlerini de kapsar; ikisi döngü üretmedi.

**Doğrulanmayan:** kullanıcının tam ölçeği bilinmiyor (yalnız "%150'den fazla, yatay"); ölçülen bant buna uyuyor ama kullanıcının ekranındaki tam durum doğrudan gözlenmedi. Dik ekran ve farklı sayfa yerleşimlerinde bant kayabilir; düzeltme ölçeğe bağlı değildir. Geçici bellek aşımı (≈+20 MiB) yerel bitmap belleğidir.

APK: dist/GVNnotes-0.17.2.apk, SHA256 `3ffd45b3094768a30f8303c66921349b17cc82b55c913bfd87a4f55d5bc6ef15`. Tablette kurulu.

## 0.17.1 — bırakınca görünüm ortaya oturur, 14 Kasım 2026

İlk 0.17.0 denemesinde bırakınca sığdırmanın altındaki ölçek geri büyütülüyordu. Kullanıcı tablette deneyip bunu reddetti: yalnız ortalanmasını, istediği kadar uzaklaşabilmeyi istedi. Bu düzeltme önceki görevdeki yanlış ölçek yorumunun yerine geçer.

InkPageView artık oturma hedefinde ve 230 ms yavaşlayan animasyonun her karesinde ölçeği aynen korur (mevcut 0.1–8 hareket sınırı). Küçük belge, PDF/boş not ayrımı olmadan yatay ve dikey ortalanır; büyük belgede konum en çok 20 dp kenar payıyla sınırlanır. Hareket serbest, oturma yalnız bırakınca/savrulma sonunda; yeni temas keser, yazarken/silerken başlatılmaz. Ekrana sığdır, sayfaya git ve boyut değişimi bekleyen hareketi keser; sığdırma işlevi sürer.

ViewportDeviceTest, test-library altında boş not ve iki sayfalı PDF ile gerçek iki parmak uzaklaştırma/bırakma, sığdırma altı ölçek ve iki eksende merkez, 0.15× ölçek, dört yönde kenar dönüşü, sınır içi konum, dar çalışma alanı, kalem gezinmesi/savrulma, meşgul durum ve yeni temasla animasyon kesilmesini denetler. DEVICE-TEST.ps1 içine yeni viewport birimi ve kaynak parmak izleri eklendi. Önceki InkCacheDeviceTest eklemesi görevle ilgilidir ve korundu: ölçek değiştiren deneme yerine sabit ölçekte 16 konum karesinde hazır mürekkebin görünürlüğünü denetler; son piksel eşitliği kontrolü sürer.

Son çevrimdışı build.ps1 -Offline başarılı: 15 core + 5 app JVM testi, 0 hata; lint 0 hata / 43 uyarı. Test APK derlemesi başarılı. Kanıt: logs/build.log, logs/test-build-0.17.1.log.

Viewport testi ve 7 önbellek testi tablette geçti. Test belgesinde 0.15× bırakma sonrası görüntüler çekilip gözle incelendi: boş not ve iki sayfalı PDF iki eksende ortada, ölçek korunmuş. Görüntüler: logs/ui-0.17.1/uzaklastir-bos.png ve uzaklastir-pdf.png. Bunlar test etkinliğinde doğrudan InkPageView görüntüleridir; gerçek kullanıcı notları üzerinde çizim/silme yapılmadı.

Son APK üzerinde DEVICE-TEST.ps1 başarılı: ilgili 11 birim (viewport, ink-cache, ink-performance, persistence-ink-regression, pdf-note, pen-tools, lasso, cursor-size, templates, finger-draw, persistence-restart) geçti; değişmeyen 7 birim önceki başarılı parmak izleriyle atlandı. Kanıt: logs/device-0.17.1.log, logs/viewport.txt, logs/ink-cache.txt; güncel durum docs/TEST-DURUMU.md ve docs/test-durumu.json. Aynı paket kimliğiyle yerinde güncellendi; uygulama tablette kurulu bırakıldı, test APK'sı kaldırıldı.

APK: dist/GVNnotes-0.17.1.apk, versionCode 28; SHA256 `046200b89e0f3e98cf5bd1d222f60e7061cb2a5def267d0741b2948f40855eb0`.

Doğrulanmayan: fiziksel parmak/S Pen ile kullanıcının hareket hissi, GPU kare süreleri ve uzun süreli saha kullanımı. Resim notu ayrıca sınanmadı; ortak belge konumlandırma kodunu kullanır. İnternetten indirme ve push yapılmadı.

## 0.16.3 — sürekli silmede karo ilerlemesi, 14 Kasım 2026

Kök neden: her silgi örneğinde artan generation, arka plandaki sonucu sürekli reddedebiliyordu. Aynı State nesnesinden gelen ara görüntü artık LRU'ya konur; generation farklıysa kirli kalır ve sonraki karede tekrar planlanır. Yalnız güncel revizyon temiz sayılır; smooth değişimi/removePage ile State değişmişse sonuç atılır. put içindeki tek değiştirme yolu bellek sayacını ve tahliyedeki kirli işaretlerini birlikte yönetir; kirli karoya drawInto ile artımlı ekleme atlanmaya devam eder.

Yeni continuousAsyncEraseMakesProgressBeforePenStops regresyonu: tek karoda 120 × 96 = 11.520 nokta (son adımda 6912 nokta, hâlâ async); 48 silme adımı, adımlar arasında yalnız 8 ms bekleme, işçinin bitmesi beklenmeden ana looper çalışır. Silinecek noktaların başlangıçta dolu olduğu doğrulanır. Silme sürerken silinen bölge en geç 150 ms içinde beyaz olmalı; kalan yazı her çizilen karede görünmeli ve ana thread raster sayacı artmamalı; bitişte senkron referansla ARGB görüntü ve tutulan bayt sayısı eşit olmalı. clear sonrası sayaç sıfır olmalı. Dosyalar zaten DEVICE-TEST.ps1 ink-cache parmak izi listesinde; yeni dosya yok.

**Düzeltmesiz kanıt:** son test sürümünde geçici olarak bd06dfd'deki generation eşitliği koşuluna dönüldü; ilk silinen bölge 153 ms sonra hâlâ dolu olduğu için kaldı (1 test / 1 failure; logs/regression-old-final.txt, logs/regression-old-final-build.log). Ardından State kimliği düzeltmesi geri kondu. Düzeltmeli tekil koşuda 48 adım sürerken 45 bölge güncellendi, en yüksek ölçülen gecikme 117 ms; son piksel eşitliği geçti (logs/regression-fixed.txt, logs/regression-latency.txt). İlk 46.080 noktalı deneme, düzeltmeli kodda da 150 ms sınırını aştığından son yük 11.520 noktaya ayarlandı; 150 ms her yoğunluk için kapasite garantisi değildir.

Son çevrimdışı build.ps1 -Offline başarılı: 15 core + 5 app JVM testi, 0 hata; lint 0 hata / 43 uyarı. Test APK derlemesi başarılı (logs/build.log, logs/test-build-0.16.3-final.log).

Son APK üzerinde DEVICE-TEST.ps1: ilgili 10 birim geçti (ink-cache, ink-performance, persistence-ink-regression, pdf-note, pen-tools, lasso, cursor-size, templates, finger-draw, persistence-restart); değişmeyen 7 birim önceki başarılı parmak izleriyle atlandı. Yeni regresyon dahil 7 önbellek testi başarılı. Tam önbellek koşusunda silme sürerken 45 bölge ilerledi, en yüksek ölçülen gecikme 90 ms; son piksel eşitliği, kalan yazı görünürlüğü ve bellek sayacı geçti. Kanıt: logs/ink-cache.txt, logs/device-0.16.3.log, logs/regression-latency-final.txt; güncel parmak izleri docs/TEST-DURUMU.md ve docs/test-durumu.json içinde. Aynı paket kimliğiyle yerinde güncellendi; test APK'sı kaldırıldı.

APK: dist/GVNnotes-0.16.3.apk, versionCode 26; SHA256 `1290fe0bf27c63e8a53d7c28d9a0e404b2b9e252d8a44962806decd236f50736`.

Doğrulanmayan: fiziksel S Pen ile silme hissi, GPU kare süreleri, uzun süreli saha kullanımı ve her sayfa yoğunluğunda 150 ms sınırı. Testler sentetik verilerle çalışır; gerçek kullanıcı notlarında çizim/silme yapılmadı. GVNpen davranışı ve kayıt biçimi değişmedi. İnternetten indirme ve push yapılmadı.

## 0.16.2 — silgi sırasında mürekkebin yanıp sönmesi, 14 Kasım 2026

**Sonraki düzeltme:** sürekli silmede gecikme açığı 0.16.3'te kapatıldı. Bu sürümdeki son görüntü/kalan yazı testi, silme sürerken görüntünün ilerlemesini denetlemiyordu.

Kök neden: silgi/geri al/kement, değişen çizgilere değen hazır karoları hemen kaldırıyordu. Yoğun sayfalarda asenkron yeniden üretim bitene kadar bu bölgeler boş veya farklı ölçekten çiziliyordu. InkTileCache artık karoyu kirli işaretler ve yenisi hazır olana kadar aynı görüntüyü gösterir. Güncel revizyon kontrolü korunur; kirli karoya artımlı ekleme yapılmaz. Bütçe/tahliye/temizle işlemleri kirli işaretleri de yönetir. GVNpen geometrisi, yan düğme, arayüz ve kayıt biçimi değiştirilmedi.

Yeni InkCacheDeviceTest regresyonu: 120 × 96 = 11.520 noktalı asenkron sayfada tüm çizgi silme, alan parçalama, geri al/yinele, kement taşıma/silme ve yenileme sürerken ekleme durumları. Her çizilen karede iki karodaki silinmeyen çizgi beyaz olmamalı; art arda değişikliklerde ana thread raster sayacı artmamalı; işler oturunca saydam mürekkep dahil sonuç senkron referansla piksel piksel aynı olmalı. Değişen InkTileCache.kt ve InkCacheDeviceTest.kt zaten DEVICE-TEST.ps1 parmak izi listelerindedir.

Çevrimdışı build.ps1 -Offline ve test APK derlemesi başarılı; 20 JVM testi, lint 0 hata / 43 uyarı. Son APK üzerinde ilgili 10 tablet birimi geçti; diğer 7 birim değişmeyen kaynak parmak izi nedeniyle atlandı. Yeni önbellek regresyonu dahil 6 önbellek testi başarılı; son görüntü ARGB olarak senkron referansla birebir aynı. Mevcut pen-tools testi kayıtlı yatay şerit tercihini dikey varsaydığı için ilk denemede takıldı; başlangıç yönü yalnız bellekte belirlenerek test kullanıcı tercihinden bağımsızlaştırıldı, tekrar geçti (dosya zaten parmak izi listesindedir). İlk eşzamanlı Gradle denemesindeki önbellek çakışması sonrasında derlemeler sırayla başarılı tamamlandı. Loglar: logs/build.log, logs/test-build-0.16.2-final.log, logs/device-0.16.2.log ve logs/device-0.16.2-final.log.

Aynı tablette yoğun sıcak çizim 6,06 ms ortanca / 6,68 ms en yüksek; zoom ana kare 8,74 / 14,54 ms (yazılım Canvas CPU ölçümü, logs/ink-0.16.2.json). Silgi değişiklikleri ana thread'de yeniden rasterleşmediği sayaçla doğrulandı. Testler sentetik test-document/test-library verilerinde çalıştı; gerçek kullanıcı notlarında çizim/silme yapılmadı. Geçici USB uyanıklığı önceki 0 değerine geri alındı.

APK: dist/GVNnotes-0.16.2.apk, versionCode 25; SHA256 `d871a652d7ae0cdbd137b556bcf366d37a506c95306cfde53bd19147fda4e8d7`. Tablette aynı paket kimliğiyle yerinde güncelleme.

Doğrulanmayan: fiziksel S Pen ile kullanıcının silme hissi, GPU kare süreleri ve uzun süreli saha kullanımı. Önceki görüntü yeni karo hazır olana kadar kısa süre kalabilir; yoğun sayfa yeniden raster işi arka plandadır. İnternetten indirme yapılmadı.

## 0.16.1 — yeni sayfada ilk çizginin yanıp sönmesi, 14 Kasım 2026

Kullanıcı sayfa değiştirince ilk yazının yanıp söndüğünü bildirdi. Kalem kalktığında canlı katman temizleniyor, henüz karosu olmayan sayfanın tamamlanmış çizgisi arka plan işçisini bekliyordu; araya boş kare giriyordu. Hazır noktaları toplam 4096'yı aşmayan hafif sayfaların eksik karoları aynı karede hazırlanır. Yoğun/soğuk sayfalarda arka plan raster ve zoom fallback sürer. Aynı karede tamamlanan karo eksik sayılmaz; yeniden açmada hazır durumu gereksiz yeni kareyi beklemez.

InkCacheDeviceTest'e ilk çizgi handoff regresyonu eklendi: boş sayfa → ilk çizgi, ana thread'den ayrılmadan kalem ve yarı saydam fosforlu için piksel sonucu senkron referansla eşit; çizgi bölgesi beyaz değil ve cache hazır. Önceki asenkron zoom/doğruluk ve önbellek tahliye testleri korunur. İlk yeniden açma denemesi hazır sayacındaki boş-kare beklemesini yakaladı; sayaç düzeltildi ve son APK tekrar denendi.

Son APK üzerinde ilgili 10 tablet birimi geçti; kalan 7 birimin kaynak parmak izi değişmedi ve önceki başarı geçerli. Lint 0 hata/43 uyarı, JVM kontrolleri başarılı. Yeniden açılan mürekkep ekranı görsel incelendi (logs/ui-0.16.1/reopened-ink.png); test uyanıklığı 0 değerine geri alındı. Çevrimdışı APK/test APK/lint ve ilgili tablet testleri: logs/first-ink-final-build.log, logs/first-ink-check.log, logs/first-ink-final-device.log. Değişmeyen kayıt/PDF/klasör birimleri önceki başarılı parmak izini korur. Fiziksel S Pen ile kullanıcının his onayı henüz yoktur. İnternetten indirme yapılmadı.

APK dist/GVNnotes-0.16.1.apk; SHA256 `dd03d57402172c8d563f9ede975e1c4192a4440362c8e253e4b00026bf9a7f6c`. Tablette aynı paket kimliğiyle yerinde güncelleme.


## 0.16.0 — sayfa bazlı kayıt ve akıcı yakınlaştırma, 14 Kasım 2026

Kullanıcı mevcut notların test olduğunu belirterek yeni kayıt mimarisini onayladı; sayfa geçişinde ve zoomda donma istemedi. SQLite şema 6: yalnız değişen sayfa/çizgi transaction içinde yazılır; değişmez noktalar küçük checksum parçalarında tutulur. Metadata açılışta, noktalar gereken sayfalar için IO'da yüklenir. Bütçeli komşu hazırlığı ve sayfa bazlı geri alma uygulanır. InkTileCache async raster yapar; zoomda hazır en yakın ölçekteki görüntü korunur. Mevcut sessiz kayıt/üst satır/alt çubuk yasağı/vektör PDF korunur; GVNpen projesi değişmedi.

Çevrimdışı build + JVM testleri + test APK + lint başarılı (20 JVM testi, 0 lint hatası / 43 uyarı; yeni uyarılar çoğunlukla KTX kolaylık önerileri). 100 sayfa / 30.000 çizgi / 2.880.000 nokta kaydet-aç; ilk açılışta noktaların lazy kalması; 3 yeni nokta için 1 sayfa/3 nokta yazımı; başlık değişiminde 0 nokta yazımı doğrulandı. SQL trigger ile transaction hatası önceki revizyonu korur; bozuk DB silinmez; eksik nokta sayısı hata verir. Async zoomda fallback ink görünür ve bitmiş sonuç senkron karolarla eşleşir.

Son SM-X810 sentetik ölçümü: yoğun sayfa sıcak çizim 6,14 ms; yoğun+uzun aktif kalem 7,29 ms; zoom 8,56 ms ortanca / 13,66 ms en yüksek ana kare. 100 sayfa/952.320 nokta ilk kayıt 2843 ms, sonraki kayıt 30,97 ms, metadata açılışı 141,83 ms. Sayfa geçişi ilk kare 3,11–6,11 ms; tam hazırlık 152–185 ms. İlk büyük kayıt önceki sürümden daha pahalıdır; IO'dadır, sonraki değişiklikler bütün notu yeniden yazmaz. Yöntem/sınırlar docs/PERFORMANS.md; logs/ink-0.16.0-final.json.

Doğrulanmayan: fiziksel parmak/S Pen uçtan uca hissi ve bir yıllık saha kullanımı. Sıfır gecikme veya sınırsız kapasite garantisi yoktur. 2000 sayfa sınırı sürer; tek çok yoğun aktif sayfanın sabitlenen noktaları RAM bütçesini aşabilir. Eski şema 4 toplam çizgi/nokta/bayt sınırları yeni biçimde uygulanmaz. Hiçbir internet indirmesi/paket kurulumu/yayın yapılmadı.

APK: dist/GVNnotes-0.16.0.apk, SHA256 `bcfeb105f3cc565af173046c6b670d2b7d24630bd8b51bbafad2aef68ef8d3df`. 17 cihaz test birimi son APK üzerinde başarılı; son iki kütüphane/klasör birimi ayrıca doğrulandı (logs/paged-final-library-device.log). Normal kullanıcı kütüphanesi açık; logs/ui-0.16.0/normal-app.png. Test uyanıklık ayarı 0 değerine geri alındı. Tablet test durumu docs/TEST-DURUMU.md. Kaynak loglar logs/paged-delivery-build.log ve logs/paged-delivery-device.log. Testler test-document/test-library ve cache verisini kullanır.


> Sürüm sürüm doğrulama günlüğü; en yeni girdi en üstte. Eski girdiler o günün kararlarını yansıtır (ör. Jetpack Ink, Samsung Notes düzeni); güncel kurallar için [AGENTS.md](../AGENTS.md).

## 0.15.0 — Kalıcı yoğun mürekkep performansı, 14 Kasım 2026

Kullanıcının 100 sayfalık dosyayı bir yıl boyunca doldurma senaryosu esas alındı. InkTileCache (64 MiB görünen parça LRU), ActiveInkCache (128 noktalık canlı uç), WeakIdentityCache (derin nokta hashCode maliyetini kaldırır), bütçeli geometri ve akışlı atomik kayıt eklendi. Küçük resimler sayfa bazında geçersizleştirilir. Şema 4 ve kayıt/geri alma/PDF/GVNpen davranışları korunur. Arayüzde yeni durum yazısı veya alt çubuk yoktur.

Aynı tablette sentetik 100 sayfa/952.320 nokta: yoğun çizim 365,03 → 11,39 ms, uzun canlı kalem 491,00 → 11,65 ms; kodlama önbelleğiyle tekrar kayıt 468,92 → 150,78 ms. CPU/sıcak yazılım çizimi ölçümüdür; FPS/fiziksel gecikme veya gerçek bir yıl saha ölçümü değildir. [Yöntem, doğrulama ve kapasite sınırları](PERFORMANS.md).

Doğrulama: çevrimdışı derleme/test APK/lint (0 hata, 36 uyarı), 20 JVM testi ve 17 cihaz birimi başarılı. 100 sayfa kaydet-aç ile 952.320 nokta eksiksiz; 100 sayfa önbellek tahliye testi geçti. Yeni dosyalar DEVICE-TEST.ps1 parmak izlerine eklendi. Başarılı, değişmeyen testler tekrar edilmedi. Uykuya bağlı siyah ekran kontrolü geçici uyanık tutularak tekrarlandı; sistem ayarı geri yüklendi. Gerçek 97 sayfalık kullanıcı PDF'si yazı eklenmeden açılıp görüntülendi: logs/ui-0.15.0/01-gercek-pdf.png. Kullanıcı S Pen hissi onayı ve çok uzun süreli saha testi henüz yoktur.

Kayıt sınırları sürer (64 MiB, 20.000 çizgi, 2.000.000 nokta); bu sürüm sınırsız/sayfa bazlı depolama vaat etmez. Yeni indirme/yayın yok. APK dist/GVNnotes-0.15.0.apk tablette kurulu; SHA256 `84a6b32e634622a6a60ffea933e1ab7c9ca0466dff8c54227471e4888e7dc016`.

## 0.14.0 — Sık liste, sessiz kayıt ve vektör PDF, 14 Kasım 2026

Kullanıcı kütüphane/klasör ekranındaki alan tekrarının azaltılmasını, sık listeyi, belgeyi örtmeyen panelleri ve PDF çıktısının düzeltilmesini istedi. Eklenen kalın alt çubuğu açıkça reddetti; son uygulamada bu çubuk kaldırıldı. Sayfa/yakınlaştırma bilgisi ile sığdırma/tam ekran mevcut üst satırda; PDF dışa aktarma üst menüde. Sürekli Kaydedildi/Çiziliyor yazıları yok, otomatik kayıt ve hata korumaları sürüyor. Yedekleme ve koyu tema özellikleri kapsam dışında.

- Sık liste varsayılan; sağ üstten kart görünümü, cihazda kalıcı seçim. Geniş ekranda klasör ağacının tekrarı olan kartlar kaldırıldı; dar ekranda kompakt klasör satırları. Ağaç 248 dp, daha dar girintiler.
- Sayfa sıralayıcı 184 dp ayrı yan sütun; tuval üzerine binmez. Boyut değişince açık sayfa korunarak sığdırılır. Altta sabit çubuk/boşluk ayrılmaz. Tam ekran sistem çubuklarını gizler; üst uygulama kontrolleri erişilebilir kalır.
- PDF: PdfBox-Android ile özgün PDF sayfası/metni/görselleri korunur; aynı GVNpen geometrisi Android PDF Canvas üzerinden vektör katman olarak eklenir. Boş sayfa şablonları da vektördür. Döndürme, kırpma ve değiştirilmiş sayfa sırası desteklenir. OCR, kaynak yer imleri/formları/dijital imzaları tam olarak yeniden kurma kapsamda değildir.
- Yeni PDF paketleri Maven Central'dan boyutları önceden kontrol edilerek alındı; toplam 14.296.843 bayt. Her biri Maven SHA1 ile doğrulandı, SHA256 listesi app/libs/SHA256SUMS.txt. Sonraki derlemeler çevrimdışı. Kullanıcının 100 MB üzeri internet indirmesi için ön izin kuralı kaydedildi.

**Doğrulama:** çevrimdışı son birleşik derleme + test APK, 17 JVM testi (4 döndürme/kırpma köşelerini denetleyen yeni test dahil) geçti. Lint 0 hata / 33 uyarı (3 uyarı yeni bcpkix bağımlılığının kullanılmayan TLS yardımcılarına ait; uygulamanın internet izni yok). SM-X810 üzerinde 15 cihaz biriminin tümü başarılı; son üst satır değişikliğinde etkilenen birimler yeniden çalıştı, değişmeyenler parmak iziyle atlandı. Durum docs/TEST-DURUMU.md; loglar logs/build-final.log ve logs/*.txt.

Yeni VectorExportDeviceTest: dört dönüş + kırpma, sayfa yeniden sıralama, araya kareli boş sayfa, kaynak dosyasının SHA256 ile değişmezliği, görünür kaynak siyah şekilleri ve kalemin konumu. İlk metin testi döndürülmüş yazıda okuyucunun eklediği satır sonlarından dolayı durdu; boşluklar normalize edilince geçti. Cihazdan alınan source.pdf/result.pdf ayrıca PyMuPDF ile açıldı: 4→5 sayfa, doğru sıra, dört SOURCE metni, sıfır gömülü resim, vektör çizimler; 1/2/3. sayfalar görsel incelendi (logs/pdf-export-0.14.0).

Son UI kontrolü: liste/kart geçişi, yatay/dikey kütüphane cihaz testleri; alt çubuğun ve sürekli kayıt yazılarının yokluğu, tam ekrandan geri çıkış ve ayrı sayfa paneli gerçek UI üzerinden denetlendi. Son ekranlar logs/ui-0.14.0/04-son-ust-satir.png, 05-son-yan-panel.png ve 06-son-kullanici-kutuphanesi.png. Önceki 01–03 görüntüleri reddedilen ara tasarımdır. Testlerden sonra normal kullanıcı kütüphanesi açıldı. Gerçek kullanıcı S Pen hissi/uzun süreli ders kullanımı ve gerçek SAF klasör silme elle bu oturumda sınanmadı.

Kurulum öncesi cihazın files/shared_prefs kopyası yedek/tablet-0.14.0-oncesi-20261114-010241.tar (446 arşiv girdisi, 259.174.400 bayt). Testler yalnız test verilerini kullandı; kullanıcı notları üzerinde çizim/silme yapılmadı.

APK: dist/GVNnotes-0.14.0.apk; SHA256 `6c56eb0c7dbcad50e2a3c781acee0d45cecdeb2d4b7bcfcb1a329fc722403f0e`; tablette kurulu.

## 0.13.0 — GVNpen şeridi yatay da olabilir, 12 Kasım 2026

Kullanıcı: "GVNpen'i yukarıdan aşağı pozisyonun yanında soldan sağa pozisyona da ekleyebilir miyiz… ayarlardan olmasın, arayüzde küçük bir tuş olsun"; "döndür simgesini internetten al"; "yataya geçince boyalar sığmamış, yatayda biraz küçült".

- Şeritte tutamağın altında küçük **Şeridi döndür** düğmesi (tutamak kadar ince). Dokununca dikey ↔ yatay; seçim GVNpen profiline `ToolbarOrientation` olarak yazılır (eski profiller dikey kalır). Araçlar, sıra, menüler ve zamanlamalar aynı; katlanma yatayda genişlikte çalışır, menüler şeridin altında (yer yoksa üstünde) açılır.
- Yataya geçince şerit altta, sayfa sayacının üstüne ortalanır (üstteki konum çipi ve yakınlaştırma paneliyle çakışmaz); dikeye dönünce eski köşesine.
- Yatayda düğmeler 32 dp, 2×2 renkler küçük, logo dar: dik ekranda da sığar.
- Simge: Lucide `rotate-cw-square` (ISC), lisans `docs/UCUNCU-TARAF.md`.

Doğrulama: derleme, JVM testleri, lint geçti. `pen-tools` dahil etkilenen 7 birim yatay şerit denetimiyle (yön, araçların aynı kalması, menünün şeridin altında açılması) tablette geçti — küçültme ve alta yerleştirme bundan sonra yapıldı; bu son hali için birimler ⬜. Tablette ekran görüntüsünde yatay şerit renkleri ve logo sığıyor. Not: tablette kullanıcının ayrı GVNpen uygulaması (com.gvn.pen) ekran üstü katman olarak açıktı; ikinci (koyu) şerit odur.

APK: `dist/GVNnotes-0.13.0.apk`, SHA256 `d7d80652692816a73360c1912da223121c2e19cddbfa46f744243cfdf4dadccf`.

## 0.12.1 — Silgi ekranda sabit boy, 12 Kasım 2026

Kullanıcı: "silginin belli bir boyutu var ama ekranı yaklaştırınca silgi daha büyük oluyor, imleç devasa oluyor; tutarlılığı sağlamalıyız." Silgi yarıçapı artık ekran ölçüsünde (GVNpen'deki gibi boyut/2 dp): yakınlaştırma ne olursa olsun imleç aynı boyda, sildiği alan tam imlecin altı; sayfadaki karşılığı ölçeğe bölünür. Kalem kalınlığı değişmedi (sayfa ölçüsünde kalır).

Doğrulama: derleme, JVM testleri, lint geçti. Yeni cihaz denetimi (`pen-tools`): aynı silgi 2× yakınlaştırmada sayfada yarı genişlikte siler. **Tablet bağlantısı koptuğu için cihaz testleri henüz çalıştırılmadı** (TEST-DURUMU.md'de ilgili birimler ⬜).

APK: `dist/GVNnotes-0.12.1.apk`, SHA256 `4a7b73b2aa343b9f34b67b5a7a9dec2f98455ef81f374a56d4c5e777b955122f`.

## 0.12.0 — Klasör silme, 30 günlük çöp, silinen dosyanın notu çöpe; akıllı test atlama, 12 Kasım 2026

Kullanıcı istekleri: "klasör silme gelmeli, silince alt klasörleriyle beraber hepsi silinmeli"; "çöp kutusunda 30 gün süre koymalı, sonra kalıcı silinmeli"; "Dosyalarım'dan bir dosya silinirse not karşılığı da çöp kutusuna taşınsın"; "bir test daha önce test edilmişse ve o alanda yeni özellik eklenmemişse test etme, tik koyarak tut".

- **Klasör silme** (`LibraryState.deleteFolder`): klasör kartı ⋯ ve klasör içinde üst ⋯ menüsü, onay penceresi. Alt ağaçtaki tüm notlar çöpe; kaynak dosyalar tek tek `.GVNnotes-çöp`'e (geri yükleme eski yola döner, klasörü yeniden oluşturur); kaynakta kalan klasör ağacı (not olmayan dosyalar dahil) `.GVNnotes-çöp`'e taşınır. Kaynakta olup yazma izni yoksa hiçbir şey yapılmaz.
- **Çöp 30 gün** (`TRASH_DAYS`): süresi dolan not açılışta/yenilemede kalıcı silinir; zamanı bilinmeyen eski çöpün süresi bugünden başlar. Çöp ekranında kalan gün.
- **Dosyalarım'da silinen kaynak:** tamamlanmış taramada yeni kaybolan kaynağın notu çöpe gider (önceki kural: not yerinde MISSING kalırdı). Dosya geri gelirse not çöpten döner; kullanıcı çöpten geri alırsa tekrar atılmaz; notu kalıcı silinmiş kayıt eşlenmez.
- **Testler:** `DEVICE-TEST.ps1` birim başına ilgili kaynak dosyalarının parmak izini tutar; değişmeyen ve geçmiş birimi atlar. `docs/TEST-DURUMU.md` (✅/⬜/❌) + `docs/test-durumu.json`. `-Hepsi`, `-Sadece`. Kütüphane testleri aynı betikte (`library`).
- Elle düzenleme (bu oturum, uygulama dışında): kullanıcının isteğiyle "5. Sınıf Matematik" klasöründen kalan 49 not (kaynak klasörü Dosyalarım'dan zaten silinmişti) uygulamanın çöp kutusuna taşındı, eşitleme kaydı TRASHED yapıldı. Birinde (14. Gün - Test 9-10) 64 çizgi yazı var; çöpte.

Doğrulama (SM-X810): JVM testleri ve lint geçti. Yeni `folder-delete` cihaz testi (alt klasörler, not olmayan dosya, uygulamada oluşturulmuş not, yeniden eşitleme, geri yükleme, 30 gün) ve güncellenen `folder-sync` (silinen kaynak → çöp, geri gelince döner, çöpten geri alınca tekrar atılmaz) dahil **13 birimin hepsi geçti**; ikinci çalıştırmada hepsi atlandı. Klasör menüsü, onay penceresi ve çöp sayacı ekran görüntüsüyle kontrol edildi (`logs/ui-0.12.0/`). Gerçek SAF (Dosyalarım) üzerinde klasör silme elle denenmedi; testler düz dosya kaynağıyla.

APK: `dist/GVNnotes-0.12.0.apk`, SHA256 `534a2ebfc659875dbebbe73ff882c2bb4d258ebba13ba34ad4059912faf18dc2`.

## 0.11.0 — "Petrol Ada" arayüzü, 12 Kasım 2026

Kullanıcı arayüzleri beğenmedi; altı tasarım yönü arasından **6-Petrol Ada**'yı seçti (`D:/Sanal Desktop/GVNnotes Arayüz Sheetleri/6-petrol-ada.html`): Derin Petrol'ün kalıcı klasör ağacı + Ada'nın yüzen panelleri. Yazma tarafı (GVNpen şeridi, ayar ekranı, çizim motoru) değişmedi; şerit zaten yüzen ve sürüklenebilir bir ada.

- **Tema** (Theme.kt): açık mavi-gri masa #E3E9F1, yüzey #F9FBFF, indigo seçim/birincil eylem #4F57B0, petrol #186C84 klasör ve eşitleme rengi. Yazma yüzeyinin masa rengi ve kement rengi buna uyarlandı.
- **Kütüphane:** solda kalıcı, yüzen klasör ağacı adası (Tüm notlar, Klasörler, iç içe daraltılabilir klasörler ve sayılar, Çöp kutusu, eşitleme kartı, Ayarlar, yeni klasör düğmesi). Dar ekranda (<840 dp) ağaç çekmeceye döner. Sağda "Yeni not" düğmesi, PDF/resim içe aktarma, klasör kısayol kartları ve önizlemeli not kartları (Tüm notlar'da klasör etiketi).
- **Düzenleyici:** başlıkta konum çipi ("Tıp Fakültesi › Fizyoloji"); dokununca **Klasör adası** açılır: aynı klasördeki notlara geçiş (önce kayıt biter), "Klasöre git", "Bu klasörde yeni". Sağ üstte yakınlaştırma/sığdır/tam ekran/dışa aktar adası; sayfa sıralayıcı sağda kapatılabilir ada; altta kayıt durumu ve sayfa sayacı (+ sayfa) adaları. "Kaydedildi" yazısının şerit altında kalması sorunu bu düzenle kalktı (yazı artık ayrı bir ada).
- **Ayarlar:** sağda kategori adası (Yazma, Kalem (GVNpen), Yeni not, Kaynak klasör, Hakkında), solda seçili bölümün kartları. "Kalem (GVNpen)" GVNpen ayar ekranını açar.
- **Yeni not:** büyük pencere: ad, klasör ağacından konum seçimi (yeni: not doğrudan seçilen klasörde oluşur), dört şablon önizlemesi.
- Cihaz testleri yeni düzene uyarlandı (geniş ekranda klasör ağacı hep açık, çekmece düğmesi yalnız dar ekranda).

- **GVNpen şeridi ve ayar ekranı renkleri** (kullanıcı isteği: "ayrıksı durmasın, özellikleri bozma"): yalnız görünüm değişti. Açık tema = ada yüzeyi #F9FBFF, seçili araç indigo zemin + vurgu çerçevesi, ayraçlar #CAD3E3, köşeler 20 dp (şerit/menü) ve 12 dp (düğme); koyu tema gece mavisi ada #1F2D42. Kaydırıcı/seçili düğme kullanıcının vurgu rengini kullanır. Ayar ekranına indigo tema (radyo, anahtar, açık durum çubuğu). Ayar biçimi 5: eski varsayılan vurgu (#4FC4DD) indigoya, tema açığa geçer; kullanıcının seçtiği özel vurgu korunur; Görünüm → Açık tema ile koyu temaya dönülebilir.
- Tablet ilk görüntülerinde bulunan kusurlar düzeltildi: "Kaydedildi" GVNpen şeridinin altında kalıyordu (alt orta adaya taşındı); üst çubuk düğmeleri sağa yaslanmıyordu.

Doğrulama (SM-X810, 12 Kasım 2026): core + app JVM testleri ve lint geçti; **13 cihaz aşaması + 2 kütüphane aşaması geçti** (son çalıştırma GVNpen renk değişikliğinden sonra). Kütüphane, düzenleyici, klasör adası, sayfa adası, ayarlar, yeni not, GVNpen şeridi/menüsü/ayarları ekran görüntüsüyle kontrol edildi (`logs/ui-0.11.0/`). Kullanıcının notları yerinde.

APK: `dist/GVNnotes-0.11.0.apk`, SHA256 `64554c3b082d55291994b0b58690e112e94fea7aef1c0916cd77e2750a114998`.

## 0.10.0 — Yazma tarafı GVNpen ile aynı, 12 Kasım 2026

Kullanıcı yazma tarafını beğenmedi: "GVNpen'in tüm özellikleri olmalı ve yazma, geçişler, ikonlar aynı olmalı." Bu, 10 Kasım'daki "GVNpen motoru taşınmaz" kararını yazma tarafı için kaldırır. Kütüphane/klasör tarafı Samsung Notes düzeninde kalır.

- **Kaynak kopya** (`app/src/main/java/com/gvn/notes/pen`, yalnız paket adı değişti): Geometry, SmartShape, ToolIcon, Prefs, SizeScale, UiKit; InkView'ün çizgi çizimi → PenRenderer; brand.png ve pencil_cursor.png.
- **Çizim motoru** (InkPageView): GVNpen InkView akışı sayfa koordinatına uyarlandı: basınç×1,4 (0,15–1,5), segment kalınlığı, yumuşatma, fosforlu %32, 0,15 birim nokta eşiği, akıllı şekil (3 birim hareket çıpası, ayarlanan bekleme, 120 ms geçiş), 2B/3B sürükleme şekilleri, kapsül silgi (tüm çizgi/alan), S Pen düğmesi (basılı tutma süresi, yaklaşınca silme), kalem imleci/silgi halkası, Android 14+ tahmin. Her kalem teması tek geri alma adımı (GVNpen checkpoint). Yakınlaştırma, çok sayfa, PDF/şablon zemini, kement ve parmak davranışı korundu. **Jetpack Ink kaldırıldı** (APK 63,6 → 56,2 MB).
- **Araç şeridi** (PenToolbar): GVNpen şeridi notun üzerinde kayan panel: tutamak, göz (katla + okuma modu; yazılar görünür kalır), gezinme (kalem sayfayı kaydırır), kalem, fosforlu, silgi, 2B, 3B, kalınlık, geri al, yinele, temizle (görünür sayfa, geri alınabilir), ayarlar, kapat (notu kapatır), 2×2 hızlı renk, renk paleti, marka. 150 ms katlanma, kalem yaklaşınca 350 ms'de açılan / 400 ms'de kapanan menüler, 120 ms soluklaşma/büyüme.
- **Ayarlar** (PenSettingsActivity): GVNpen ayar ekranının Çizim, Silgi, Görünüm, Kalem düğmesi, Dosyalar (profil) bölümleri aynen. Ayarlar GVNpen biçiminde (`gvnpen` SharedPreferences, profil dosyası uyumlu). Kütüphane ayarlarındaki "Parmakla çizim" GVNpen "Parmakla çizimi engelle"nin tersidir. Kement üst çubuğa taşındı.
- **Kayıt biçimi 4:** Ink verisi yok; basınç GVNpen katsayısı olarak saklanır. Biçim 1–3 okunur (ham basınç ×1,4; eski yarı saydam fosforlu tam renge çevrilir).

Doğrulama: GVNpen'in kendi JVM testleri (GeometryTests, EraseTests: 43.568 silgi kontrolü) kopya koda karşı geçti ve build.ps1'e eklendi; core JVM testleri geçti; lint 0 hata. Cihazda: kalem araçları testi GVNpen semantiğine göre yeniden yazıldı (yaklaşırken yan düğme, akıllı şekil, dikdörtgen, küp, temizle). Test turları iki gerçek hata yakaladı: kalkıştan sonra yaklaşma olmazsa imlecin asılı kalması (160 ms'de gizlenir) ve kısa not alanında şerit yazısının otomatik boyut çökmesi (düzeltildi; hata kaydı mekanizması çökmeyi yakaladı). Son tam çalıştırma: **13 cihaz aşaması + 2 kütüphane aşaması geçti.** 0.10.0 kuruldu; gerçek not GVNpen şeridiyle açıldı (ekran görüntüsüyle kontrol edildi). Bilinen küçük kusur: sol alttaki "Kaydedildi" yazısı şeridin altında kalabiliyor.

APK: `dist/GVNnotes-0.10.0.apk`, SHA256 `33ef6aa8c08dd8a8eea94fcda305a996b91ed048bf0f67136c9fd4f5210bedde`. Fiziksel S Pen ile kullanıcı denemesi bekleniyor.

## 0.9.1 — Tablet test oturumu, 12 Kasım 2026

Kullanıcı tableti bağladı. Önce gerçek notların yedeği alındı (`yedek/gvnnotes-notlar-20261112-0038.tar`, 28 öge, 9 kayıt dosyası; git dışında). Not: PowerShell 5.1 `>` ikili veriyi bozuyor; yedek Git Bash `adb exec-out` ile alındı ve `tar -tf` ile doğrulandı.

Otomatik cihaz testleri (gerçek SM-X810):
- İlk çalıştırmada 0.6.0 kalem araçları (alan silgisi, S Pen düğmesi, akıllı şekil, sayfa ekleme, ayar kalıcılığı) **geçti**.
- **Kement testi gerçek bir hata yakaladı:** kalem kalkarken son konum işlenmiyordu; taşıma 100 yerine 90 birim yapıldı. UP olayında son konum hesaba katılarak düzeltildi; kement, şablon ve parmakla çizim testleri geçti.
- İkinci tam çalıştırmada Ink regresyon testi başarısız oldu: kalem testi `pen.txt`'ye alan silgisi ayarı bırakmış, kalıcılık özelliği doğru çalıştığı için sonraki test bunu yükledi. Uygulama hatası değil, test yalıtımı: silgi kullanan eski testler modu açıkça ayarlar, kalem testleri ayar dosyasını temizler.
- Son tam çalıştırma: **13 cihaz aşaması + 2 kütüphane aşaması geçti** (PageStore, Ink regresyonu, PDF, klasör eşitleme, not yönetimi, iki yönlü işlemler, kalem araçları, kement, şablonlar, parmakla çizim, kalıcılık 3 aşama, kütüphane seed/verify).
- `build.ps1 -Offline` BUILD SUCCESSFUL, JVM testleri geçti, lint 0 hata / 9 uyarı.
- 0.9.1 kuruldu; kullanıcının 5 notu listede, 97 sayfalık PDF notu açıldı ("Sayfa 1 / 97", "Kaydedildi"), çökme kaydı yok.

APK: `dist/GVNnotes-0.9.1.apk`, SHA256 `1c6d37ab481efd5a5b6668a812fac12e8d16ccebf1fd60a81ed493e51dcb0174`. Sırada: kullanıcıyla S Pen elle deneme listesi (TABLET-TESTI.md bölüm 2).

## 0.9.0 — Yayına hazırlık (cihazda henüz denenmedi), 11 Kasım 2026

- **Ayarlar** (liste ⋮ → Ayarlar): Parmakla çizim (açıkken tek parmak yazar; yazarken ikinci parmak değerse yarım çizgi iptal edilip kaydırmaya geçilir; avuç eşiği korunur), yeni notların varsayılan şablonu, kaynak klasör durumu (okuma/yazma izni) ile bağla/yeniden bağla/değiştir/şimdi eşitle/bağlantıyı kaldır, sürüm ve veri saklama notu. Ayarlar `files/settings.json`.
- **Hata kaydı:** yakalanmayan istisnalar `files/crash-log.txt`'ye eklenir (en çok ~48 KB geçmiş). Ayarlar'da paylaş/temizle.
- **Kimlik:** uygulama adı "GVNnotes", özgün uyarlanabilir simge (petrol zemin, sayfa + kalem; tek renkli simge desteği). versionName `0.9.0`, versionCode 14. Paket kimliği verileri korumak için değişmedi.
- **Belgeler:** `docs/KULLANIM.md` (kullanım kılavuzu), `docs/TABLET-TESTI.md` (birlikte yapılacak test oturumu planı ve bilinen riskler), README güncellendi.

Doğrulama: `build.ps1 -Offline` BUILD SUCCESSFUL, JVM testleri geçti, lint 0 hata / 11 uyarı. `FingerDrawDeviceTest` yazıldı. **0.6.0–0.9.0 cihaz testleri tablette çalıştırılmadı.**

APK: `dist/GVNnotes-0.9.0.apk`, SHA256 `4a42f03338c931f3f812850e742cd1727e7ae8bff1eeeca21eaf2f12a85c670c`.

## 0.8.0 — Sayfa şablonları (cihazda henüz denenmedi), 11 Kasım 2026

- Boş sayfa şablonları: Düz, Çizgili (sol kenar çizgisi ile), Kareli, Noktalı; aralık 38 birim ≈ 8 mm. Ekranda, küçük resimlerde ve dışa aktarılan PDF'te çizilir; çizgiler her yakınlaştırmada 1 px.
- Yeni not penceresinde şablon seçimi. Eklenen sayfa yanındaki boş sayfanın şablonunu alır (PDF sayfasının yanına eklenirse notun ilk boş sayfasınınkini). Sayfa sıralayıcı menüsünde "Şablon: …" ile değiştirme (PDF/resim sayfalarında yok).
- **Kayıt biçimi 3:** her sayfaya şablon kodu eklendi. Biçim 1 ve 2 okunmaya devam eder (şablon = düz); ilk değişiklikte biçim 3 yazılır. 0.7.x ve öncesi biçim 3'ü açmaz, üzerine yazmaz.

Doğrulama: `build.ps1 -Offline` BUILD SUCCESSFUL, JVM testleri geçti, lint 0 hata / 11 uyarı. `TemplateDeviceTest` yazıldı; **tablette çalıştırılmadı.**

## 0.7.0 — Kement seçimi (cihazda henüz denenmedi), 11 Kasım 2026

- Araç şeridinde **Kement**: kalemle döngü çizilir, örneklerinin en az yarısı döngü içinde kalan çizgiler (aynı sayfada) seçilir. Seçim kesikli çerçeve ve hafif dolguyla gösterilir.
- Çerçevenin içinden kalemle sürükleme seçimi taşır (sürükleme sırasında yalnız çizim kaydırılır; bırakınca çizgiler örneklerden yeniden kurulur, kimlikler korunur).
- Seçim çubuğu: Sil, Çoğalt (+24 birim kaydırılmış kopya, seçim kopyaya geçer), Rengi uygula (fosforlu saydamlığı korunur), Seçimi kaldır. Her işlem tek geri alma adımı; geri/ileri al ve araç değişimi seçimi kaldırır.
- `Tool.LASSO` yalnız araçtır; kayıt okuyucu çizgilerde yalnız Kalem/Fosforlu kabul eder.

Doğrulama: JVM 14 test (kement geometrisi dahil) geçti; `build.ps1 -Offline` BUILD SUCCESSFUL, lint 0 hata / 11 uyarı. `LassoDeviceTest` yazıldı, DEVICE-TEST.ps1'e eklendi; **tablette çalıştırılmadı.**

## 0.6.0 — Kalem araçları ve hız (cihazda henüz denenmedi), 11 Kasım 2026

Kullanıcı tablet bağlı değilken 0.9.0'a kadar soru sormadan kodlanmasını, cihaz testlerinin birlikte yapılmasını istedi; kalem konusunda kendi GVNpen projesinin (`D:/Sanal Desktop/tablet kalem uygulaması`) kullanılabileceğini söyledi. Bu, önceki "GVNpen kodu taşınmaz" kararını kalem davranışları için günceller; çizim motoru Jetpack Ink olarak kalır.

- **GVNpen'den taşınan geometri** (`core/PenGeometry.kt`): süpürülen kapsül silgisi (tam sınırdan kesim, fazla/eksik silme yok) ve akıllı şekil (çizgi, üçgen, döndürülmüş dikdörtgen, daire; bükük kareler kabul, ovaller dikdörtgen olmaz). GVNpen EraseTests/GeometryTests kontrolleri `PenGeometryTest` olarak JVM'de çalışır (400 rastgele silgi senaryosu dahil): **5 test geçti**.
- **Silgi:** varsayılan "dokunulan çizgiyi tamamen sil" (GVNpen ile aynı); silgiye tekrar dokununca alan silgisine geçiş ve boyut. Alan silgisi parçaları kalem örneklerinden yeniden kurulur; sınır kutusuyla ön eleme yapılır.
- **S Pen yan düğmesi:** basılıyken geçici silgi, araç değişmez.
- **Akıllı şekil:** kalem çizgisini uçta ~0,55 sn bekletince düzgün şekle çevrilir (tek geri alma adımı); kalem ayarından kapatılabilir.
- **Renk ve kalınlık:** GVNpen paleti + ek tonlarla 24 renklik seçici; son 6 özel renk şeritte. Seçili kalınlığa tekrar dokununca 0,5–16 ince ayar. Araç/renk/kalınlık/silgi/akıllı şekil ayarları tüm notlar için hatırlanır (`notebooks/pen.txt`).
- **Sayfa:** üstteki "+" mevcut sayfanın arkasına ekler; sıralayıcı menüsünde "Arkasına boş sayfa ekle".
- **Hız:** not listesi, kayıt dosyaları değişmediyse not başına `card.json` özetinden okunur (çizgiler çözülmez).

Doğrulama: `build.ps1 -Offline` BUILD SUCCESSFUL, JVM 13 test geçti, lint 0 hata / 15 uyarı. Cihaz testi `PenToolsDeviceTest` yazıldı ve DEVICE-TEST.ps1'e eklendi; **tablette çalıştırılmadı.**

## 0.5.0 — İki yönlü klasör işlemleri, resimden not (tablette kısmen denendi), 11 Kasım 2026

Klasör oluşturma/not taşıma/çöpe atma kaynak klasöre yansır (yazma izni gerekir; mevcut bağlantıda yazma izni yoksa "Yeniden bağla" kartı). Ad çakışmasında işlem reddedilir; kalıcı silme kullanıcı dosyasını silmez. Resimden not (EXIF yönü, çoklu resim tek not), eşitleme resimleri de alır. Ada/tarihe göre sıralama, klasör adında arama, not son sayfadan açılır. TwoWayDeviceTest tablette geçti (başlatma sırasındaki bir çökme hatasını yakaladı, düzeltildi); gerçek SAF üzerinde SafWriteDeviceTest ve arayüzden taşıma denendi. Tam test seti geçti.

## 0.4.0 — Sayfa yönetimi, PDF dışa aktarma, çöp kutusu, 11 Kasım 2026

- **Sayfa yönetimi:** Sayfa sıralayıcıda her küçük resmin "⋮" menüsü: Yukarı taşı, Aşağı taşı, Sayfayı sil (onaylı). Yazılar ve PDF zemini sayfayla birlikte taşınır. Silme sayfanın yazılarını da siler ve geri alma geçmişini sıfırlar; son sayfa silinemez.
- **PDF olarak dışa aktar:** Editör menüsünde. Sistem "Kaydet" penceresiyle hedef seçilir. Her sayfa PDF zemini ve yazılarla 1500 px genişlikte (~180 dpi) görüntü olarak yazılır (raster PDF; 1000 birim = 595 pt). İlerleme ve sonuç tuvalin altında gösterilir. Özgün PDF ve not kaydı değişmez.
- **Çöp kutusu:** Not satırındaki "⋮" menüsü: Yeniden adlandır, Çöp kutusuna taşı. Not klasörü olduğu gibi `trash/` altına taşınır. Yan menüde "Çöp kutusu (N)"; çöp görünümünde Geri yükle ve Kalıcı sil (onaylı). Otomatik silme yok.
- **Eşitlemeyle etkileşim:** Klasörden gelen not çöpe atılınca eşitleme dizininde `TRASHED` olur; PDF klasörde dursa da yeniden not yapılmaz. Kalıcı silinse de geri gelmez. Geri yüklenince kaynak yeniden izlenir. Kaynak PDF bu sürümde klasörde kalır (klasöre yansıyan silme/taşıma sonraki sürüm).

Doğrulama:
- Yeni `NoteManagementDeviceTest`: 3 sayfalı PDF notunda 2. sayfaya yazı ve sayfayı başa taşıma (yazı ve PDF zemini sayfayla birlikte, sıra numaraları doğru); dışa aktarılan PDF'de 3 sayfa, zemin ve yazı piksel olarak var; yazılı sayfanın silinmesi yazıyı da siliyor; son sayfa silinemiyor; çöpteki eşitlenmiş not yeniden içe aktarılmıyor, geri yüklenince klasörüyle dönüyor, kalıcı silinince geri gelmiyor; arayüzde menüden çöpe taşıma, yan menüden çöp kutusu ve geri yükleme. **OK.** Ekran görüntüsü: `logs/ui-0.4.0/01-trash.png`.
- Tüm cihaz testleri (PageStore, Ink regresyonu 2, PDF, klasör eşitleme, not yönetimi, kalıcılık 3 aşama, kütüphane seed/verify) geçti.
- `build.ps1 -Offline`: ilk denemede lint `SuspiciousIndentation` hatasıyla durdu; satır birleştirilerek düzeltildi. BUILD SUCCESSFUL, lint 0 hata / 14 uyarı.
- 0.4.0 tablete kuruldu; kullanıcının notları ve bağlı klasör ("Klasör güncel") yerinde.

APK: `dist/GVNnotes-0.4.0-prototype.apk`, SHA256 `385c7158c7da5b76be7bab4327153d45d9fe3d0c415a5c33732bf213a889d20e`.

## 0.3.0 — Documents/GVNnotes klasör eşitlemesi, 11 Kasım 2026

Kullanıcı 0.2.1 arayüzünü onayladı ("güzel olmuş") ve klasör eşitlemesini istedi. Kaynak klasör kararı: `Documents/GVNnotes` (tablette boş olarak oluşturuldu).

**Akış:** Not listesinde "Documents/GVNnotes klasörünü bağla" kartı. Sistem klasör seçicisi doğrudan bu klasörde açılır; kullanıcı "Bu klasörü kullan → İzin ver" der. Okuma izni kalıcı alınır. Klasör uygulama açılışında, öne gelişte, not kapanınca ve "Şimdi eşitle"/"Notları yenile" ile taranır (anlık arka plan algılaması yoktur).

**Kurallar (`FolderSync`, docs/TASARIM.md ile uyumlu):**
- Yeni PDF → yeni not; klasör yolu korunur. Gizli (`.` ile başlayan, ör. `.GVNnotes-çöp`) klasörlere girilmez.
- Boyut/zaman yalnız ipucudur; değişince içerik yeniden hash'lenir. Kopyalama ve hash aynı akıştan üretilir.
- Aynı belge tekrar görülürse yeni not oluşmaz.
- Kaybolan kaynakla aynı içerikte tek yeni belge varsa **taşıma** sayılır: aynı not, aynı yazılar, klasör güncellenir. Başlık yalnız kullanıcı değiştirmemişse dosya adını izler.
- İçerik değişirse eski not ve yazıları korunur ("Kaynak PDF değişti" etiketi), yeni sürüm ayrı not olur.
- Tamamlanmış taramada bulunamayan kaynak → not silinmez, "Kaynak PDF klasörde bulunamadı · yazılar korunuyor". Yarım taramada (izin/okuma hatası) hiçbir not kayıp sayılmaz.
- Aynı içerik iki klasörde → iki ayrı not.
- Bu sürümde tek yön: klasör → uygulama. Uygulama içinden taşıma/adlandırmanın klasöre yansıması ve çöp sonraki sürümde.

**Arayüz:** "Tüm notlar"da üstte klasör kutucukları (not sayısıyla); klasöre girince alt klasör kutucukları ve o klasörün notları; geri/üst klasör, en üst klasörden "Tüm notlar"a döner. Hamburger menüde Tüm notlar, Klasörler ve girintili klasör ağacı (sayılarla), kaynak klasör durumu, "Şimdi eşitle" ve "Klasörü değiştir". Başlık altında eşitleme özeti (ör. "3 yeni PDF not oldu"), eşitleme sırasında ince ilerleme çubuğu.

**Güvenlik:** Her açık notun editör durumu kendi ViewModelStore'unda tutulur ve not kapanınca temizlenir. Böylece kapalı bir notun bellekteki eski durumu, eşitlemenin taşıdığı notu tekrar açınca diske geri yazılamaz; kapanan notun PDF önbelleği de serbest kalır. Eşitleme not açıkken çalışmaz; eşitleme sürerken açılan not, eşitleme bitince açılır.

**Doğrulama:**
- Yeni `FolderSyncDeviceTest` (düz klasör kaynağıyla, aynı motor): ilk tarama 3 not + boş klasör, çöp klasörü taranmaz; arayüzde klasör kutucukları, alt klasöre giriş, üst klasöre dönüş ve menü ağacı; tekrar taramada kopya yok; taşıma (aynı not, eklenen sayfa korunuyor); yeniden adlandırma (kullanıcının başlığı korunuyor); içerik değişimi (eski not REPLACED, yeni 3 sayfalı not); yarım tarama hiçbir şeyi kayıp saymıyor; silinen kaynak MISSING, not duruyor; geri gelen dosya kopya üretmeden yeniden bağlanıyor. **OK.** Test ilk çalıştırmada iki gerçek hata yakaladı: düz klasör kaynağının gizli klasörün içine girmesi ve en üst klasörden geri dönünce "Tüm notlar" yerine ara bir ekrana gidilmesi. İkisi de düzeltildi.
- **Gerçek SAF denemesi** (test kütüphanesinde, ayrı `Documents/GVNnotes-test` klasörüyle): seçici `Documents/GVNnotes` klasöründe açıldı, test klasörü seçilip izin verildi; alt klasördeki PDF "Alt klasor" klasöründe not oldu. Uygulama arka plandayken eklenen PDF, öne gelişte "1 yeni PDF not oldu" ile algılandı. Test klasörü ve test verisi ardından silindi.
- Tüm cihaz testleri (PageStore, Ink regresyonu 2, PDF + sayfa sıralayıcı, klasör eşitleme, kalıcılık 3 aşama, kütüphane seed/verify) geçti. `build.ps1 -Offline` BUILD SUCCESSFUL, lint 0 hata / 14 uyarı.
- 0.3.0 tablete kuruldu; kullanıcının mevcut 3 notu ve "Bağla" kartı görünüyor. Gerçek klasör bağlama kullanıcıya bırakıldı (izin kullanıcı kararıdır).

APK: `dist/GVNnotes-0.3.0-prototype.apk`, SHA256 `88953133987afe9f097484c1d7e447330cffc3ca5ccfb46321e1d57c60a84d66`.

## 0.2.1 — Samsung Notes düzeninde arayüz, 11 Kasım 2026

Kullanıcı 0.2.0'ı onayladı ("evet düzgün şuan") ve tabletteki Samsung Notes arayüzünün referans alınmasını istedi. Samsung Notes tablette yalnızca görüntülenerek incelendi; not içerikleri kaydedilmedi. Yerleşim ve davranış referans alındı; Samsung kodu, simgeleri ve görselleri kullanılmadı.

- **Not listesi:** "Tüm notlar" başlığı ve not sayısı; satırlarda 96 dp küçük resim, başlık ve "tarih · N sayfa · PDF". Sağ üstte PDF içe aktarma, arama (başlığa göre süzme) ve menü (Notları yenile); sağ altta yuvarlak "Yeni not" düğmesi.
- **Editör:** Üstte geri, dokunulunca değiştirilebilen başlık, sayfa sıralayıcı, sona sayfa ekle, tam ekran ve menü (başlığı değiştir, fare ile çizim). Solda dar, açık renkli araç şeridi: kalem, fosforlu, silgi; 5 renk; ince/orta/kalın ön ayarları; geri/ileri al. Alt kalınlık çubuğu kaldırıldı.
- **Tuval üstü:** Sağ üstte yakınlaştırma yüzdesi ve "Ekrana sığdır"; sağ altta sayfa sayacı (erişilebilirlik: "Sayfa X / N"); sol altta kayıt durumu; hata olursa üstte "Tekrar dene" bandı.
- **Sayfa sıralayıcı:** Soldaki panelde numaralı küçük resimler (PDF zemini + yazılar, arka planda üretilir); dokununca o sayfaya gider, + kutucuğu sona boş sayfa ekler.
- **Tam ekran:** Üst çubuk ve sistem çubukları gizlenir; çıkış düğmesi tuvalin sağ üstünde.

Doğrulama: PDF testine sayfa sıralayıcı adımı eklendi (panel açılır, 3. sayfaya dokunulur, sayaç "Sayfa 3 / 4" olur). Tüm cihaz testleri (PageStore, Ink regresyonu 2, PDF, kalıcılık 3 aşama, kütüphane seed/verify) geçti. `build.ps1 -Offline` BUILD SUCCESSFUL, lint 0 hata / 12 uyarı. Ekran görüntüleri `logs/ui-0.2.1/`. Tablete kuruldu; kullanıcının mevcut notları yeni listede önizlemeleriyle görünüyor.

APK: `dist/GVNnotes-0.2.1-prototype.apk`, SHA256 `762935a3d8b31390e9c714597d050387e984aed9a72a38e404b424ffde8503a7`. Kullanıcının arayüz denemesi bekleniyor.

## 0.2.0 — PDF notları, çok sayfa ve kaydırma, 11 Kasım 2026

Yeni özellikler:
- **PDF'den not:** Kütüphanedeki düğme sistem dosya seçicisini açar. PDF uygulamanın özel not klasörüne `source.pdf` olarak kopyalanır (özgün dosyaya dokunulmaz), SHA256 parmak izi alınır, her PDF sayfası bir not sayfası olur. Sayfa genişliği boş sayfayla aynı (1000 birim), en-boy oranı korunur. En çok 512 MiB / 2000 sayfa; bozuk/şifreli PDF'de açık hata gösterilir, yarım not bırakılmaz.
- **Çok sayfa:** Sayfalar alt alta dizilir. Çizgiler kendi sayfalarının yerel koordinatında saklanır. Üst çubukta "Sayfa X / N" ve önceki/sonraki düğmeleri; araç şeridinde "Sona boş sayfa ekle".
- **PDF görüntüsü:** `PdfPages`, PdfRenderer'ı tek arka plan iş parçacığında kullanır; yakınlaştırmaya göre 512–4096 px genişlikte görüntü üretir ve 160 MiB'lık önbellekte tutar. Hazırlanırken başka çözünürlükteki görüntü geçici gösterilir.
- **Kaydırma:** Tek parmakla kaydırma ve savurma, iki parmakla yakınlaştırma. 90 dp'den geniş temas avuç sayılıp yok sayılır. Belge ekran dışına kaçmaz.
- **Kayıt biçimi 2:** Sayfa listesi ve PDF kaynağı kaydedilir. Biçim 1 (eski tek sayfalı notlar) okunmaya devam eder; ilk değişiklikte biçim 2 olarak yazılır. 0.1.x uygulaması biçim 2'yi açmaz ve üzerine yazmaz.
- **Kütüphane:** Kartlar "N sayfa · PDF" bilgisini ve PDF'nin ilk sayfasını önizleme olarak gösterir.

Kalem yedeği (0.1.4 üzerine): Kütüphane testinde, yeni açılan notta çizimden hemen sonra Geri'ye basıldığında Ink'in çizgiyi hiç tamamlamadığı görüldü (yaklaşık 16 çizgide 2; `busy=true`, çizgi sayısı 0). Teslim gecikmesine karşı eklenen ilk güvenlik ağı bu durumu çözemiyordu. Artık iki deneme (~1,4 sn) sonunda çizgi kalemden kaydedilen gerçek örneklerden `MutableStrokeInputBatch` ile yeniden kuruluyor; bozulan canlı yüzey yenisiyle değiştiriliyor. Ink'in neden tamamlamadığı henüz bilinmiyor.

Doğrulama (gerçek SM-X810):
- Yeni `PdfNoteDeviceTest`: 3 sayfalı (biri yatay) test PDF'i üretilir ve içe aktarılır. Kontroller: SHA256 eşleşmesi, sayfa sayısı ve oranları, PDF içeriğinin ekranda doğru konumda piksel olarak görünmesi, beyaz alanın beyaz kalması, 2. sayfaya geçiş ve yazı (çizgi 2. sayfaya ait, yerel koordinat 400/300), sona boş sayfa ekleme, kaydın geri okunması, kütüphanede "4 sayfa · PDF" ve önizleme. **OK.** Ekran görüntüleri: `logs/pdf-screenshots/`.
- Kütüphane testi yeni yedekle 8/8 geçti (yedek 2 kez devreye girdi ve çizgi koordinatları doğru kaydedildi). Yedekten önce 6 denemede 1 hata vardı.
- Tüm cihaz testleri: PageStore, Ink regresyonu (2), PDF, kalıcılık seed/reopen/deleted, kütüphane seed/verify geçti.
- `build.ps1 -Offline`: BUILD SUCCESSFUL, JVM testleri, lint 0 hata / 12 uyarı.
- 0.2.0 tablete kuruldu. Kullanıcının eski biçim 1 notu "İlk sayfam" yazılarıyla açıldı ve görsel olarak incelendi.

APK: `dist/GVNnotes-0.2.0-prototype.apk`, SHA256 `89f43ea59e6ddee83e0cc4d76e4a15df5927aec6b2a1f2ea64c1785c3f7a5df9`. Fiziksel S Pen ile, gerçek ders PDF'leriyle kullanıcı denemesi bekleniyor. Henüz yok: `Documents/GVNnotes` klasör senkronu (0.3), resimler ve dışa aktarma (0.4), sayfa silme/sıralama.

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
