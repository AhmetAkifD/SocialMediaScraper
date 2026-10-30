# Gönderi Takip (Android)

Belirlediğiniz X (Twitter) hesaplarının son gönderilerini web scraping ile çeken Android uygulaması.
Giriş yapmanız, API anahtarı ya da ücretli bir hizmet gerekmez.

## Özellikler

- **Ayarlar** ekranından `@TwitterHesabi` (ya da `https://x.com/TwitterHesabi`) yazarak hesap ekleme/kaldırma; hesap eklenirken doğrulanır
- Tüm hesapların gönderilerini tek akışta gösterme, hesaba göre filtreleme, aşağı çekerek yenileme
- Metin (uzun tweet'ler dahil), fotoğraf, video/GIF önizlemesi, alıntılanan tweet, sabitlenmiş gönderi, yanıt/RT/beğeni/görüntülenme sayıları
- Gönderiyi X'te açma, paylaşma, metni kopyalama
- Arka planda periyodik kontrol (15 dk – 24 saat) ve yeni gönderi bildirimi
- İsteğe bağlı X girişiyle **gönderilere gelen yanıtları** görme ve bir hesabın **son 5'ten eski gönderilerini** elle yükleme
- X sayfa yapısını değiştirirse hesabın altında "X sayfa yapısı değişmiş…" hatası
- Gönderiler cihazda saklanır (hesap başına en fazla 500)

## Nasıl çalışıyor?

`https://x.com/<hesap>` sayfası oturum açılmadan istendiğinde X sayfayı sunucu tarafında render eder ve
kendi GraphQL yanıtlarını (`UserByScreenName`, profil zaman tüneli) sayfaya gömer. Uygulama bu gömülü
JSON'u ayrıştırır (`scraper/TwitterParser.kt`). Böylece WebView ya da kırılgan HTML seçicileri gerekmez.

Kısıtlar:

- Oturum açılmadan X her hesap için yalnızca **son ~5 orijinal gönderiyi** (+ sabitlenmiş gönderi) gösterir.
  Uygulama düzenli kontrol ettikçe gönderiler cihazda birikir.
- Gizli (protected) hesaplar çekilemez.

### X hesabıyla giriş (isteğe bağlı)

**Ayarlar → X hesabı → X'e giriş yap** ile uygulama içinde x.com'un kendi giriş sayfası açılır. Şifre yalnızca
x.com'a gider; uygulama sadece tarayıcının oturum çerezini kullanır. **Çıkış yap** çerezleri siler.

Giriş yapıldıktan sonra:

- **Gönderiye gelen yanıtlar:** bir gönderinin altındaki 💬 (yanıt sayısı) simgesine dokunun. Gönderi sayfası
  görünmeyen bir tarayıcıda açılır ve yanıtlar uygulama içinde listelenir; yanıtlara verilen yanıtlar girintilidir.
  **Daha fazla yanıt yükle** sayfayı kaydırır ve X'in "diğer yanıtları göster" düğmelerine basar.
- **Eski gönderiler:** akışta bir hesap seçip listenin sonundaki **"eski gönderileri yükle"** düğmesine basın.
  Hesabın *Gönderiler* sekmesi açılır ve aşağı kaydırılır. Hesabın başkalarına yazdığı yanıtlar kaydedilmez.
  Tarama, cihazdaki en eski gönderiden daha eski 40 gönderi bulununca, zaman tünelinin sonuna gelince ya da en fazla
  20 kaydırmada durur.

Her ikisinde de X'in kendi GraphQL yanıtları yakalanıp ayrıştırılır (`scraper/XWebScraper.kt`, `assets/x_hook.js`).

- Bunlar **yalnızca elle** çalışır; arka plan kontrolü girişi kullanmaz (istek sınırı ve hesap güvenliği için).
- X otomatik veri çekmeyi kullanım koşullarında yasaklar; hesap kilitlenebilir. Ana hesap yerine ayrı bir hesap kullanın.
- X bir doğrulama isterse tarama ekranındaki sayfadan yanıtlanabilir.

### Hata mesajları

Hatalar hesabın altında (akışta ve Ayarlar'da), yanıtlar ekranındakiler ise yanıt listesinin sonunda görünür:

| Mesaj | Anlamı |
| --- | --- |
| X sayfa yapısı değişmiş, gönderiler okunamadı… | Sayfada beklenen veri yok ya da tweet girdileri tanınmıyor: `TwitterParser` güncellenmeli |
| X oturumu sona ermiş… | Elle taramada giriş çerezi yok ya da X giriş sayfasına yönlendirdi |
| X istek sınırına ulaşıldı… | HTTP 429 ya da X hata kodu 88 |
| Hesap gizli… / @hesap bulunamadı | Hesap korumalı ya da yok |

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
├── scraper/   TwitterScraper (girişsiz HTTP), TwitterParser (GraphQL JSON ayrıştırma),
│              XSession (giriş çerezi), XWebScraper (girişli, elle tarama)
├── data/      Modeller, Repository, SettingsStore (hesaplar/ayarlar), PostStore (posts.json)
├── work/      RefreshWorker (WorkManager ile periyodik kontrol)
├── notify/    Notifier (yeni gönderi bildirimleri)
└── ui/        Jetpack Compose ekranları (akış, ayarlar, gönderi kartı)
```
