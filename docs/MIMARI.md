# Mimari Genel Bakış

## Katmanlar

```
┌──────────────────────────── İSTEMCİ ────────────────────────────┐
│ KeyBindings → MatchScreen → C2S paketleri                        │
│ HudOverlay (skor + güç)   ClientMatchData (salt-okunur ayna)     │
│ SoccerBallRenderer + BallModel (takla animasyonu)                │
└──────────────────────────────┬───────────────────────────────────┘
                                │ SimpleChannel (sportcraft:main)
┌──────────────────────────────┴──────────── SUNUCU ───────────────┐
│ JoinTeamC2S / MatchControlC2S (yetki ve değer doğrulama)         │
│ MatchManager (SavedData): durum, skor, süre, takımlar            │
│ GoalBlockEntity: AABB gol algılama → MatchManager.onGoal          │
│ SoccerBallEntity: sunucu-otoriter fizik (move + çarpışma hattı)  │
│ ModGameEvents: saldırı/etkileşim/tick/komut olayları             │
└──────────────────────────────────────────────────────────────────┘
```

## Temel tasarım kararları

### Neden topun fiziği sadece sunucuda?
Çok oyunculu iki farklı istemcinin topu farklı simüle etmesiyle oluşacak
çatışmaların önüne geçmek için. Vanilla entity takipçisi
(`updateInterval(1)` + hız güncellemeleri açık) konumu her tick yayar;
istemci yalnızca `lerpPositionAndRotationStep` ile yumuşatır. Tek yetkili
simülasyon, çift vuruş ve çift gol riskini de ortadan kaldırır.

### Neden tamamen yeni bir fizik motoru yok?
`Entity.move(MoverType.SELF, Vec3)` Minecraft'ın kendi voxel çarpışma
pipeline'ıdır; bloklar, levhalar, çitler, merdivenler ve sıvı sınırları
zaten doğru işlenir. SportCraft sadece yerçekimi, sürtünme ve sekme
kurallarını bu hattın üstüne ekler; oyunun geri kalan fizik davranışına dokunmaz.

### Gol tespiti nasıl çalışıyor?
`SoccerGoalBlock` yönelimlidir (`HORIZONTAL_FACING`). `GoalBlockEntity`
her tick, bloğun baktığı yönde 1.5 blok derinliğinde, 3 blok genişliğinde
ve 2 blok yüksekliğinde bir AABB üretir ve `Level#getEntitiesOfClass` ile
içindeki topları sorgular. Top tarafında 60 tick, blok tarafında 40 tick
soğuma ile aynı golün birden sayılması imkânsızdır.

### Kalıcı durum
`MatchManager`, overworld'ün `DimensionDataStorage` alanına `SavedData`
olarak yazılır. Sunucu durdurulup açıldığında skor, süre ve takım
atamaları geri yüklenir. NBT şeması sürüme dayanıklıdır; bilinmeyen enum
değerleri güvenli varsayılanlara düşer.

### Ağ güvenliği
- `MatchControlC2S` her işlemde `hasPermissions(2)` kontrol eder.
- Tüm sayısal girişler sunucuda sınırlandırılır (süre 60–3600 sn, aksiyon kimliği doğrulanır).
- İstemciye giden paketler salt görüntü amaçlıdır; istemci durumu asla kaynak kabul edilmez.
- Protokol sürümü eşleşmeyen bağlantılar reddedilir.

## Performans notları

- Kale BlockEntity'si saniyede 20 küçük AABB sorgusu yapar; pahalı blok taraması yoktur.
- Top başına tick başına bir `move` çağrısı ve sabit sayıda vektör işlemi vardır;
  nesne havuzu gerektirecek kadar allocasyon yoktur (yalnız çarpışma anında kısa ömürlü Vec3).
- Maç durumu yalnız değişiklik olduğunda ve saniyede bir zamanlayıcı tick'inde yayınlanır.
- Boşta duran topun hızı sıfırlanarak gereksiz hareket paketi trafiği önlenir.

## Genişletme noktaları

- Yeni sporlar: `Team`/`MatchState` spor-bağımsızdır; yeni top entity'leri
  `SoccerBallEntity`'nin fizik iskeletini temel alabilir.
- Yeni ekipman: mevcut `DeferredRegister` sistemine yeni blok/eşya eklemek yeterli.
- Yeni ağ mesajları: `ModNetwork#register` içine sıralı ID ile eklenir.
