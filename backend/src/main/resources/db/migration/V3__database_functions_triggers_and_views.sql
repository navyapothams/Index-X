-- Reusable database functions.
CREATE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE FUNCTION current_stock(p_product_id BIGINT, p_warehouse_id BIGINT)
RETURNS INTEGER
LANGUAGE sql
STABLE
AS $$
    SELECT COALESCE((SELECT i.quantity_on_hand
                     FROM inventory i
                     WHERE i.product_id = p_product_id AND i.warehouse_id = p_warehouse_id), 0)
$$;

CREATE FUNCTION calculate_sales_order_total(p_sales_order_id BIGINT)
RETURNS NUMERIC(14,2)
LANGUAGE sql
STABLE
AS $$
    SELECT COALESCE(SUM(soi.quantity * soi.unit_price * (1 - soi.discount_rate)), 0)::NUMERIC(14,2)
    FROM sales_order_items soi
    WHERE soi.sales_order_id = p_sales_order_id
$$;

CREATE FUNCTION write_audit_log()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_old JSONB;
    v_new JSONB;
    v_entity_id VARCHAR(160);
    v_actor_id BIGINT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        v_new := TO_JSONB(NEW);
    ELSIF TG_OP = 'UPDATE' THEN
        v_old := TO_JSONB(OLD);
        v_new := TO_JSONB(NEW);
    ELSE
        v_old := TO_JSONB(OLD);
    END IF;

    -- Password hashes are authentication material and are never copied to the audit log.
    IF TG_TABLE_NAME = 'users' THEN
        v_old := v_old - 'password_hash';
        v_new := v_new - 'password_hash';
    END IF;

    IF TG_TABLE_NAME = 'inventory' THEN
        v_entity_id := FORMAT('warehouse=%s;product=%s',
            COALESCE(v_new ->> 'warehouse_id', v_old ->> 'warehouse_id'),
            COALESCE(v_new ->> 'product_id', v_old ->> 'product_id'));
    ELSIF TG_TABLE_NAME IN ('user_roles', 'supplier_products') THEN
        v_entity_id := FORMAT('key1=%s;key2=%s',
            COALESCE(v_new ->> 'user_id', v_new ->> 'supplier_id', v_old ->> 'user_id', v_old ->> 'supplier_id'),
            COALESCE(v_new ->> 'role_id', v_new ->> 'product_id', v_old ->> 'role_id', v_old ->> 'product_id'));
    ELSE
        v_entity_id := COALESCE(v_new ->> 'id', v_old ->> 'id', 'unknown');
    END IF;

    v_actor_id := NULLIF(CURRENT_SETTING('indexx.user_id', TRUE), '')::BIGINT;
    INSERT INTO audit_logs (actor_user_id, entity_name, entity_id, action, old_values, new_values)
    VALUES (v_actor_id, TG_TABLE_NAME, v_entity_id, TG_OP, v_old, v_new);

    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION validate_inventory_transaction()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_allowed_quantity BIGINT;
    v_posted_quantity BIGINT;
    v_expected_warehouse BIGINT;
    v_matches BOOLEAN;
