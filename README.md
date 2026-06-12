# 📖 Spring Boot + Cloudflare + Stripe + OAuth2 + Gmail

## 🚀 Краткое описание

Этот проект — **Spring Boot серверное приложение** для системы бронирования авиабилетов, интегрированное с:

* **Cloudflare Tunnel** для безопасного доступа по HTTPS.
* **PostgreSQL** для хранения данных.
* **OAuth2 (Google)** для авторизации.
* **Stripe** для приема и обработки платежей.
* **Gmail SMTP** для отправки почтовых уведомлений.

Проект развернут на домене `skyflightbooking.ru` и `www.skyflightbooking.ru`.

---

## ✨ Возможности

* Регистрация и авторизация пользователей (в т.ч. через Google OAuth2).
* Управление рейсами и бронированиями.
* Оплата билетов через **Stripe API**.
* Уведомления по email.
* Cloudflare Tunnel для защищенного HTTPS.
* JWT-токены для API аутентификации.

---

## 🛠️ Стек технологий

* **Backend**: Kotlin + Spring Boot 3.5.3
* **База данных**: PostgreSQL
* **OAuth2**: Google Identity Platform
* **Оплата**: Stripe
* **SSL/Tunnel**: Cloudflare Tunnel
* **Email**: Gmail SMTP
* **Сборка**: Gradle + Kotlin DSL

---

## 📂 Структура проекта

```
src/main/kotlin/com/wheezy/server
│   SpringBootIntelliJSeApplication.kt   # Главный класс запуска Spring Boot приложения
├───Component                           # Компоненты, например планировщики задач
├───Controller                          # REST-контроллеры (API для клиентов)
├───DTO                                 # Data Transfer Objects — классы для передачи данных
├───Enums                               # Перечисления (например, статус бронирования)
├───Exception                           # Пользовательские исключения
├───Interface                           # Контракты для сервисов
├───Models                              # JPA-сущности (таблицы базы данных)
├───Repository                          # Репозитории для работы с БД
├───Security                            # Конфигурация безопасности (JWT, OAuth2)
└───Service                             # Бизнес-логика приложения
```

---

## ⚙️ Установка и запуск (локально)

### 1. Клонирование проекта

```bash
git clone https://github.com/IvankoSidan/sky-book-server.git
cd spring-boot-intelij-se
```

### 2. Настройка PostgreSQL

Создайте базу данных:

```sql
-- Создаем базу данных
CREATE DATABASE flights_db;

-- Создаем пользователя
CREATE USER user WITH PASSWORD 'your_password';

-- Даём пользователю привилегии на базу
GRANT ALL PRIVILEGES ON DATABASE flights_db TO user;
```

### 3. Конфигурация свойств

Создайте файл `src/main/resources/application-local.properties`:

```properties
spring.application.name=spring-boot-intelij-se
spring.datasource.url=jdbc:postgresql://localhost:5432/flights_db
spring.datasource.username=postgres
spring.datasource.password=YOUR_DB_PASSWORD
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.open-in-view=false
logging.file.name=app.log
server.port=8443

# SSL
server.ssl.enabled=true
server.ssl.key-store=classpath:origin.p12
server.ssl.key-store-password=YOUR_KEYSTORE_PASSWORD
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=skyflightbooking

# JWT
jwt.secret=YmFzZTY0LWVuY29kZWQtc2VjcmV0LWtleS1leGFtcGxl
jwt.expiration=3600000

# OAuth2 Google
spring.security.oauth2.client.registration.google.client-id=YOUR_CLIENT_ID
spring.security.oauth2.client.registration.google.client-secret=YOUR_CLIENT_SECRET
spring.security.oauth2.client.registration.google.redirect-uri=https://skyflightbooking.ru/login/oauth2/code/google
spring.security.oauth2.client.provider.google.user-info-uri=https://www.googleapis.com/oauth2/v3/userinfo

# Stripe
stripe.api-key=YOUR_STRIPE_SECRET_KEY
stripe.publishable-key=YOUR_STRIPE_PUBLISHABLE_KEY
stripe.webhook-secret=YOUR_STRIPE_WEBHOOK_SECRET

# Gmail SMTP
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=YOUR_EMAIL@gmail.com
spring.mail.password=YOUR_APP_PASSWORD
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

### 4. Запуск проекта

```bash
./gradlew bootRun
```

---

## 🔐 SSL и Cloudflare Tunnel

### 1. Создание аккаунта в Cloudflare

1. Перейдите на [https://dash.cloudflare.com](https://dash.cloudflare.com).
2. Зарегистрируйтесь и добавьте ваш домен.
3. Установите Cloudflare CLI: [cloudflared](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/install-and-setup/installation/).

### 2. Генерация сертификата и туннеля

```bash
cloudflared tunnel create skyflightbooking
cloudflared tunnel route dns skyflightbooking skyflightbooking.ru
```

### 3. Преобразование сертификата в PKCS12

```bash
openssl pkcs12 -export -in origin.pem -inkey origin-key.pem -out origin.p12 -name skyflightbooking
```

### 4. Config.yml

```yaml
tunnel: 9e2569ad-2acf-4238-924d-17e7c52ea787
credentials-file: C:/Users/Intel-PC/.cloudflared/9e2569ad-2acf-4238-924d-17e7c52ea787.json

