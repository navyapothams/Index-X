# IndexX database design

## Migration strategy

Flyway migrations are in `backend/src/main/resources/db/migration` and run in numeric order. V1 is the existing foundation and is left unchanged. V2 preserves existing rows by renaming `app_users` to `users`, `app_roles` to `roles`, and `products.unit_price` to `products.selling_price`; it then adds the operational tables and fields. Existing foreign keys follow PostgreSQL table renames. V3 adds database functions, workflow and audit triggers, and reporting views. No table is recreated and no existing row is intentionally deleted.

## Tables and relationships

| Area | Tables | Main relationships |
|---|---|---|
| Classification | `categories`, `subcategories`, `brands` | One category has many subcategories and products; products may reference a brand. A composite FK ensures a product's optional subcategory belongs to its category. |
| Product and supply | `products`, `suppliers`, `supplier_products` | `supplier_products` is the many-to-many junction. Its primary key is `(supplier_id, product_id)` and it stores supplier-specific code, price, lead time, and preferred status. |
| Parties and access | `customers`, `users`, `roles`, `user_roles` | Customers are business records, not login accounts. `user_roles` is the many-to-many junction between internal users and roles. |
| Locations and stock | `warehouses`, `inventory`, `inventory_transactions` | `inventory` has one row per `(warehouse_id, product_id)`. Transactions are the append-only movement ledger. |
| Purchasing | `purchase_orders`, `purchase_order_items` | One supplier and warehouse per order; one row per product line. A receipt posts its received quantity to the inventory ledger. |
| Sales and payments | `sales_orders`, `sales_order_items`, `payments` | An order may belong to a customer; line items are separate. Each payment belongs to the order header, never to a line item. |
| Returns | `returns`, `return_items` | A return references exactly one source sales or purchase order. Each item references the matching source order line. |
| Other | `product_reviews`, `search_history`, `audit_logs` | Reviews are unique per product/customer. Search history can be retained without requiring login. Audit events retain a textual entity key and before/after JSON. |

## Normalization and stored values

The schema separates master data from order and movement records, and uses junction tables for both many-to-many relationships. Category, brand, supplier, and customer details are not repeated in product records. Order line prices and costs are snapshots of the transaction price, so later price-list edits do not rewrite history. Order totals are computed by `calculate_sales_order_total`; they are not copied into order headers.

`inventory.quantity_on_hand` is the current warehouse/product balance used for quick reads. It is a maintained balance projection of the append-only `inventory_transactions` ledger: one trigger updates both in the same PostgreSQL transaction. A guard prevents direct balance edits or deletes; post a movement instead. V2 turns any pre-existing V1 stock balances into opening adjustment movements without changing their quantities. Per-warehouse `inventory.reorder_level` can override the product default; `NULL` means inherit `products.reorder_level`.

## Important constraints and delete behavior

- Master IDs are primary keys; SKU, order/return numbers, role names, warehouse codes, and applicable business keys are unique.
- Foreign keys protect every relationship. Historical order lines, stock movements, returns, and payments restrict deletion of referenced records. Draft order lines may cascade with their draft header. Customer and employee references on historical orders can become null while preserving the order. Audit actor references restrict hard deletion; deactivate users instead.
- Prices and payment amounts cannot be negative (payments must be positive); order and return quantities must be positive; stock cannot go below zero; discounts are between 0 and 1; ratings are 1–5. Status, role, payment method, disposition, and movement type values are checked.
- `inventory_transactions` uses a signed `quantity_delta`: purchases and restock returns are positive, sales and supplier returns are negative. Adjustments require a note. The ledger cannot be updated or deleted; correct mistakes with a compensating adjustment.

## Indexes

Unique constraints provide indexes for SKU, names/codes, emails, order numbers, and junction keys. Additional indexes cover lowercased and English full-text product-name search, product category/brand, product-to-supplier lookups, role-to-user lookups, warehouse/product balance lookups, stock movement history by product/warehouse/time and type/time, purchase and sales dates/statuses, customer order history, return source/status, payment order/status, review product/status, and audit actor/entity/timestamp queries. Indexes on nullable movement references are partial to avoid indexing empty references.

## Views and functions

- `product_inventory_summary`: per-product aggregate quantity, warehouse count, and inventory cost value.
- `low_stock_products`: active product/warehouse balances at or below the warehouse override or product threshold.
- `monthly_sales_summary`: completed order counts and totals grouped by month.
- `category_sales_summary`: completed units and revenue grouped by category.
- `supplier_performance`: purchase counts, received spend, delivery duration, and catalog breadth by supplier.
- `customer_purchase_summary`: completed order count, lifetime value, and last purchase by customer.
- `current_stock(product_id, warehouse_id)`: returns the current balance, or zero when no balance row exists.
- `calculate_sales_order_total(sales_order_id)`: computes discounted line totals from the order's current immutable-after-completion lines.

Example date-filtered aggregate query (Spring parameters shown):

```sql
SELECT c.id, c.name, COUNT(DISTINCT so.id) AS order_count,
       SUM(soi.quantity * soi.unit_price * (1 - soi.discount_rate)) AS sales_total
FROM categories c
JOIN products p ON p.category_id = c.id
JOIN sales_order_items soi ON soi.product_id = p.id
JOIN sales_orders so ON so.id = soi.sales_order_id
WHERE so.status = 'COMPLETED'
  AND so.order_date >= :from_date AND so.order_date < :to_date
GROUP BY c.id, c.name
HAVING SUM(soi.quantity * soi.unit_price * (1 - soi.discount_rate)) > :minimum_sales
ORDER BY sales_total DESC;
```



## Triggers, auditing, and transaction consistency

- `set_updated_at` maintains timestamps for mutable rows.
- Purchase orders and sales orders must follow checked status transitions. A purchase receipt inserts a purchase movement; completing a sale inserts sale movements; completing an approved return inserts restock or supplier-return movements. Trigger failures roll back the status change and stock movement together.
- Inventory transaction validation checks that the movement matches a source order/return line, warehouse, status, sign, and remaining quantity. Applying a movement updates the current balance; the balance check constraint rejects stock below zero.
- Return approval locks the source order lines and checks cumulative returned quantities, preventing over-return across separate return requests.
- Row triggers write inserts, updates, and deletes for important master and workflow tables to `audit_logs`. User password hashes are removed from audit JSON. To record the authenticated actor from the API, set `indexx.user_id` transaction-locally (for example `SET LOCAL indexx.user_id = '42'`). Audit records and inventory movements are append-only.

The Spring service should perform purchase receiving, sale completion, and return approval/completion in a single database transaction. The database triggers make associated ledger and balance changes part of that same transaction; the service should not independently adjust inventory a second time.


## Query-plan example

On a populated database, inspect a filtered product lookup with:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, sku, name FROM products WHERE LOWER(name) = LOWER('bolt');
```

The planner chooses whether the supporting index helps based on table size and statistics. `ANALYZE` runs the query, so use a read-only `SELECT` when profiling.

