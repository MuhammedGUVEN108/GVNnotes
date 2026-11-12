---
name: GVNnotes
description: "Petrol Ada: kalıcı klasör ağacı ve yüzen adalar; yazma tarafı GVNpen"
colors:
  desk: "#E3E9F1"
  island-surface: "#F9FBFF"
  ink: "#1F2D42"
  muted: "#58687F"
  accent-indigo: "#4F57B0"
  accent-soft: "#E6E8FA"
  line: "#CAD3E3"
  petrol-folder: "#186C84"
  danger: "#B3261E"
  paper: "#FFFFFF"
  pen-tile: "#EEF1F8"
  pen-dark-island: "#1F2D42"
  pen-dark-tile: "#2C3B54"
  pen-dark-selected: "#3A4380"
rounded:
  island: "24dp"
  card: "20dp"
  row: "16dp"
  pen-bar: "20dp"
  pen-tile: "12dp"
  pill: "28dp"
spacing:
  island-inset: "16dp"
  grid-gap: "22dp"
  touch-target: "48dp"
components:
  island:
    backgroundColor: "{colors.island-surface}"
    rounded: "{rounded.island}"
    elevation: "6dp"
  tree-row-selected:
    backgroundColor: "{colors.accent-soft}"
    textColor: "{colors.ink}"
    rounded: "{rounded.row}"
    height: "48dp"
  primary-button:
    backgroundColor: "{colors.accent-indigo}"
    textColor: "#FFFFFF"
    height: "48dp"
  folder-icon:
    textColor: "{colors.petrol-folder}"
  pen-tool-selected:
    backgroundColor: "{colors.accent-soft}"
    borderColor: "{colors.accent-indigo}"
    rounded: "{rounded.pen-tile}"
---
# Design System: GVNnotes — "Petrol Ada"

Kullanıcı 12 Kasım 2026'da altı tasarım yönünden **6-Petrol Ada**'yı seçti: Derin Petrol'ün kalıcı klasör ağacı + Ada'nın yüzen panelleri. Referans sheet: `D:/Sanal Desktop/GVNnotes Arayüz Sheetleri/6-petrol-ada.html`. Kod kaynağı: `app/src/main/kotlin/com/gvn/notes/Theme.kt` (Ada.* renkleri, `AdaColors`, `Island`).

## Overview

Klasörler sabit bir omurga, araçlar yüzen adalar. Masa açık mavi-gri; her şey bu masanın üstünde yumuşak gölgeli, yuvarlak köşeli adalarda durur. Kâğıt beyazdır ve en çok yeri alır.

- **Klasör ağacı adası** kütüphanede hep görünür (≥ 840 dp); dar ekranda çekmeceye döner.
- **Düzenleyicide** sayfa sıralayıcı, eylemler, kayıt durumu ve klasör listesi ayrı adalardır; gerektiğinde açılır, işi bitince sayfaya yer açar.
- **GVNpen araç şeridi** de bir adadır: yüzen, sürüklenebilir, katlanabilir. Düzeni ve davranışı GVNpen'den gelir ve değişmez; yalnız renkleri bu sisteme uyarlanmıştır.
- Renk rolleri: **indigo** = seçim ve birincil eylem; **petrol mavisi** = klasör ve eşitleme kimliği; **ink/muted** = metin.

## Colors

| Token | Hex | Kullanım |
|---|---|---|
| desk | #E3E9F1 | Uygulama ve yazma yüzeyi zemini |
| island-surface | #F9FBFF | Adalar, kartlar, üst çubuk, GVNpen şeridi (açık tema) |
| ink | #1F2D42 | Başlık ve gövde metni, simgeler |
| muted | #58687F | İkincil metin, sayılar, pasif simgeler |
| accent-indigo | #4F57B0 | Seçili durum çerçevesi, birincil düğme, kaydırıcı, kement |
| accent-soft | #E6E8FA | Seçili satır/araç zemini, konum çipi |
| line | #CAD3E3 | Ayraçlar, kenarlıklar |
| petrol-folder | #186C84 | Klasör simgeleri, eşitleme simgesi |
| danger | #B3261E | Kalıcı silme, kaynak bulunamadı |

