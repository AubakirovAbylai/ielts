# IELTS Platform

Монолит Spring Boot для подготовки к IELTS: трекер прогресса, ИИ-проверка Writing и практика Speaking с голосом в браузере (Web Speech API — распознавание и синтез бесплатные, $0 за голос).

## Модули

**1. Progress Tracker** — ввод баллов L/R/W/S, автоматический Overall по официальной математике IELTS (среднее 4 навыков; дробная часть .25 округляется вверх до .5, .75 — до целого; любой пропущенный навык ⇒ Overall не считается), графики динамики (SVG, без библиотек), экспорт CSV.

**2. AI Writing Checker** — банк заданий учителя (Task 1/2, Academic/General, лимит слов и времени), редактор эссе с античит-метриками (время набора, счётчик вставок, режим экзамена с таймером), проверка ИИ по критериям TR/CC/LR/GRA, учитель может переопределить баллы и оставить комментарий. Наборы критериев учителя версионируются: загрузка с тем же названием = новая версия, старая деактивируется.

**3. AI Speaking Practice** — банк тем с вопросами, сессия практики: вопрос озвучивается (SpeechSynthesis), ответ голосом (SpeechRecognition) или текстом, отчёт ИИ по критериям FC/LR/GRA/P с рекомендациями на русском. Есть ручной ввод текста — работает без микрофона.

**Роли:** TEACHER (кабинет: ученики, задания, критерии, темы, проверка эссе) и STUDENT (трекер, writing, speaking). Доступ ученика — только к своим данным; `/api/admin/**` — только учитель.

## Запуск

```bash
cd ielts-platform
mvn package -DskipTests
java -jar target/ielts-platform-0.1.0.jar
# → http://localhost:8080
```

Требуется JDK 17+ и Maven. База — H2-файл `./data/ielts` (создаётся автоматически, при первом старте seeded демо-данными). H2-консоль: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:file:./data/ielts`, пользователь `SA`, пароль пустой).

## Демо-доступы

| Роль | Логин | Пароль |
|---|---|---|
| Учитель | `teacher@ielts.local` | `teacher123` |
| Ученик | `student@ielts.local` | `student123` |

Демо-ученик связан с учителем, целевой балл 7.0. При старте с пустой базой создаются: 2 задания Writing (Task 1 таблица, Task 2 эссе), 1 активный набор критериев, 3 темы Speaking (Education, Work, Technology).

## Переменные окружения

| Переменная | По умолчанию | Описание |
|---|---|---|
| `AI_PROVIDER` | `mock` | `mock` — всё работает офлайн без ключа; `gemini` — реальная ИИ-проверка |
| `GEMINI_API_KEY` | — | Ключ [Google AI Studio](https://aistudio.google.com/) (free tier) |
| `APP_JWT_SECRET` | `dev-secret-...` | Секрет JWT. **Обязательно сменить вне localhost** |
| `SERVER_PORT` | `8080` | Порт приложения |

### Включить реальную ИИ-проверку

```bash
AI_PROVIDER=gemini GEMINI_API_KEY=ваш_ключ java -jar target/ielts-platform-0.1.0.jar
```

Используется `gemini-flash-lite-latest` (free tier, alias на актуальную лёгкую модель Google) с JSON-режимом ответа; конкретную модель можно переопределить переменной `AI_MODEL`. Writing и Speaking шлют разные промпты; для Writing активный набор критериев учителя вставляется в системный промпт. Без ключа или без сети приложение работает в mock-режиме: эссе и speaking получают заглушечный отчёт с пометкой «МОК-режим».

## PostgreSQL для продакшена

H2 работает в режиме совместимости с PostgreSQL. Для перехода:

1. Добавьте драйвер в `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.postgresql</groupId>
       <artifactId>postgresql</artifactId>
       <scope>runtime</scope>
   </dependency>
   ```
2. Запустите с профилем и переменными:
   ```bash
   SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/ielts \
   SPRING_DATASOURCE_USERNAME=... SPRING_DATASOURCE_PASSWORD=... \
   APP_JWT_SECRET=длинная_случайная_строка \
   java -jar target/ielts-platform-0.1.0.jar
   ```
   Схема создаётся автоматически (`ddl-auto: update`). Если задать `SPRING_DATASOURCE_URL`, встроенная H2 не используется.

## Стек

- Java 17+ / Spring Boot 3.4 (Web, Security, JPA, Validation)
- H2 (MODE=PostgreSQL) — по умолчанию; PostgreSQL для продакшена
- jjw 0.12 (JWT-аутентификация, роли TEACHER/STUDENT)
- Frontend: vanilla JS SPA в `src/main/resources/static/` (без сборки, без зависимостей), графики SVG, Web Speech API
- ИИ: Gemini free tier или детерминированный mock (переключается `AI_PROVIDER`)

## Структура

```
src/main/java/kz/ielts/platform/
├── ai/            AiChecker, AiConfig (Gemini/Mock провайдеры)
├── config/        DataSeeder (демо-данные), SecurityConfig
├── controller/    REST: auth, admin, results, essays, writing, speaking, profile
├── entity/        User, StudentGroup, TestResult, IeltsBandMath, WritingTask,
│                  Essay, EssayCheck, CriteriaSet, SpeakingTopic/Session/Answer
├── repository/    Spring Data JPA
├── security/      JwtAuthFilter, JwtService, WebConfig
└── service/       EssayCheckService, SpeakingReportService
src/main/resources/static/   index.html, app.js, style.css (SPA)
```

## Сброс данных

База хранится в `./data/`. Для полной очистки: остановите приложение, удалите папку `data`, запустите снова — сидер пересоздаст демо-данные.
