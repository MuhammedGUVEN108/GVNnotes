# GVNnotes

Proje merkezi: `D:/Sanal Desktop/GVNnotes`.

## Kesinleşen kararlar

- Hedef cihaz: Samsung Galaxy Tab S9+ ve S Pen.
- **Son kullanıcı kararı:** GVNpen yalnız arayüz referansıdır; mevcut kodu ve çizim motoru taşınmaz. Yeni motor Jetpack Ink tabanlı prototiple değerlendiriliyor.
- Ayrı ekran kaplaması olmayacak; çizgiler doğrudan ilgili sayfanın verisi olacak.
- Samsung Notes arayüz ve işleyiş referansı olarak inceleniyor.
- Kullanıcı talebi (10 Kasım 2026): Tablette Dosyalarım üzerinden erişilen belirli bir klasöre eklenen PDF'ler GVNnotes'ta otomatik not olacak; uygulama içinden tek tek PDF seçmek gerekmeyecek. Kesin klasör yolu henüz belirlenmedi. Arka plan algılama zamanlaması uygulama aşamasında cihazda doğrulanacak.

## Mevcut durum

Kotlin + Compose + uygulama içi Ink View prototipi oluşturuldu. Boş sayfada kalem, fosforlu, tüm çizgi silgisi, geri alma/ileri alma ve iki parmak zoom/kaydırma mevcut. **0.1.2 kalıcı kayıt kullanıcı tarafından onaylandı. 0.1.3 notlar ana ekranı, gerçek küçük önizlemeler, ad vererek yeni bağımsız not oluşturma, not açma ve güvenli geri dönüş ekler.** Kullanıcının eski İlk sayfam kaydı yerinde korunur. Yeni arayüzün kullanıcı denemesi bekleniyor.

[Samsung Notes inceleme raporu](research/samsung-notes-4.3.831/RAPOR.md)

## Sonraki adım

Kullanıcı tablette Documents/GVNnotes klasörünü bağlayıp 0.3.0 eşitlemesini dener. Sonra: uygulama içinden klasör/not taşıma-adlandırmanın klasöre yansıması ve çöp (iki yönlü), resimler, dışa aktarma, sayfa silme/sıralama.

## Belgeler

- [Düzeltilmiş tasarım ve açık kararlar](docs/TASARIM.md)
- [GVNpen kaynak incelemesi](docs/GVNPEN-INCELEME.md)
- [Kodun neyi neden yaptığı](docs/KOD-REHBERI.md)
- [Otomatik kayıt ve kurtarma yapısı](docs/KAYIT-MIMARISI.md)

## Derleme

`./build.ps1` birim testleri, debug APK ve Android lint çalıştırır. Mevcut JDK21/SDK35 ve yerel Gradle8.11.1 kullanılır. İlk çalışmada resmî Maven depolarından bağımlılıklar indirilebilir. Sonraki çalışmada `./build.ps1 -Offline` kullanılabilir. Android Studio bu klasörü Gradle projesi olarak açabilir.

Güncel çıktı: `dist/GVNnotes-0.3.0-prototype.apk`: Documents/GVNnotes klasör eşitlemesi ve klasör ağacı, Samsung Notes düzeninde arayüz, PDF notları, çok sayfa; [ayrıntı](docs/DOGRULAMA.md).

0.1.1: Tamamlanan çizgilerin Canvas sayfa dönüşümü düzeltildi; yukarı sıçrama ve görünen yazıyla silgi konumunun ayrılması aynı kök nedene bağlıydı. Gerçek tablet üzerinde sentetik stylus olayı + ekran pikseli kullanan regresyon testi eklendi. Test sonucu docs/DOGRULAMA.md içinde.
