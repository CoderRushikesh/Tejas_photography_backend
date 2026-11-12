Tejas Photography: Backend API

Spring Boot REST API for a photography booking system: customer and admin JWT auth, date enquiries with clash detection, admin dashboard data, and optional WhatsApp Cloud API confirmations.

Frontend repository: Tejas_photography (React website and admin dashboard)

Features
Stateless JWT authentication with two roles: USER (customer) and ADMIN
Admin account created automatically on first start from configuration
Public enquiry submission and date availability check
Admin endpoints to list, confirm and reject enquiries
Booking rule: the number of confirmed bookings per day is configurable (MAX_CONFIRMED_PER_DAY)
Optional automatic WhatsApp confirmation through the Meta Cloud API (off by default; the dashboard's WhatsApp button works without it)
Flyway migrations, Swagger UI, CORS configured for the frontend
Tech stack

Java 17 · Spring Boot 3.5 · Spring Security (JWT) · Spring Data JPA / Hibernate · Flyway · PostgreSQL (production) / MySQL (local testing) · springdoc OpenAPI · Maven

API endpoints

Base URL (local): http://localhost:8081 (or the port shown on startup)

Method	Path	Auth	Purpose
GET	/api/health	none	Health check
POST	/api/auth/register	none	Customer sign-up
POST	/api/auth/login	none	Login. Body has role (user or admin), email, password
GET	/api/auth/me	token	Current user
GET	/api/dates/check?date=YYYY-MM-DD	none	Enquiry count and whether the date is booked
POST	/api/inquiries	none	Submit an enquiry
GET	/api/inquiries/mine	token	The logged-in customer's enquiries
GET	/api/inquiries	admin	All enquiries (dashboard)
PATCH	/api/inquiries/{id}/status	admin	Set status: confirmed / rejected / new



How a request flows
React site ──▶ JwtAuthenticationFilter ──▶ Controller ──▶ Service ──▶ Repository ──▶ Database
                 (checks Bearer token)       (validation)   (booking rules)   (JPA)
A customer submits POST /api/inquiries. The service validates it and stores it with status NEW.
The admin logs in and receives a JWT, then calls GET /api/inquiries for the dashboard.
Confirming a date (PATCH .../status) applies the per-day booking rule, updates the row and returns a WhatsApp link for the customer.
Proof of working

The API was tested end to end from the React frontend, with the backend running locally in STS. The console shows the Hibernate SQL for each request.

Enquiry submitted from the website: the date-count query and insert into inquiries appear in the console. Enquiry form and backend console

The stored enquiry appears on the admin dashboard: SQL logs on the left, dashboard on the right. Backend SQL logs and admin dashboard

More screenshots of the full flow (clash detection, confirm / reject, WhatsApp button) are in the frontend README.

Run locally

Prerequisites: JDK 17, Maven, a database (MySQL or PostgreSQL).

bash
git clone https://github.com/CoderRushikesh/Tejas_photography_backend.git
cd Tejas_photography_backend

Create src/main/resources/application-local.properties (do not commit it) with your own values:

properties
spring.datasource.url=jdbc:mysql://localhost:3306/tejas_photography?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=<your-db-password>
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

app.cors.allowed-origins=http://localhost:3000
app.admin.email=<admin-email>
app.admin.password=<admin-password>
app.admin.name=Tejas
app.admin.phone=<admin-phone>
app.booking.max-confirmed-per-day=1
app.whatsapp.enabled=false

Run with the local profile:

bash
mvn spring-boot:run "-Dspring-boot.run.profiles=local"

The admin account is created on first start. The log shows Seeded admin account. Test with Swagger UI or Postman.

For PostgreSQL (the default, used with Neon in production) set DATABASE_URL and the other variables below, and run without the local profile.

Configuration (environment variables)
Variable	Purpose
DATABASE_URL / DB_URL, DB_USERNAME, DB_PASSWORD	Database connection
JWT_SECRET	JWT signing key, at least 32 characters.

