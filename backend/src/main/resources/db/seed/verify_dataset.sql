\set ON_ERROR_STOP on
\set seed_prefix IXLARGE
\echo 'Row counts for the major tables'
SELECT 'categories' AS table_name, COUNT(*)::bigint AS row_count FROM categories
UNION ALL SELECT 'subcategories', COUNT(*) FROM subcategories
UNION ALL SELECT 'brands', COUNT(*) FROM brands
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'suppliers', COUNT(*) FROM suppliers
UNION ALL SELECT 'supplier_products', COUNT(*) FROM supplier_products
UNION ALL SELECT 'customers', COUNT(*) FROM customers
UNION ALL SELECT 'warehouses', COUNT(*) FROM warehouses
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'purchase_orders', COUNT(*) FROM purchase_orders
UNION ALL SELECT 'purchase_order_items', COUNT(*) FROM purchase_order_items
UNION ALL SELECT 'sales_orders', COUNT(*) FROM sales_orders
UNION ALL SELECT 'sales_order_items', COUNT(*) FROM sales_order_items
UNION ALL SELECT 'payments', COUNT(*) FROM payments
UNION ALL SELECT 'returns', COUNT(*) FROM returns
UNION ALL SELECT 'return_items', COUNT(*) FROM return_items
UNION ALL SELECT 'inventory', COUNT(*) FROM inventory
UNION ALL SELECT 'inventory_transactions', COUNT(*) FROM inventory_transactions
UNION ALL SELECT 'product_reviews', COUNT(*) FROM product_reviews
UNION ALL SELECT 'search_history', COUNT(*) FROM search_history
UNION ALL SELECT 'audit_logs', COUNT(*) FROM audit_logs
ORDER BY table_name;

\echo 'Counts belonging to the selected seed profile (change seed_prefix to IXDEV for normal data)'
SELECT 'categories' AS table_name, COUNT(*)::bigint AS row_count
  FROM categories WHERE name LIKE 'IndexX ' || :'seed_prefix' || ' Category %'
UNION ALL SELECT 'subcategories', COUNT(*) FROM subcategories WHERE name LIKE 'IndexX ' || :'seed_prefix' || ' Segment %'
UNION ALL SELECT 'brands', COUNT(*) FROM brands WHERE name LIKE 'IndexX ' || :'seed_prefix' || ' Brand %'
UNION ALL SELECT 'products', COUNT(*) FROM products WHERE sku LIKE :'seed_prefix' || '-P-%'
UNION ALL SELECT 'suppliers', COUNT(*) FROM suppliers WHERE email LIKE LOWER(:'seed_prefix') || '.supplier.%'
UNION ALL SELECT 'supplier_products', COUNT(*) FROM supplier_products sp JOIN products p ON p.id = sp.product_id WHERE p.sku LIKE :'seed_prefix' || '-P-%'
UNION ALL SELECT 'customers', COUNT(*) FROM customers WHERE email LIKE LOWER(:'seed_prefix') || '.customer.%'
UNION ALL SELECT 'warehouses', COUNT(*) FROM warehouses WHERE code LIKE :'seed_prefix' || '-WH-%'
UNION ALL SELECT 'users', COUNT(*) FROM users WHERE email LIKE LOWER(:'seed_prefix') || '.%'
UNION ALL SELECT 'purchase_orders', COUNT(*) FROM purchase_orders WHERE order_number LIKE :'seed_prefix' || '-PO-%'
UNION ALL SELECT 'purchase_order_items', COUNT(*) FROM purchase_order_items poi JOIN purchase_orders po ON po.id = poi.purchase_order_id WHERE po.order_number LIKE :'seed_prefix' || '-PO-%'
UNION ALL SELECT 'sales_orders', COUNT(*) FROM sales_orders WHERE order_number LIKE :'seed_prefix' || '-SO-%'
UNION ALL SELECT 'sales_order_items', COUNT(*) FROM sales_order_items soi JOIN sales_orders so ON so.id = soi.sales_order_id WHERE so.order_number LIKE :'seed_prefix' || '-SO-%'
UNION ALL SELECT 'payments', COUNT(*) FROM payments pay JOIN sales_orders so ON so.id = pay.sales_order_id WHERE so.order_number LIKE :'seed_prefix' || '-SO-%'
UNION ALL SELECT 'returns', COUNT(*) FROM returns WHERE return_number LIKE :'seed_prefix' || '-%'
UNION ALL SELECT 'return_items', COUNT(*) FROM return_items ri JOIN returns r ON r.id = ri.return_id WHERE r.return_number LIKE :'seed_prefix' || '-%'
UNION ALL SELECT 'inventory', COUNT(*) FROM inventory i JOIN products p ON p.id = i.product_id WHERE p.sku LIKE :'seed_prefix' || '-P-%'
UNION ALL SELECT 'inventory_transactions', COUNT(*) FROM inventory_transactions it JOIN products p ON p.id = it.product_id WHERE p.sku LIKE :'seed_prefix' || '-P-%'
UNION ALL SELECT 'product_reviews', COUNT(*) FROM product_reviews r JOIN products p ON p.id = r.product_id WHERE p.sku LIKE :'seed_prefix' || '-P-%'
UNION ALL SELECT 'search_history', COUNT(*) FROM search_history sh JOIN users u ON u.id = sh.user_id WHERE u.email LIKE LOWER(:'seed_prefix') || '.%'
UNION ALL SELECT 'audit_logs_for_seed_reviews', COUNT(*) FROM audit_logs al
 WHERE al.entity_name = 'product_reviews'
   AND al.action = 'INSERT'
   AND al.new_values ->> 'product_id' IN (SELECT id::text FROM products WHERE sku LIKE :'seed_prefix' || '-P-%')
