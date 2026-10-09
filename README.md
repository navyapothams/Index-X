# IndexX — Product & Inventory Management System

IndexX is a DBMS-focused product and inventory management project. PostgreSQL is the source of truth; the Spring Boot API owns business operations and the React client consumes REST endpoints.

## Project layout

- `backend/` — Java 17, Spring Boot, Spring Data JPA, PostgreSQL, Flyway, Maven
- `frontend/` — React, TypeScript, Vite, Tailwind CSS, React Router, Axios, Recharts

## Requirements

- Java 17+
- Maven 3.9+
- Node.js 20+
- PostgreSQL 15+

## Configuration

Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the process environment where the backend is launched. `backend/.env.example` lists the expected values; Spring Boot does not load `.env` files by default. No database credentials are committed.

Create a database named `indexx` (or set another database name in `DB_URL`). Flyway creates the starter schema on backend startup.

## Run

Backend (from `backend/`):

```sh
mvn spring-boot:run
```

Frontend (from `frontend/`):

```sh
npm install
npm run dev
```

The API defaults to `http://localhost:8080/api`; Vite serves the client at `http://localhost:5173` and proxies `/api` to the API server.

## Foundation scope

The first migration establishes normalized identity, product classification, business party, warehouse, product, and inventory tables, with foreign keys, unique constraints, checks, and useful indexes. The initial API exposes a health endpoint and API DTOs. Purchasing, sales, returns, reports, audit workflows, and authentication are intentionally future modules.
