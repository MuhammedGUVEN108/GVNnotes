# Yoğun mürekkep performansı — 0.16.0

## Yeni depolama ve yakınlaştırma

Şema 6 SQLite kayıt yalnız değişen sayfa/çizgiyi günceller. Noktalar sayfa gerektiğinde IO'da yüklenir; aktif sayfa ve bütçeye sığan komşuları hazırlanır. Ekran karoları arka planda üretilir; zoom sırasında en yakın hazır ölçekteki mürekkep görüntüsü kullanılır. Yeni ölçek netleşince karolar yerini alır. Eski görüntünün yalnız kesişen parçaları çizilir. Kalem geometrisi ve PDF vektör çıktısı aynı kaynaktan üretilmeye devam eder.

Aynı SM-X810 tablette son kontrollü yazılım Canvas ölçümü (`logs/ink-0.16.0-final.json`):

| İş | Süre |
|---|---:|
| Yoğun sayfa sıcak çizim, 2000 × 96 nokta | 6,14 ms ortanca |
| Yoğun sayfa + 12.000 noktalık aktif kalem | 7,29 ms ortanca |
| 80 adımlık zoom taraması, ana kare | 8,56 ms ortanca / 13,66 ms en yüksek |
| 100 sayfa / 952.320 nokta, ilk kayıt (IO) | 2843 ms |
| Değişmeyen noktalarla sonraki kayıt (IO) | 30,97 ms |
| Noktaları yüklemeden metadata açılışı | 141,83 ms |
| 2/3/51/52/100. sayfaya geçiş, ilk ana kare | 3,11–6,11 ms |
| Aynı geçişlerde tam mürekkep hazırlığı | 152–185 ms |

İlk büyük kayıt SQLite parça/hash/index maliyeti nedeniyle 0.15.0'dan daha yavaştır; IO'da çalışır. Tek sayfa eklemesinde bütün belge tekrar yazılmaz. Sıcak ölçümden önce background işler ve ana thread callback'leri tamamlanır; boş/eksik görüntü performans sonucu sayılmaz. Sayfa geçişi denemesi kasıtlı bekleme olmadan art arda yapılır; komşu hazırlığının tamamlanması garanti edilmez. Bu sayfalar 100 çizgi / 9600 nokta yoğunluğundadır; çok daha yoğun tek sayfanın hazırlığı daha uzun sürebilir.

Bunlar CPU çizim/IO süreleridir; GPU FPS, fiziksel S Pen gecikmesi veya bir yıllık saha dayanıklılığı ölçümü değildir. Ana thread donmadan kısa bir soğuk sayfa hazırlığı olabilir. Nokta ve geometri LRU'ları toplam uygulama RAM sınırı değildir; tek aktif sayfa nokta bütçesini aşabilir.

## Kapasite ve doğruluk

100 sayfa / 30.000 çizgi / 2.880.000 nokta kaydet-aç testi geçti: eski 64 MiB/20k çizgi/2M nokta sınırları SQLite biçiminde uygulanmaz. Metadata açıldığında ilk/son sayfanın noktaları henüz yüklenmemiştir. Tek sayfaya 3 nokta eklemek 1 sayfa/3 nokta yazar; başlık değiştirmek 0 nokta yazar. Sayfa sayısı 2000 sınırı sürer; disk/RAM ve tek sayfanın yoğunluğu pratik sınırdır.

Async karo testi zoom sırasında mürekkebin kaybolmadığını, hazır sonuçta senkron karolarla RGB ortalama farkın 0,01/255 altında olduğunu denetler. Önceki basınç/saydamlık/kalem/silgi/kement/vektör PDF ve process-restart kontrolleri korunur. Bozuk DB silinmez; SQL trigger ile başarısız transaction önceki revizyonu korur; eksik nokta kümesi geçerli çizgi sayılmaz.

Aşağıdaki 0.15.0 sonuçları tarihsel karşılaştırmadır; oradaki şema 4 sınırları güncel SQLite biçimine uygulanmaz.

---

## Önceki 0.15.0 ölçümleri

## İş yükü ve yöntem

Kullanıcı senaryosu: aynı 100 sayfalık ders dosyasının bir yıl boyunca kalıcı mürekkeple doldurulması. GVNpen'in anlık overlay modeli burada yeterli varsayım değildir.

Aynı SM-X810 / Android 16 tablette 0.14.0 ile 0.15.0 karşılaştırıldı. Sentetik çizgiler deterministik; zamanlama Canvas ile gerçek Android View çiziminin 7 tekrarından ortanca CPU süresidir, ısınmış önbellek kullanılır. Yazılım Canvas ölçümü GPU FPS veya fiziksel kalem uçtan uca gecikmesi değildir. Farklı yükler ayrı ölçüldü; sonuçlar gerçek bir yıllık kullanım testi sayılmaz.

| İş yükü | 0.14.0 | 0.15.0 son ölçüm |
|---|---:|---:|
| Hafif sayfa, 20 çizgi | 5,45 ms | 5,81 ms |
| Yoğun sayfa, 2000 × 96 nokta | 365,03 ms | 11,39 ms |
| Aynı sayfa + 12.000 noktalık canlı kalem | 491,00 ms | 11,65 ms |
| İlk sayfa hafif, toplam 100 sayfa / 952.320 nokta | 25,64 ms | 11,30 ms |
| Bu dosyanın ilk kaydı (IO) | 1579,17 ms | 847,40 ms |
| Aynı dosyanın kodlama önbelleğiyle tekrar kaydı (IO) | 468,92 ms | 150,78 ms |

