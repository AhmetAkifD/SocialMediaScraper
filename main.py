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

# Loglama ayarları
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

# Çevre değişkenlerinden (environment variables) alınacak gizli bilgiler
SMTP_EMAIL = os.getenv("SMTP_EMAIL")
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD")
TARGET_EMAIL = os.getenv("TARGET_EMAIL")

# Takip edilecek X hesapları
TARGET_ACCOUNTS = [
    "Ataritacom",
    "GeeklikOtesine",
    "ithakiyayinlari"
]

# Nitter instance'ları (Biri çökerse diğerine geçmek için)
NITTER_INSTANCES = [
    "https://nitter.privacydev.net",
    "https://nitter.poast.org",
    "https://nitter.projectsegfau.lt"
]

STATE_FILE = "state.json"

def load_state():
    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
        except Exception as e:
            logger.error(f"state.json okunamadi: {e}")
            return {}
    return {}

def save_state(state):
    try:
        with open(STATE_FILE, 'w', encoding='utf-8') as f:
            json.dump(state, f, indent=4)
        logger.info("state.json guncellendi.")
    except Exception as e:
        logger.error(f"state.json kaydedilemedi: {e}")

def send_email(account, post_url, content):
    if not all([SMTP_EMAIL, SMTP_PASSWORD, TARGET_EMAIL]):
        logger.warning("E-posta gondermek icin gerekli ortam degiskenleri eksik! Atlandi.")
        return

    msg = MIMEMultipart()
    msg['From'] = SMTP_EMAIL
    msg['To'] = TARGET_EMAIL
    msg['Subject'] = f"[{account}] Yeni X Gonderisi!"

    body = f"Merhaba,\n\n{account} hesabından yeni bir gönderi paylaşıldı.\n\n"
    body += f"İçerik özeti:\n{content}\n\n"
    body += f"Gönderi Linki: {post_url}\n\nİyi günler!"
    
    msg.attach(MIMEText(body, 'plain', 'utf-8'))

    try:
        server = smtplib.SMTP('smtp.gmail.com', 587)
        server.starttls()
        server.login(SMTP_EMAIL, SMTP_PASSWORD)
        text = msg.as_string()
        server.sendmail(SMTP_EMAIL, TARGET_EMAIL, text)
        server.quit()
        logger.info(f"{account} icin e-posta basariyla gonderildi: {TARGET_EMAIL}")
    except Exception as e:
        logger.error(f"E-posta gonderilirken hata olustu: {e}")

def fetch_latest_post(account):
    for instance in NITTER_INSTANCES:
        url = f"{instance}/{account}/rss"
        logger.info(f"[{account}] RSS kontrol ediliyor: {url}")
        
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
        
        try:
            response = requests.get(url, headers=headers, timeout=15)
            if response.status_code == 200:
                feed = feedparser.parse(response.content)
                if feed.entries:
                    # En yeni gonderi ilk siradadir
                    latest_entry = feed.entries[0]
                    # Icerigi temizle (HTML etiketlerinden arindir)
                    soup = BeautifulSoup(latest_entry.description, "html.parser")
                    clean_text = soup.get_text(separator=' ', strip=True)
                    
                    # Nitter linkini X linkine cevir
                    post_url = latest_entry.link
                    for inst in NITTER_INSTANCES:
                        post_url = post_url.replace(inst, "https://twitter.com")
                    
                    # Nitter instance'ina asiri yuklenmemek icin kucuk bekleme
                    time.sleep(random.uniform(1.0, 3.0))
                    
                    return {
                        "id": latest_entry.id,
                        "url": post_url,
                        "content": clean_text[:200] + "..." if len(clean_text) > 200 else clean_text
                    }
                else:
                    logger.info(f"[{account}] Icin hic gonderi bulunamadi.")
                    return None
            else:
                logger.warning(f"[{account}] Nitter instance {instance} yanit vermedi. Durum kodu: {response.status_code}")
        except Exception as e:
            logger.error(f"[{account}] RSS alinirken {instance} uzerinde hata olustu: {e}")
            
    logger.error(f"[{account}] Tum Nitter instance'lari basarisiz oldu.")
    return None

def main():
    logger.info("Sosyal medya tarayicisi baslatildi.")
    state = load_state()
    has_changes = False

    for account in TARGET_ACCOUNTS:
        latest_post = fetch_latest_post(account)
        if latest_post:
            post_id = latest_post["id"]
            last_seen_id = state.get(account)
            
            if last_seen_id != post_id:
                logger.info(f"[{account}] YENI GONDERİ TESPIT EDILDI! ID: {post_id}")
                send_email(account, latest_post["url"], latest_post["content"])
                state[account] = post_id
                has_changes = True
            else:
                logger.info(f"[{account}] Yeni gonderi yok. Son id ayni: {post_id}")
        else:
            logger.warning(f"[{account}] Bilgi alinamadigi icin atlandi.")

    if has_changes:
        save_state(state)
        logger.info("Durum kaydedildi.")
    else:
        logger.info("Herhangi bir degisiklik yasanmadi, state.json guncellenmedi.")
        
    logger.info("Tarama tamamlandi.")

if __name__ == "__main__":
    main()
