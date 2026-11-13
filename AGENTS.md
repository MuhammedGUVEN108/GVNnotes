# AGENTS.md — GVNnotes

Bu dosya, bu projede çalışan yapay zekâ ajanları (Codex, Claude, Cursor, Gemini vb.) içindir. Güncel durum burada yazılıdır; başka bir belgeyle çelişirse **bu dosya ve kod** geçerlidir. Son güncelleme: 14 Kasım 2026, sürüm 0.15.0.

## Proje ne?

GVNnotes, Samsung Galaxy Tab S9+ (SM-X810, Android 16 / API 36) ve S Pen için Android not uygulaması. Kullanıcı ders PDF'leri ve resimleri üzerine el yazısıyla not alır. `Documents/GVNnotes` klasörüne konan PDF/resimler alt klasör düzeniyle otomatik not olur. Tek kullanıcı (proje sahibi), yerel kurulum; Play Store yok, internet yok.

Kullanıcı Türkçe konuşur. Arayüz metinleri, belgeler, kod yorumları ve commit mesajları **Türkçe** yazılır.

## Kesin kullanıcı kararları (değiştirme)

1. **Yazma tarafı GVNpen ile birebir aynı** (12 Kasım 2026): "GVNpen'in tüm özellikleri olmalı ve yazma, geçişler, ikonlar aynı olmalı." GVNpen, kullanıcının kendi kalem uygulamasıdır (`D:/Sanal Desktop/tablet kalem uygulaması`). Kodu `app/src/main/java/com/gvn/notes/pen/` altına kopyalandı. Araç sırası, simgeler, menüler, zamanlamalar (150 ms katlanma, 350/400 ms kalem yaklaşınca açılma/kapanma, 120 ms soluklaşma), çizim davranışı ve ayar ekranı **değiştirilmez**. Yalnız renk/görünüm GVNnotes temasına uyarlanabilir (kullanıcı bunu istedi).
2. **Arayüz "Petrol Ada"** (12 Kasım 2026): kütüphane, düzenleyici çerçevesi, ayarlar ve yeni not bu tasarım dilindedir. Referans: `D:/Sanal Desktop/GVNnotes Arayüz Sheetleri/6-petrol-ada.html`. Ayrıntı: [DESIGN.md](DESIGN.md).
3. Ayrı pencere/overlay yok. Çizgiler sayfa koordinatında, PDF/resim zeminiyle birlikte hareket eder.
4. Kaynak klasör eşitlemesi iki yönlüdür; "kaynak kazanır"; hedefte aynı ad varsa işlem reddedilir; uygulama kullanıcı dosyasını **asla silmez** (çöp = `.GVNnotes-çöp` klasörüne taşıma).
5. Aynı içerik iki klasörde iki ayrı nottur (not kimliği UUID, hash yalnız içerik kimliği).
6. Dosyalarım'dan silinen kaynağın notu **çöp kutusuna** gider (12 Kasım 2026; önceki "not yerinde kalır" kuralının yerine). Yalnız tamamlanmış taramada; eksik/izinsiz taramada hiçbir not çöpe gitmez. Dosya geri konursa not çöpten kendiliğinden döner. Kullanıcı çöpten geri alırsa not bir daha atılmaz.
7. **Çöp kutusu 30 gün** (`TRASH_DAYS`): süresi dolan not açılışta/yenilemede kalıcı silinir. Kaynak dosyalar `.GVNnotes-çöp`'te kalır; uygulama kullanıcı dosyası silmez.
8. **Klasör silme** alt klasörleriyle birliktedir: içindeki tüm notlar çöpe, kaynak dosyaları ve kalan klasör ağacı `.GVNnotes-çöp`'e taşınır.
9. Samsung Notes yalnız davranış referansıdır; Samsung kodu, simgesi, görseli kopyalanmaz.

