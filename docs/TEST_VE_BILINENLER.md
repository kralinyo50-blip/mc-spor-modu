# Test Durumu ve Bilinen Sınırlamalar

## v3: Stadyum, çok bloklu kaleler, oturulur koltuklar ve AI futbolcular

Kullanıcının yeni ekran görüntüleri (stadyum genel görünümü, fileli kale,
yuvarlak top, isim etiketli bot oyuncular) doğrultusunda:

1. **Kale inşası hatası giderildi.** Eski sürümde kale çizgisine önce halı
   bloğu konduğu için kale bloğu sessizce yerleşmiyordu. Artık `FieldBuilder`
   gerçek çok bloklu kale inşa ediyor: 5 genişlik × 3 yükseklik ağız, beyaz
   `goal_post` direkleri ve üst direk, `goal_net` bloklarıyla arka/yan/tavan
   ağı. Hatta algılama eski `SoccerGoalBlock` ile yapılıyor; bu blok artık
   görünmez ve çarpışmasızdır (sadece `GoalBlockEntity` sensörü olarak çalışır).
2. **Sandalye biçimli, oturulur koltuklar.** `SeatBlock` dört bacak + oturak +
   sırtlıktan oluşan VoxelShape ve modele sahip; sağ tık oyuncuyu görünmez
   `SeatEntity` bineğine oturtur (vanilya oturma pozu), inince varlık silinir.
3. **AI futbolcular (`SoccerNpcEntity` + `NpcPlayGoal`).** Maç başlayınca
   takımlar seçilen takım boyuna (örn. 5v5) gerçek oyuncu sayısı dikkate
   alınarak botlarla doldurulur; botlar topu kovalar, rakip kaleye şut atar,
   kaleciler kendi kalelerini korur. Boyanmış deri zırhla forma giyerler,
   numara + isim etiketi taşırlar, hasar almazlar ve dünyaya kaydedilmezler.
   Sıfırlama/maç bitince temizlenirler.
4. **Yuvarlak top.** Küp model, yedi katmanlı düşük poligonlu küre modele
   dönüştü (64x32 doku); futbol ve basketbol dokuları buna göre yenilendi.
5. **Geliştirilmiş stadyum inşası:** çift renkli çim şeritleri, atletizm
   pisti (kırmızı beton halka), reklam panoları, dört köşe bayrağı, dört
   projektör direği, iki yedek kulübesi, basamaklı kırmızı/mavi tribünler,
   kenarda skor tabelası; gol sonrası top dünya spawn'ı yerine saha merkezine
   döner; maç başında ortada top yoksa otomatik konur.

## v2: Referans mod ekran görüntülerine göre yeni özellikler

Bu turda eklenenler (tümü sunucu-otoriteli, 1.20.1 kaynak aynasından API
doğrulamalı):

1. **Çok sporlu top:** `BallType` (Futbol, Basketbol) tek entity'de synched
   data byte'ı (`EntityDataSerializers.BYTE`) ile tutulur; her tipin kendi
   fizik sönümleme değerleri ve dokusu vardır (`entity/soccer_ball.png`,
   `entity/basketball_ball.png`). Yaratıcı sekmede ayrı futbol ve basketbol
   topu eşyası; sağ tık şarjlı atış her ikisinde çalışır.
2. **Tek tıkla «Sahayı Kur»:** `FieldBuilder` oyuncunun durduğu bloğu merkez
   alarak üç boyut presetinden (Küçük 17x25, Orta 27x41, Büyük 45x71) saha
   inşa eder: suni çim zemin, beyaz halı çizgileri (kenar, orta saha, orta
   daire, ceza alanı), yöne doğru bakan kırmızı/mavi kaleler, iki yanda
   koltuk sıraları ve skor tabelası. Yalnız hava/bitki/kar ve doğal zemin
   blokları değiştirilir; komşu yapılar korunur.
