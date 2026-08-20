import os
import json
import logging
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
import time
import random
from playwright.sync_api import sync_playwright, TimeoutError as PlaywrightTimeoutError

# --- LOGLAMA AYARLARI (Maksimum Detay) ---
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - [%(levelname)s] - %(funcName)s:%(lineno)d - %(message)s',
    handlers=[logging.StreamHandler()]
)
logger = logging.getLogger(__name__)

logger.info("--- PLAYWRIGHT TABANLI X (TWITTER) SCRAPER BASLATILIYOR ---")
logger.info("Ortam degiskenleri okunuyor...")

SMTP_EMAIL = os.getenv("SMTP_EMAIL")
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD")
TARGET_EMAIL = os.getenv("TARGET_EMAIL")

logger.info(f"SMTP_EMAIL: {'OK' if SMTP_EMAIL else 'Eksik!'} | SMTP_PASSWORD: {'OK' if SMTP_PASSWORD else 'Eksik!'} | TARGET_EMAIL: {'OK' if TARGET_EMAIL else 'Eksik!'}")

TARGET_ACCOUNTS = ["Ataritacom", "GeeklikOtesine", "ithakiyayinlari"]
logger.info(f"Hedef hesaplar ({len(TARGET_ACCOUNTS)} adet): {TARGET_ACCOUNTS}")

STATE_FILE = "state.json"

def load_state():
    logger.info("load_state() cagirildi. state.json kontrol ediliyor...")
    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, 'r', encoding='utf-8') as f:
                data = json.load(f)
                logger.info(f"state.json okundu. Mevcut veriler: {data}")
                return data
        except Exception as e:
            logger.error(f"state.json okunurken parcalanma (parse) hatasi: {e}")
            return {}
    logger.warning("state.json bulunamadi. Ilk calistirma varsayiliyor, bos sozluk donuyor.")
    return {}

def save_state(state):
    logger.info(f"save_state() cagirildi. Yazilacak veriler: {state}")
    try:
        with open(STATE_FILE, 'w', encoding='utf-8') as f:
            json.dump(state, f, indent=4)
        logger.info("state.json diske basariyla yazildi.")
    except Exception as e:
        logger.error(f"state.json yazilirken kritik I/O hatasi: {e}")

def send_email(account, post_url, content):
    logger.info(f"send_email() baslatildi -> Hesap: {account}")
    if not all([SMTP_EMAIL, SMTP_PASSWORD, TARGET_EMAIL]):
        logger.error("Gerekli SMTP bilgileri eksik! E-posta gonderimi iptal edildi.")
        return

    msg = MIMEMultipart()
    msg['From'] = SMTP_EMAIL
    msg['To'] = TARGET_EMAIL
    msg['Subject'] = f"[{account}] Yeni X Gonderisi!"

    body = f"Merhaba,\n\n{account} hesabindan yeni bir gonderi paylasildi.\n\n"
    body += f"Icerik ozeti:\n{content}\n\n"
    body += f"Gonderi Linki: {post_url}\n\nIyi gunler!"
    
    msg.attach(MIMEText(body, 'plain', 'utf-8'))
    logger.info("E-posta MIME yapisi olusturuldu.")

    try:
        logger.info("smtp.gmail.com:587 ile baglanti kuruluyor...")
        server = smtplib.SMTP('smtp.gmail.com', 587, timeout=20)
        logger.info("STARTTLS guvenlik katmani aktif ediliyor...")
        server.starttls()
        logger.info("SMTP sunucusunda oturum aciliyor...")
        server.login(SMTP_EMAIL, SMTP_PASSWORD)
        logger.info("Oturum basarili, veri paketleri (mail) iletiliyor...")
        server.sendmail(SMTP_EMAIL, TARGET_EMAIL, msg.as_string())
        server.quit()
        logger.info(f"Mail basariyla firlatildi! ({TARGET_EMAIL})")
    except Exception as e:
        logger.error(f"SMTP iletimi sirasinda bir istisna (exception) firlatildi: {e}")

