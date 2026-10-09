\set ON_ERROR_STOP on
\set seed_prefix IXDEV
\set category_count 5
\set subcategory_count 20
\set brand_count 10
\set product_count 500
\set supplier_count 25
\set warehouse_count 5
\set customer_count 1000
\set purchase_order_count 625
\set sales_order_count 1250
\set review_count 5000
\set search_count 5000
\ir generate_dataset.sql