loglevel: debug
protocol: http2
origin-ca-pool: C:/Users/Intel-PC/.cloudflared/cert.pem

ingress:
  - hostname: skyflightbooking.ru
    service: https://localhost:8443
    originRequest:
      noTLSVerify: true
  - hostname: www.skyflightbooking.ru
    service: https://localhost:8443
    originRequest:
      noTLSVerify: true
  - service: http_status:404
```

### 5. Запуск туннеля

```bash
cloudflared tunnel run skyflightbooking
```

---

## 📧 Настройка почтового сервиса

Используется **Gmail SMTP**. Для работы нужно:

1. Включить 2FA в Google аккаунте.
2. Сгенерировать App Password (16 символов).
3. Указать его в `spring.mail.password`.

---

## 🔑 OAuth2 Google

### Redirect URIs — что это?

Redirect URI — это адрес, на который Google перенаправляет пользователя после успешной авторизации.

Пример:

* `https://skyflightbooking.ru/login/oauth2/code/google`
* `https://www.skyflightbooking.ru/login/oauth2/code/google`

---

## 🌍 DNS-записи

### Почему Cloudflare NS, а не Reg.ru?

* Reg.ru выдает стандартные NS-серверы, но они только обслуживают базовый DNS.
* Cloudflare предоставляет **прокси, кэширование, защиту от DDoS и SSL**.
* Поэтому мы указываем **NS от Cloudflare** в панели Reg.ru:

```
carioca.ns.cloudflare.com
vern.ns.cloudflare.com
```

⏳ Обновление NS может занимать до **24 часов**.

---

## 💳 Stripe Webhook

Webhook endpoint:

```
https://skyflightbooking.ru/api/stripe/webhook
```

**Подписанные события и почему именно они:**

* `payment_intent.canceled` → нужно отслеживать отмененные платежи.
* `payment_intent.payment_failed` → фиксируем неудачные транзакции.
* `payment_intent.succeeded` → успешные оплаты (бронируем билет).

---

## 📑 SQL схема БД

<details>
<summary>Показать SQL-скрипт</summary>