BEGIN
    IF NEW.transaction_type = 'PURCHASE' THEN
        SELECT po.warehouse_id, poi.quantity_received
          INTO v_expected_warehouse, v_allowed_quantity
          FROM purchase_orders po
          JOIN purchase_order_items poi ON poi.purchase_order_id = po.id
         WHERE po.id = NEW.purchase_order_id AND poi.product_id = NEW.product_id
           AND po.status IN ('ORDERED','PARTIALLY_RECEIVED','RECEIVED');
        IF NOT FOUND OR v_expected_warehouse <> NEW.warehouse_id THEN
            RAISE EXCEPTION 'Purchase stock movements must match a received purchase order line and warehouse.';
        END IF;
        SELECT COALESCE(SUM(quantity_delta), 0) INTO v_posted_quantity
          FROM inventory_transactions
         WHERE purchase_order_id = NEW.purchase_order_id AND product_id = NEW.product_id;
        IF v_posted_quantity + NEW.quantity_delta > v_allowed_quantity THEN
            RAISE EXCEPTION 'Purchase stock movement exceeds the quantity received on its order line.';
        END IF;
    ELSIF NEW.transaction_type = 'SALE' THEN
        SELECT so.warehouse_id, soi.quantity
          INTO v_expected_warehouse, v_allowed_quantity
          FROM sales_orders so
          JOIN sales_order_items soi ON soi.sales_order_id = so.id
         WHERE so.id = NEW.sales_order_id AND soi.product_id = NEW.product_id
           AND so.status = 'COMPLETED';
        IF NOT FOUND OR v_expected_warehouse <> NEW.warehouse_id THEN
            RAISE EXCEPTION 'Sale stock movements must match a completed sales order line and warehouse.';
        END IF;
        SELECT COALESCE(SUM(-quantity_delta), 0) INTO v_posted_quantity
          FROM inventory_transactions
         WHERE sales_order_id = NEW.sales_order_id AND product_id = NEW.product_id;
        IF v_posted_quantity + (-NEW.quantity_delta) > v_allowed_quantity THEN
            RAISE EXCEPTION 'Sale stock movement exceeds the quantity on its order line.';
        END IF;
    ELSIF NEW.transaction_type = 'RETURN' THEN
        IF NEW.quantity_delta > 0 THEN
            SELECT EXISTS (
                SELECT 1 FROM returns r
                JOIN sales_orders so ON so.id = r.sales_order_id
                JOIN return_items ri ON ri.return_id = r.id AND ri.disposition = 'RESTOCK'
                JOIN sales_order_items soi ON soi.id = ri.sales_order_item_id
                WHERE r.id = NEW.return_id AND r.return_type = 'CUSTOMER' AND r.status = 'COMPLETED'
                  AND so.warehouse_id = NEW.warehouse_id AND soi.product_id = NEW.product_id
            ) INTO v_matches;
        ELSE
            SELECT EXISTS (
                SELECT 1 FROM returns r
                JOIN purchase_orders po ON po.id = r.purchase_order_id
                JOIN return_items ri ON ri.return_id = r.id AND ri.disposition = 'RETURN_TO_SUPPLIER'
                JOIN purchase_order_items poi ON poi.id = ri.purchase_order_item_id
                WHERE r.id = NEW.return_id AND r.return_type = 'SUPPLIER' AND r.status = 'COMPLETED'
                  AND po.warehouse_id = NEW.warehouse_id AND poi.product_id = NEW.product_id
            ) INTO v_matches;
        END IF;
        IF NOT v_matches THEN
            RAISE EXCEPTION 'Return stock movements must match a completed return line and warehouse.';
        END IF;
        SELECT COALESCE(SUM(ABS(quantity_delta)), 0) INTO v_posted_quantity
          FROM inventory_transactions
         WHERE return_id = NEW.return_id AND product_id = NEW.product_id;
        IF NEW.quantity_delta > 0 THEN
            SELECT COALESCE(SUM(ri.quantity), 0) INTO v_allowed_quantity
              FROM returns r JOIN return_items ri ON ri.return_id = r.id
              JOIN sales_order_items soi ON soi.id = ri.sales_order_item_id
             WHERE r.id = NEW.return_id AND ri.disposition = 'RESTOCK' AND soi.product_id = NEW.product_id;
        ELSE
            SELECT COALESCE(SUM(ri.quantity), 0) INTO v_allowed_quantity
              FROM returns r JOIN return_items ri ON ri.return_id = r.id
              JOIN purchase_order_items poi ON poi.id = ri.purchase_order_item_id
             WHERE r.id = NEW.return_id AND ri.disposition = 'RETURN_TO_SUPPLIER' AND poi.product_id = NEW.product_id;
        END IF;
        IF v_posted_quantity + ABS(NEW.quantity_delta) > v_allowed_quantity THEN
            RAISE EXCEPTION 'Return stock movement exceeds the quantity authorized on its return line.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE FUNCTION apply_inventory_transaction()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_previous_setting TEXT;