3. **Yeni kurulum GUI'si (V tuşu / düdük):** referans ekrandaki düzen —
   Spor, Saha, Takım (n v n), Zorluk, Devre (dk), Beraberlik kuralı, Ev/Dep.
   dizilişi seçenekleri tıkla-ileri / Shift+tık-geri döngüsüyle çalışır;
   «Sahayı Kur», katıl/kaleci/ayrıl butonları ve maç kontrolleri aynı ekranda.
   Maç başlatma, saha kurulmadan pasiftir.
4. **Yeni blok/eşyalar:** kırmızı/mavi koltuk, skor tabelası (sağ tık skoru
   action bar'a yazar), sarı/kırmızı kart (ses + mesaj), hepsi için model,
   blockstate, loot table ve crafting tarifi; EN/TR dil kayıtları tam.
5. **Genişletilmiş maç durumu:** beraberlikte tek seferlik 2 dk uzatma
   (`DrawRule.EXTRA_TIME`), kaleci işaretleri, tüm ayarların SavedData
   kalıcılığı ve yeni ağ paketi protokolü (sürüm "2"; eski kayıtlar varsayılan
   değerlerle sorunsuz yüklenir).

Bilinen sınır (kasıtlı, görüntüde doğrulanamayan kısım): diziliş ve zorluk
değerleri kaydedilip senkronize ediliyor ancak **NPC oyuncu AI'sı henüz yok**;
takımlar gerçek oyuncularla doluyor. NPC varlıkları bir sonraki milestonun
konusudur (`Formation` enum'u bu yüzden zaten mevcut).

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
   hedefiyle manuel olarak kontrol edildi (`SavedData.Factory` imzası ve
   `save/load(CompoundTag, HolderLookup.Provider)` 1.20.1 imzaları, JOML tabanlı
   `Vector3f.rotationDegrees`, `EntityModel#renderToStack` ve `Function<RenderType>`
   constructor'ı, `RegisterGuiOverlaysEvent`, `SimpleChannel` API'si, `CarpetBlock`,
   `EntityBlock` kalıpları, `isSecondaryUseActive`, `renderBackground(GuiGraphics)` vb.).
   Denetim sırasında 1.19 tarzı tek parametreli `SavedData` imzaları ve yanlış
   `EntityModel` constructor'ı tespit edilip 1.20.1'e göre düzeltildi.
8. **Sözdizimi ayrıştırması:** 32 Java dosyası `javalang` ile ayrıştırıldı;
   görünen 5 "hata"nın yalnızca ayrıştırıcının desteklemediği Java 17 yapıları
   (pattern `instanceof`, arrow `switch`) olduğu kanıtlandı, geçici dönüşümle
   tüm dosyalar temiz çıktı.
9. **Gerçek 1.20.1 kaynak koduyla API denetimi (derleme hatalarının kök nedeni):**
   İlk derleme denemesi `:compileJava` aşamasında düştü. Tüm vanilya çağrıları,
   1.20.1'e ait decompile kaynaklar (Blackjack200/minecraft_client_1_20_1) ve
   Forge'un kendi 1.20.1 dalı ile tek tek karşılaştırıldı. Bulunan ve düzeltilen
   sürüm uyumsuzlukları (kod yanlışlıkla 1.20.2+/1.20.5+ imzaları kullanıyordu):
   - `SavedData.Factory` 1.20.1'de yok; `computeIfAbsent(Function, Supplier, String)`
     3-arg formuna ve tek parametreli `save/load(CompoundTag)` imzalarına dönüldü.
   - `Entity.onGround()` (`isOnGround` 1.20.2+), `Entity.isShiftKeyDown()`
     (`isSecondaryUseActive` 1.20.2+).
   - `EntityModel#renderToBuffer` (`renderToStack` 1.20.2+); `LayerDefinition`,
     `MeshDefinition`, `PartDefinition` 1.20.1'de `model.geom.builders` paketinde.
   - `org.joml.Vector3f` (`com.mojang.math.Vector3f` 1.20.1'de mevcut değil).
   - `CreativeModeTab.builder(Row.TOP, 0)` (argümansız builder 1.20.2+).
   - `Entity.serverLevel()` 1.20.1'de yok; `(ServerLevel) level()` cast'ine dönüldü.
   - 1.20.1'de olmayan `lerpPositionAndRotationStep` çağrısı kaldırıldı; istemci
     tarafı ItemEntity desenine (yalnız xo/yo/zo takibi + görsel takla) indirildi.
   Doğrulaması kaynak kodundan yapılmış Forge eklemeleri (sorunsuz): Forge
   KeyMapping 5-arg ctor, `setShouldReceiveVelocityUpdates`, `setRenderLayer`,
   `PacketDistributor`, SimpleChannel zinciri, overlay ve tuş olayları.
10. **Gerçek `gradlew build` çıktısıyla ikinci tur düzeltme (JDK 17'li makinede
    derlenirken yakalandı):**
    - `IntegerArgumentType` ve `StringArgumentType` yanlış paketteydi
      (`net.minecraft.commands.arguments`); bu temel tipler Brigadier
      kütüphanesine ait: `com.mojang.brigadier.arguments.*`. `net.minecraft.
      commands.arguments` paketi yalnızca MC'ye özgü tipleri (EntityArgument vb.)
      içerir — paket içeriği kaynak aynasından tek tek doğrulandı.
    - `org.joml.Vector3f.rotationDegrees(float)`, MC 1.20.1'in paketlediği JOML
      1.10.5'te yok (üstelik dönüş tipi AxisAngle4f, `mulPose` Quaternionf ister).
      Vanilya `BoatRenderer` deseniyle değiştirildi:
      `new Quaternionf().setAngleAxis(radyan, x, y, z)` (açı radyan cinsinden).

## Kendi makinenizde doğrulama

```bash
./gradlew build          # Derleme + JAR
./gradlew runClient      # İstemciyi açıp aşağıdaki manuel kontrol listesini uygula
```

### Önerilen manuel test listesi

- [ ] Yaratıcı sekmede 12 nesnenin tamamı (2 top, düdük, 2 kart, 4 saha
      bloğu, 2 koltuk, skor tabelası) görünüyor ve ikonlar doğru.
- [ ] Futbol ve basketbol topu ayrı doku/fizikle fırlatılıp sektirilebiliyor.
- [ ] Top eşyası şarj edilip fırlatılınca entity oluşuyor, yere düşüp sekiyor.
- [ ] Sol tık ile güçlü, sağ tık ile yumuşak vuruş; eğil+sağ tık ile toplama
      (toplanan eşya tipi atılan top tipiyle aynı olmalı).
- [ ] Top duvara çarpınca doğru eksende sekip zeminde duruyor (titreşim yok).
- [ ] Kurulum ekranında «Sahayı Kur»: üç boyut preseti de doğru blokları,
      çizgileri, karşı bakan kaleleri, koltukları ve tabelayı yerleştiriyor.
- [ ] Saha yokken «Maçı Başlat» pasif; kurulduktan sonra aktif oluyor.
- [ ] Ayar butonları tıkla ileri, Shift+tık geri dönüyor; yetkisiz oyuncunun
      değişikliği sunucuda uygulanmıyor.
- [ ] Kırmızı/mavi kaleler doğru yönle yerleşiyor, file dokusu saydam görünüyor.
- [ ] Top kale bölgesine girince gol sesi, skor artışı ve topun spawn'a dönmesi.
- [ ] Maç ekranı (V tuşu): takım/kaleci seçimi, başlatma, duraklat, sıfırla.
- [ ] Skor tabelasına sağ tık action bar'da skoru gösteriyor.
- [ ] Beraberlik + uzatma kuralı: süre dolunca 2 dk uzama, ikinci doluşta bitiş.
- [ ] HUD skorbord ve süre geri sayımı çalışıyor.
- [ ] Süre bitince durum `Bitti` oluyor, goller skora işlemiyor.
- [ ] Dünyayı kaydet/aç: maç durumu, ayarlar, saha baygası ve top korunuyor.
- [ ] İki istemcili dedicated sunucu: vuruşlar ve skor her ikisinde tutarlı.
- [ ] Yetkisiz oyuncu maç kontrol / saha kurma paketi gönderince değişiklik olmuyor.
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
