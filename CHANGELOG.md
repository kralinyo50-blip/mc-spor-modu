# Sürüm Notları / Changelog

## 1.3.0 — Stadyum Görsel Yenilemesi (1 Kasım 2026)

### 🇹🇷 Türkçe

#### 🪧 Reklam panosu yeniden yapıldı
- Pano artık **çift yüzlü**: aynı yazı iki taraftan da okunuyor, sahaya hangi yönden bakarsan bak görünür.
- **Panoya 48 karaktere kadar yazı yazılır; sığmayan yazı LED tabela gibi kayar.** Kısa slogan ortada durur, uzun metin duraklayıp sağdan sola akar — yazı hiçbir zaman çerçevenin dışına taşmaz.
- Yazı **tam parlaklıkta** çizilir: gece ve gölgede de net okunur.
- Yeni 3B model: ayaklı kaide, iki taşıyıcı bacak, alüminyum çerçeve ve ayrı LED ekran dokusu.
- Düzenleme ekranı yenilendi: canlı **karakter sayacı**, "Temizle" düğmesi, ipucu satırı ve yazma yetkisi olmayan oyuncular için net uyarı.
- Sunucu doğrulaması sıkılaştırıldı: yetki seviyesi 2 + **panonun 8 blok yakınında olma** şartı; uzaktan paket gönderen istemci yok sayılır.

#### 🏟️ Skor tabelası yeniden yapıldı
- Tabela artık **yönlü**: yerleştirirken baktığın yöne dönüyor ve otomatik kurulan stadyumda tribünün üstüne, **sahaya bakan** şekilde monte ediliyor. Önceden gökyüzüne dönük kaldığı için boş görünüyordu.
- Ekran düzeni baştan yazıldı: **takım adları kulüp renklerinde**, ortada büyük **skor**, altta **maç saati ve durum**. Uzun takım adları panelden taşmasın diye otomatik küçültülür.
- Maç yokken boş panel yerine **"MC SPORT — Maç başlaması bekleniyor"** bekleme ekranı görünür.
- Tam parlaklıkta çizim: kapalı arenada, çatı altında ve gece de okunur.
- Yeni 3B model: ağır metal çerçeve, direk ayağı ve ayrı ekran dokusu.

#### 🏆 Şampiyonluk kupası 3B oldu
- Düz kupa kutusu gitti; yerine **gerçek 3B kupa heykeli** geldi: mermer kaide, altın basamaklı taban, ince gövde, topuz, kademeli kase, geniş ağız ve **iki yan kulp**.
- Altın dokusu ışık yansıtan dikey gradyan + parlama çizgisiyle, kaide dokusu damarlı mermerle yeniden çizildi.
- Vuruş kutusu modelle uyumlu (kaideden kaseye kadar), yürüme engeli yalnızca kaidede: kupaya yürüyerek geçilebilir, görünmez duvar yok.

#### 🔧 Düzeltmeler ve sistem
- **Kupa ve reklam panosunun craft tarifi eklendi**; kupa, koltuklar ve stadyum bloklarının **kırınca düşme (loot) tabloları** eksikti, tamamlandı — artık kırılan blok eşyasını veriyor.
- Ağ protokolü **9**'a yükseltildi; 1.2.0 istemcileriyle uyumsuz (istemeden yanlış veri akışını engellemek için bilinçli).
- Eski dünyalar korunur: skor tabelası yön bilgisi olmayan eski blok durumları varsayılan yöne düşer.

#### 🛠️ Geliştirici araçları
- `tools/preview_models.py` eklendi: blok modellerini oyunu açmadan izometrik olarak önizler (`docs/model_preview.png`), geometri hatalarını JAR derlemeden önce yakalamak için.
- Doku üreticisi (`tools/generate_textures.py`) yeni paneller için `panel_frame()` / `led_screen()` / `trophy()` fonksiyonlarıyla güncellendi; dokular hâlâ deterministik olarak betikten üretiliyor.

#### ⚠️ Bilinenler
- Kumanda/parlaklık doğrulaması bu ortamda yapılamadı; render'lar 1.20.1 API'sine göre yazıldı, nihai doğrulama `JAR-YAP.bat` derlemesi ve oyun içi testtir.
- Model önizlemeleri repoda `docs/` altında değil, yerel olarak üretildi; oyun içi görünüm gerçek render ile teyit edilmelidir.

---

### 🇬🇧 English

