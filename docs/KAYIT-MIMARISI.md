# Kayıt ve kurtarma — 0.11.0

## Kullanıcı akışı

Kalem kalktığında, silindiğinde, geri alındığında ya da sayfa işlemlerinde kayıt otomatik başlar. Düzenleyicinin alt ortasındaki adada Çiziliyor / Kaydediliyor / Kaydedildi görünür. "Kaydedildi", dosyanın yazılıp geri okunarak doğrulandığını bildirir. Kayıt hatasında hata ve "Tekrar dene" çıkar; bellekteki çizim atılmaz. Not kapatılırken (geri ya da Klasör adasından başka nota geçiş) kayıt bitene kadar beklenir; kaydedilemezse "Notta kal / Tekrar dene" sorulur.

Not yeniden açılınca sayfalar, çizgiler, renkler, kalınlıklar ve basınç örnekleri yüklenir; not kapatıldığı sayfadan açılır. Geri al geçmişi yalnız oturum içindir.

## Veri konumları (uygulamanın özel alanı, `files/`)

| Yol | İçerik |
|---|---|
| `notebooks/<uuid>/document-a.gvn`, `document-b.gvn` | Notun kaydı (iki slot) |
| `notebooks/<uuid>/preview.png`, `preview-revision.txt` | Kütüphane önizlemesi (silinirse yeniden üretilir) |
| `notebooks/<uuid>/card.json` | Liste özeti önbelleği (silinirse yeniden üretilir) |
| `notebooks/<uuid>/` içindeki kaynak kopyası | İçe aktarılan PDF/resimlerin kopyası (asıl dosyaya dokunulmaz) |
| `trash/<uuid>/` | Çöpteki notlar (`trashed-at.txt` ile) |
| `sync-index.json`, `sync-source.txt` | Klasör eşitleme dizini ve bağlı SAF klasörü |
| `settings.json` | Uygulama ayarları (varsayılan şablon) |
| `crash-log.txt` | Çökme kaydı (en çok 64 KB) |
| `shared_prefs/gvnpen.xml` | GVNpen ayarları (araç, renk, kalınlık, tema, şerit konumu…) |
| `test-document/`, `test-library/` | Yalnız tablet testleri; kullanıcı verisi değildir |

Bulut ya da dış yedek yoktur. Uygulamayı kaldırmak bu verileri siler; önemli notlar PDF olarak dışa aktarılmalı.

## Neden bu yapı?

- **Sayfa koordinatları:** ekran çözünürlüğü veya yakınlaştırma değişince yazı sayfa üzerinde yer değiştirmez. 1000 birim = A4 genişliği.
- **İki atomik slot:** `document-a.gvn` ve `document-b.gvn` dönüşümlü yazılır. Son kayıt bozuksa önceki sağlam kayıt açılır ve bilgi gösterilir. İki slot da açılamazsa boş sayfa eski dosyaların üzerine yazılmaz.
- **Zarf + SHA256 (`core/SaveEnvelope`):** yarım/bozuk dosya sağlam veri gibi açılmaz; daha yeni bir uygulamanın yazdığı bilinmeyen biçimin üzerine yazılmaz.
- **Başarı doğrulaması:** flush + fd.sync, AtomicFile tamamlanır, sonuç geri okunup karşılaştırılır.
- **Arka plan yazıcısı:** tek IO coroutine'i yazar; biriken isteklerde son tam durum yazılır, eski durum yeninin üzerine yazamaz.
- **Açılış koruması:** veri yüklenirken çizim engellenir.

## Biçim sürümleri

- Kayıt şeması **4** (`PageStore.SCHEMA`, 0.10.0): Jetpack Ink verisi yok; basınç GVNpen katsayısı (0,15–1,5) olarak saklanır. Şema 1–3 okunur (ham basınç ×1,4; eski yarı saydam fosforlu tam renge çevrilir).
- Daha eski bir APK'ya dönülürse şema 4 notlar açılmaz (veri silinmez).
- Sınırlar: kayıt başına 64 MiB, 2000 sayfa.

## Doğrulama

`DEVICE-TEST.ps1` içindeki kayıt aşamaları: zarf kodlama, çizgi/piksel regresyonu, bozulan son slot, iki slotun bozuk olması, başarısız commit ve gerçek force-stop / yeniden açma döngüsü (seed → reopen → deletion-reopen). Testler `files/test-document` kullanır; kullanıcı notlarına dokunmaz.