Eskiyen kararlar (artık geçersiz): "GVNpen yalnız arayüz referansı, kodu taşınmaz" (10 Kasım); "çizim motoru Jetpack Ink" (0.1–0.9); "kütüphane Samsung Notes düzeninde" (0.2–0.10). Bu ifadeleri eski belgelerde görürsen uygulama.

## Kod haritası

```
core/                         Saf Kotlin (JVM testli)
  Model.kt                    Folder, Note, Page, PageStroke, Sample, Viewport, History
  PenGeometry.kt              Silgi/kement geometrisi
  SaveEnvelope.kt             Kayıt zarfı: sihirli sayı, sürüm, revizyon, SHA256, en çok 64 MiB
app/src/main/java/com/gvn/notes/pen/   GVNpen kopyası (Java) — davranışı değiştirme
  PenToolbar.java             Notun üstünde yüzen, sürüklenebilir araç şeridi + açılır menüler
  ToolIcon.java               Şerit simgeleri (Canvas)
  Prefs.java                  GVNpen ayarları ("gvnpen" SharedPreferences, profil JSON'u; DefaultsVersion 5)
  PenSettingsActivity.java    GVNpen ayar ekranı (Çizim, Silgi, Görünüm, Kalem düğmesi, Dosyalar)
  Geometry.java, SmartShape.java, PenRenderer.java, SizeScale.java, UiKit.java
app/src/main/kotlin/com/gvn/notes/
  MainActivity.kt             Giriş, tema, kütüphane ↔ düzenleyici geçişi, düzenleyici ekranı (konum çipi, Klasör adası, sayfa sıralayıcı adası, alt adalar)
  LibraryScreen.kt            Kütüphane: klasör ağacı adası, not kartları, çöp, ayarlar, yeni not penceresi
  Theme.kt                    Petrol Ada renkleri (Ada.*), AdaColors, Island, PatternPreview
  InkTileCache.kt             Görünen sayfa karoları; 64 MiB LRU, artımlı ekleme ve bölgesel geçersizleştirme
  ActiveInkCache.kt           Uzun kalemin kesinleşen başı + 128 noktalık canlı uç
  WeakIdentityCache.kt        Derin nokta hashCode maliyeti olmayan, bütçeli zayıf kimlik önbelleği
  InkPageView.kt              Yazma yüzeyi: GVNpen InkView akışı sayfa koordinatında; zoom/kaydırma, çok sayfa, kement
  EditorState.kt              Açık notun durumu (ViewModel), geri al/yinele, otomatik kayıt tetikleme
  StrokeBuild.kt              Kayıtlı çizgi ↔ GVNpen çizgisi dönüşümü
  PageStore.kt                Not kaydı: iki slotlu atomik dosya, şema 4
  NoteLibrary.kt              Not dizinleri, liste/kart önbelleği, oluşturma, taşıma, çöp; LibraryState (ViewModel)
  FolderSync.kt               Documents/GVNnotes SAF eşitlemesi, SyncIndex
  PdfPages.kt, ImageImport.kt PDF sayfa çizimi, resimden not
  PagePattern.kt              Sayfa şablonları (Düz, Çizgili, Kareli, Noktalı; kod değerleri kayıtta, değiştirme)
  Export.kt                   PDF dışa aktarma (özgün sayfa + vektör yazı)
  AppSettings.kt              Uygulama ayarları (settings.json) + CrashLog
app/src/test/java/.../pen/    GVNpen'in kendi JVM testleri (GeometryTests, EraseTests)
app/src/androidTest/          Tablet testleri (aşağıda)
```

Ayrıntılı anlatım: [docs/KOD-REHBERI.md](docs/KOD-REHBERI.md). Kayıt yapısı: [docs/KAYIT-MIMARISI.md](docs/KAYIT-MIMARISI.md).

## Veri ve uyumluluk (dikkat)

