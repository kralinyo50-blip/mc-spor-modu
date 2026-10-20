# Test Durumu ve Bilinen Sınırlamalar

## Bu ortamda ne test edilebildi?

Geliştirme sandbox'ında JDK kurulu değildi ve Forge Maven
(`maven.minecraftforge.net`) ile Gradle dağıtım sunucusu (`services.gradle.org`)
ağ erişim listesinde kapalıydı. Bu yüzden burada gerçek bir `./gradlew build`
ve oyun içi çalıştırma **yapılamadı**. Bunun yerine aşağıdaki statik doğrulamalar
çalıştırıldı ve geçti:

1. **JSON doğrulama:** 27 JSON dosyasının tamamı (blockstate, model, tarif,
   loot table, lang, sounds, pack.mcmeta) Python ile ayrıştırıldı; sözdizimi hatası yok.
2. **Doku referans eşleşmesi:** Model JSON'larında referans verilen tüm
   `sportcraft:` doku yollarının karşılığı olan PNG dosyaları mevcut.
3. **Paket tutarlılığı:** Her Java dosyasının `package` bildirimi klasör yoluyla eşleşiyor.
4. **İç import çözümlemesi:** `com.sportcraft.*` altındaki tüm importların sınıf dosyası mevcut.
5. **Süslü parantez dengesi:** Tüm Java dosyalarında `{` / `}` sayıları eşit.
6. **Doku üretim doğrulaması:** 9 PNG doğru boyut ve renk moduyla (RGB/RGBA) üretildi,
   görsel temas tek elden (aynı betik, sabit tohum) çıktı.
7. **API uyumu:** Kullanılan tüm Forge/Minecraft API'leri 1.20.1 / Forge 47
   hedefiyle manuel olarak kontrol edildi (SavedData.Factory imzası, JOML tabanlı
   `Vector3f.rotationDegrees`, `EntityModel#renderToStack`, `RegisterGuiOverlaysEvent`,
   `SimpleChannel` API'si, `CarpetBlock`, `EntityBlock` kalıpları vb.).

## Kendi makinenizde doğrulama

```bash
./gradlew build          # Derleme + JAR
./gradlew runClient      # İstemciyi açıp aşağıdaki manuel kontrol listesini uygula
```

### Önerilen manuel test listesi

- [ ] Yaratıcı sekmede 6 nesnenin tamamı görünüyor ve ikonlar doğru.
- [ ] Top eşyası şarj edilip fırlatılınca entity oluşuyor, yere düşüp sekiyor.
- [ ] Sol tık ile güçlü, sağ tık ile yumuşak vuruş; eğil+sağ tık ile toplama.
- [ ] Top duvara çarpınca doğru eksende sekip zeminde duruyor (titreşim yok).
- [ ] Kırmızı/mavi kaleler doğru yönle yerleşiyor, file dokusu saydam görünüyor.
- [ ] Top kale bölgesine girince gol sesi, skor artışı ve topun spawn'a dönmesi.
- [ ] Maç ekranı (V tuşu): takım seçimi, başlatma, duraklat, sıfırla.
- [ ] HUD skorbord ve süre geri sayımı çalışıyor.
- [ ] Süre bitince durum `Bitti` oluyor, goller skora işlemiyor.
- [ ] Dünyayı kaydet/aç: maç durumu ve top korunuyor.
- [ ] İki istemcili dedicated sunucu: vuruşlar ve skor her ikisinde tutarlı.
- [ ] Yetkisiz oyuncu maç kontrol paketi gönderince değişiklik olmuyor.
- [ ] Tüm crafting tarifleri beklenen çıktıyı veriyor; blok kırılınca eşya düşüyor.

## Bilinen sınırlamalar ve tasarım tercihleri

1. **Kale modeli tek bloklu "ağzı açık küp" formunda.** Gerçek çok bloklu bir
   kale çerçevesi yerine, yönelimli tek blok + önündeki 3×2×1.5 algılama hacmi
   kullanıldı. Birden fazla kale bloğu yan yana konularak daha geniş kale ağzı
   inşa edilebilir; her blok kendi bölgesini ayrı tarar.
2. **Oyuncu modelinde animasyon değişikliği yok.** Talimatta gereksiz oyuncu
   modeli değişikliği yapılmaması istendiği için vuruş animasyonu vanilla kol
   sallanması ile sınırlı tutıldı; topun takla animasyonu entity tarafında.
3. **Sesler vanilya ses dosyalarına yeniden yönlendiriliyor.** Telif ve boyut
   nedeniyle özel OGG paketlenmedi; `sounds.json` vanilya seslerini kullanır.
4. **Referans video izlenemedi.** Mekanikler futbol temelli yedek plana göre
   inşa edildi; video farklı bir spor gösteriyorsa modun modüler yapısı yeni
   spor eklemeye uygundur (bkz. `docs/MIMARI.md`).
5. **Higgsfield erişimi yoktu.** Tüm görseller yerel piksel sanat betiğinden
   çıktı; hazır üretim promptları `docs/HIGGSFIELD_PROMPTS.md` içinde.
6. **JAR bu ortamda üretilemedi.** Derleme araç zinciri (JDK 17 + ağ erişimi)
   olan standart bir makinede `./gradlew build` yeterlidir; wrapper ve tüm
   yapılandırma Forge MDK standartlarına uygun hazırlanmıştır.
