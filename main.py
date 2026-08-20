import os
import json
import logging
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
import feedparser
import requests
from bs4 import BeautifulSoup
import time
import random

# Loglama ayarları (En ince ayrıntısına kadar log dökmesi için yapılandırıldı)
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - [%(levelname)s] - %(funcName)s:%(lineno)d - %(message)s',
    handlers=[
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

logger.info("--- SISTEM BASLATILIYOR ---")
logger.info("Ortam degiskenleri (Environment Variables) okunuyor...")

# .yml dosyanla birebir uyumlu ortam değişkenleri
SMTP_EMAIL = os.getenv("SMTP_EMAIL")
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD")
TARGET_EMAIL = os.getenv("TARGET_EMAIL")

logger.info(f"SMTP_EMAIL degeri mevcut mu?: {'EVET' if SMTP_EMAIL else 'HAYIR'}")
logger.info(f"SMTP_PASSWORD degeri mevcut mu?: {'EVET' if SMTP_PASSWORD else 'HAYIR'}")
logger.info(f"TARGET_EMAIL degeri mevcut mu?: {'EVET' if TARGET_EMAIL else 'HAYIR'}")

TARGET_ACCOUNTS = [
    "Ataritacom",
    "GeeklikOtesine",
    "ithakiyayinlari"
]
logger.info(f"Takip edilecek toplam hesap sayisi: {len(TARGET_ACCOUNTS)} -> {TARGET_ACCOUNTS}")

NITTER_INSTANCES = [
    "https://nitter.privacydev.net",
    "https://nitter.poast.org",
    "https://nitter.projectsegfau.lt"
]
logger.info(f"Kullanilacak Nitter instance sayisi: {len(NITTER_INSTANCES)}")

STATE_FILE = "state.json"
logger.info(f"Durum dosyasi olarak '{STATE_FILE}' kullanilacak.")

def load_state():
    logger.info("load_state() fonksiyonu tetiklendi.")
    if os.path.exists(STATE_FILE):
        logger.info(f"'{STATE_FILE}' dosyasi sistemde bulundu. Icerik okunuyor...")
        try:
            with open(STATE_FILE, 'r', encoding='utf-8') as f:
                data = json.load(f)
                logger.info(f"Dosya basariyla okundu. Icindeki anahtar sayisi: {len(data)}")
                logger.info(f"Mevcut state verisi: {data}")
                return data
        except Exception as e:
            logger.error(f"Dosya okunurken kritik hata olustu! Hata detayi: {e}")
            return {}
    
    logger.warning(f"'{STATE_FILE}' dosyasi bulunamadi! Bu ilk calistirma olabilir. Bos state donduruluyor.")
    return {}

def save_state(state):
    logger.info("save_state() fonksiyonu tetiklendi.")
    logger.info(f"Kaydedilecek veri paketi: {state}")
    try:
        with open(STATE_FILE, 'w', encoding='utf-8') as f:
            json.dump(state, f, indent=4)
        logger.info(f"Veriler '{STATE_FILE}' dosyasina basariyla yazildi.")
    except Exception as e:
        logger.error(f"Dosya yazma islemi sirasinda hata firlatildi! Hata: {e}")

def send_email(account, post_url, content):
    logger.info(f"send_email() fonksiyonu '{account}' hesabi icin baslatildi.")
    
    if not all([SMTP_EMAIL, SMTP_PASSWORD, TARGET_EMAIL]):
        logger.error("E-POSTA GONDERIMI IPTAL! Gerekli sifre/mail bilgileri eksik. Lutfen GitHub Secrets ayarlarini kontrol et.")
        return

    logger.info("E-posta taslagi (MIMEMultipart) olusturuluyor...")
    msg = MIMEMultipart()
    msg['From'] = SMTP_EMAIL
    msg['To'] = TARGET_EMAIL
    msg['Subject'] = f"[{account}] Yeni X Gonderisi!"

    body = f"Merhaba,\n\n{account} hesabindan yeni bir gonderi paylasildi.\n\n"
    body += f"Icerik ozeti:\n{content}\n\n"
    body += f"Gonderi Linki: {post_url}\n\nIyi gunler!"
    
    msg.attach(MIMEText(body, 'plain', 'utf-8'))
    logger.info("E-posta icerigi ve basliklari basariyla eklendi.")

    try:
        logger.info("SMTP sunucusuna (smtp.gmail.com:587) baglanti istegi gonderiliyor...")
        server = smtplib.SMTP('smtp.gmail.com', 587)
        
        logger.info("TLS (Transport Layer Security) baslatiliyor...")
        server.starttls()
        
        logger.info("SMTP sunucusuna login girisimi basladi...")
        server.login(SMTP_EMAIL, SMTP_PASSWORD)
        logger.info("Login basarili! Kimlik dogrulandi.")
        
        text = msg.as_string()
        logger.info(f"Mail paketi '{TARGET_EMAIL}' adresine iletiliyor...")
        server.sendmail(SMTP_EMAIL, TARGET_EMAIL, text)
        
        logger.info("Gonderim tamamlandi, SMTP baglantisi kapatiliyor.")
        server.quit()
        logger.info(f"BINGO! '{account}' hesabi icin mail basariyla ulasti.")
    except Exception as e:
        logger.error(f"MAIL GONDERIM HATASI! Beklenmeyen bir cokus yasandi: {type(e).__name__} - {e}")

def fetch_latest_post(account):
    logger.info(f"fetch_latest_post() calistirildi. Hedef hesap: {account}")
    
    for instance in NITTER_INSTANCES:
        url = f"{instance}/{account}/rss"
        logger.info(f"-> Nitter Instance deneniyor: {url}")
        
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
        
        try:
            logger.info("HTTP GET istegi firlatiliyor...")
            response = requests.get(url, headers=headers, timeout=15)
            logger.info(f"Sunucudan donen HTTP Status: {response.status_code}")
            
            if response.status_code == 200:
                logger.info("Yanit 200 OK. RSS datasi feedparser ile analiz ediliyor...")
                feed = feedparser.parse(response.content)
                
                if feed.entries:
                    logger.info(f"RSS icinde {len(feed.entries)} adet gonderi bulundu.")
                    latest_entry = feed.entries[0]
                    logger.info(f"En guncel gonderi ID: {latest_entry.id}")
                    logger.info(f"Orijinal Nitter Linki: {latest_entry.link}")
                    
                    logger.info("BeautifulSoup ile HTML etiketleri ayiklaniyor...")
                    soup = BeautifulSoup(latest_entry.description, "html.parser")
                    clean_text = soup.get_text(separator=' ', strip=True)
                    logger.info(f"Temizlenen metin uzunlugu: {len(clean_text)} karakter.")
                    
                    post_url = latest_entry.link
                    for inst in NITTER_INSTANCES:
                        if inst in post_url:
                            logger.info(f"Link icindeki '{inst}' kismi 'https://twitter.com' ile degistiriliyor.")
                            post_url = post_url.replace(inst, "https://twitter.com")
                    
                    logger.info(f"Final X URL'si: {post_url}")
                    
                    sleep_time = random.uniform(1.0, 3.0)
                    logger.info(f"Spam filtrelerine takilmamak icin {sleep_time:.2f} saniye uykuya geciliyor...")
                    time.sleep(sleep_time)
                    logger.info("Uyku bitti. Veri paketi donduruluyor.")
                    
                    return {
                        "id": latest_entry.id,
                        "url": post_url,
                        "content": clean_text[:200] + "..." if len(clean_text) > 200 else clean_text
                    }
                else:
                    logger.info("feed.entries bos dondu. Bu hesapta hic gonderi yok veya gizli.")
                    return None
            else:
                logger.warning(f"Nitter instance patladi veya rate limit yedi. Durum kodu: {response.status_code}")
        except Exception as e:
            logger.error(f"Bu instance'a ({instance}) baglanirken hata yasandi: {e}")
            
    logger.error(f"Eyvah! '{account}' hesabi icin tum Nitter instance'lari basarisiz oldu. Diger hesaba gecilecek.")
    return None

def main():
    logger.info("=== ANA DONGU (MAIN) BASLADI ===")
    state = load_state()
    has_changes = False

    for account in TARGET_ACCOUNTS:
        logger.info(f"*** {account} hesabi isleme aliniyor ***")
        latest_post = fetch_latest_post(account)
        
        if latest_post:
            post_id = latest_post["id"]
            last_seen_id = state.get(account)
            
            logger.info(f"Karsilastirma yapiliyor -> State ID: {last_seen_id} | Guncel ID: {post_id}")
            
            if last_seen_id != post_id:
                logger.info(f"YENI GONDERİ YAKALANDI! ({account}) Mail sureci tetikleniyor...")
                send_email(account, latest_post["url"], latest_post["content"])
                
                logger.info(f"State dosyasi icin '{account}' degeri guncelleniyor.")
                state[account] = post_id
                has_changes = True
            else:
                logger.info(f"Gonderi ayni, degisiklik yok. Geciliyor...")
        else:
            logger.warning(f"[{account}] icin veri cekilemedi, bu hesap simdilik atlandi.")
            
        logger.info(f"*** {account} islemi bitti ***\n")

    if has_changes:
        logger.info("Dongu bitti, degisiklikler var. save_state() cagiriliyor.")
        save_state(state)
    else:
        logger.info("Hicbir hesapta yeni gonderi yok, state guncellemeye gerek duyulmadi.")
        
    logger.info("=== TUM SUREC BASARIYLA TAMAMLANDI ===")

if __name__ == "__main__":
    main()