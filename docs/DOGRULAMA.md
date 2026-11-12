# İlk prototip doğrulaması — 10 Kasım 2026

## 0.11.0 — "Petrol Ada" arayüzü, 12 Kasım 2026

Kullanıcı arayüzleri beğenmedi; altı tasarım yönü arasından **6-Petrol Ada**'yı seçti (`D:/Sanal Desktop/GVNnotes Arayüz Sheetleri/6-petrol-ada.html`): Derin Petrol'ün kalıcı klasör ağacı + Ada'nın yüzen panelleri. Yazma tarafı (GVNpen şeridi, ayar ekranı, çizim motoru) değişmedi; şerit zaten yüzen ve sürüklenebilir bir ada.

- **Tema** (Theme.kt): açık mavi-gri masa #E3E9F1, yüzey #F9FBFF, indigo seçim/birincil eylem #4F57B0, petrol #186C84 klasör ve eşitleme rengi. Yazma yüzeyinin masa rengi ve kement rengi buna uyarlandı.
- **Kütüphane:** solda kalıcı, yüzen klasör ağacı adası (Tüm notlar, Klasörler, iç içe daraltılabilir klasörler ve sayılar, Çöp kutusu, eşitleme kartı, Ayarlar, yeni klasör düğmesi). Dar ekranda (<840 dp) ağaç çekmeceye döner. Sağda "Yeni not" düğmesi, PDF/resim içe aktarma, klasör kısayol kartları ve önizlemeli not kartları (Tüm notlar'da klasör etiketi).
- **Düzenleyici:** başlıkta konum çipi ("Tıp Fakültesi › Fizyoloji"); dokununca **Klasör adası** açılır: aynı klasördeki notlara geçiş (önce kayıt biter), "Klasöre git", "Bu klasörde yeni". Sağ üstte yakınlaştırma/sığdır/tam ekran/dışa aktar adası; sayfa sıralayıcı sağda kapatılabilir ada; altta kayıt durumu ve sayfa sayacı (+ sayfa) adaları. "Kaydedildi" yazısının şerit altında kalması sorunu bu düzenle kalktı (yazı artık ayrı bir ada).
- **Ayarlar:** sağda kategori adası (Yazma, Kalem (GVNpen), Yeni not, Kaynak klasör, Hakkında), solda seçili bölümün kartları. "Kalem (GVNpen)" GVNpen ayar ekranını açar.
- **Yeni not:** büyük pencere: ad, klasör ağacından konum seçimi (yeni: not doğrudan seçilen klasörde oluşur), dört şablon önizlemesi.
- Cihaz testleri yeni düzene uyarlandı (geniş ekranda klasör ağacı hep açık, çekmece düğmesi yalnız dar ekranda).

Doğrulama: core + app JVM testleri ve lint geçti; cihaz testleri derleniyor. **Tablet bağlı değildi: cihaz testleri çalıştırılmadı, arayüz cihazda görülmedi.**

APK: `dist/GVNnotes-0.11.0.apk`, SHA256 `ce9836173dcec356ac44663dbf73337ac77d234760dcb5d21f3928eacbd179bc`.

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