BEGIN
    v_previous_setting := CURRENT_SETTING('indexx.inventory_movement', TRUE);
    PERFORM SET_CONFIG('indexx.inventory_movement', 'on', TRUE);

    UPDATE inventory
       SET quantity_on_hand = quantity_on_hand + NEW.quantity_delta,
           updated_at = CURRENT_TIMESTAMP
     WHERE warehouse_id = NEW.warehouse_id AND product_id = NEW.product_id;

    IF NOT FOUND THEN
        BEGIN
            INSERT INTO inventory (warehouse_id, product_id, quantity_on_hand, reorder_level)
            VALUES (NEW.warehouse_id, NEW.product_id, NEW.quantity_delta, NULL);
        EXCEPTION WHEN unique_violation THEN
            PERFORM SET_CONFIG('indexx.inventory_movement', 'on', TRUE);
            UPDATE inventory
               SET quantity_on_hand = quantity_on_hand + NEW.quantity_delta,
                   updated_at = CURRENT_TIMESTAMP
             WHERE warehouse_id = NEW.warehouse_id AND product_id = NEW.product_id;
        END;
    END IF;

    PERFORM SET_CONFIG('indexx.inventory_movement', COALESCE(v_previous_setting, 'off'), TRUE);
    RETURN NEW;
END;
$$;

CREATE FUNCTION protect_inventory_balance()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Inventory balance rows are persistent; post a movement instead of deleting a balance.';
    END IF;
    IF CURRENT_SETTING('indexx.inventory_movement', TRUE) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'Inventory quantity can be changed only by an inventory transaction.';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION reject_inventory_transaction_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Inventory transactions are append-only; post a compensating adjustment instead.';
END;
$$;

CREATE FUNCTION validate_purchase_order_status()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'Purchase orders must be created as DRAFT.';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.status = OLD.status THEN RETURN NEW; END IF;

    IF OLD.status = 'DRAFT' AND NEW.status = 'ORDERED' THEN
        IF NOT EXISTS (SELECT 1 FROM purchase_order_items WHERE purchase_order_id = NEW.id) THEN
            RAISE EXCEPTION 'A purchase order needs at least one item before ordering.';
        END IF;
    ELSIF OLD.status = 'DRAFT' AND NEW.status = 'CANCELLED' THEN
        NULL;
    ELSIF OLD.status = 'ORDERED' AND NEW.status = 'PARTIALLY_RECEIVED' THEN
        IF NOT EXISTS (SELECT 1 FROM purchase_order_items WHERE purchase_order_id = NEW.id AND quantity_received > 0)
           OR NOT EXISTS (SELECT 1 FROM purchase_order_items WHERE purchase_order_id = NEW.id AND quantity_received < quantity_ordered) THEN
            RAISE EXCEPTION 'PARTIALLY_RECEIVED requires both received and outstanding quantities.';
        END IF;
    ELSIF OLD.status = 'ORDERED' AND NEW.status = 'RECEIVED' THEN
        IF EXISTS (SELECT 1 FROM purchase_order_items WHERE purchase_order_id = NEW.id AND quantity_received <> quantity_ordered) THEN
            RAISE EXCEPTION 'A purchase order can be RECEIVED only after every item is received.';
        END IF;
    ELSIF OLD.status = 'ORDERED' AND NEW.status = 'CANCELLED' THEN
        NULL;
    ELSIF OLD.status = 'PARTIALLY_RECEIVED' AND NEW.status = 'RECEIVED' THEN
        IF EXISTS (SELECT 1 FROM purchase_order_items WHERE purchase_order_id = NEW.id AND quantity_received <> quantity_ordered) THEN
            RAISE EXCEPTION 'A purchase order can be RECEIVED only after every item is received.';
        END IF;
    ELSIF OLD.status = 'PARTIALLY_RECEIVED' AND NEW.status = 'CANCELLED' THEN
        NULL;
    ELSE
        RAISE EXCEPTION 'Invalid purchase order status change: % -> %.', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_purchase_order_items()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_order_id BIGINT;
    v_status VARCHAR(24);