def main():
    logger.info("=== MAIN DONGUSU BASLIYOR ===")
    state = load_state()
    has_changes = False

    logger.info("Playwright sync_playwright() context'i baslatiliyor...")
    with sync_playwright() as p:
        logger.info("Chromium tarayicisi headless (arkaplan) modda tetikleniyor...")
        browser = p.chromium.launch(headless=True)
        
        logger.info("Tarayici context'i bot korumalarini asmak uzere yapilandiriliyor (User-Agent)...")
        context = browser.new_context(
            user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            viewport={"width": 1920, "height": 1080},
            locale="tr-TR"
        )
        
        logger.info("Yeni bir sekme (page) aciliyor...")
        page = context.new_page()

        for account in TARGET_ACCOUNTS:
            logger.info(f"*** Islenen Hesap: {account} ***")
            url = f"https://twitter.com/{account}"
            
            try:
                logger.info(f"Gidilecek URL: {url}")
                # domcontentloaded -> Sayfanın iskeleti yüklendiğinde bekleme biter
                page.goto(url, wait_until="domcontentloaded", timeout=45000)
                
                logger.info("Sayfa iskeleti yuklendi. Tweetlerin render edilmesi icin zorunlu bekleme yapiliyor (5 saniye)...")
                page.wait_for_timeout(5000)
                
                logger.info("Sayfadaki [data-testid='tweet'] elementleri araniyor...")
                tweets = page.locator("[data-testid='tweet']").all()
                logger.info(f"Sayfada {len(tweets)} adet tweet elementi bulundu.")

                if len(tweets) > 0:
                    first_tweet = tweets[0]
                    logger.info("En ustteki (ilk) tweet elementi secildi. Icerik metni cikariliyor...")
                    
                    tweet_text = first_tweet.inner_text()
                    logger.info(f"Cekilen ham tweet metni uzunlugu: {len(tweet_text)} karakter.")
                    
                    # Tweet icindeki linkleri (time elementi href) ariyoruz
                    logger.info("Tweet icindeki zaman/link (<time>) elementi araniyor...")
                    time_element = first_tweet.locator("time")
                    
                    if time_element.count() > 0:
                        logger.info("Time elementi bulundu, ust ugragindaki (parent) baglanti adresi aliniyor...")
                        link_href = time_element.locator("xpath=..").get_attribute("href")
                        full_post_url = f"https://twitter.com{link_href}"
                        
                        # Linkin sonundaki ID'yi ayıklıyoruz (örn: /ithakiyayinlari/status/123456789)
                        post_id = full_post_url.split("/")[-1]
                        logger.info(f"Cikarilan Gönderi ID: {post_id}")
                        logger.info(f"Cikarilan Gönderi Linki: {full_post_url}")
                        
                        last_seen_id = state.get(account)
                        logger.info(f"Karsilastirma: Kayitli ID: {last_seen_id} | Yeni ID: {post_id}")
                        
                        if last_seen_id != post_id:
                            logger.info("YENI GONDERİ TESPIT EDILDI! Mail fonksiyonu tetikleniyor...")
                            clean_text = tweet_text.replace("\n", " ")[:200] + "..."
                            send_email(account, full_post_url, clean_text)
                            
                            state[account] = post_id
                            has_changes = True
                            logger.info(f"State sözlügü güncellendi -> {account}: {post_id}")
                        else:
                            logger.info("Yeni gonderi yok, eski ID ile ayni. Atlaniyor.")
                    else:
                        logger.warning(f"[{account}] İlk tweet bulundu ancak link (time) elementi DOM icinde bulunamadi!")
                else:
                    # Sayfa yönlendirmesi var mı kontrolü (Login wall)
                    current_url = page.url
                    logger.info(f"Sayfada hic tweet bulunamadi. Mevcut URL kontrol ediliyor: {current_url}")
                    if "login" in current_url.lower():
                        logger.warning(f"[{account}] X bizi Login (Giris Yap) sayfasina yonlendirdi! Engelleme mevcut.")
                    else:
                        logger.warning(f"[{account}] Sayfa yüklendi ama tweet kalıpları eslesmedi.")
            
            except PlaywrightTimeoutError:
                logger.error(f"[{account}] Sayfa yuklenirken zaman asimi (Timeout) yasandi! Ag baglantisi zayif olabilir.")
            except Exception as e:
                logger.error(f"[{account}] Bektlenmeyen bir isleyis hatasi (Exception) olustu: {e}")
            
            # X algoritmalarına robot gibi görünmemek için rastgele bekleme
            sleep_time = random.uniform(3.0, 7.0)
            logger.info(f"Anti-bot korumasindan kacmak icin {sleep_time:.2f} saniye uykuya geciliyor...")
            page.wait_for_timeout(int(sleep_time * 1000))

        logger.info("Tum hesaplarin taranmasi bitti. Tarayici arka planda kapatiliyor...")
        browser.close()
    
    if has_changes:
        logger.info("Durumda (state) degisiklikler var. Kayit (I/O) asamasina geciliyor...")
        save_state(state)
    else:
        logger.info("Hicbir degisiklik olmadi, diske yazma islemi iptal edildi.")

    logger.info("=== TUM SUREC BASARIYLA SONLANDIRILDI ===")

if __name__ == "__main__":
    main()
