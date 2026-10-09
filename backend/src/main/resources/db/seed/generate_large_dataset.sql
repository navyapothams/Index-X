\set ON_ERROR_STOP on
\set seed_prefix IXLARGE
\set category_count 50
\set subcategory_count 200
\set brand_count 300
\set product_count 10000
\set supplier_count 500
\set warehouse_count 20
\set customer_count 20000
\set purchase_order_count 50000
\set sales_order_count 100000
\set review_count 100000
\set search_count 100000
\ir generate_dataset.sql
