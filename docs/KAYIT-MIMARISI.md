# Otomatik kayıt — 0.1.2

## Kullanıcı akışı

Kalem çizgisi tamamlandığında, silindiğinde, geri veya ileri alındığında kayıt otomatik başlar. Alt satırda Çiziliyor / Kaydediliyor / Kaydedildi durumu görünür. `Kaydedildi`, dosyanın yazılıp geri okunarak doğrulandığını bildirir. Kayıt hatasında açık hata ve Tekrar dene düğmesi vardır; bellekteki çizim atılmaz.

Uygulama yeniden açılınca aynı sayfa kimliği, çizgiler, renkler, kalınlıklar ve basınç/eğim örnekleri yüklenir. Görünüm sayfaya sığdırılır; son kamera yakınlaştırması ve araç seçimi henüz kalıcı değildir. Undo/redo geçmişi aynı oturumda çalışır; süreç yeniden açılınca geçmiş temiz başlar.

## Neden bu yapı?

- **Sayfa koordinatları:** Ekran çözünürlüğü veya yakınlaştırma değişince yazı sayfa üzerinde yer değiştirmez.
- **Ink storage:** Gerçek Ink girdileri ve fırça ailesi saklanır; görüntüye düzleştirilmez. Renk, kalınlık ve silgi için kullanılan ham çizgi örnekleri de sürümlü biçimde tutulur.
- **Arka plan yazıcısı:** Tek IO coroutine'i diske yazar; çizim anında ana iş parçacığına dosya işi verilmez. Biriken isteklerde son tam sayfa durumu bekletilir, eski durum yeninin üzerine yazamaz.
- **İki atomik slot:** `document-a.gvn` ve `document-b.gvn` dönüşümlü yazılır. Son kayıt bozuksa önceki sağlam kayıt açılır ve bilgi gösterilir.
- **Sürüm + SHA256:** Yarım/bozuk dosya sağlam veri gibi açılmaz. Daha yeni uygulamaya ait bilinmeyen biçim üzerine yazılmaz.
- **Başarı doğrulaması:** Dosya flush + fd.sync ile yazılır, AtomicFile tamamlanır, sonuç geri okunup beklenen baytlarla karşılaştırılır. Bazı AtomicFile hatalarının yalnız loglanmasına karşı arayüz yanlış başarı göstermez.
- **Açılış koruması:** Veri yüklenirken çizim engellenir. İki kayıt da açılamazsa boş sayfa eski kayıtların üzerine yazılmaz; dosyalar korunur.

## Sınırlar

Şu an tek boş sayfa kaydedilir; PDF/resim/klasör işleri sonraki aşamadır. Kayıt uygulamanın özel alanında `files/notebooks/main/` içindedir; bulut veya dışa aktarılan yedek değildir. Uygulamayı kaldırmak bu yerel veriyi silebilir.

Her kayıt en çok 64 MiB, 20.000 çizgi ve toplam 2.000.000 ham örnek ile sınırlıdır. Büyük notlar için parçalı sayfa/çizgi depolamasına geçilecek; sınır aşılırsa kayıt hatası gösterilir, önceki dosya korunur. Kaydedildi görünmeden süreç aniden öldürülürse henüz diske ulaşmamış son değişiklik korunmuş sayılmaz.

## Doğrulama

`DEVICE-TEST.ps1`: Kullanıcı dosyasından ayrı `files/test-document` ve test cache klasörlerinde çalışır. Çizgi/piksel regresyonu, Ink verisinin geri yüklenmesi, bozulan son slot, iki slotun da bozuk olması, başarısız dosya commit'i ve gerçek force-stop/yeniden açma döngüsünü sınar. Debug test klasörü yalnız test intent'iyle seçilir; normal açılış kullanıcı dosyasına döner.