BEGIN
    v_order_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.purchase_order_id ELSE NEW.purchase_order_id END;
    SELECT status INTO v_status FROM purchase_orders WHERE id = v_order_id FOR UPDATE;

    IF TG_OP = 'INSERT' THEN
        IF v_status <> 'DRAFT' OR NEW.quantity_received <> 0 THEN
            RAISE EXCEPTION 'Purchase items can be added only to a DRAFT order; receive after ordering.';
        END IF;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        IF v_status <> 'DRAFT' THEN
            RAISE EXCEPTION 'Purchase items can be removed only from a DRAFT order.';
        END IF;
        RETURN OLD;
    END IF;

    IF v_status = 'DRAFT' THEN
        IF NEW.quantity_received <> OLD.quantity_received OR NEW.quantity_received <> 0 THEN
            RAISE EXCEPTION 'A purchase order must be ordered before receiving stock.';
        END IF;
    ELSIF v_status IN ('ORDERED','PARTIALLY_RECEIVED') THEN
        IF NEW.purchase_order_id IS DISTINCT FROM OLD.purchase_order_id
           OR NEW.product_id IS DISTINCT FROM OLD.product_id
           OR NEW.quantity_ordered IS DISTINCT FROM OLD.quantity_ordered
           OR NEW.unit_cost IS DISTINCT FROM OLD.unit_cost
           OR NEW.quantity_received <= OLD.quantity_received THEN
            RAISE EXCEPTION 'After ordering, purchase items may only record a positive receipt increment.';
        END IF;
    ELSE
        RAISE EXCEPTION 'Items on a % purchase order cannot be changed.', v_status;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION receive_purchase_order_quantity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_warehouse_id BIGINT;
    v_actor_id BIGINT;
    v_status VARCHAR(24);
    v_all_received BOOLEAN;
    v_any_received BOOLEAN;
BEGIN
    IF NEW.quantity_received <= OLD.quantity_received THEN RETURN NEW; END IF;

    SELECT warehouse_id, created_by INTO v_warehouse_id, v_actor_id
    FROM purchase_orders WHERE id = NEW.purchase_order_id;
    INSERT INTO inventory_transactions
        (product_id, warehouse_id, transaction_type, quantity_delta, purchase_order_id, created_by, note)
    VALUES
        (NEW.product_id, v_warehouse_id, 'PURCHASE', NEW.quantity_received - OLD.quantity_received,
         NEW.purchase_order_id, v_actor_id, 'Purchase order receipt');

    SELECT BOOL_AND(quantity_received = quantity_ordered), BOOL_OR(quantity_received > 0)
    INTO v_all_received, v_any_received
    FROM purchase_order_items WHERE purchase_order_id = NEW.purchase_order_id;

    v_status := CASE WHEN v_all_received THEN 'RECEIVED'
                     WHEN v_any_received THEN 'PARTIALLY_RECEIVED'
                     ELSE 'ORDERED' END;
    UPDATE purchase_orders
       SET status = v_status,
           received_at = CASE WHEN v_status = 'RECEIVED' THEN COALESCE(received_at, CURRENT_TIMESTAMP) ELSE received_at END
     WHERE id = NEW.purchase_order_id;
    RETURN NEW;
END;
$$;

CREATE FUNCTION validate_sales_order_status()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'DRAFT' THEN RAISE EXCEPTION 'Sales orders must be created as DRAFT.'; END IF;
        RETURN NEW;
    END IF;
    IF NEW.status = OLD.status THEN RETURN NEW; END IF;
    IF OLD.status <> 'DRAFT' OR NEW.status NOT IN ('COMPLETED','CANCELLED') THEN
        RAISE EXCEPTION 'Invalid sales order status change: % -> %.', OLD.status, NEW.status;
    END IF;
    IF NEW.status = 'COMPLETED' AND NOT EXISTS
       (SELECT 1 FROM sales_order_items WHERE sales_order_id = NEW.id) THEN
        RAISE EXCEPTION 'A sales order needs at least one item before completion.';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_sales_order_items()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_order_id BIGINT;
    v_status VARCHAR(20);
