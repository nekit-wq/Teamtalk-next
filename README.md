# TeamTalk Project (Client, SDK & Server)

Комплексный проект голосового и текстового мессенджера **TeamTalk 5**, включающий в себя модифицированный Android-клиент (**Teamtalk-next**), полный исходный код кроссплатформенного **TeamTalk 5 SDK** и серверные сервисы.

---

## 📁 Структура рабочей области

```text
/root/tt/
├── client/     # Android-клиент (Teamtalk-next)
├── sdk/        # Исходный код TeamTalk 5 SDK (C++, JNI, Java, .NET, Python, Rust)
├── server/     # Серверные сервисы (yt-dlp stream resolver для медиапотоков)
└── README.md   # Документация по всему проекту
```

---

## 📱 1. Android-клиент (`client/`)

Модифицированный клиент **TeamTalk 5** для Android с расширенным функционалом администрирования, кастомизацией звуковых тем, встроенным эквалайзером, улучшенным плеером и защитой от спама.

### 🌟 Основные возможности
* **Интеллектуальная защита от спама:** автоматическая отписка от флудящих пользователей, настраиваемые лимиты сообщений.
* **Кастомизация звуковых тем:** назначение локальных аудиофайлов на любые события (вход/выход, PTT, сообщения) и встроенный режим тишины.
* **Расширенное администрирование сервера:**
  * Управление аккаунтами пользователей (создание, редактирование прав, удаление).
  * Управление бан-листом сервера и канала.
  * Редактирование параметров сервера и просмотр детальной статистики RX/TX и аптайма.
* **Мультимедиа и стриминг:** локальный медиаплеер с управлением воспроизведением, поддержка стриминга файлов в каналы.
* **Эквалайзер и аудиоэффекты:** 10-полосный эквалайзер, усиление басов, реверберация, 3D-звук.

### 🏗️ Архитектура кода (`client/src/main/java/`)
* **`org.nekit.ttproplus.backend/`**
  * `TeamTalkService.java` — главная фоновая служба, управляющая соединением, аудиопотоками и взаимодействием с SDK.
  * `AudioEffectsManager.java` — управление эквалайзером и звуковыми эффектами.
  * `BluetoothHeadsetHelper.java` — интеграция с Bluetooth-гарнитурами (SCO режим).
* **`org.nekit.ttproplus.gui/`**
  * `MainActivity.java` — основной интерфейс (каналы, чат, участники, медиа).
  * `ServerStatsActivity.java` — просмотр статистики трафика и аптайма сервера.
  * `UserAccountsActivity.java`, `ServerBannedUsersActivity.java` — интерфейсы администрирования.
  * `EqualizerActivity.java` — экран эквалайзера и аудиопресетов.
* **`org.nekit.ttproplus.data/`**
  * `Preferences.java` — константы ключей настроек приложения.
  * `ChatHistoryDbHelper.java` — база данных SQLite для сохранения истории чата.
* **`dk.bearware.events/`**
  * `ServerStatsHelper.java` — мост для обработки событий статистики сервера из JNI SDK.

### 🔨 Сборка Android-клиента

#### Требования
* **JDK 17**
* **Android SDK** (API 21+)

#### Команды для сборки
```bash
cd /root/tt/client

# Сборка отладочной версии (Debug APK)
./gradlew assembleDebug

# Сборка релизной версии (Release APK)
./gradlew assembleRelease
```

Собранные файлы APK будут находиться в:
`client/build/outputs/apk/debug/`

---

## ⚙️ 2. TeamTalk 5 SDK (`sdk/`)

Официальный репозиторий **TeamTalk 5 SDK** от BearWare.dk, содержащий исходный код ядра и обёрток для различных платформ и языков.

### 🧩 Компоненты SDK
* **`Library/TeamTalkLib/`** — C++ ядро библиотеки TeamTalk (сетевой протокол, аудиодвижок, кодеки Opus/Speex, алгоритмы эхоподавления WebRTC).
* **`Library/TeamTalkJNI/`** — JNI-мост и Java-интерфейсы (`dk.bearware.*`), используемые в Android-клиенте.
  * `src/dk/bearware/` — Java классы API SDK.
  * `jni/` — C++ реализация JNI методов.
* **`Library/TeamTalk_DLL/`** — C-API интерфейсы и заголовки библиотеки.
* **`Library/TeamTalk.NET/`** — библиотека-обёртка для платформы .NET (C#).
* **`Library/TeamTalkPy/`** — Python-модуль для работы с TeamTalk.
* **`Library/teamtalk_rust/`** — Rust-биндинги для работы с библиотекой.
* **`Client/`** — примеры клиентов для различных ОС (Qt, MFC, .NET, iOS, Android).
* **`Server/`** — исходный код и примеры серверов TeamTalk.

### 🔄 Связь между `sdk/` и `client/`
Android-клиент использует скомпилированные артефакты SDK:
1. **Java API:** `client/libs/TeamTalk5.jar` (собирается из `sdk/Library/TeamTalkJNI/src`).
2. **Нативные JNI-библиотеки:** `client/src/main/jniLibs/` (`arm64-v8a`, `armeabi-v7a`), собираемые из C++ ядра `TeamTalkLib` и `TeamTalkJNI`.

Сборка нативных библиотек из `sdk/` осуществляется с помощью CMake и инструментов в `sdk/Build/` (или через Docker):
```bash
# Сборка библиотек под Android через Docker:
docker compose -f sdk/Build/Docker/docker-compose.yml run --rm android make -C /TeamTalk5/Build android-all
---

## 🌐 3. Серверные сервисы (`server/`)

Сервисы поддержки серверной инфраструктуры и потокового вещания медиа.

### `server/yt-dlp-resolver/`
HTTP-микросервис на Flask/Python, используемый TeamTalk Next для резолвинга ссылок YouTube в прямые временные аудиопотоки для трансляции в каналы TeamTalk.

* Запуск сервиса:
```bash
cd /root/tt/server/yt-dlp-resolver
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python3 app.py
```
* Эндпоинты:
  * `GET /health` — проверка работоспособности сервиса.
  * `GET /resolve?url=<youtube_url>` — получение прямой аудиоссылки.

---

## 🛠️ Руководство разработчика по внесению изменений

1. **Добавление новой настройки в Android-клиент:**
   * Зарегистрируйте ключ в `client/src/main/java/org/nekit/ttproplus/data/Preferences.java`.
   * Добавьте элемент интерфейса в нужный XML-файл (`client/src/main/res/xml/`).
   * Добавьте строки в `res/values/strings.xml` и русский перевод в `res/values-ru/strings.xml`.

2. **Логирование и отладка:**
   * Просмотр логов клиента: `adb logcat -s TeamTalkService MainActivity`.
