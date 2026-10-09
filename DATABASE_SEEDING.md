# IndexX development data

The seed scripts live under `backend/src/main/resources/db/seed`. They are separate from Flyway migrations: they add demonstration rows only and never create, alter, truncate, or drop schema objects. V1–V3 remain unchanged. The generator uses PostgreSQL `generate_series()` and deterministic formulas rather than a large hand-written list of rows. It uses the existing order, inventory, return, and audit triggers so generated stock balances and the movement ledger stay in sync.

## What the profiles create

| Data | Normal development profile | Large demonstration profile |
|---|---:|---:|
| Categories / subcategories / brands | 5 / 20 / 10 | 50 / 200 / 300 |
| Products | 500 | 10,000 |
| Suppliers / warehouses / customers | 25 / 5 / 1,000 | 500 / 20 / 20,000 |
| Supplier-product links | 500 | 10,000 |
| Purchase orders and items | 625 / 2,500 | 50,000 / 200,000 |
| Sales orders and items | 1,250 / 2,500 | 100,000 / 200,000 |
| Product reviews / searches | 5,000 / 5,000 | 100,000 / 100,000 |
| Returns | A few percent of eligible orders | A few percent of eligible orders |
| Inventory movements | Posted by purchase, sale, and return triggers | More than 300,000 from the normal workflow |
| Audit logs | Includes audit-triggered changes | At least 100,000 review insert events, plus master/order/inventory changes |

Each profile also ensures one inactive, credential-disabled application user per required role. Suppliers and customers remain business records, not login users. Synthetic email addresses use the reserved `.invalid` domain.

## Requirements and connection

Use PostgreSQL 16 client tools and a migrated IndexX database. The scripts prompt for the PostgreSQL password and do not save it. For example, from the repository root in PowerShell:

```powershell
$pgBin = 'C:\Program Files\PostgreSQL\16\bin'
& "$pgBin\psql.exe" -X -W -h localhost -U postgres -d indexx -f backend\src\main\resources\db\seed\seed_dev.sql
```

For a dedicated, disposable large-data database, first create `indexx_demo`, set `DB_URL=jdbc:postgresql://localhost:5432/indexx_demo` for the backend, and start Spring Boot once so Flyway applies the existing migrations. Then run:

```powershell
& "$pgBin\psql.exe" -X -W -h localhost -U postgres -d indexx_demo -f backend\src\main\resources\db\seed\generate_large_dataset.sql
```

To run the large profile against the existing `indexx` database, use `-d indexx` instead. This is additive, but it writes hundreds of thousands of rows and generates audit history; reserve several minutes and disk space. Run the generator once per profile/database. Each script detects its own product SKU prefix and stops instead of duplicating rows. Neither profile deletes data or changes existing product, user, or order rows.

## Resetting demo data

The large generator is append-only and deliberately has no in-place cleanup command because inventory transactions and audit logs are immutable, and historical orders restrict deletion of master data. For a clean repeat, use the dedicated disposable database `indexx_demo`; never point the reset script at `indexx`.

After checking that `indexx_demo` contains no data you need, reset it with:

```powershell
& backend\src\main\resources\db\seed\reset_demo_database.ps1 -ConfirmReset
```

The script has a fixed database-name guard and drops/recreates only `indexx_demo`. Set `INDEXX_PG_BIN` if PostgreSQL is installed somewhere else. Afterward, point `DB_URL` at `indexx_demo`, start Spring Boot to run Flyway, and run the desired seed profile. The scripts do not reset `indexx`.

## Reproducibility and inventory rules

Row counts, business keys, names, statuses, order dates, review/search dates, quantities, and prices use fixed integer formulas. The order dates are spread over a fixed 24-month window ending 2026-10-01; review and search dates use fixed windows too. PostgreSQL identity values depend on prior rows, so generated surrogate IDs can differ between databases; relationships are resolved by unique SKUs, emails, and business numbers. Database-managed `created_at`, `updated_at`, audit, receipt, and inventory movement timestamps reflect when the seed is executed.

The generator creates a purchase catalog with supplier-product links, creates purchase headers and four items per order, then orders and receives those purchases through the database workflow. Each product/warehouse pair receives stock before its corresponding sale is completed. A small portion of purchases are partially received or cancelled after partial receipt. Sales are completed, cancelled, or left as drafts; only completed orders post negative inventory movements and receive settled payments. Customer returns restock one sold unit, while supplier returns remove one received unit. The ledger trigger updates the protected current inventory balance. No trigger is disabled for speed.

## Verification and query plans

After seeding, run:

```powershell
& "$pgBin\psql.exe" -X -W -h localhost -U postgres -d indexx_demo -f backend\src\main\resources\db\seed\verify_dataset.sql
```

Use `-d indexx` if seeding the existing database. Verification reports counts for all major tables, product distributions by category and brand, supplier catalog sizes, warehouse stock, monthly sales, top products/customers, low stock, FK/check/unique constraints, and indexes. The ledger consistency query must return zero rows. The final `EXPLAIN (ANALYZE, BUFFERS)` statements execute read-only indexed SKU and date-filtered reporting queries and show actual row counts, buffers, and execution time after `ANALYZE` refreshes planner statistics.

## Files

- `seed/seed_dev.sql` selects the normal development profile.
- `seed/generate_large_dataset.sql` selects the large profile.
- `seed/generate_dataset.sql` is the shared SQL generator.
- `seed/verify_dataset.sql` contains row-count, reporting, consistency, and query-plan checks.
- `seed/reset_demo_database.ps1` recreates only the dedicated `indexx_demo` database after explicit confirmation.