- Paket kimliği `com.gvn.notes.prototype` — **değiştirme**; değişirse kullanıcının notları yeni uygulamada görünmez.
- Kullanıcı verisi: `files/notebooks/<uuid>/` (document-a.gvn / document-b.gvn, preview.png, card.json, kaynak PDF/resim kopyası), `files/trash/`, `files/sync-index.json`, `files/sync-source.txt`, `files/settings.json`, `files/crash-log.txt`, GVNpen ayarları `shared_prefs/gvnpen.xml`.
- Kayıt şeması 4 (`PageStore.SCHEMA`). Şema 1–3 okunur. Yeni alan eklerken şemayı artır, eskiyi okumaya devam et; daha eski APK yeni şemayı açamaz (veri silinmez).
- `PagePattern` kodları ve `Prefs.json()` anahtarları kayıtlı veridir; yalnız ekleme yap.
- Yeni GVNpen varsayılanı gerekiyorsa `Prefs.load` içindeki `DefaultsVersion` göçünü kullan; kullanıcının kendi seçtiği değeri ezme.

## Derleme ve test

PowerShell 5.1 (Windows). Araç yolları `build.ps1` içinde (JDK 21, Android SDK 35, yerel Gradle 8.11.1).

```powershell
./build.ps1 -Offline        # core + app JVM testleri, debug APK, lint; APK'yı dist/GVNnotes-<sürüm>.apk olarak kopyalar
# Tablet testleri için önce test APK'sı:
gradle -p . :app:assembleDebugAndroidTest --offline   # build.ps1'deki gradle.bat ve JAVA_HOME/ANDROID_HOME ile
./DEVICE-TEST.ps1           # yalnız ilgili kodu değişen test birimlerini çalıştırır; durum docs/TEST-DURUMU.md (✅/⬜/❌)
./DEVICE-TEST.ps1 -Hepsi    # hepsini zorla (sürüm öncesi, emin olunamayan geniş değişiklikte)
./DEVICE-TEST.ps1 -Sadece folder-sync,lasso
```

