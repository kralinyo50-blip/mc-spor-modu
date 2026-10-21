# Oyun İçi Kullanım Rehberi

## 1. Saha kurulumu

1. Zemine **Suni Çim** blokları döşe (ör. 11×7 blokluk bir alan).
2. Çizgiler için **Saha Çizgisi** bloklarını halı gibi yerleştir.
3. Sahanın iki kısa kenarına birer kale koy:
   - **Kırmızı Kale**: kırmızı takımın savunduğu kale.
   - **Mavi Kale**: mavi takımın savunduğu kale.
4. Kaleyi yerleştirirken bloğun "ağzı" (gol algılama bölgesi) otomatik olarak
   sahaya, yani seni yerleştirdiğin yöne doğru döner. Yanlış dönerse bloğu
   kırıp karşıdan yeniden yerleştir.

İpucu: Kaleleri saha çizgisi hizasında, aralarında en az 5 blok olacak şekilde koy.

## 2. Topla etkileşim

| Eylem | Nasıl |
|---|---|
| Topu sahaya sürme | Envanterden top eşyası ile sağ tık basılı tut, şarj et, bırak |
| Şut | Yerdeki topa **sol tık**; saldırı göstergesi doluyken güçlü, koşarken daha güçlü |
| Pas / yumuşak vuruş | Topa **sağ tık** |
| Topu geri alma | **Eğil (Shift) + sağ tık** |
| Atış gücü | Sağ tık ne kadar uzun basılı tutulursa HUD'daki güç çubuğu o kadar dolar |
| Kayarak müdahale | **C tuşu**; bakış yönüne kayma hamlesi, faul olabilir |
| Maç ekranı | **V tuşu** veya Hakem Düdüğü |

Top yerçekimi ile düşer, zeminde seker ve sürtünmeyle yavaşlar; duvarlara
çarpınca doğru eksende sekerek geri döner.

## 3. Maç akışı

1. **V tuşu** veya **Hakem Düdüğü** ile maç ekranını aç.
2. Oyuncular "Kırmızı Takıma Katıl" / "Mavi Takıma Katıl" butonlarını kullanır
   (veya `/sportcraft join red`, `/sportcraft join blue`).
3. Operatör süre (dakika) girip "Maçı Başlat"a basar:
   - `/sportcraft start 10` → 10 dakikalık maç.
4. Üst orta HUD'da skor, kalan süre, maç durumu ve kendi takımın görünür.
5. Top bir kalenin algılama bölgesine girerse:
   - Karşı takımın skoru artar, korna çalar, top orta noktaya döner.
6. Süre bitince maç durumu `Bitti` olur. "Maçı Sıfırla" ile yeni maç başlatılabilir.

Maç başlamadan da top oynanabilir; bu durumda goller skora işlenmez, yalnızca
top sahaya resetlenir (serbest ısınma).

## 4. Çok oyunculu

- Mod hem istemcide hem sunucuda kurulu olmalı.
- Skor ve maç durumu yalnızca sunucuda değişir; istemci değişiklikleri kabul edilmez.
- Maç kontrolleri (başlat/duraklat/sıfırla) operatör yetkisi ister; takım seçimi herkese açıktır.
- Sunucu yeniden başlatıldığında skor ve takım atamaları dünya verisiyle korunur.

## 5. Crafting tarifleri

| Çıktı | Tarif |
|---|---|
| Futbol Topu | 3×3: köşeler ve kenar ortaları beyaz yün, ara noktalar deri, merkez mürekkep kesesi |
| Hakem Düdüğü | Şekilsiz: demir külçe + bakır külçe |
| Suni Çim (×4) | Şekilsiz: toprak + yeşil boya + buğday tohumu |
| Saha Çizgisi (×2) | Şekilsiz: suni çim + beyaz boya |
| Kırmızı Kale | 3×3: demir külçelerden çerçeve, üst orta kırmızı boya, kalan iki sıra beyaz yün |
| Mavi Kale | Aynı tarif, mavi boya ile |

Tariflerin tamamı `src/main/resources/data/sportcraft/recipes/` altında JSON
olarak da görülebilir.