GVNpen şeridi temaları (`Prefs.bg/surface/selectedBg/line`): açık tema island-surface / #EEF1F8 karo / accent-soft seçili; koyu tema #1F2D42 ada / #2C3B54 karo / #3A4380 seçili. Seçili araç çerçevesi kullanıcının "Vurgu rengi"dir (varsayılan indigo). Varsayılan GVNpen teması açıktır; koyu tema Görünüm ayarından seçilebilir.

## Typography

Sistem fontu (Roboto/Samsung One), Material 3 ölçeği, sp. Ekran başlığı 28 sp SemiBold; ada başlığı 22 sp Bold; kart başlığı 16 sp SemiBold; satır 15 sp; ikincil 12–13 sp muted. Yeni not penceresi başlığı 32 sp.

## Layout

- Kütüphane: solda 288 dp klasör ağacı adası (16 dp dış boşluk), sağda 84 dp başlık satırı (başlık + alt bilgi, ara, menü, "Yeni not"), PDF/Resim düğmeleri, klasör kısayol kartları (232 dp), not kartı ızgarası (en az 212 dp sütun, 22 dp aralık, önizleme oranı 1,3).
- Düzenleyici: 68 dp üst çubuk (geri, başlık, konum çipi; sağda kement, sayfa sıralayıcı, +, ⋯). Tuval tüm alanı kaplar. Sağ üst eylem adası; sağda 212 dp sayfa sıralayıcı adası; alt ortada kayıt durumu + sayfa sayacı adası. Sol alt köşe GVNpen şeridine bırakılır (orada başka ada yok).
- Ayarlar: solda seçili bölümün kartları, sağda 272 dp kategori adası.
- Tüm dokunma hedefleri en az 48 dp.

## Elevation & Depth

Adalar 6 dp gölge; kartlar 2–3 dp; Klasör adası ve yeni not penceresi 12 dp. Klasör adası açılınca tuval %10 ink perdesiyle hafifçe kararır.

## Shapes

Ada 24 dp, kart 20 dp, satır 16 dp, hap (pill) 28 dp köşe. GVNpen şeridi ve menüleri 20 dp, şerit karoları 12 dp. Kâğıt köşesizdir.

## Components

- **Klasör ağacı adası:** "GVNnotes" + yeni klasör düğmesi; Tüm notlar, Klasörler, iç içe daraltılabilir klasörler ve not sayıları; Çöp kutusu; eşitleme kartı (dokun: bağla / eşitle); Ayarlar.
- **Not kartı:** yüzen önizleme, başlık, "tarih · N sayfa · PDF", Tüm notlar'da klasör etiketi, ⋯ menüsü (yeniden adlandır, taşı, çöpe at).
- **Konum çipi + Klasör adası:** başlıkta "Klasör › Alt klasör"; dokununca aynı klasördeki notlar (açık olan işaretli), "Klasöre git", "Bu klasörde yeni". Başka nota geçmeden önce kayıt tamamlanır.
- **Sayfa sıralayıcı adası:** "Sayfalar N" + kapat; küçük resimler, ⋯ menüsü, sona sayfa ekleme kutusu.
- **Yeni not penceresi:** ad, klasör ağacından konum, dört şablon önizlemesi.
- **GVNpen şeridi:** tutamak, göz, gezinme, kalem, fosforlu, silgi, 2B, 3B, kalınlık, geri al, yinele, temizle, ayarlar, kapat, 2×2 hızlı renk, renk paleti, GVNpen markası. Sıra ve simgeler GVNpen'den; değiştirme.

## Do's and Don'ts

### Do:
- **Do** Yeni yüzeyleri `Island` ile, renkleri `Ada.*` ile kur.
- **Do** Seçimi hem görsel durum hem erişilebilirlik semantiğiyle (`selected`, içerik açıklaması) belirt.
- **Do** Arayüz değişikliğini tablette ekran görüntüsüyle kontrol et.

### Don't:
- **Don't** GVNpen şeridinin araçlarını, sırasını, simgelerini, zamanlamalarını veya davranışını değiştirme; yalnız renk/şekil uyarlanabilir.
- **Don't** Sol alt köşeye kalıcı öge koyma (GVNpen şeridiyle çakışır).
- **Don't** Samsung Notes kodu, simgesi veya görselini kopyalama.
