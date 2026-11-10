# Notlar ana ekranı

Mode: Operate. Platform: native Android / Tab S9+.

Kullanıcı not listesi, gerçek küçük önizlemeler, ad vererek yeni not oluşturma, bağımsız not açma ve listeye geri dönme adımını onayladı. Mevcut onaylı çizim ekranı korunur. Bu dar iş akışında Material not ızgarası ve açık Yeni not eylemi kullanılır; ikinci bir görsel kimlik veya alternatif gezinme dünyası tasarlanmaz.

- Material 3 TopAppBar: Notlar ve GVNnotes; yenileme eylemi.
- GridCells.Adaptive(220dp), dış 24dp, kart arası 20dp, FAB için alt 112dp.
- Gerçek sayfadan yerel üretilen PNG önizleme; beyaz sayfa. Yüklenirken hazırlık metni, hata/boş durumları ayrı.
- Kart 12dp köşe, gölge yok; footer Material surfaceContainerLow #E3EDF1. Başlık titleMedium, 2 satır/ellipsis; 1 sayfa ve göreli son değişiklik zamanı.
- Birincil eylem: petrol mavisi #186C84 ve beyaz metinli ExtendedFloatingActionButton, Yeni not.
- Ad dialogu 1–100 karakter; boş ad oluşturulamaz. Tekrarlanan adlar bağımsız UUID dosyalarıdır.
- Editör üstünde Notlara dön düğmesi + Android Back. İşlem tamamlanıp kaydedilmeden nottan ayrılmaz; kayıt hatasında kullanıcı notta kalabilir/tekrar deneyebilir.
- Önceki notebooks/main doğrudan listelenir, taşınmaz ve kaydı yeniden yazılmaz.

İlk aşama yalnız not listesi/yeni bağımsız boş not/aç/geri akışıdır. Klasörler, PDF/resim ve kaynak klasör senkronu bu ekranda hazırmış gibi gösterilmez.

Doğrulama: source LibraryScreen.kt, MainActivity.kt, NoteLibrary.kt. Gerçek tablet portre/yatay, ad dialogu, bağımsız notlar ve process-restart testleri; ekran görüntüleri logs/library-screenshots içinde. Native olduğundan HTML/CSS detector uygulanmaz.
