# GVNnotes
<!-- impeccable:product-schema 1 -->

## Platform
android

## Stack
Kotlin + Compose + uygulama içi View çizim yüzeyi: kullanıcı tarafından tasarım ve ilk prototip kapsamı onaylandı. Motor seçimi prototipte doğrulanacak; Jetpack Ink ilk aday.

## Users
Muhammed; Galaxy Tab S9+ ve S Pen ile kendi ders PDF ve resimleri üzerinde çalışan kullanıcı.

## Product Purpose
Dosyalarım'da seçilen klasöre eklenen PDF/resimleri klasör yapısıyla otomatik not yapmak ve doğrudan sayfaya yazmak.

## Capabilities and Constraints
- 10 Kasım 2026 son düzeltmesi: GVNpen **yalnız arayüz referansı**. GVNpen kaynak kodu/çizim motoru taşınmayacak.
- Ayrı pencere/overlay yok. S Pen çizgileri sayfa koordinatında, zeminle birlikte hareket eder.
- PDF + JPEG/PNG/WebP, küçük resimler dahil; çoklu resim tek not desteği isteniyor, birleştirme akışı açık.
- İç içe ve boş klasörler; iki yönlü adlandırma/taşıma/klasör oluşturma.
- Silme .GVNnotes-çöp klasörüne taşıma; aynı ad varsa reddet; iki klasörde aynı içerik ayrı not; kaynak çatışmada kazanır.
- Kaynak kaybolunca not ve yazılar korunur. Dışa aktarma zemin ve yazıları içerir.
- Arka planda anında algılama garantisi yok. Açılış/ön plan taraması ana mekanizma.
- İlk prototip boş sayfada kalem/silgi, geri al/ileri al, basınç/eğim, zoom/kaydırma. 0.1.2'de otomatik/kalıcı kayıt eklendi; PDF ve klasör senkronu sonraki aşamalar.
- 11 Kasım 2026 kullanıcı önce uygulama kabuğunun kurulmasını istedi ve notlar ana ekranı + bağımsız çoklu not adımını onayladı. 0.1.3 bu adımı uygular: gerçek önizlemeler, ad vererek oluştur/aç/geri. Klasör gezintisi, PDF/resim ve kaynak klasör senkronu sonraki adımlardır.

## Operating Context
Proje D:/Sanal Desktop/GVNnotes. Kullanıcı tasarım + ilk prototipi açıkça istedi. Gerçek SM-X810 Tab S9+, Android16/API36 bağlı; 0.1.1 yazma ekranı ve 0.1.2 kalıcı kayıt kullanıcı tarafından onaylandı. 0.1.3 çoklu not arayüzü cihazda otomatik testli, kullanıcı yeni sürümü henüz onaylamadı.

## Brand Commitments
GVNpen'in kompakt yan araç şeridi ve araç düzeni referans. Samsung Notes yalnız davranış/mimari referansı; Samsung kodu/görselleri kopyalanmaz.

## Evidence on Hand
research/samsung-notes-4.3.831/RAPOR.md; yerel GVNpen Java kaynakları incelendi. Gerçek tablet testleri ve kullanıcı onayları docs/DOGRULAMA.md içinde. Sürümlerin doğrulama kapsamları birbirine genellenmez.
