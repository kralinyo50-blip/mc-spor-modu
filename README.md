# mcsport — Minecraft Forge 1.20.1 Spor (Futbol) Modu

mcsport; Minecraft Java Edition 1.20.1 / Forge 47.x için gerçekten çalışan,
sunucu-otoriter futbol mekanikleri sunan bir moddur. Fizikli bir futbol topu,
kale blokları, gol algılama, takımlar, skor/maç yönetimi, HUD ve maç ekranı,
crafting tarifleri ve tam kaynak paketi içerir.

- **Mod ID:** `mcsport`
- **Görünen ad:** mcsport
- **Hedef sürüm:** Minecraft 1.20.1, Forge 47.2.0 (47.x uyumlu)
- **Java:** 17
- **Derleme:** Gradle 8.1.1 + ForgeGradle 6 (wrapper dahil)

---

## 1. Referans videosu hakkında dürüst not

İstenen referans video (`https://www.youtube.com/watch?v=EBi49WI5C4M`) bu geliştirme
ortamından **açılamadı**: ortamın ağ erişimi yalnızca sınırlı bir host listesine
izin veriyor ve YouTube bu listede yok; video kimliğiyle yapılan web aramaları da
videoyu tanımlayan bir sonuç döndürmedi. Bu yüzden video izlenmiş gibi davranılmadı,
videoya özgü doğrulanamayan hiçbir ayrıntı koda yansıtılmadı.

Proje talimattaki yedek plana göre, kamuya açık spor modu referanslarının (futbol
ağırlıklı klasik "Sports Mod" tarzı) bilinen ortak mekanikleri temel alınarak
**futbol temelli** olarak inşa edildi. Video erişimi olan bir ortamda mekanikler
genişletilmeye uygun modüler bir yapıda tasarlandı.

## 2. Higgsfield AI hakkında dürüst not

`higgsfield.ai` bu ortamın izin verilen host listesinde yer almıyor; dolayısıyla
Higgsfield ile gerçek bir görsel üretimi **yapılamadı** ve üretilmedi de.
Moddaki tüm dokular, tutarlı tek bir sanat yönetimi sağlamak amacıyla
`tools/generate_textures.py` betiği ile **yerel olarak deterministik piksel sanat**
şeklinde üretildi (Python + Pillow). Hazır Higgsfield promptları ve üretim iş akışı
için bkz. [`docs/HIGGSFIELD_PROMPTS.md`](docs/HIGGSFIELD_PROMPTS.md).

## 3. İçerik listesi

### Bloklar

| Kayıt adı | Türkçe ad | İşlev |
|---|---|---|
| `mcsport:artificial_turf` | Suni Çim | Saha zemini, normal katı blok |
| `mcsport:field_line` | Saha Çizgisi | Halı benzeri ince çizgi bloğu (cutout render) |
| `mcsport:red_goal` | Kırmızı Kale | Kale ağzı; önündeki bölgeden gol algılar, kırmızı file |
| `mcsport:blue_goal` | Mavi Kale | Kale ağzı; önündeki bölgeden gol algılar, mavi file |

### Eşyalar

| Kayıt adı | Türkçe ad | İşlev |
|---|---|---|
| `mcsport:soccer_ball` | Futbol Topu | Sağ tık basılı tut → şarj et, bırak → fırlat |
| `mcsport:whistle` | Hakem Düdüğü | Maç yönetim ekranını açar, düdük sesi çalar |

### Entity

| Kayıt adı | İşlev |
|---|---|
| `mcsport:soccer_ball` | Sunucu taraflı fizik simülasyonu yapan spor topu (futbol/basketbol) |
| `mcsport:soccer_npc` | AI oyuncu; takıma göre bot, kaleci veya sarı formalı hakem olarak doğar |

### Hakem ve kararları
- Her maç başında orta nokta yanında bir **hakem NPC'si** doğar (sarı forma, "Hakem" etiketi).
- Hakem oyunu çizgi hakemi pozisyonundan takip eder; başlama düdüğü, gol ve faul
  kararlarını düdük sesi + kaldırdığı kol animasyonu + sohbet duyurusu ile bildirir.
- Fauller kayma müdahalelerinin temassız temasından sunucu tarafında üretilir;
  hakem yalnızca `MatchManager`'daki olaylara tepki verir, kendisi skor üretemez.