0.15.0'da bu dosyanın açılması 944,98 ms sürdü; sayfa/çizgi/nokta sayıları ve ilk/son çizgilerin verileri doğrulandı. Önceki sürüm için aynı açma ölçümü yoktur. İlk soğuk karo oluşturma süresi yukarıdaki sıcak çizim süresinden farklıdır; yeni sayfa/ölçek ilk kez rasterleştirilirken geçici maliyet olabilir.

Ham kayıtlar (git dışında): logs/performance/ink-0.14.0-100-pages.json ve ink-0.15.0-final.json; test InkPerformanceDeviceTest.kt. Daha erken 0.15.0 ölçümü 139 ms tekrar kayıt vermişti; tabloda yeni kaydet-aç kontrolünü de içeren son çalıştırma kullanılır.

## Neler değişti?

- GVNpen'de bulunan tamamlanmış mürekkep önbelleği yaklaşımı sayfa koordinatlarına uyarlandı. InkTileCache: sadece görünen 512 px parçalar, 64 MiB LRU, ölçek kademeleri, parça kenarında pay; yazmada artımlı ekleme, silgi/geri al/renk/kementte etkilenen parçaların yeniden üretilmesi.
- Gezilmiş her sayfa için kalıcı bitmap tutulmaz. 100 sayfayı sırayla ziyaret edip ilk sayfaya dönme testinde 3 MiB test bütçesi aşılmadı ve tahliye edilen mürekkep doğru geri üretildi. Bu önbellek bütçesidir, uygulamanın toplam RAM miktarı değildir.
- ActiveInkCache: uzun kalemin sabit başı biriktirilir; son 128 nokta civarı canlı çizilir. Basınç, eğri ve saydamlık korunur. Fosforlu çizgiler tek yol olarak kalır.
- PageStroke veri sınıfının hashCode'u bütün noktaları dolaşıyordu; WeakHashMap erişimleri bu yüzden ucuz değildi. Bounds/kodlama/geometri erişimleri zayıf nesne kimliğiyle yapılır. Geometri önbelleği 300.000 nokta, kodlanmış veri önbelleği 64 MiB ile sınırlıdır.
- Kayıt sırasında 28–64 MiB veri dizilerinin birkaç kopyası birden üretilmez. Payload geçici dosyaya akışla, SHA256 zarf slot dosyasına akışla yazılıp doğrulanır. Açılışta iki slotun nokta nesneleri aynı anda çözülmez.
- Küçük resim anahtarından belge genelindeki revizyon çıkarıldı; başka sayfadaki yazı görünmeyen/değişmeyen küçük resimleri yeniden çizdirmiyor.

## Doğruluk ve sınırlar

20 JVM testi, 17 cihaz test birimi; yeni testler: kimlik önbelleğinde derin hash çağrılmaması ve bütçe/LRU; zarfın akışlı biçim uyumu ve bozulma/yarım dosya; kalem basıncı/saydamlığı/yumuşatmada parça sınırları; karo ekle/sil/geri al/renk/pan/zoom; 100 sayfa tahliye ve yeniden üretme; 100 sayfa/952.320 nokta kalıcı kaydet-aç. Kesirli zoomda raster örneklemesinin kenar yumuşatma farkı için RGB ortalama fark eşiği 2,5/255, 1× işlemler ve canlı kalem için 1,5/255 kullanıldı. İlk kesirli zoom kontrolü 1,604/255 farkla sıkı eşiğe takılmıştı; geometri/yerleşim değişikliği değildi, eşik yalnız kesirli örnekleme için ayrıldı. Mevcut gerçek View piksel/silgi/yeniden açma testleri de geçti.

Bir yeniden açma ekran testi tablet uykuya geçince siyah şarj ekranını mürekkep sandı. Cihaz test sırasında geçici USB uyanıklığıyla tekrar denendi, geçti; önceki sistem ayarı (0) geri yüklendi.

**Kayıt şeması 4 ve kapasite sınırları değişmedi:** 64 MiB kayıt, 20.000 çizgi, 2.000.000 nokta, 2000 sayfa. Nokta sınırı yazmadan önce de denetlenir; okunamayacak kayıt başarıyla kaydedilmiş gibi gösterilmez. Model hâlâ bütün notu bellekte açar ve tam snapshot yazar. Bu çalışma sınırsız birikim veya sayfa bazlı lazy depolama değildir. Çok daha büyük yıl boyu dosyalar için sayfa bazlı kayıt/lazy yükleme ayrıca tasarlanmalıdır; sayfa sayısını tek başına sınırsız kapasite kanıtı sayma.

Dışa aktarılan PDF hâlâ vektördür; ekran karoları kayıt veya PDF kaynağı değildir. GVNpen projesi değiştirilmedi, yeni internet indirmesi yapılmadı. Fiziksel S Pen hissi ve gerçek bir yıl boyunca saha davranışı kullanıcı geri bildirimi bekler.
