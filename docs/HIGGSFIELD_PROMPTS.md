# Higgsfield AI Görsel Üretim Rehberi ve Hazır Promptlar

## Erişim durumu (dürüst beyan)

Bu modun geliştirildiği sandbox ortamının izin verilen ağ listesinde
`higgsfield.ai` yer almıyordu. Dolayısıyla Higgsfield ile **gerçek bir görsel
üretimi yapılamadı** ve hiçbir dosya "Higgsfield çıktısı" olarak etiketlenmedi.

Modda kullanılan dokuların tamamı, tek tip sanat yönetimi ve Minecraft piksel
sanatı uyumu için `tools/generate_textures.py` betiğiyle yerel olarak üretildi
(Pillow tabanlı deterministik piksel sanatı). Üretilen dosyalar:

```
assets/sportcraft/textures/item/soccer_ball.png      16×16
assets/sportcraft/textures/item/whistle.png          16×16
assets/sportcraft/textures/entity/soccer_ball.png    16×16
assets/sportcraft/textures/block/artificial_turf.png 16×16
assets/sportcraft/textures/block/field_line.png      16×16
assets/sportcraft/textures/block/goal_post.png       16×16
assets/sportcraft/textures/block/red_goal_net.png    16×16 (saydam)
assets/sportcraft/textures/block/blue_goal_net.png   16×16 (saydam)
docs/sportcraft_logo.png                             128×128
```

## Higgsfield ile üretim yapmak istersen iş akışı

1. Aşağıdaki promptları Higgsfield'da çalıştır.
2. Çıkan görselleri kare formda, mümkünse düz/şeffaf arka planla indir.
3. Minecraft 1.20.1 doku boyutlarına indir: eşya/entity için 16×16 (veya 32×32),
   blok yüzleri için 16×16. File dokuları RGBA, saydam alanları koru.
4. Pikselleştirme için en yakın komşu (nearest-neighbor) yeniden örnekleme kullan;
   kenar yumuşatma (blur) uygulama.
5. Dosyaları yukarıdaki aynı yollarla değiştir; model JSON'larındaki doku
   referansları değişmeden kalır.
6. Oyun içi kontrol: envanter ikonu okunabilirliği, saydam file kenarları,
   blok yüzlerinde UV taşması olmamalı.

## Hazır promptlar (İngilizce — model başına ayrı üretim)

### 1. Futbol topu — eşya ikonu (16×16 hedef)
```
Create a clean Minecraft Java Edition 1.20.1 mod asset: a high-quality pixel-art
soccer ball item icon, consistent with vanilla Minecraft's blocky visual style,
readable classic black pentagon and white hexagon pattern, crisp pixel edges,
centered object, flat transparent background, no text, no logo, no watermark.
Isolated game-ready PNG asset, limited readable color palette, 16x16 pixel art.
```

### 2. Futbol topu — entity yüz dokusu (16×16, kaplamasız küp yüzü)
```
Minecraft-style tileable pixel-art texture for the face of a small cube-shaped
soccer ball entity, white leather with bold black pentagon patches touching the
edges so adjacent faces connect visually, even lighting, no shading gradients,
no border, no text, no watermark, 16x16 pixels, game-ready PNG.
```

### 3. Hakem düdüğü — eşya ikonu
```
Clean Minecraft Java Edition 1.20.1 pixel-art referee whistle item icon, golden
brass body with a small grey mouthpiece and a finger loop, vanilla Minecraft
texture style, simple geometric shapes, crisp pixel edges, transparent
background, centered object, no text, no watermark, 16x16 game-ready PNG.
```

### 4. Suni çim blok dokusu
```
Minecraft 1.20.1 block texture: artificial soccer turf in alternating light and
dark green mowing stripes, dense short synthetic grass feel rendered as readable
pixels, vanilla grass-block color harmony, seamless tileable edges, 16x16
pixel art, no text, no watermark, game-ready PNG.
```

### 5. Saha çizgisi dokusu
```
Minecraft-style flat white sports field marking texture, slightly off-white
wool-like pixel surface with subtle per-pixel noise, seamless tileable, very
light contrast, 16x16 pixel art, no text, no watermark, game-ready PNG.
```

### 6. Kale direği dokusu
```
Minecraft 1.20.1 pixel-art texture of a white painted metal goalpost bar,
light steel grey-white surface with one soft vertical highlight stripe and
slightly darker edges, vanilla metal block style, tileable vertically, 16x16
pixels, no text, no watermark.
```

### 7. Kırmızı ve mavi kale fileleri (iki ayrı üretim)
```
Minecraft-style transparent goal net texture, [red / blue] woven rope grid on
a fully transparent background, regular square mesh with slightly brighter knot
pixels at intersections, crisp pixel edges, alpha channel preserved, 16x16
pixel art, no frame, no text, no watermark, game-ready PNG.
```
İki üretim yap: biri `red` (kırmızı ip), biri `blue` (mavi ip) kelimesiyle.

### 8. Mod logosu / tanıtım görseli
```
Square pixel-art logo for a Minecraft soccer mod called SportCraft: a tiny
top-down stylized soccer field with mowing stripes, white centre line and
centre circle, a small red goal on the left, blue goal on the right and a
black-and-white soccer ball in the centre, vanilla Minecraft visual language,
bold readable silhouette, flat background, no letters, no watermark.
```

## Tutarlılık kontrol listesi

- Tüm görseller aynı çözünürlük ailesinde (16×16) ve aynı palet doygunluğunda olmalı.
- Siyah yerine yumuşak kirli siyah (ör. #18181C), beyaz yerine hafif kırık beyaz
  (ör. #EEEEEC) kullan; vanilya dokular saf siyah/beyaz kullanmaz.
- Her dokuda en fazla 8–12 ana renk; gradyan değil piksel bazlı gölgeleme.
- Saydam dokularda yarı saydam değil, ya tam opak ya tam saydam pikseller tercih et
  (cutout render katmanı ile uyum için).
