# Tab S9+ prototip kabul kontrolü

Bu liste test sonucu değildir. 10 Kasım 2026 başlangıç kontrolünde ADB cihaz listesi boştu; emülatör kurulumu yoktu.

## Kullanım

Debug APK: dist/GVNnotes-0.1.0-prototype.apk. Mevcut GVNpen'den ayrı kurulur. Bu ilk sürümde PDF, resim, klasör ve diske kayıt bulunmaz. Deneme çizimleri süreç kapanınca kaybolabilir. Fare simgesi yalnız emülatör/masaüstü giriş denemesi içindir; varsayılan kapalıdır.

## Fiziksel kalem

1. Yavaş/hızlı çizgi, nokta ve el yazısı; Samsung Notes ile aynı koşullarda hissi karşılaştır.
2. Basıncı değiştir: kalınlık değişimi; eğim verisi alımı ve kullanılan fırçanın görsel tepkisi ayrı değerlendirilir. Eğim mutlaka görünür sonuç verecek diye varsayılmaz.
3. Avucu ekrana koy: yazı üretmemeli, yazarken zoom/pan olmamalı.
4. İki parmakla zoom/pan; yeni çizgi kalem ucuyla hizalı mı?
5. Kâğıt kenarından dışarı çiz: canlı ve bitmiş çizgi aynı sınırda kesilmeli. Yakınlaştırıp sayfayı kısmen ekran dışına taşıyarak tekrarla.
6. Fosforlu çizgi bitince koyulaşma, boşluk veya sıçrama olmamalı.
7. Silgiyle uzun çizginin ortasından hızlı geç; tüm çizgi silinmeli. Geri al çizgiyi geri getirmeli; ileri al tekrar silmeli.
8. Sistem teması iptal edince yarım çizgi veya geçici silme tamamlanmış işlem sayılmamalı. Ayrı parmak iptali devam eden gerçek kalemi iptal etmemeli.

## Arayüz ve yaşam döngüsü

- Dikey/yatay görünüm; sistem çubukları, 130% yazı ölçeği, araç şeridinin kaydırılması.
- TalkBack: araç adları, etkin araç ve etkin renk okunmalı.
- Ekran dönünce bitmiş çizgiler ViewModel üzerinden RAM'de korunmalı; aktif çizgi güvenli iptal edilmeli.
- Undo/redo geçmişi ve yeni çizgiden sonra redo'nun kapanması.
- Uygulamayı süreç sonlandırarak açmak kalıcı kayıt testi değildir: Aşama 2 henüz yapılmadı.

S Pen yan düğmesi/hover davranışı henüz eşlenmedi. GVNpen'in tüm araç ve ayarları bu prototipte yok.
