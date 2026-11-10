---
name: GVNnotes
description: "GVNpen arayüz kimliğini referans alan yerel kalem prototipi"
colors:
  rail-midnight: "#18222E"
  tool-active: "#186C84"
  accent-cyan: "#4FC4DD"
  chrome-surface: "#EFF5F9"
  workspace: "#E5EBEF"
  paper: "#FFFFFF"
  rail-divider: "#394B5C"
  icon-disabled: "#738393"
  ink-red: "#D33F49"
  ink-yellow: "#E5A900"
rounded:
  circle: "50%"
spacing:
  tool-gap: "8dp"
  rail-inset: "12dp"
  page-margin: "20dp"
components:
  tool-button:
    textColor: "{colors.paper}"
    rounded: "{rounded.circle}"
    size: "48dp"
  tool-button-selected:
    backgroundColor: "{colors.tool-active}"
    textColor: "{colors.paper}"
    rounded: "{rounded.circle}"
    size: "48dp"
  tool-rail:
    backgroundColor: "{colors.rail-midnight}"
    width: "72dp"
    padding: "12dp 0"
  color-swatch:
    rounded: "{rounded.circle}"
    size: "30dp"
  paper:
    backgroundColor: "{colors.paper}"
---
# Design System: GVNnotes

## Overview

Mevcut GVNpen arayüz kimliği referans alınır; motor kodu kullanılmaz. Mod: Operate. Kompakt, uygulama içine yerleşik sol araç şeridi kâğıt çalışma alanını öncelikli tutar. Samsung Notes yalnız davranış/mimari referansıdır; Samsung kodu veya görseli kopyalanmaz.

**Key Characteristics:**
- Gece mavisi kompakt sol şerit, petrol mavisi seçili araç, beyaz kâğıt.
- Material 3 bağlam satırı, Material Icons (Apache 2.0) ve özgün silgi vektörü.
- İlk prototip açık tema; koyu tema henüz teslim kapsamına dahil değil.

Bu belge MainActivity.kt ve InkPageView.kt kaynaklarından çıkarılmıştır. Gerçek cihaz bağlı değil; emülatör, ekran görüntüsü ve native detector sonucu yoktur. Render ve S Pen hissi henüz görsel/fiziksel olarak doğrulanamadı. Kod incelemesi ekran kanıtı yerine geçmez.

## Colors

Ön bilgideki tokenlar MainActivity.kt ve InkPageView.kt sabitleridir. Gece mavisi araç şeridi; petrol mavisi etkin araç ve Material primary; camgöbeği Material secondary; açık yüzey uygulama çerçevesi; gri çalışma masası ve beyaz sayfa birbirinden ayrılır. Kırmızı ve sarı hızlı mürekkep seçenekleridir. Şerit ayıracı ve pasif simge renkleri ayrı tokenlardır. Varsayılan Material 3 renk rolleri burada yeniden tanımlanmaz.

## Typography

Material tipografi; sistem fontu ve sp ölçekleme. Başlık titleLarge, sayfa adı bodyMedium, yüzde/kalınlık labelLarge, şerit markası labelMedium ve kayıt uyarısı bodySmall rollerini kullanır. Özel font veya özel tipografik ölçek tanımlanmamıştır; Material sürümünün varsayılanları geçerlidir.

## Layout

Üst bağlam satırı yatay 20 dp, dikey 10 dp dolgulu; solda uygulama/sayfa adı, sağda yakınlaştırma yüzdesi bulunur. Sol şerit 72 dp genişliğinde, dikey 12 dp dolgulu ve kısa ekranda kaydırılabilirdir. Araç hedefleri 48 dp, araç ve renk hedefleri arası 8 dp'dir. Renk dairesi 30 dp olup 48 dp hedefin ortasındadır. Kalan alan çizim yüzeyidir. Sayfa ilk yerleşimde 20 dp kenar payıyla sığdırılır. Alt kalınlık satırı yatay 16 dp, kaydırıcı yatay 12 dp dolgulu; uyarı alt boşluğu 8 dp'dir. Sistem çubukları safeDrawingPadding ile korunur. Başka breakpoint tanımlanmamıştır.

## Elevation & Depth

Kaynakta özel gölge/elevation tokenı yoktur. Sayfa ve çalışma masası düz renk ayrımıyla okunur; özel hareketli dekorasyon yoktur. Material bileşenlerinin varsayılan durum davranışları korunur.

## Shapes

Araç düğmeleri ve renk örnekleri daireseldir. Seçili renk 3 dp, diğerleri 1 dp beyaz çerçevelidir. Kâğıt dikdörtgendir; kaynakta yuvarlatılmış köşe yoktur. Hem tamamlanmış mürekkep hem canlı çizim yüzeyi görünür kâğıt sınırına göre kırpılır; fiziksel render doğrulaması beklenir.

## Components

- **Araç şeridi:** Kalem, fosforlu, tüm çizgi silgisi; ardından undo/redo ve hızlı renkler. Seçili araç petrol mavisi daireyle, selected semantiğiyle tanımlanır; simgeler içerik açıklaması taşır. Pasif simgeler ayrı nötr renktedir.
- **Renk seçici:** Koyu mürekkep, petrol mavisi, kırmızı ve sarı. Seçili renk çerçeve ve selected semantiğiyle belirtilir. Her hedef sonrası boşluk korunur.
- **Kalınlık kontrolü:** Material Slider, 1–12 aralığı ve mevcut tam sayı etiketi. Yanında fareyle çizim denemesi anahtarı bulunur.
- **Sayfa:** Beyaz zemin ve gri masa; iki parmakla zoom/kaydırma. Mürekkep sayfa koordinatlarında tutulur ve zeminle birlikte hareket eder; ayrı pencere/overlay yoktur.
- **Prototip durumu:** Dosya/klasör menüleri gösterilmez; çizimlerin henüz diske kaydedilmediği açıkça yazılır. Yoğun çizim sırasında araç kontrolleri devre dışı kalır; undo/redo geçmişe göre etkinleşir.

Silgi simgesi uygulamaya ait basit poligon/çizgi geometrili EraserIcon ImageVector’dür; GVNpen veya Samsung görseli kopyalanmaz.

Sidecar renk rampaları yalnız panel için sentezlenmiş OKLCH türevleridir; uygulamanın normatif paleti değildir.

Sidecar HTML/CSS örnekleri native bileşenlerin belgelenmiş renk ve geometrisinin tarayıcı önizlemeleridir; Android render veya Material davranış eşdeğerliği kanıtı değildir. CSS px değerleri yalnız önizleme içindir; uygulamadaki ölçüler dp'dir. Yeni hover veya hareket dili tanımlamaz.

## Do's and Don'ts

### Do:
- **Do** Kâğıt çalışma alanını ve kompakt, uygulama içi sol şeridi koru.
- **Do** Seçimi hem görsel durum hem erişilebilirlik semantiğiyle belirt.
- **Do** Görsel ve S Pen kabulünü gerçek tablette ayrıca doğrula.

### Don't:
- **Don't** GVNpen motorunu/kodunu veya Samsung kodu/görsellerini taşıma.
- **Don't** Henüz bulunmayan kayıt veya dosya yönetimini çalışıyormuş gibi gösterme.
- **Don't** Kaynak incelemesini görsel veya fiziksel kabul olarak sunma.
