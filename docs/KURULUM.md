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
| `Unsupported class file major version 65` | **Java 21 ile Gradle 8.1.1 çalıştırılıyor.** Aşağıdaki özel bölümü uygula: JDK 17 kullan |
| `Unsupported class file major version` (diğer) | Yanlış Java sürümü; JDK 17 kullan, `JAVA_HOME` değişkenini kontrol et |
| `Could not open cp_settings generic class cache` | Başarısız derlemeden kalan bozuk önbellek; aşağıdaki önbellek temizleme adımını uygula |
| İlk derleme ağ hatası | Maven/Gradle sunucularına erişim engellenmiş olabilir; VPN/proxy kontrol et |
| Oyun açılışında eksik doku uyarısı | `src/main/resources` yolunun derlemeye dahil olduğunu doğrula |
| Tuş çalışmıyor | Seçenekler → Kontroller → SportCraft kategorisinden çakışan tuşu değiştir |

## 6. `Unsupported class file major version 65` hatasının kesin çözümü (Windows)

### Neden oluyor?

- `major version 65` = **Java 21**. Makinedeki varsayılan `java` komutu JDK 21'e ait.
- Projedeki Gradle 8.1.1, JVM olarak en fazla Java 20 destekler; Java 21 desteği
  Gradle 8.5'te geldi. Gradle daha başlangıç aşamasında, `settings.gradle`'ı
  bile derleyemeden düştüğü için bunu build script içinden engellemenin yolu yoktur.
- Minecraft 1.20.1 ve Forge 47 zaten **JDK 17** gerektirir; yani JDK 17'yi
  yalnız derleme için değil, `runClient` ve oyunun kendisi için de kullanacaksın.

### Adım 1 — JDK 17 kur (yoksa)

- https://adoptium.net/temurin/releases/?version=17 adresinden **Windows x64 .msi** indir.
- Kurulum sihirbazında **"Set JAVA_HOME variable"** özelliğini işaretle.
- Kurulum sonrası yeni bir terminal aç ve doğrula:

```cmd
"%JAVA_HOME%\bin\java" -version
```

Çıktıda `17.x.x` görmelisin (21 değil).

JDK 17 zaten kurulu olabilir; şu klasörlere bak:
`C:\Program Files\Eclipse Adoptium\`, `C:\Program Files\Java\`,
`C:\Program Files\Microsoft\`.

### Adım 2 — Gradle'ı JDK 17 ile çalıştır

**PowerShell (tek seanslık, en hızlı yol):**

```powershell
# Klasör adını kendi kurulumuna göre düzelt:
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

cd C:\Users\quantum\Desktop\mc-spor-modu-main
.\gradlew.bat --version   # "JVM" satırında 17 görünmeli
.\gradlew.bat build
```

**CMD (tek seanslık):**

```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
gradlew.bat build
```

**Kalıcı çözüm:** Windows arama → "Sistem ortam değişkenlerini düzenle" →
Ortam Değişkenleri → `JAVA_HOME` değişkenini JDK 17 klasörünü gösterecek şekilde
düzenle → terminalleri kapatıp yeniden aç.

**IntelliJ IDEA kullanıyorsan:** File → Settings → Build, Execution, Deployment
→ Build Tools → Gradle → **Gradle JVM** ayarını JDK 17 yap (proje JDK'sı yetmez,
bu ayırdır).

### Adım 3 — Bozuk Gradle önbelleğini temizle

Hata mesajındaki `cp_settings generic class cache` satırı, Java 21 ile başarısız
denemeden kalan önbelleği gösterir. JDK 17'ye geçtikten sonra hata sürerse:

```cmd
rmdir /s /q "%USERPROFILE%\.gradle\caches\8.1.1"
```

PowerShell:

```powershell
Remove-Item -Recurse -Force "$env:USERPROFILE\.gradle\caches\8.1.1"
```

Sonra tekrar `.\gradlew.bat build` çalıştır. İlk derleme Forge/Minecraft
bağımlılıklarını indireceği için birkaç dakika sürebilir.

### Alternatif: Java 21'de kalmak istiyorsan

JDK 17 kurmak istemiyorsan (önermiyoruz; 1.20.1 için yine de lazım olacak),
Gradle wrapper'ını 8.8'e yükseltip derleme toolchain'inin foojay üzerinden
otomatik JDK 17 indirmesini sağlayabilirsin:

```properties
# gradle/wrapper/gradle-wrapper.properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.8-bin.zip
```

Bu yol ikincil tercihtir; resmi Forge 1.20.1 MDK'sının test edilmiş kombinasyonu
**Gradle 8.1.1 + JDK 17** olarak kalır.

## Sürüm doğrulama tablosu

| Bileşen | Sürüm |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.2.0 (`[47,)` aralığı kabul edilir) |
| Java | 17 |
| Gradle | 8.1.1 (wrapper) |
| ForgeGradle | 6.0.16+ |
| Mappings | official 1.20.1 |
