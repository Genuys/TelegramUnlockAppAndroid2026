<div align="center">
  <img src="https://raw.githubusercontent.com/Genuys/TelegramUnlockAppAndroid2026/main/github_banner_proxy.png" width="100%" alt="TG Proxy 2026 Banner"/>

  <h1>🚀 TG Proxy Android 2026</h1>
  <p><strong>Ультра-быстрый локальный SOCKS5-прокси со встроенным WebSocket/VLESS обходом блокировок для Telegram</strong></p>

  [![Telegram Channel](https://img.shields.io/badge/Telegram-Канал-blue?style=for-the-badge&logo=telegram)](https://t.me/TgUnlock2026)
  [![Android Support](https://img.shields.io/badge/Android-7.0+-3DDC84?style=for-the-badge&logo=android)](https://github.com/Genuys)
  [![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](#)
</div>

---

## ⚡ Особенности и Преимущества

В отличие от классических MTProto-прокси, **TG Proxy 2026** работает совершенно иначе. Это полностью локальное приложение, которое поднимает "прозрачный" SOCKS5-сервер прямо на вашем смартфоне (`127.0.0.1:1080`).

Весь трафик Telegram пропускается через мощный внутренний фильтр приложения и оборачивается в **WebSocket** или **VLESS**, пробивая любые блокировки DPI, ТСПУ и VPN-фаерволов провайдера с нулевой потерей скорости.

### 🌟 Ключевые фичи:
- **🚄 Нулевой пинг (Connection Pooling):** Приложение держит в фоне пул "горячих" TLS-подключений ко всем дата-центрам Telegram. Медиа, видео и кружочки грузятся **моментально**, в отличие от обычных Python-скриптов.
- **🛡️ 3 Режима работы:** 
  1. **Оригинал** — Прямой зашифрованный транспорт (быстрый).
  2. **Python-обходник** — Полная имитация браузерного трафика для обмана ТСПУ.
  3. **VLESS** — Интеграция с вашим личным сервером VLESS (через Xray/Reality) по одной ссылке.
- **🔋 Оптимизация батареи:** Работает в фоне через Android Foreground Service с потреблением не более 1-2% заряда в сутки.
- **🔄 Динамический порт:** Защита от обнаружения локальными фаерволами (создаёт случайный порт 10000+ вместо классического 1080 при каждом старте).
- **📱 Автозапуск:** Функция автоматического включения обходчика сразу при включении телефона.

---

## 📸 Дизайн и Интерфейс

<div align="center">
  <img src="https://raw.githubusercontent.com/Genuys/TelegramUnlockAppAndroid2026/main/proxy_logo_cyberpunk.png" width="250px" alt="TG Proxy Logo"/>
  <br/>
  <em>Стильный темный интерфейс (Dark Mode Glassmorphism)</em>
</div>

---

## 🛠 Установка и Использование

1. Скачайте последний **APK-файл** из раздела [Releases](https://github.com/Genuys/TelegramUnlockAppAndroid2026/releases) или скомпилируйте проект в Android Studio.
2. Откройте приложение, выберите нужный режим: **Оригинал** / **Python** / **VLESS**
3. Нажмите кнопку **"Запустить"**.
4. В самом приложении кликните на графу **"Ссылка для Telegram"** (`tg://socks?server=...`). Она автоматически скопируется в буфер обмена.
5. Откройте **Telegram** → Настройки → Данные и память → Настройки прокси → вставьте вашу ссылку (либо нажмите "Добавить" и вставьте скопированный IP `127.0.0.1` и выданный приложением случайный Порт).
6. Готово! Пользуйтесь Telegram без ограничений на максимальной скорости. Сами режимы обхода работают абсолютно одинаково быстро благодаря встроенному кэшированию соединений. Разница только в методе обмана алгоритмов серверов провайдеров.

---

## 💻 Для разработчиков (Сборка из исходников)

Проект полностью написан на **Java** (SDK 34) без громоздких сторонних VPN-фреймворков. Сетевая логика работает через прямые сокеты и многопоточные connection-пулы для минимальных пингов.

### Требования:
- Android Studio Iguana+ / Gradle 8.2+
- Настроенный JDK 11-17

### Инструкция по сборке:

```bash
# Склонируйте репозиторий
git clone https://github.com/Genuys/TelegramUnlockAppAndroid2026.git

# Откройте в Android Studio
# Нажмите Build -> Make Project
# Либо соберите APK через Gradle в терминале (Windows):
.\gradlew assembleDebug
```

---

## 📡 Контакты и Поддержка

Будем рады вашим Pull Request'ам и вопросам! Если вы нашли баг или хотите предложить улучшения для новых версий обходчика:
- 📢 Официальный канал разработчиков: [@TgUnlock2026](https://t.me/TgUnlock2026)
- 🐛 Issues: [Создать баг-репорт Github](https://github.com/Genuys/TelegramUnlockAppAndroid2026/issues)

<div align="center">
  <br/>
  <i>Разработано с ❤️ для свободного интернета 2026</i>
</div>
