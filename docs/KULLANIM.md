# GVNnotes kullanım kılavuzu (0.12.0)

GVNnotes, Galaxy Tab S9+ ve S Pen için PDF, resim ve boş sayfalar üzerine el yazısıyla not alma uygulamasıdır. Yazma tarafı GVNpen ile aynıdır. Notlar yalnız tablette, uygulamanın kendi alanında saklanır; internet kullanılmaz.

## Kütüphane

- **Sol panel (klasör ağacı):** Tüm notlar, Klasörler ve iç içe klasörler (sayılarıyla; ok ile daralt/genişlet), Çöp kutusu. En altta eşitleme kartı (dokun: klasörü bağla ya da şimdi eşitle) ve Ayarlar. Sağ üstteki klasör+ düğmesi yeni klasör açar. Tablet dik tutulup ekran daralırsa panel ☰ düğmesiyle açılan çekmeceye döner.
- **Yeni not:** sağ üstteki **Yeni not**. Ad ver, konumu (klasör) seç, sayfa türünü seç (Boş, Çizgili, Kareli, Noktalı) → **Notu oluştur**.
- **PDF içe aktar / Resimden not:** başlığın altındaki düğmeler. Birden çok resim seçersen tek not olur, her resim bir sayfa. Asıl dosyaya dokunulmaz, kopyası alınır.
- **Klasör kartları:** bulunduğun klasörün alt klasörleri; dokununca içine girersin. Sol üstteki ← üst klasöre çıkar.
- **Klasörü sil:** klasör kartının **⋯** menüsü ya da klasörün içindeyken üstteki **⋯ → Bu klasörü sil**. Klasör **alt klasörleriyle birlikte** silinir; içindeki notlar çöp kutusuna, `Documents/GVNnotes`'taki dosyaları `.GVNnotes-çöp` klasörüne gider.
- **Not kartı:** önizleme, ad, tarih, sayfa sayısı, PDF ise "PDF"; Tüm notlar'da klasör adı. **⋯** → Yeniden adlandır, Klasöre taşı, Çöp kutusuna taşı.
- **Ara / sırala:** büyüteç notun adında ve klasöründe arar. **⋯** → Ada / Tarihe göre sırala, Yeni klasör, Ayarlar, Notları yenile, Kaynak klasörü değiştir.
- **Çöp kutusu:** Geri yükle ya da Kalıcı sil. Notlar **30 gün** kalır, sonra kendiliğinden kalıcı silinir; her notta kalan gün yazar.

## Documents/GVNnotes klasörü (otomatik not)

1. Eşitleme kartına ya da Ayarlar → Kaynak klasör → **Klasörü bağla**'ya dokun. Seçici `Documents/GVNnotes` klasöründe açılır: **Bu klasörü kullan → İzin ver**.
2. PDF ve resimlerini bu klasöre, istediğin alt klasör düzeniyle koy (ör. `1. KURUL/Histoloji/...`).
3. Uygulamayı açınca ya da uygulamaya dönünce klasör taranır; yeni dosyalar not olur, klasör düzeni korunur.

Kurallar:
- Dosyayı taşıdıysan ya da adını değiştirdiysen aynı not olarak tanınır, yazıların korunur.
- İçeriği değişen dosya yeni not olur; eski not yazılarıyla kalır ("Kaynak PDF değişti").
- Dosyalarım'dan bir dosyayı silersen notu **çöp kutusuna** gider (yazıları 30 gün orada durur). Dosyayı geri koyarsan not çöpten kendiliğinden döner. Çöpten kendin geri alırsan not yerinde kalır ("Kaynak bulunamadı · yazılar korunuyor").
- Uygulamada klasör oluşturma, not taşıma ve çöpe atma klasöre de yansır. Çöpe atılan dosya `.GVNnotes-çöp` klasörüne gider, geri yüklenince yerine döner. Uygulama hiçbir dosyayı silmez; hedefte aynı ad varsa işlemi yapmaz.
- Yazma izni yoksa listede "Yeniden bağla" kartı çıkar.

## Not ekranı