BEGIN
    v_order_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.sales_order_id ELSE NEW.sales_order_id END;
    SELECT status INTO v_status FROM sales_orders WHERE id = v_order_id FOR UPDATE;
    IF v_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Sales items can be changed only while the order is DRAFT.';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION post_completed_sale()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status = 'COMPLETED' AND OLD.status IS DISTINCT FROM NEW.status THEN
        INSERT INTO inventory_transactions
            (product_id, warehouse_id, transaction_type, quantity_delta, sales_order_id, created_by, note)
        SELECT soi.product_id, so.warehouse_id, 'SALE', -soi.quantity, so.id, so.created_by, 'Completed sales order'
        FROM sales_order_items soi
        JOIN sales_orders so ON so.id = soi.sales_order_id
        WHERE so.id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION guard_return_items()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_return_id BIGINT;
    v_type VARCHAR(12);
    v_status VARCHAR(16);
    v_sales_order_id BIGINT;
    v_purchase_order_id BIGINT;
BEGIN
    v_return_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.return_id ELSE NEW.return_id END;
    SELECT return_type, status, sales_order_id, purchase_order_id
      INTO v_type, v_status, v_sales_order_id, v_purchase_order_id
      FROM returns WHERE id = v_return_id FOR UPDATE;
    IF v_status <> 'REQUESTED' THEN
        RAISE EXCEPTION 'Return items can be changed only while the return is REQUESTED.';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;

    IF v_type = 'CUSTOMER' THEN
        IF NEW.sales_order_item_id IS NULL OR NEW.purchase_order_item_id IS NOT NULL
           OR NEW.disposition NOT IN ('RESTOCK','WRITE_OFF')
           OR NOT EXISTS (SELECT 1 FROM sales_order_items
                          WHERE id = NEW.sales_order_item_id AND sales_order_id = v_sales_order_id) THEN
            RAISE EXCEPTION 'Customer return items must reference a line from the linked sales order.';
        END IF;
    ELSE
        IF NEW.purchase_order_item_id IS NULL OR NEW.sales_order_item_id IS NOT NULL
           OR NEW.disposition <> 'RETURN_TO_SUPPLIER'
           OR NOT EXISTS (SELECT 1 FROM purchase_order_items
                          WHERE id = NEW.purchase_order_item_id AND purchase_order_id = v_purchase_order_id) THEN
            RAISE EXCEPTION 'Supplier return items must reference a line from the linked purchase order.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION validate_return_status()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'REQUESTED' THEN RAISE EXCEPTION 'Returns must be created as REQUESTED.'; END IF;
        RETURN NEW;
    END IF;
    IF NEW.status = OLD.status THEN RETURN NEW; END IF;
    IF OLD.status = 'REQUESTED' AND NEW.status IN ('APPROVED','REJECTED') THEN
        IF NEW.status = 'APPROVED' THEN
            IF NOT EXISTS (SELECT 1 FROM return_items WHERE return_id = NEW.id) THEN
                RAISE EXCEPTION 'A return needs at least one item before approval.';
            END IF;
            PERFORM assert_return_capacity(NEW.id);
        END IF;
    ELSIF OLD.status = 'APPROVED' AND NEW.status = 'COMPLETED' THEN
        NEW.completed_at := COALESCE(NEW.completed_at, CURRENT_TIMESTAMP);
    ELSE
        RAISE EXCEPTION 'Invalid return status change: % -> %.', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION assert_return_capacity(p_return_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_return RECORD;
    v_item RECORD;
    v_order_quantity INTEGER;
    v_already_returned BIGINT;
BEGIN
    SELECT return_type, sales_order_id, purchase_order_id
      INTO v_return FROM returns WHERE id = p_return_id;

    FOR v_item IN
        SELECT * FROM return_items
         WHERE return_id = p_return_id
         ORDER BY COALESCE(sales_order_item_id, purchase_order_item_id)
    LOOP
        IF v_return.return_type = 'CUSTOMER' THEN
            SELECT soi.quantity INTO v_order_quantity FROM sales_order_items soi JOIN sales_orders so ON so.id = soi.sales_order_id WHERE soi.id = v_item.sales_order_item_id AND soi.sales_order_id = v_return.sales_order_id AND so.status = 'COMPLETED' FOR UPDATE OF soi;
            IF NOT FOUND THEN RAISE EXCEPTION 'Return item does not belong to the linked sales order.'; END IF;
            SELECT COALESCE(SUM(ri.quantity), 0) INTO v_already_returned
              FROM return_items ri JOIN returns r ON r.id = ri.return_id
             WHERE ri.sales_order_item_id = v_item.sales_order_item_id
               AND r.status IN ('APPROVED','COMPLETED') AND r.id <> p_return_id;
        ELSE
            SELECT poi.quantity_received INTO v_order_quantity FROM purchase_order_items poi JOIN purchase_orders po ON po.id = poi.purchase_order_id WHERE poi.id = v_item.purchase_order_item_id AND poi.purchase_order_id = v_return.purchase_order_id AND po.status IN ('PARTIALLY_RECEIVED','RECEIVED') FOR UPDATE OF poi;
            IF NOT FOUND THEN RAISE EXCEPTION 'Return item does not belong to the linked purchase order.'; END IF;
            SELECT COALESCE(SUM(ri.quantity), 0) INTO v_already_returned
              FROM return_items ri JOIN returns r ON r.id = ri.return_id
             WHERE ri.purchase_order_item_id = v_item.purchase_order_item_id
               AND r.status IN ('APPROVED','COMPLETED') AND r.id <> p_return_id;
        END IF;
        IF v_already_returned + v_item.quantity > v_order_quantity THEN
            RAISE EXCEPTION 'Return quantity exceeds the quantity on the source order line.';
        END IF;
    END LOOP;
END;
$$;

CREATE FUNCTION post_completed_return()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status = 'COMPLETED' AND OLD.status IS DISTINCT FROM NEW.status THEN
        IF NEW.return_type = 'CUSTOMER' THEN
            INSERT INTO inventory_transactions
                (product_id, warehouse_id, transaction_type, quantity_delta, return_id, created_by, note)
            SELECT soi.product_id, so.warehouse_id, 'RETURN', ri.quantity, NEW.id, NEW.requested_by, 'Customer return restocked'
              FROM return_items ri
              JOIN sales_order_items soi ON soi.id = ri.sales_order_item_id
              JOIN sales_orders so ON so.id = soi.sales_order_id
             WHERE ri.return_id = NEW.id AND ri.disposition = 'RESTOCK';
        ELSE
            INSERT INTO inventory_transactions
                (product_id, warehouse_id, transaction_type, quantity_delta, return_id, created_by, note)
            SELECT poi.product_id, po.warehouse_id, 'RETURN', -ri.quantity, NEW.id, NEW.requested_by, 'Supplier return dispatched'
              FROM return_items ri
              JOIN purchase_order_items poi ON poi.id = ri.purchase_order_item_id
              JOIN purchase_orders po ON po.id = poi.purchase_order_id
             WHERE ri.return_id = NEW.id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION prevent_audit_log_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Audit log records are immutable.';
END;
$$;

-- Enforce legal workflow transitions and transactionally post stock movements.
CREATE TRIGGER trg_purchase_orders_status
    BEFORE INSERT OR UPDATE OF status ON purchase_orders
    FOR EACH ROW EXECUTE FUNCTION validate_purchase_order_status();
CREATE TRIGGER trg_purchase_order_items_guard
    BEFORE INSERT OR UPDATE OR DELETE ON purchase_order_items
    FOR EACH ROW EXECUTE FUNCTION guard_purchase_order_items();
CREATE TRIGGER trg_purchase_order_items_receive
    AFTER UPDATE OF quantity_received ON purchase_order_items
    FOR EACH ROW EXECUTE FUNCTION receive_purchase_order_quantity();
CREATE TRIGGER trg_sales_orders_status
    BEFORE INSERT OR UPDATE OF status ON sales_orders
    FOR EACH ROW EXECUTE FUNCTION validate_sales_order_status();
CREATE TRIGGER trg_sales_order_items_guard
    BEFORE INSERT OR UPDATE OR DELETE ON sales_order_items
    FOR EACH ROW EXECUTE FUNCTION guard_sales_order_items();
CREATE TRIGGER trg_sales_orders_post_stock
    AFTER UPDATE OF status ON sales_orders
    FOR EACH ROW EXECUTE FUNCTION post_completed_sale();
CREATE TRIGGER trg_returns_status
    BEFORE INSERT OR UPDATE OF status ON returns
    FOR EACH ROW EXECUTE FUNCTION validate_return_status();
CREATE TRIGGER trg_return_items_guard
    BEFORE INSERT OR UPDATE OR DELETE ON return_items
    FOR EACH ROW EXECUTE FUNCTION guard_return_items();
CREATE TRIGGER trg_returns_post_stock
    AFTER UPDATE OF status ON returns
    FOR EACH ROW EXECUTE FUNCTION post_completed_return();
CREATE TRIGGER trg_inventory_protect_insert_delete
    BEFORE INSERT OR DELETE ON inventory
    FOR EACH ROW EXECUTE FUNCTION protect_inventory_balance();
CREATE TRIGGER trg_inventory_protect_quantity_update
    BEFORE UPDATE OF quantity_on_hand ON inventory
    FOR EACH ROW EXECUTE FUNCTION protect_inventory_balance();
CREATE TRIGGER trg_inventory_transactions_validate
    BEFORE INSERT ON inventory_transactions
    FOR EACH ROW EXECUTE FUNCTION validate_inventory_transaction();
CREATE TRIGGER trg_inventory_transactions_apply
    AFTER INSERT ON inventory_transactions
    FOR EACH ROW EXECUTE FUNCTION apply_inventory_transaction();
CREATE TRIGGER trg_inventory_transactions_immutable
    BEFORE UPDATE OR DELETE ON inventory_transactions
    FOR EACH ROW EXECUTE FUNCTION reject_inventory_transaction_change();
CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_mutation();

DO $$
DECLARE
    v_table TEXT;
BEGIN
    FOREACH v_table IN ARRAY ARRAY[
        'users','roles','categories','subcategories','brands','suppliers',
        'supplier_products','customers','warehouses','products','inventory',
        'purchase_orders','purchase_order_items','sales_orders','sales_order_items',
        'payments','returns','return_items','product_reviews'
    ] LOOP
        EXECUTE FORMAT('CREATE TRIGGER %I BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
                       'trg_' || v_table || '_updated_at', v_table);
    END LOOP;

    FOREACH v_table IN ARRAY ARRAY[
        'users','roles','categories','subcategories','brands','suppliers',
        'supplier_products','customers','warehouses','products','inventory',
        'purchase_orders','purchase_order_items','sales_orders','sales_order_items',
        'payments','returns','return_items','inventory_transactions','product_reviews'
    ] LOOP
        EXECUTE FORMAT('CREATE TRIGGER %I AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION write_audit_log()',
                       'trg_' || v_table || '_audit', v_table);
    END LOOP;
END;
$$;

-- Read models keep reporting joins and aggregates in PostgreSQL.
CREATE VIEW product_inventory_summary AS
SELECT p.id AS product_id, p.sku, p.name AS product_name, p.selling_price, p.cost_price,
       COALESCE(SUM(i.quantity_on_hand), 0)::BIGINT AS total_quantity,
       COUNT(i.warehouse_id)::BIGINT AS warehouse_count,
       COALESCE(SUM(i.quantity_on_hand * p.cost_price), 0)::NUMERIC(14,2) AS inventory_cost_value
  FROM products p
  LEFT JOIN inventory i ON i.product_id = p.id
 GROUP BY p.id, p.sku, p.name, p.selling_price, p.cost_price;

CREATE VIEW low_stock_products AS
SELECT p.id AS product_id, p.sku, p.name AS product_name,
       w.id AS warehouse_id, w.code AS warehouse_code, w.name AS warehouse_name,
       i.quantity_on_hand,
       COALESCE(i.reorder_level, p.reorder_level) AS reorder_level
  FROM inventory i
  JOIN products p ON p.id = i.product_id
  JOIN warehouses w ON w.id = i.warehouse_id
 WHERE p.active AND w.active
   AND i.quantity_on_hand <= COALESCE(i.reorder_level, p.reorder_level);

CREATE VIEW monthly_sales_summary AS
SELECT DATE_TRUNC('month', so.order_date)::DATE AS sales_month,
       COUNT(*)::BIGINT AS completed_order_count,
       SUM(calculate_sales_order_total(so.id))::NUMERIC(16,2) AS sales_total
  FROM sales_orders so
 WHERE so.status = 'COMPLETED'
 GROUP BY DATE_TRUNC('month', so.order_date)::DATE;

CREATE VIEW category_sales_summary AS
SELECT c.id AS category_id, c.name AS category_name,
       COALESCE(SUM(soi.quantity), 0)::BIGINT AS units_sold,
       COALESCE(SUM(soi.quantity * soi.unit_price * (1 - soi.discount_rate)), 0)::NUMERIC(16,2) AS sales_total
  FROM categories c
  JOIN products p ON p.category_id = c.id
  JOIN sales_order_items soi ON soi.product_id = p.id
  JOIN sales_orders so ON so.id = soi.sales_order_id AND so.status = 'COMPLETED'
 GROUP BY c.id, c.name;

CREATE VIEW supplier_performance AS
WITH order_metrics AS (
    SELECT po.supplier_id,
           COUNT(*)::BIGINT AS purchase_order_count,
           COUNT(*) FILTER (WHERE po.status = 'RECEIVED')::BIGINT AS completed_order_count,
           AVG(EXTRACT(EPOCH FROM (po.received_at - po.order_date)) / 86400.0)
               FILTER (WHERE po.status = 'RECEIVED') AS average_delivery_days
      FROM purchase_orders po
     GROUP BY po.supplier_id
), receipt_metrics AS (
    SELECT po.supplier_id,
           SUM(poi.quantity_received * poi.unit_cost)::NUMERIC(16,2) AS received_purchase_value
      FROM purchase_orders po
      JOIN purchase_order_items poi ON poi.purchase_order_id = po.id
     GROUP BY po.supplier_id
), catalog_metrics AS (
    SELECT supplier_id, COUNT(*)::BIGINT AS supplied_product_count
      FROM supplier_products GROUP BY supplier_id
)
SELECT s.id AS supplier_id, s.name AS supplier_name,
       COALESCE(om.purchase_order_count, 0)::BIGINT AS purchase_order_count,
       COALESCE(om.completed_order_count, 0)::BIGINT AS completed_order_count,
       COALESCE(rm.received_purchase_value, 0)::NUMERIC(16,2) AS received_purchase_value,
       om.average_delivery_days,
       COALESCE(cm.supplied_product_count, 0)::BIGINT AS supplied_product_count
  FROM suppliers s
  LEFT JOIN order_metrics om ON om.supplier_id = s.id
  LEFT JOIN receipt_metrics rm ON rm.supplier_id = s.id
  LEFT JOIN catalog_metrics cm ON cm.supplier_id = s.id;

CREATE VIEW customer_purchase_summary AS
WITH customer_orders AS (
    SELECT so.customer_id, COUNT(*)::BIGINT AS completed_order_count,
           SUM(calculate_sales_order_total(so.id))::NUMERIC(16,2) AS lifetime_purchase_value,
           MAX(so.order_date) AS last_order_at
      FROM sales_orders so
     WHERE so.customer_id IS NOT NULL AND so.status = 'COMPLETED'
     GROUP BY so.customer_id
)
SELECT c.id AS customer_id, c.name AS customer_name,
       COALESCE(co.completed_order_count, 0)::BIGINT AS completed_order_count,
       COALESCE(co.lifetime_purchase_value, 0)::NUMERIC(16,2) AS lifetime_purchase_value,
       co.last_order_at
  FROM customers c
  LEFT JOIN customer_orders co ON co.customer_id = c.id;