ORDER BY table_name;

\echo 'Products per category'
SELECT c.name, COUNT(p.id) AS product_count
  FROM categories c LEFT JOIN products p ON p.category_id = c.id
 GROUP BY c.id, c.name ORDER BY product_count DESC, c.name;

\echo 'Products per brand'
SELECT b.name, COUNT(p.id) AS product_count
  FROM brands b LEFT JOIN products p ON p.brand_id = b.id
 GROUP BY b.id, b.name ORDER BY product_count DESC, b.name LIMIT 30;

\echo 'Supplier catalog counts'
SELECT s.name, COUNT(sp.product_id) AS product_count
  FROM suppliers s LEFT JOIN supplier_products sp ON sp.supplier_id = s.id
 GROUP BY s.id, s.name ORDER BY product_count DESC, s.name LIMIT 30;

\echo 'Inventory per warehouse'
SELECT w.code, w.name, COUNT(i.product_id) AS stocked_skus,
       COALESCE(SUM(i.quantity_on_hand), 0) AS quantity_on_hand
  FROM warehouses w LEFT JOIN inventory i ON i.warehouse_id = w.id
 GROUP BY w.id, w.code, w.name ORDER BY w.code;

\echo 'Completed sales by month (last 24 months represented in demo data)'
SELECT DATE_TRUNC('month', order_date)::date AS sales_month,
       COUNT(*) AS order_count,
       ROUND(SUM(calculate_sales_order_total(id)), 2) AS revenue
  FROM sales_orders WHERE status = 'COMPLETED'
 GROUP BY DATE_TRUNC('month', order_date)::date ORDER BY sales_month;

\echo 'Top selling products'
SELECT p.sku, p.name, SUM(soi.quantity) AS units_sold,
       ROUND(SUM(soi.quantity * soi.unit_price * (1 - soi.discount_rate)), 2) AS revenue
  FROM products p JOIN sales_order_items soi ON soi.product_id = p.id
  JOIN sales_orders so ON so.id = soi.sales_order_id AND so.status = 'COMPLETED'
 GROUP BY p.id, p.sku, p.name ORDER BY units_sold DESC, p.sku LIMIT 20;

\echo 'Top customers'
SELECT customer_id, customer_name, completed_order_count, lifetime_purchase_value, last_order_at
  FROM customer_purchase_summary ORDER BY lifetime_purchase_value DESC LIMIT 20;

\echo 'Low stock products'
SELECT product_id, sku, product_name, warehouse_code, quantity_on_hand, reorder_level
  FROM low_stock_products ORDER BY quantity_on_hand, sku LIMIT 50;

\echo 'Ledger-to-balance consistency (must return zero rows)'
WITH ledger AS (
    SELECT product_id, warehouse_id, SUM(quantity_delta)::bigint AS quantity
      FROM inventory_transactions GROUP BY product_id, warehouse_id
)
SELECT i.product_id, i.warehouse_id, i.quantity_on_hand, COALESCE(l.quantity, 0) AS ledger_quantity
  FROM inventory i LEFT JOIN ledger l USING (product_id, warehouse_id)
 WHERE i.quantity_on_hand <> COALESCE(l.quantity, 0)
UNION ALL
SELECT l.product_id, l.warehouse_id, NULL, l.quantity
  FROM ledger l LEFT JOIN inventory i USING (product_id, warehouse_id)
 WHERE i.product_id IS NULL AND l.quantity <> 0;

\echo 'Database constraints and indexes are present'
SELECT conrelid::regclass AS table_name, COUNT(*) FILTER (WHERE contype = 'f') AS foreign_keys,
       COUNT(*) FILTER (WHERE contype = 'c') AS checks,
       COUNT(*) FILTER (WHERE contype = 'u') AS unique_constraints
  FROM pg_constraint
 WHERE connamespace = 'public'::regnamespace
 GROUP BY conrelid ORDER BY table_name;
SELECT tablename, indexname FROM pg_indexes
 WHERE schemaname = 'public' ORDER BY tablename, indexname;

\echo 'Read-only indexed lookup profile'
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, sku, name FROM products
 WHERE sku = (SELECT sku FROM products ORDER BY id LIMIT 1);

\echo 'Date-filtered sales aggregation profile'
EXPLAIN (ANALYZE, BUFFERS)
SELECT DATE_TRUNC('month', so.order_date)::date AS sales_month,
       COUNT(*) AS completed_orders,
       SUM(soi.quantity * soi.unit_price * (1 - soi.discount_rate)) AS sales_total
  FROM sales_orders so JOIN sales_order_items soi ON soi.sales_order_id = so.id
 WHERE so.status = 'COMPLETED'
   AND so.order_date >= TIMESTAMPTZ '2025-01-01 00:00:00+00'
   AND so.order_date <  TIMESTAMPTZ '2026-01-01 00:00:00+00'
 GROUP BY DATE_TRUNC('month', so.order_date)::date;