- **Test atlama (kullanıcı isteği):** her birimin ilgili kaynak dosyaları `DEVICE-TEST.ps1` içindeki `$units` tablosundadır; bu dosyaların parmak izi son başarılı çalıştırmadakiyle aynıysa birim atlanır. Kayıt: `docs/test-durumu.json` (makine) ve `docs/TEST-DURUMU.md` (tikli tablo). **Yeni test ya da yeni kaynak dosyası eklerken tabloyu güncelle**; bir birimin kullandığı dosya listede yoksa o dosyadaki değişiklik testi tetiklemez. Sürüm numarası değişikliği testi tetiklemez.
- adb: `C:/Users/Muhammed/AppData/Local/Ajan-Araclari/android/sdk/platform-tools/adb.exe`, tablet seri no `TABLET-SERI-NO`.
- Tablet testleri kullanıcı notlarına dokunmaz: `files/test-document`, `files/test-library` ve test kaynak klasörleri kullanılır (yalnız debug derlemede, test intent'iyle). GVNpen ayarları testte yalnız bellekte değiştirilir, kaydedilmez.
- Test seçicileri `testTag` ve içerik açıklamalarına bağlıdır (ör. `library`, `new-note`, `note-title`, `create-note`, `editor`, `back-to-notes`, `page-sorter`, `page-indicator`, `folder-<ad>`, "Sayfa sıralayıcı", "Üst klasör", "<klasör>, N not"). Arayüzü değiştirirken bunları koru ya da testleri birlikte güncelle.
- Arayüz değişikliğinden sonra tablette ekran görüntüsü al ve bak (`adb exec-out screencap -p > logs/...png`); derlemenin geçmesi görünüşün doğru olduğunu kanıtlamaz.
- `build.ps1` **UTF-8 BOM'lu** kalmalı: BOM silinirse PowerShell 5.1 Türkçe karakterli Gradle yolunu ("yerel-araclar") bozar ve derleme internetten Gradle indirmeye çalışıp takılır. PowerShell 5.1'de `>` ikili veriyi bozar; ekran görüntüsü için bash/Git Bash kullan.

## Çalışma kuralları

- Sürüm: `app/build.gradle.kts` içinde `versionName` / `versionCode`. Her anlamlı teslimde artır.
- Her teslimde `docs/DOGRULAMA.md` başına sürüm girdisi ekle: ne değişti, nasıl doğrulandı, **neyin doğrulanmadığı**, APK SHA256. README'deki "Mevcut durum"u güncelle.
- Commit mesajı: `<sürüm>: <kısa Türkçe başlık>`. `dist/`, `logs/`, `yedek/` git'e girmez.
- Tablet bağlı değilse bunu açıkça yaz; cihaz testi yapılmamış değişikliği "test edildi" diye sunma.
- Kullanıcının ikinci beyni (Obsidian) `C:/Users/Muhammed/Desktop/IkinciBeyin`; proje kararları `knowledge/concepts/gvnnotes-samsung-referansi.md` notunda. Önemli bir kullanıcı kararından sonra oraya tarihli bir girdi ekle ve kasanın kendi `AGENTS.md` protokolünü izle (`py -3 beyin.py sync`, receipt).

## Belgeler

| Belge | İçerik |
|---|---|
| [README.md](README.md) | Özet, mevcut durum, derleme |
| [PRODUCT.md](PRODUCT.md) | Ürün amacı, kullanıcı, kısıtlar |
| [DESIGN.md](DESIGN.md) | Petrol Ada tasarım sistemi + GVNpen şeridi |
| [docs/KULLANIM.md](docs/KULLANIM.md) | Kullanım kılavuzu (kullanıcı için) |
| [docs/KOD-REHBERI.md](docs/KOD-REHBERI.md) | Kodun neyi neden yaptığı |
| [docs/KAYIT-MIMARISI.md](docs/KAYIT-MIMARISI.md) | Kayıt, kurtarma, veri konumları |
| [docs/TABLET-TESTI.md](docs/TABLET-TESTI.md) | Tablet test oturumu |
| [docs/DOGRULAMA.md](docs/DOGRULAMA.md) | Sürüm sürüm doğrulama geçmişi (tarihsel) |
| [docs/TASARIM.md](docs/TASARIM.md) | Klasör eşitleme ve veri modeli gerekçeleri (tarihsel, kısmen geçerli) |
| [docs/GVNPEN-INCELEME.md](docs/GVNPEN-INCELEME.md) | GVNpen kaynak incelemesi (tarihsel) |
| `research/` | Samsung Notes ve Ink API incelemeleri (tarihsel) |

## 0.14.0 kullanıcı kararı — 14 Kasım 2026

Kütüphane sadeleşir: sık liste varsayılan, kart görünümü seçilebilir; geniş ekranda klasör ağacı aynı klasör kartlarıyla tekrar edilmez. Düzenleyicide sayfa paneli kendi yerini ayırır, sayfa/yakınlaştırma bilgisi ve eylemler mevcut üst satırdadır. Kalıcı alt çubuk ve Kaydedildi/Çiziliyor gibi sürekli durum yazıları kullanıcı tarafından reddedildi; yalnız hatalar görünür. GVNpen şeridi davranışı korunur. PDF çıktısı özgün metin/görselleri korur, yazılar vektör eklenir. Yedekleme ve koyu tema bu sürümün dışında. İnternetten 100 MB üzeri indirme için kullanıcıdan önceden açık izin alınır.

## 0.15.0 performans doğrulaması

100 sayfalık dosyanın bir yıl boyunca kalıcı mürekkeple doldurulması kullanıcı senaryosudur; GVNpen gibi anlık çizim varsayma. docs/PERFORMANS.md ölçümünü ve kayıt sınırlarını oku. Önbellek atılması mürekkebi silmez; model ve kayıt asıl kaynaktır. Kayıt şeması 4 kaldı. Tüm belge hâlâ snapshot olarak yazılır; sınırsız veya sayfa bazlı depolama iddiası yapma. 100 sayfa/952.320 nokta kaydet-aç ve 100 sayfa önbellek tahliye testleri geçti.