```sql
BEGIN;

CREATE TABLE IF NOT EXISTS public.users (
    id bigint NOT NULL GENERATED ALWAYS AS IDENTITY (
        INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 9223372036854775807 CACHE 1
    ),
    email character varying(255) COLLATE pg_catalog."default" NOT NULL,
    password character varying(255) COLLATE pg_catalog."default",
    google_id character varying(255) COLLATE pg_catalog."default",
    name character varying(255) COLLATE pg_catalog."default",
    profile_picture character varying(255) COLLATE pg_catalog."default",
    is_enabled boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    stripe_customer_id character varying(255) COLLATE pg_catalog."default",
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_email_key UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS public.flights (
    flight_id integer NOT NULL DEFAULT nextval('flights_flight_id_seq'::regclass),
    airline_logo text COLLATE pg_catalog."default",
    airline_name character varying(100) COLLATE pg_catalog."default",
    arrive_time character varying(20) COLLATE pg_catalog."default",
    class_seat character varying(50) COLLATE pg_catalog."default",
    flight_date character varying(20) COLLATE pg_catalog."default",
    departure_city character varying(50) COLLATE pg_catalog."default",
    departure_short character varying(10) COLLATE pg_catalog."default",
    total_seats integer,
    price numeric(10, 2),
    reserved_seats text COLLATE pg_catalog."default",
    departure_time character varying(10) COLLATE pg_catalog."default",
    arrival_city character varying(50) COLLATE pg_catalog."default",
    arrival_short character varying(10) COLLATE pg_catalog."default",
    CONSTRAINT flights_pkey PRIMARY KEY (flight_id)
);

CREATE TABLE IF NOT EXISTS public.locations (
    location_id integer NOT NULL,
    city_name character varying(50) COLLATE pg_catalog."default",
    CONSTRAINT locations_pkey PRIMARY KEY (location_id)
);

CREATE TABLE IF NOT EXISTS public.bookings (
    id integer NOT NULL DEFAULT nextval('bookings_id_seq'::regclass),
    user_id bigint NOT NULL,
    flight_id bigint NOT NULL,
    seat_count integer NOT NULL DEFAULT 1,
    seat_numbers character varying(255) COLLATE pg_catalog."default" NOT NULL,
    status character varying(32) COLLATE pg_catalog."default" NOT NULL,
    booking_date timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT bookings_pkey PRIMARY KEY (id),
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_bookings_flight FOREIGN KEY (flight_id)
        REFERENCES public.flights (flight_id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.payments (
    id integer NOT NULL DEFAULT nextval('payments_id_seq'::regclass),
    user_id bigint NOT NULL,
    booking_id bigint NOT NULL,
    flight_id bigint NOT NULL,
    amount bigint NOT NULL,
    currency character varying(3) COLLATE pg_catalog."default" NOT NULL,
    provider_payment_id character varying(255) COLLATE pg_catalog."default" NOT NULL,
    status character varying(50) COLLATE pg_catalog."default" NOT NULL,
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remember_card boolean NOT NULL DEFAULT false,
    CONSTRAINT payments_pkey PRIMARY KEY (id),
    CONSTRAINT payments_provider_payment_id_key UNIQUE (provider_payment_id),
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id)
        REFERENCES public.bookings (id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_payments_flight FOREIGN KEY (flight_id)
        REFERENCES public.flights (flight_id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.notifications (
    id bigint NOT NULL DEFAULT nextval('notifications_id_seq'::regclass),
    user_id bigint NOT NULL,
    message text COLLATE pg_catalog."default" NOT NULL,
    "timestamp" timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_read boolean NOT NULL DEFAULT false,
    CONSTRAINT notifications_pkey PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

END;
```

</details>

---

## 📱 Клиент Android

Мобильное приложение (Android Studio) доступно по ссылке:
👉 [sky-book-mobile-app](https://github.com/IvankoSidan/sky-book-mobile-app)

---

## 🔍 Проверка API через Postman

1. Установите [Postman](https://www.postman.com/).
2. Отправьте GET-запрос на:

   ```
   https://skyflightbooking.ru/api/flights
   ```
3. Авторизуйтесь через Google OAuth2 или JWT.
4. Для тестирования платежей используйте тестовые карты Stripe:

   * `4242 4242 4242 4242` (успешный платеж)
   * `4000 0000 0000 9995` (отклоненный платеж)

---

## 📝 Properties Summary & Details

Для удобства все ключи и настройки собраны в одном месте:

| Свойство                     | Описание                      |
| ---------------------------- | ----------------------------- |
| `spring.datasource.url`      | URL подключения к базе данных |
| `spring.datasource.username` | Имя пользователя БД           |
| `spring.datasource.password` | Пароль пользователя БД        |
| `server.port`                | Порт приложения               |
| `server.ssl.*`               | Настройки SSL для HTTPS       |
| `jwt.secret`                 | Секретный ключ для JWT        |
| `jwt.expiration`             | Время жизни JWT               |
| `spring.security.oauth2.*`   | Настройки Google OAuth2       |
| `stripe.*`                   | Настройки Stripe API          |
| `spring.mail.*`              | Настройки Gmail SMTP          |

---

## 🎯 Заключение

Перед вами — готовое и полнофункциональное решение для современной системы бронирования авиабилетов. Оно включает в себя все ключевые компоненты: интегрированную платежную систему, безопасный доступ для пользователей и автоматические уведомления.

Теперь вы можете:

* Быстро развернуть сервер на собственном домене.
* Подключить мобильное или веб-приложение для ваших клиентов.
* Управлять пользователями, рейсами и бронированиями через удобную админ-панель.
* Легко масштабировать систему и добавлять новый функционал под ваши задачи.

Приятной разработки и успешных полетов! ✈️
