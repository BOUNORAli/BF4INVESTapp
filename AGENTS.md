# AGENTS.md

## Cursor Cloud specific instructions

### Architecture

BF4 Invest is a 2-tier app (Angular 21 frontend + Spring Boot 3.2 backend) with MongoDB 7.0 as the database. See `README.md` for full details.

### Required Services

| Service | Port | Start Command |
|---------|------|---------------|
| MongoDB 7.0 | 27017 | `sudo docker start mongodb` (or `sudo docker run -d -p 27017:27017 --name mongodb mongo:7.0` if container doesn't exist) |
| Backend (Spring Boot) | 8080 | `cd backend && mvn spring-boot:run -DskipTests` |
| Frontend (Angular) | 4200 | `cd frontend && npm run dev` |

### Running Services

1. **Start MongoDB first** — the backend will fail to connect without it.
2. **Start backend** — it auto-seeds dev data on first run (admin user, payment modes, chart of accounts).
3. **Start frontend** — connects to backend at `http://localhost:8080/api`.

### Default Login

- Email: `admin@bf4invest.ma`
- Password: `admin123`

### Lint / Type-check

- **Frontend**: `cd frontend && npx tsc --noEmit` (no ESLint configured in this project).
- **Backend**: compilation is validated during `mvn clean install -DskipTests`.

### Tests

- **Backend**: `cd backend && mvn test` — uses Testcontainers (requires Docker). Note: 3 of 9 tests have pre-existing failures (`TVAServiceTest` x2, `AuthServiceTest` x1) unrelated to environment setup.
- **Frontend**: no test framework configured.

### Build

- **Frontend**: `cd frontend && npx ng build --configuration=development`
- **Backend**: `cd backend && mvn clean install -DskipTests`

### Gotchas

- Java 21 (system default) works fine for compiling/running this Java 17-targeted project.
- Docker requires `sudo` in this environment. MongoDB runs as a Docker container.
- The backend creates default users and seed data automatically on first startup when the DB is empty.
- Optional services (SMTP, Cloudinary, Supabase, Gemini OCR) are configured via environment variables in `backend/src/main/resources/application.yml`; the app runs fully without them.
