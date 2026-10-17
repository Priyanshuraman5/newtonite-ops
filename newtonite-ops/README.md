# newtonite-ops — Operations Under Pressure

Internal work-item tracker API: team-scoped roles, server-enforced workflow, append-only activity log, filtered paginated lists.
Stack: Java 21, Spring Boot 3.3, PostgreSQL 16 (JdbcTemplate + Flyway), JUnit 5 + Testcontainers.

## Run
Prerequisites: JDK 21, Maven, Docker.
    docker compose up -d
    mvn spring-boot:run          # migrates and seeds on first start (3 teams, 8 users, 5000 items)
## Test (needs Docker)
    mvn test
## Demo logins (password `password123`)
alice@ops.test LEAD (Payments) · bob / carol MEMBER (Payments) · dave VIEWER (Payments) · erin LEAD (Support) · frank MEMBER (Support) · grace MEMBER (Compliance) · heidi LEAD (Compliance)
## Try it
    TOKEN=$(curl -s localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"email":"bob@ops.test","password":"password123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
    curl -H "Authorization: Bearer $TOKEN" 'localhost:8080/items?view=urgent&limit=5'
    curl -X POST -H "Authorization: Bearer $TOKEN" localhost:8080/items/1/claim
## Known limitations
No frontend yet; no outbox/worker; tokens are in-memory (lost on restart, single instance); no input enum validation; no reopen from CLOSED; app is not containerised (only Postgres is in compose).
