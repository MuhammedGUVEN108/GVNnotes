# GVNpen kaynak incelemesi

> **Tarihsel belge (10 Kasım 2026).** Sondaki "kod aktarılmayacak" kararı 12 Kasım 2026'da kaldırıldı: GVNpen kalem kodu `app/src/main/java/com/gvn/notes/pen/` altına kopyalandı ve InkView akışı sayfa koordinatına uyarlandı (overlay servisi alınmadı). Güncel durum: [AGENTS.md](../AGENTS.md).

Kaynak: C:/Users/Muhammed/Desktop/tablet kalem uygulaması/app/src/main/java/com/gvn/pen (10 Kasım 2026).

- Dil Java, Android Views/Canvas; Compose veya Kotlin değil.
- OverlayService.java:52 TYPE_APPLICATION_OVERLAY; :54 yeni InkView ve WindowManager.addView. Ek accessibility overlay yolu da mevcut.
- InkView.java:8 OverlayService owner; constructor doğrudan servisi alıyor. Girdi, araç ayarı, imleç, çizim ve servis çağrıları aynı sınıfta.
- InkView.java:39 onTouchEvent; history noktaları ve basınç işleniyor. ACTION_CANCEL mevcut cancel() ile endPen() çağırarak çizgiyi tamamlıyor; yeni tasarımda bu semantik taşınmamalı.
- Geometry.java bağımsız Point/Stroke ve geometri/silgi/şekil işlemleri içeriyor. Point x/y/p tutuyor; tilt ve zaman yok.
- InkDocument.java çizgiler ve 40 tam snapshot undo/redo, JSON formatı, dp koordinatı ve AtomicFile ile kayıt içeriyor. Yeni sayfa koordinat modeliyle aynı değil.
- SmartShape.java geometri tabanlı otomatik şekil tanıma; ToolIcon.java Canvas simgeleri; DESIGN.md kompakt yan şerit ve renk düzenini tanımlıyor.

Sonuç: Eski sınıflamadaki A ve B beraber geçerli: overlay ve kendi motoru var. View/servis bağını ayırmadan motor doğrudan gömülemez.

**Son kullanıcı kararı:** GVNpen sadece arayüz referansı. Bu nedenle kod aktarımı, servis entegrasyonu veya geometri sınıflarının kopyalanması yapılmayacak. Yeni uygulama bağımsız geliştirilecek; kaynak GVNpen projesi değiştirilmedi.
