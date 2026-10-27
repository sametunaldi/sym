# Symphony Lite

Aimetis Symphony mobil istemcisinin sadeleştirilmiş yeniden yazımı.
Kotlin + Jetpack Compose + Media3 (ExoPlayer) + OkHttp.

**Var:** sunucu girişi, kamera listesi (küçük resim), canlı H.264 izleme, geçmiş kayıt izleme, ayarlar.
**Yok:** alarm, PTZ, DIO, kurallar, sunucu keşfi, JPEG modu.

## APK derleme

### 1) Android Studio
Klasörü aç, Gradle sync bitince `Build > Build APK(s)`.

### 2) Komut satırı (JDK 17 + Android SDK gerekir)
```
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

### 3) GitHub Actions
Projeyi bir GitHub reposuna yükle. `Actions > Build APK` çalışınca
çıktı sayfasındaki `symphony-lite-debug-apk` dosyasını indir.

## Protokol özeti (orijinal APK'dan çıkarıldı)
- `GET /` (önce https, olmazsa http, varsayılan port 8433) -> JSON: `ApiVersion`, `ServerAgent`, `CamerasLink`, `UtcOffset`
- Basic auth: `base64(base64(user):pass)`; 401/403 gelirse `base64(user:pass)` denenir
- `GET {CamerasLink}` -> `[ {Id, Name, ThumbnailLink, LiveFootageLink, HistFootageLink}, ... ]`
- Küçük resim: `{ThumbnailLink}?width=W&height=H`
- Canlı: `{LiveFootageLink}?width=W&height=H&audio=true|false` -> `video/mp2t`
- Geçmiş: `HistFootageLink` içindeki `{year}{month}{day}{hour}{minute}{second}{ms}` UTC ile doldurulur

## Bilinen eksikler
- Gerçek sunucuda test EDİLMEDİ. İlk denemede sorun çıkarsa Logcat çıktısını (etiket: `ExoPlayer`, `OkHttp`) paylaş.
- Parola SharedPreferences'ta düz metin saklanır.
- Self-signed sertifikalar varsayılan olarak kabul edilir (orijinal uygulama gibi). Ayarlardan kapatılabilir.
