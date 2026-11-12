# Tejas Photography — Backend (Spring Boot + Neon PostgreSQL)

REST API for the React website: **login / register, date availability, enquiries, admin dashboard, WhatsApp confirmation.**
Java 17 · Spring Boot 3.5 · Spring Security (stateless JWT) · Spring Data JPA · Flyway · PostgreSQL (Neon).

> **Honest note about Vercel:** Vercel cannot run a Java/Spring Boot server (its functions support Node, Python, Go, Ruby…).
> So the setup is: **React site on Vercel (or Netlify) + this backend on Render/Railway/Koyeb (Docker) + database on Neon.**
> All three have free tiers. Steps are in section 6.
>
> **Not yet compiled:** the build sandbox this was written in could not download Maven dependencies, so `mvn test`
> has **not been run**. Run `mvn test` once on your PC (section 5) — if anything fails, send me the error.
> (Verified separately: the Neon URL parser, and the patched React `Contact.jsx` builds.)

---------------------------------------------------------------------------------------------------

## 1. How the system works

```
 Customer / Admin browser (React on Vercel)
        │  HTTPS + JSON   (Authorization: Bearer <JWT> after login)
        ▼
 Spring Boot API  (Render / Railway, Docker)
   ├─ Spring Security filter chain: CORS → JWT filter → role rules
   ├─ Controllers  ──►  Services  ──►  Repositories (JPA)
   └─ WhatsAppService ──► Meta WhatsApp Cloud API   (optional)
        │  JDBC over SSL
        ▼
 Neon PostgreSQL   (tables: users, inquiries — created by Flyway)
```

### Request flows
**Register / login** — `POST /api/auth/register` stores the user with a **BCrypt** password hash and returns
`{role, name, email, phone, token}` (exactly what the frontend keeps as `tp_session`). `POST /api/auth/login` takes
`{role, email, password}`; the **User / Admin tab** is enforced: a customer cannot log in on the Admin tab and vice-versa.
The token is a signed JWT (24 h, HS256) containing the user id and role.

**Customer sends an enquiry** — the form calls `GET /api/dates/check?date=…` (shows *available / n enquiries / already
booked*) and then `POST /api/inquiries`. This is public (no login needed). If the customer *is* logged in, the enquiry is
linked to their account. The phone is normalised to `+91…`; past dates are rejected. Status starts as **`new`**.

**Admin dashboard** — `GET /api/inquiries` (ADMIN only) lists everything newest-first; the UI groups by date and flags
clashes. `PATCH /api/inquiries/{id}/status` with `{status:"confirmed"|"rejected"|"new"}`:

1. Short DB transaction: the day's rows are locked (`SELECT … FOR UPDATE`); if the date already has
   `MAX_CONFIRMED_PER_DAY` (default **1**) confirmed enquiries, the API answers **409 "This date is already booked"**
   → no double booking, even with two admins clicking at once.
2. Status saved.
3. **After** the transaction, the WhatsApp message goes to the customer (Cloud API). The result
   (`SENT / FAILED / DISABLED`) is saved on the enquiry. A WhatsApp failure **never** undoes the confirmation, and the
   response always contains `whatsappLink` (a `wa.me` link) as a manual fallback — the dashboard's WhatsApp button.

### API
| Method & path | Who | Purpose |
|---|---|---|
| `POST /api/auth/register` | public | create customer account → session |
| `POST /api/auth/login` | public | `{role,email,password}` → session |
| `GET /api/auth/me` | logged in | current user |
| `GET /api/dates/check?date=2026-12-24` | public | `{date,count,booked}` |
| `POST /api/inquiries` | public | create enquiry |
| `GET /api/inquiries/mine` | logged in | my enquiries |
| `GET /api/inquiries` | **admin** | all enquiries |
| `PATCH /api/inquiries/{id}/status` | **admin** | confirm / reject (+ WhatsApp) |
| `GET /api/health` | public | health check (Render) |
| `/swagger-ui.html` | public | interactive docs |

Errors are JSON `{status, error, message, path}`; the React app shows `message`.

---------------------------------------------------------------------------------------------------

## 2. Database structure (Neon / PostgreSQL)
Created automatically by Flyway from `src/main/resources/db/migration/V1__init.sql` (copy in `docs/schema.sql`).
Hibernate runs with `ddl-auto=validate`, so it never changes the schema by itself.

```
 users 1 ───────────< inquiries        (inquiries.user_id is NULL for anonymous visitors)
```

**`users`**
| column | type | notes |
|---|---|---|
| id | BIGINT identity PK | |
| full_name | VARCHAR(100) | |
| email | VARCHAR(150) | **UNIQUE** (stored lower-case) |
| phone | VARCHAR(20) | `+919876543210` |
| password | VARCHAR(255) | BCrypt hash |
| role | VARCHAR(20) | `USER` \| `ADMIN` (CHECK) |
| enabled | BOOLEAN | default true |
| created_at | TIMESTAMPTZ | |