## 4. Kontroller

| Kontrol | Eylem |
|---|---|
| **Sol tık (topa)** | Vuruş; saldırı göstergesi doluysa güçlü, koşarken daha da güçlü şut |
| **Sağ tık (topa)** | Yumuşak/pas vuruşu |
| **Eğil + Sağ tık (topa)** | Topu envantere geri al |
| **Sağ tık basılı tut (top eşyası)** | Atış gücünü şarj et, bırakınca fırlat (HUD'da güç çubuğu) |
| **V tuşu** | Maç yönetim ekranını aç (kontrol ayarlarından değiştirilebilir) |
| **C tuşu** | Kayarak müdahale (slide tackle): bakış yönüne hamle, temassız faul olabilir |
| **Sağ tık (düdük)** | Maç yönetim ekranını aç |

Tuş ataması Minecraft'ın standart **Seçenekler → Kontroller** menüsündeki
"mcsport" kategorisinden değiştirilebilir.

### Komutlar

```
/mcsport join red|blue|spectator
/mcsport start [dakika]      # yetki seviyesi 2 (operatör)
/mcsport pause               # yetki seviyesi 2
/mcsport resume              # yetki seviyesi 2
/mcsport reset               # yetki seviyesi 2
```

## 5. Uygulanan mekanikler

### Top fiziği (`SoccerBallEntity`)
- Fizik yalnızca **sunucuda** çalışır; istemciler vanilla entity takipçisi ile
  her tick senkronize edilir (çok oyunculu tutarlılığı).
- Yerçekimi, zemin sürtünmesi, hava direnci, zemin/duvar sekmesi ve sönümlenme.
- Minecraft'ın kendi `move(MoverType, Vec3)` çarpışma hattı kullanılır; duvar,
  levha, çit vb. tüm blok çarpışmaları doğru çalışır, oyunun fizik sistemi değiştirilmez.
- Yatay çarpışmada sadece bloke olan eksende yansıma; gereksiz enerji kaybı yok.
- Hareketsiz kalan top titreşmeden durur; boşluğa düşen veya 10 dakika yaşayan
  top temizlik amacıyla kaybolur.
- Görsel olarak hareket yönüne dik eksende takla atar (`roll` interpolasyonu).
- Vuruşlar 5 tick'lik soğuma ile çift vuruşları önler.
- NBT ile kaydedilir; dünya kaydedilip açıldığında konum/durum korunur.
- **Kale üstü kurtarma:** top bir kale direği/ağı bloğunun veya basketbol
  panyasının üstünde durursa otomatik olarak sahaya doğru geri fırlatılır;
  çok bloklu kalede crossbar üzerinde sıkışıp maçtan çıkamaz.
- Görsel model 16 bantlı, çember denkleminden türetilmiş gerçek bir küredir
  (eski 7 bantlı model gözle faklı görünüyordu).

### Kale ve gol sistemi (`GoalBlockEntity` + `SoccerGoalBlock`)
- Kale bloğu yönelimlidir; yerleştirirken oyuncuya bakar, yani ağzı sahaya dönüktür.
- BlockEntity her tick yalnızca küçük bir AABB sorgusu çalıştırır; bölgeye giren
  topu tespit eder.
- Gol yalnızca maç `RUNNING` durumundaysa skora işlenir; serbest oyunda top yine
  orta noktaya ışınlanır ama skor değişmez.
- Hem kale tarafında hem top tarafında soğuma süreleri ile çift gol engellenir.
- Gol sonrası korna sesi, skor güncellenir ve top dünya spawn noktasına resetlenir.

### Maç ve skor (`MatchManager`, `SavedData`)
- Takım atamaları, skor, kalan süre ve maç durumu tamamen sunucuda tutulur ve
  overworld `SavedData` ile diske kalıcı yazılır.
- Maç durumu: `IDLE / RUNNING / PAUSED / FINISHED`; süre saniye bazlı sunucu tick'inde ilerler.
- İstemciye yalnızca görünüm paketleri (`MatchStateS2C`) gönderilir; istemci
  durumu kendisi değiştiremez.
- Maç kontrol paketleri sunucuda operatör yetkisi (seviye 2) ile doğrulanır;
  yetkisiz istemci paketi skora etki edemez.

### Taktiksel AI ve animasyonlar (`NpcPlayGoal` + `RefereeGoal`)
- Rol tabanlı beyin korunur: tek kovalayıcı, dizilişe yayılan destek oyuncuları,
  ceza alanında açılan kaleci.
- **Pas zekâsı:** baskı altındaki kovalayıcı, ileride boş takım arkadaşı varsa
  şut yerine yumuşak pas atar; kaleci topu uzaktan uzaklaştırır.
- **Kayarak müdahale:** botlar topa yakın rakip varken kayma hamlesi yapar
  (özel yatık gövde/bacak animasyonu, toz parçacıkları); oyuncular **C** tuşu
  ile aynı mekaniği kullanır. Topa uzak temas faulle sonuçlanır, serbest vuruş
  için top yavaşlatılır.
- **Basketbol:** kovalayıcı topu sektire sektire (dribble) potaya taşır,
  rakip yakınken yana kaçma çalımı (crossover) atar ve atış mesafesinde yaylı
  şut çıkar; dribble sırasında pompalayan kol animasyonu oynar.
- Hakem kol kaldırma, kayma ve dribble animasyonları `NpcPlayerModel` içinde
  vanilla oyuncu modeli üzerinde, senkronize entity verisi ile çalışır.
- Yeni saha kurulumunda eski takım atamaları sıfırlanır; bot doğumları
  çarpışmasız nokta denemeleri ve `addFreshEntity` geri bildirimi ile yapılır,
  böylece 1v1 dahil her boyutta eksik bot kalmaz.

### Arayüz
- Üst orta skorbord: takım isimleri, skor, durum, süre, kendi takımın.
- Şarj edilen atış için altta güç çubuğu.
- `V` veya düdük ile açılan maç ekranı: takım seçimi, süreli başlatma,
  duraklat/devam/sıfırla; tüm butonlar doğrulanan C2S paketleri gönderir.

### Çok oyunculu
- Tüm mekanikler mantıksal sunucuda çalışır; tek oyuncu ve dedicated sunucuda aynı kod yolu.
- Oyuncu girişte anlık durum paketi alır.
- Topa aynı anda birden fazla oyuncu vursa bile sunucu tarafı soğuma + tek yetkili
  simülasyon sayesinde çift vuruş/çift skor oluşmaz.

## 6. Kurulum ve derleme

Kısa sürüm:

```bash
# JDK 17 kurulu olmalı
./gradlew build
# Üretilen JAR: build/libs/mcsport-1.0.0.jar
```

Detaylı kurulum, IDE ayarları ve test çalıştırma:
[`docs/KURULUM.md`](docs/KURULUM.md)

## 7. Test durumu ve bilinen sınırlamalar

Bkz. [`docs/TEST_VE_BILINENLER.md`](docs/TEST_VE_BILINENLER.md). Özet: bu
ortamda JDK ve Forge Maven erişimi olmadığı için JAR burada derlenemedi;
proje standart Forge MDK yapısına birebir uygun hazırlanmış, JSON/doku/import
tutarlılık kontrollerinden geçirilmiştir. Kendi makinenizde `./gradlew build`
ile JAR üretilebilir.

## 8. Proje yapısı

```
src/main/java/com/mcsport/
├── mcsport.java               # Mod giriş noktası
├── registry/                     # DeferredRegister kayıtları
├── entity/SoccerBallEntity.java  # Fizikli top
├── block/ + blockentity/         # Bloklar ve gol BlockEntity'si
├── item/                         # Top ve düdük eşyaları
├── match/                        # Takım, durum, MatchManager (SavedData)
├── network/                      # SimpleChannel + paketler
├── event/                        # Forge olayları ve /mcsport komutu
└── client/                       # Renderer, model, HUD, ekran, tuşlar
src/main/resources/
├── META-INF/mods.toml
├── pack.mcmeta
├── assets/mcsport/            # Modeller, blockstate, doku, lang, ses
└── data/mcsport/              # Tarifler ve loot tabloları
tools/generate_textures.py        # Tüm dokuları üreten betik
```

Mimari ayrıntılar: [`docs/MIMARI.md`](docs/MIMARI.md)
