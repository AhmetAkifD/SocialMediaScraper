# Gönderi Takip (Android)

Belirlediğiniz X (Twitter) hesaplarının son gönderilerini web scraping ile çeken Android uygulaması.
Giriş yapmanız, API anahtarı ya da ücretli bir hizmet gerekmez.

## Özellikler

- **Ayarlar** ekranından `@TwitterHesabi` (ya da `https://x.com/TwitterHesabi`) yazarak hesap ekleme/kaldırma; hesap eklenirken doğrulanır
- Tüm hesapların gönderilerini tek akışta gösterme, hesaba göre filtreleme, aşağı çekerek yenileme
- Metin (uzun tweet'ler dahil), fotoğraf, video/GIF önizlemesi, alıntılanan tweet, sabitlenmiş gönderi, yanıt/RT/beğeni/görüntülenme sayıları
- Gönderiyi X'te açma, paylaşma, metni kopyalama
- Arka planda periyodik kontrol (15 dk – 24 saat) ve yeni gönderi bildirimi
- Gönderiler cihazda saklanır (hesap başına en fazla 200)

## Nasıl çalışıyor?

`https://x.com/<hesap>` sayfası oturum açılmadan istendiğinde X sayfayı sunucu tarafında render eder ve
kendi GraphQL yanıtlarını (`UserByScreenName`, profil zaman tüneli) sayfaya gömer. Uygulama bu gömülü
JSON'u ayrıştırır (`scraper/TwitterParser.kt`). Böylece WebView ya da kırılgan HTML seçicileri gerekmez.

Kısıtlar:

- Oturum açılmadan X her hesap için yalnızca **son ~5 orijinal gönderiyi** (+ sabitlenmiş gönderi) gösterir.
  Retweet ve yanıtlar gelmez. Uygulama düzenli kontrol ettikçe gönderiler cihazda birikir.
- Gizli (protected) hesaplar çekilemez.
- X sayfa yapısını değiştirirse ayrıştırıcının güncellenmesi gerekebilir. Bu durumda hesapların altında
  "Sayfada gönderi verisi bulunamadı" hatası görünür.

## Derleme ve kurulum

Gereksinimler: JDK 17 ve Android SDK (platform 36). Android Studio ile `android/` klasörünü açıp
**Run** demeniz yeterli. Komut satırından:

```bash
cd android
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`. Telefona kopyalayıp açarak (bilinmeyen kaynaklardan
yüklemeye izin vererek) ya da USB hata ayıklama açıkken `adb install app-release.apk` ile kurabilirsiniz.

> Release APK, kolay kurulum için Android'in debug anahtarıyla imzalanır. Google Play'e yüklemek isterseniz
> kendi imza anahtarınızı `app/build.gradle.kts` içindeki `signingConfig` ile tanımlayın.

## Testler

```bash
./gradlew testDebugUnitTest                                   # birim testleri
./gradlew testDebugUnitTest -PliveAccounts=NASA,Ataritacom    # x.com'dan canlı çekim testi
./gradlew testDebugUnitTest -PxHtmlDir=/kayitli/sayfalar      # kaydedilmiş .html sayfalarını ayrıştır
```

## Proje yapısı

```
app/src/main/java/com/socialmediascraper/
├── scraper/   TwitterScraper (HTTP), TwitterParser (gömülü GraphQL JSON ayrıştırma)
├── data/      Modeller, Repository, SettingsStore (hesaplar/ayarlar), PostStore (posts.json)
├── work/      RefreshWorker (WorkManager ile periyodik kontrol)
├── notify/    Notifier (yeni gönderi bildirimleri)
└── ui/        Jetpack Compose ekranları (akış, ayarlar, gönderi kartı)
```
