# Kurulum ve Derleme Rehberi

## Gereksinimler

- **JDK 17** (ör. Eclipse Temurin 17). `java -version` çıktısında 17 görülmeli.
- İnternet erişimi olan bir makine (ilk derlemede Forge, Minecraft ve Gradle
  bağımlılıkları indirilir).
- Yaklaşık 2–3 GB boş disk alanı.

> Not: Gradle'ı ayrıca kurman gerekmez; depodaki Gradle wrapper (`gradlew`)
> doğru Gradle 8.1.1 sürümünü kendisi indirir.

## 1. Kaynak koddan derleme

Depo kökünde:

```bash
# Linux / macOS
./gradlew build

# Windows
gradlew.bat build
```

İlk çalışma birkaç dakika sürebilir (ForgeGradle Minecraft'ı decompile eder).
Başarılı derlemenin ardından JAR şurada oluşur:

```
build/libs/sportcraft-1.0.0.jar
```

## 2. Modu oynama

1. Minecraft 1.20.1 için **Forge 47.2.0** kur (https://files.minecraftforge.net).
2. `sportcraft-1.0.0.jar` dosyasını `.minecraft/mods` klasörüne kopyala.
3. Forge profiliyle oyunu başlat.
4. Yaratıcı mod envanterinde **SportCraft** sekmesi açılır.

Dedicated sunucu için aynı JAR'ı sunucunun `mods/` klasörüne koy; mod hem
istemcide hem sunucuda kurulu olmalı (tek oyunculu için tek kurulum yeterli).

## 3. Geliştirme ortamı

### IntelliJ IDEA

```bash
./gradlew idea
```

Ardından proje klasörünü IDEA'da aç. Gradle import'u otomatik tamamlanır.
Çalıştırma konfigürasyonları (`runClient`, `runServer`) ForgeGradle tarafından üretilir.

### Eclipse

```bash
./gradlew eclipse
```

### Test istemcisi / sunucusu

```bash
./gradlew runClient   # geliştirme istemcisi
./gradlew runServer   # nogui geliştirme sunucusu
```

## 4. Dokuları yeniden üretme

Tüm piksel sanat dokuları deterministik bir betikten üretilir:

```bash
pip3 install pillow
python3 tools/generate_textures.py
```

Aynı tohum (`random.Random(4201)`) sayesinde çıktılar her çalıştırmada aynıdır.

## 5. Yaygın sorunlar

| Belirti | Çözüm |
|---|---|
| `Unsupported class file major version` | Yanlış Java sürümü; JDK 17 kullan, `JAVA_HOME` değişkenini kontrol et |
| İlk derleme ağ hatası | Maven/Gradle sunucularına erişim engellenmiş olabilir; VPN/proxy kontrol et |
| Oyun açılışında eksik doku uyarısı | `src/main/resources` yolunun derlemeye dahil olduğunu doğrula |
| Tuş çalışmıyor | Seçenekler → Kontroller → SportCraft kategorisinden çakışan tuşu değiştir |

## Sürüm doğrulama tablosu

| Bileşen | Sürüm |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.2.0 (`[47,)` aralığı kabul edilir) |
| Java | 17 |
| Gradle | 8.1.1 (wrapper) |
| ForgeGradle | 6.0.16+ |
| Mappings | official 1.20.1 |
