# Переводчик звонков (ES → RU)

Android-приложение: синхронный перевод испанского в русский прямо во время звонка.

## Что делает

- Автоматически включается при входящем/исходящем звонке
- Показывает overlay поверх экрана звонка
- Распознаёт испанскую речь (Google STT)
- Переводит на русский в реальном времени (MyMemory API, бесплатно)
- Записывает аудио и текстовый транскрипт
- Сохраняет в папку `Android/data/com.calltranslator/files/recordings/`

## Сборка APK

### Вариант 1 — Android Studio (рекомендуется)

1. Скачай [Android Studio](https://developer.android.com/studio)
2. Открой папку `callTranslator/` как проект
3. Build → Build Bundle(s) / APK(s) → Build APK(s)
4. APK будет в `app/build/outputs/apk/debug/app-debug.apk`

### Вариант 2 — командная строка (если установлен JDK + Android SDK)

```bash
cd callTranslator
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Вариант 3 — GitHub Actions (без установки Android Studio)

1. Создай репозиторий на GitHub
2. Загрузи туда папку `callTranslator/`
3. Добавь файл `.github/workflows/build.yml` (см. ниже)
4. Actions автоматически соберут APK и опубликуют как artifact

**.github/workflows/build.yml:**
```yaml
name: Build APK
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build APK
        run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v3
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
```

## Установка APK на телефон

1. На POCO M7: Настройки → Безопасность → Установка из неизвестных источников → включить
2. Перенести APK на телефон (USB / Telegram себе / Google Drive)
3. Открыть файл — установить

## Первый запуск

1. Открой приложение
2. Нажми **"Выдать разрешения"** — разреши микрофон и телефон
3. Нажми **"Разрешить overlay"** — в списке найди приложение, включи
4. Убедись что переключатель включён
5. Всё — при следующем звонке переводчик запустится автоматически

## Важно для Xiaomi/POCO (HyperOS)

HyperOS дополнительно блокирует фоновые приложения. После установки:

1. Настройки → Приложения → Переводчик звонков → Батарея → **Без ограничений**
2. Настройки → Приложения → Переводчик звонков → Автозапуск → **Включить**
3. Настройки → Приложения → Переводчик звонков → Другие разрешения → **Показывать поверх других приложений** → Включить

## Как пользоваться

1. Тебе звонит испанец
2. Берёшь трубку
3. **Включаешь громкую связь** (одна кнопка на экране звонка)
4. Внизу экрана появляется тёмная панель с переводом
5. Разговариваешь — видишь перевод с задержкой 1-3 секунды
6. Звонок закончился — панель исчезает, файлы сохранены

## Файлы после звонка

Путь: `Телефон/Android/data/com.calltranslator/files/recordings/`

- `call_2026-05-03_14-30-00.mp4` — аудио запись
- `transcript_2026-05-03_14-30-00.txt` — текстовый транскрипт с переводом

## Технический стек

| Компонент | Решение | Стоимость |
|-----------|---------|-----------|
| STT | Android SpeechRecognizer (Google) | Бесплатно |
| Перевод | MyMemory API | Бесплатно (500K символов/мес) |
| Overlay | WindowManager TYPE_APPLICATION_OVERLAY | — |
| Запись | MediaRecorder (MIC) | — |
| Обнаружение звонка | BroadcastReceiver PHONE_STATE | — |