#### 🪧 Advertising board rebuilt
- The board is now **double-sided**: the same message reads from both faces, so it is visible from anywhere around the pitch.
- **Up to 48 characters per board, and text that does not fit scrolls like a real LED board.** Short slogans sit centred; long lines pause, then glide right to left — the message never spills outside the frame.
- Text is drawn **full-bright**, so it stays readable at night and in shadow.
- New 3D model: standing plinth, two support legs, aluminium frame and a separate LED screen texture.
- Reworked editor screen: live **character counter**, "Clear" button, hint line and a clear warning for players without edit rights.
- Stricter server validation: permission level 2 **and** the player must stand within 8 blocks of the board; packets sent from further away are ignored.

#### 🏟️ Scoreboard rebuilt
- The scoreboard is now **directional**: it faces the way you place it, and the auto-built stadium mounts it above the stand **facing the pitch**. Previously it stayed pointed at the sky and looked empty.
- New layout: **team names in club kit colours**, a large **score** in the middle, and the **match clock and state** underneath. Long club names are auto-shrunk so they always fit.
- With no match running the panel shows a **"MC SPORT — waiting for kick-off"** standby card instead of a blank face.
- Full-bright rendering: readable under the stadium roof and at night.
- New 3D model: heavy metal frame, support mast and a separate screen texture.

#### 🏆 Championship trophy is now 3D
- The flat cube is gone; in its place is a **real 3D trophy statue**: marble plinth, stepped gold base, slim stem, knop, tapered bowl, flared rim and **two side handles**.
- New gold texture (vertical gradient plus a specular streak) and a veined marble base.
- The selection box matches the model from plinth to rim, while only the plinth blocks movement — no invisible walls, you can walk through the cup.

#### 🔧 Fixes and internals
- **Added crafting recipes for the trophy and the advertising board**, and the missing **loot tables** for the trophy, the seats and the stadium blocks — breaking them now returns the item.
- Network protocol bumped to **9** (deliberately incompatible with 1.2.0 clients to avoid mixed-traffic bugs).
- Existing worlds stay loadable: pre-1.3 scoreboard states without a facing fall back to the default direction.

#### 🛠️ Developer tooling
- Added `tools/preview_models.py`, which renders the block models isometrically without launching the game (`docs/model_preview.png`) so geometry mistakes are caught before building a JAR.
- The texture generator (`tools/generate_textures.py`) gained `panel_frame()`, `led_screen()` and `trophy()`; all textures are still produced deterministically by the script.

#### ⚠️ Known limitations
- No in-game verification was possible in this environment; the renderers are written against the 1.20.1 API and the final check is the `JAR-YAP.bat` build plus an in-game look.
- Model previews were generated locally and are not shipped; the real in-game appearance must be confirmed in game.

---

## 1.2.0 — Dev Eğlence Güncellemesi / Big Fun Update (28 Ekim 2026)

### 🇹🇷 Türkçe

#### 🎉 Eğlence Paketi
Kurulumdan seçilen, maç boyunca sabit beş mod:
- **Kapalı** — standart kurallar
- **Düşük Yerçekimi** — top daha yavaş düşer, daha yükseğe zıplar (yerçekimi ×0.45)
- **Büyük Top** — top %60 büyür, çarpışma kutusu da büyür
- **Süper Şut** — tüm vuruşlar %60 daha güçlü (oyuncu şutu, bot pas/şut, basket atışı)
- **Parti Modu** — ~30 saniyede bir 15 saniyelik sürpriz olaylar (düşük yerçekimi / süper şut / bot hız patlaması), sohbette duyurulur

#### 😴 Bot Yorgunluğu
- Kurulumdan aç/kapa; maç uzadıkça botlar en fazla %35 yavaşlar
- Sonradan giren yedekler sıfır yorgunlukla başlar
- Hakemler etkilenmez

#### 🪧 Reklam Panosu (ilk sürüm)
- Yönlü blok, sağ tıkla metin ekranı, 48 karakter, op-2 doğrulamalı C2S paketi

#### 🔧 Teknik
- Ağ protokolü 8; eğlence modu ve yorgunluk ayarı senkronize edilir

### 🇬🇧 English

#### 🎉 Fun Pack
Five match-wide modes chosen in the setup screen:
- **Off** — standard rules
- **Low Gravity** — the ball falls slower and bounces higher (gravity ×0.45)
- **Big Ball** — the ball grows by 60%, collision box included
- **Super Shot** — every kick is 60% stronger (player shots, bot passes and shots)
- **Party Mode** — 15-second surprise events roughly every 30 seconds, announced in chat

#### 😴 Bot Fatigue
- Toggle in the setup screen; bots slow down by up to 35% as the match goes on
- Substitutes start with zero fatigue
- Referees are unaffected

#### 🪧 Advertising Board (first version)
- Directional block, right-click text screen, 48 characters, operator-gated C2S packet

#### 🔧 Internals
- Network protocol 8; fun mode and fatigue settings are synced