- **Üst çubuk:** ←, başlık (dokun → değiştir), **konum çipi** (ör. "art"), sağda **Kement**, **Sayfa sıralayıcı**, **+** (bu sayfanın arkasına boş sayfa), **⋯** (Tam ekran, Başlığı değiştir, PDF olarak dışa aktar, Fare ile çizim).
- **Konum çipi → Klasör paneli:** aynı klasördeki notlar; birine dokununca mevcut not kaydedilip o not açılır. **Klasöre git** kütüphanede o klasörü açar; **Bu klasörde yeni** o klasörde yeni not penceresini açar.
- **Üst satır:** başlık altında sayfa ve yakınlaştırma bilgisi; Ekrana sığdır ve Tam ekran düğmeleri. PDF dışa aktarma üstteki ⋯ menüsündedir. Kalıcı alt çubuk ve sürekli kayıt yazısı yoktur; kayıt hatası olursa uyarı görünür.
- **Sayfa sıralayıcı (sağ panel):** küçük resme dokun → o sayfaya git. Küçük resmin ⋮ menüsü: Yukarı/Aşağı taşı, Arkasına boş sayfa ekle, Şablon (boş sayfalarda), Sayfayı sil. En alttaki kutu sona sayfa ekler. ✕ ile kapanır.
- **Tuval:** parmakla kaydır, iki parmakla yakınlaştır. Not, kapattığın sayfadan açılır.
- **Kement:** yazıların etrafına döngü çiz → seçilir; çerçevenin içinden sürükle → taşınır. Üstteki çubuk: Sil, Çoğalt, Rengi uygula, Seçimi kaldır.

## GVNpen araç şeridi

GVNpen'deki şeridin aynısıdır; üstteki noktalı tutamaktan sürükleyip istediğin yere taşıyabilirsin.

- **Göz:** şeridi katlar ve okuma moduna geçer (yazılar görünür kalır). **Ok:** gezinme; kalem sayfayı kaydırır.
- **Kalem, Fosforlu, Silgi, 2B şekiller, 3B cisimler:** basılı tut ya da kalemi düğmeye yaklaştır → kalınlık / şekil menüsü açılır.
- **Kalınlık (•••), Geri al, Yinele, Temizle** (görünen sayfayı temizler, geri alınabilir), **Ayarlar** (GVNpen ayar ekranı), **✕** (notu kapatır).
- **Renkler:** 2×2 hızlı renk ve renk paleti.
- **S Pen yan düğmesi:** basılıyken siler (ayarlanabilir). **Akıllı şekil:** çizgi, üçgen, dikdörtgen ya da daire çizip kalemi kaldırmadan kısa süre beklet → düzgün şekle döner.

## Ayarlar

- **Uygulama (kütüphanede Ayarlar):** Yazma (parmakla çizim), Kalem (GVNpen ayar ekranını açar), Yeni not (varsayılan sayfa), Kaynak klasör (bağla, değiştir, şimdi eşitle, bağlantıyı kaldır), Hakkında (sürüm, hata kaydını paylaş).
- **GVNpen ayarları:** Çizim, Silgi, Görünüm (açık/koyu tema, imleç, şerit boyutu, vurgu rengi, hızlı renkler), Kalem düğmesi, Dosyalar (profil). **Kaydet** ile uygulanır.

## Bilmen gerekenler

- Uygulamayı kaldırmak notları siler. Önemli notları **PDF olarak dışa aktar**.
- PDF dışa aktarmada özgün metin ve görseller korunur; yazılar/şablonlar vektör eklenir. Taranmış belgelere kendiliğinden metin tanıma yapılmaz.
- Klasör taraması uygulama açıkken / öne gelince yapılır; uygulama kapalıyken arka planda tarama yoktur.

## Sık liste (0.14.0)

Kütüphane sık listeyle açılır. Sağ üstteki görünüm düğmesiyle kartlara geçebilir, tekrar listeye dönebilirsin; seçimin saklanır. Geniş ekranda klasörleri soldaki ağaçtan aç. Sayfa sıralayıcı açıkken belge ayrı alana sığar; panel belgeyi örtmez.