**`inquiries`**
| column | type | notes |
|---|---|---|
| id | BIGINT identity PK | |
| user_id | BIGINT FK → users(id) | `ON DELETE SET NULL`, optional |
| name / phone / email | VARCHAR | customer details |
| event_date | DATE | requested date, optional |
| service | VARCHAR(100) | e.g. "Wedding Photography" |
| message | VARCHAR(2000) | |
| status | VARCHAR(20) | `NEW` \| `CONFIRMED` \| `REJECTED` (CHECK) — API shows lower-case |
| whatsapp_status | VARCHAR(20) | `NOT_SENT` \| `SENT` \| `FAILED` \| `DISABLED` |
| whatsapp_message_id / whatsapp_error | VARCHAR | result of the Cloud API call |
| created_at / updated_at / confirmed_at | TIMESTAMPTZ | |

Indexes: `(event_date, status)` (availability + clash checks), `user_id`, `created_at DESC`.
The first **admin user is created automatically** on start from `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

> You wrote "neo db" — I read it as **Neon** (serverless Postgres). If you meant **Neo4j** (graph database), tell me;
> this data is relational so Postgres is the better fit.

---------------------------------------------------------------------------------------------------

## 3. Project layout
```
src/main/java/com/tejas/photography
  config/      AppProperties, DataSourceConfig + DatabaseUrl (Neon URL parser), AdminSeeder, OpenApiConfig
  security/    SecurityConfig, JwtService, JwtAuthenticationFilter, UserPrincipal, JSON 401/403 handler
  controller/  AuthController, InquiryController, HealthController
  service/     AuthService, InquiryService (rules + locking), WhatsAppService
  repository/  UserRepository, InquiryRepository
  entity/ dto/ enums/ exception/ util/
src/main/resources/ application.properties, db/migration/V1__init.sql
frontend-integration/  Contact.jsx (wired to the API) + env example
Dockerfile · render.yaml · .env.example
```

## 4. Configuration (environment variables)
See `.env.example`. Required in production: `DATABASE_URL`, `JWT_SECRET` (32+ chars), `ADMIN_EMAIL`,
`ADMIN_PASSWORD`, `CORS_ORIGINS` (wildcards allowed: `https://*.vercel.app`).
Optional: `MAX_CONFIRMED_PER_DAY`, `WHATSAPP_*`.

## 5. Run locally
Needs Java 17 + Maven. Either point `DATABASE_URL` at your Neon database, or use local Postgres:
```bash
# option A: Neon
export DATABASE_URL='postgresql://USER:PASS@ep-xxx.aws.neon.tech/neondb?sslmode=require'
# option B: local Postgres with a database called tejas_photography (user postgres / password postgres)

mvn test                 # runs the H2 integration tests + URL parser tests
mvn spring-boot:run      # http://localhost:8080/swagger-ui.html
```
Admin login (default, change it!): `admin@tejasphotography.com` / `Admin@12345`.
Point the React app at it: `REACT_APP_API_URL=http://localhost:8080 npm start`.

## 6. Deploy free (Neon + Render + Vercel)
1. **Neon** — neon.tech → create project → copy the connection string (`postgresql://…?sslmode=require`). Nothing else:
   Flyway creates the tables on the first start.
2. **Backend on Render** — push this folder to a GitHub repo → Render → *New → Blueprint* (uses `render.yaml`) or
   *New → Web Service → Docker*. Set env vars: `DATABASE_URL`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_PHONE`,
   `CORS_ORIGINS=https://<your-site>.vercel.app`. (`JWT_SECRET` is generated.) Health check path `/api/health`.
   Railway / Koyeb / Fly.io work the same way with the Dockerfile. Free Render services sleep after ~15 min idle, so the
   first request can take ~30–60 s; Neon also wakes in about a second.
3. **Frontend on Vercel** — import the React repo; add env var `REACT_APP_API_URL=https://<render-service>.onrender.com`
   (no trailing slash) and redeploy. (On Netlify: same variable, build `npm run build`, publish `build`.)
   Replace `src/components/Contact.jsx` with `frontend-integration/Contact.jsx` — **your current repo's form only opens
   WhatsApp and never calls the API**, so no enquiry would reach the dashboard without this change. The new form
   reads the `prefill-inquiry` event from the *Book Date* buttons, shows live availability, and saves the enquiry.
4. Remove the `DEMO_ADMIN` fallback in `api.js` when you go live (it only applies when `REACT_APP_API_URL` is empty).

## 7. WhatsApp confirmation (optional, automatic)
Without any setup, confirming still gives the dashboard's **WhatsApp** button (opens WhatsApp with the message).
For automatic sending: create a Meta WhatsApp Business app → get `WHATSAPP_PHONE_NUMBER_ID` and a permanent token →
create the template `booking_confirmed` (3 variables) with body
`Hi {{1}}, your {{2}} on {{3}} with Tejas Photography is confirmed. We will contact you with the next steps.`
→ set `WHATSAPP_ENABLED=true`. (`WHATSAPP_MODE=text` only delivers if the customer messaged you in the last 24 h.)

## 8. Known limits
* Not compiled/tested in the build sandbox (see top). Flyway SQL is Postgres-only; tests use H2 with Hibernate-created tables.
* No email verification / password reset yet. Passwords: min 6 chars.
* JWT lives in the browser's localStorage (as the existing frontend does); rotate `JWT_SECRET` to log everyone out.
