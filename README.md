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

The database migrations establish normalized identity, product classification, business parties, warehouses, inventory, purchasing, sales, returns, audit history, and reporting views. The backend exposes health checks plus DTO-based CRUD and paginated search for products, categories, subcategories, and brands. Supplier/customer/warehouse APIs, transaction workflows, reports, and authentication remain future modules.

## Database demonstration data

Repeatable normal and large PostgreSQL data profiles, safe reset guidance for a dedicated `indexx_demo` database, verification queries, and `EXPLAIN ANALYZE` examples are documented in [DATABASE_SEEDING.md](DATABASE_SEEDING.md).
