# Social Media Scraper

Bu proje, belirlenen X (Twitter) hesaplarındaki en son paylaşımları Nitter (RSSHub alternatifi) aracılığıyla takip eder ve yeni bir gönderi tespit ettiğinde size e-posta ile bildirim gönderir. GitHub Actions üzerinde her 12 saatte bir (veya manuel olarak) çalışacak şekilde ayarlanmıştır.

## Gereksinimler

- Python 3.11+
- GitHub Repository (Projeyi fork'layıp veya kendi repolunuza yükleyip çalıştırabilirsiniz)

## Kurulum ve Kullanım

1. **Projeyi GitHub'a Yükleyin:** Bu klasördeki dosyaları kendi GitHub deponuza yükleyin.
2. **GitHub Secrets Ayarları:**
   GitHub deponuzda `Settings` -> `Secrets and variables` -> `Actions` yolunu izleyip şu **Repository secrets**'leri ekleyin:
   - `SMTP_EMAIL`: E-postayı gönderecek olan Gmail adresiniz (örnek: bot.email@gmail.com)
   - `SMTP_PASSWORD`: Gmail App Password (Uygulama Şifresi). Hesabınızda 2 Aşamalı Doğrulama (2FA) açık olmalı ve "Uygulama Şifreleri" bölümünden bu şifreyi üretmelisiniz.
   - `TARGET_EMAIL`: Bildirimleri almak istediğiniz e-posta adresiniz.
3. **Çalıştırma:**
   - Proje varsayılan olarak **her 12 saatte bir** çalışacaktır.
   - İlk çalışmasını manuel olarak tetiklemek için GitHub deponuzda `Actions` sekmesine gidin, sol taraftan `Social Media Checker` workflow'unu seçin ve `Run workflow` butonuna tıklayın.

## Takip Edilen Hesaplar
Takip edilen hesapları değiştirmek isterseniz `main.py` içindeki `TARGET_ACCOUNTS` listesini düzenleyebilirsiniz. Varsayılan olarak:
- @Ataritacom
- @GeeklikOtesine
- @ithakiyayinlari
