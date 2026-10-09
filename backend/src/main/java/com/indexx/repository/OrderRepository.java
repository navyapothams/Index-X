package com.indexx.repository;

import com.indexx.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class OrderRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public OrderRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

    public Long createPurchase(String number,long supplierId,long warehouseId,LocalDate expectedDate,String notes){
        KeyHolder key=new GeneratedKeyHolder();
        jdbc.getJdbcTemplate().update(connection->{PreparedStatement ps=connection.prepareStatement(
                "INSERT INTO purchase_orders(order_number,supplier_id,warehouse_id,expected_date,notes) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,number);ps.setLong(2,supplierId);ps.setLong(3,warehouseId);
            if(expectedDate==null)ps.setObject(4,null);else ps.setObject(4,expectedDate);
            ps.setString(5,notes);return ps;},key);
        return key.getKey().longValue();
    }
    public void addPurchaseItem(long orderId,PurchaseItemRequest item){
        jdbc.update("INSERT INTO purchase_order_items(purchase_order_id,product_id,quantity_ordered,unit_cost) VALUES(:order,:product,:quantity,:cost)",
                new MapSqlParameterSource().addValue("order",orderId).addValue("product",item.productId()).addValue("quantity",item.quantity()).addValue("cost",item.unitCost()));
    }
    public String purchaseStatusForUpdate(long id){return jdbc.queryForObject("SELECT status FROM purchase_orders WHERE id=:id FOR UPDATE",Map.of("id",id),String.class);}
    public String purchaseLineStatus(long itemId,long orderId){return jdbc.query("SELECT po.status FROM purchase_order_items poi JOIN purchase_orders po ON po.id=poi.purchase_order_id WHERE poi.id=:item AND poi.purchase_order_id=:order",new MapSqlParameterSource().addValue("item",itemId).addValue("order",orderId),rs->rs.next()?rs.getString(1):null);}
    public int receivePurchaseLine(long orderId,long itemId,int quantity){return jdbc.update("UPDATE purchase_order_items SET quantity_received=quantity_received+:quantity WHERE id=:item AND purchase_order_id=:order AND quantity_received+:quantity<=quantity_ordered",new MapSqlParameterSource().addValue("item",itemId).addValue("order",orderId).addValue("quantity",quantity));}
    public int changePurchaseStatus(long id,String status){return jdbc.update("UPDATE purchase_orders SET status=:status WHERE id=:id",Map.of("status",status,"id",id));}
    public PurchaseOrderResponse purchase(long id){
        PurchaseOrderResponse header=jdbc.query("SELECT po.id,po.order_number,po.supplier_id,s.name supplier_name,po.warehouse_id,w.name warehouse_name,po.status,po.order_date,po.expected_date,po.received_at,po.notes,COALESCE(SUM(poi.quantity_ordered*poi.unit_cost),0)::numeric(14,2) total FROM purchase_orders po JOIN suppliers s ON s.id=po.supplier_id JOIN warehouses w ON w.id=po.warehouse_id LEFT JOIN purchase_order_items poi ON poi.purchase_order_id=po.id WHERE po.id=:id GROUP BY po.id,s.name,w.name",Map.of("id",id),(rs,row)->new PurchaseOrderResponse(rs.getLong("id"),rs.getString("order_number"),rs.getLong("supplier_id"),rs.getString("supplier_name"),rs.getLong("warehouse_id"),rs.getString("warehouse_name"),rs.getString("status"),rs.getTimestamp("order_date").toInstant(),rs.getObject("expected_date",LocalDate.class),rs.getTimestamp("received_at")==null?null:rs.getTimestamp("received_at").toInstant(),rs.getString("notes"),rs.getBigDecimal("total"),List.of())).stream().findFirst().orElse(null);
        if(header==null)return null;
        List<PurchaseItemResponse> items=jdbc.query("SELECT poi.id,poi.product_id,p.sku,p.name product_name,poi.quantity_ordered,poi.quantity_received,poi.unit_cost,(poi.quantity_ordered*poi.unit_cost)::numeric(14,2) line_total FROM purchase_order_items poi JOIN products p ON p.id=poi.product_id WHERE poi.purchase_order_id=:id ORDER BY poi.id",Map.of("id",id),(rs,row)->new PurchaseItemResponse(rs.getLong("id"),rs.getLong("product_id"),rs.getString("sku"),rs.getString("product_name"),rs.getInt("quantity_ordered"),rs.getInt("quantity_received"),rs.getBigDecimal("unit_cost"),rs.getBigDecimal("line_total")));
        return new PurchaseOrderResponse(header.id(),header.orderNumber(),header.supplierId(),header.supplierName(),header.warehouseId(),header.warehouseName(),header.status(),header.orderDate(),header.expectedDate(),header.receivedAt(),header.notes(),header.total(),items);
    }
    public Page<PurchaseOrderResponse> purchases(Long supplierId,String status,Instant from,Instant to,int page,int size){
        MapSqlParameterSource p=new MapSqlParameterSource();StringBuilder where=new StringBuilder(" WHERE 1=1 ");
        if(supplierId!=null){where.append(" AND po.supplier_id=:supplier");p.addValue("supplier",supplierId);}
        if(status!=null&&!status.isBlank()){where.append(" AND po.status=:status");p.addValue("status",status.toUpperCase(java.util.Locale.ROOT));}
        if(from!=null){where.append(" AND po.order_date>=:from");p.addValue("from",from);}
        if(to!=null){where.append(" AND po.order_date<:to");p.addValue("to",to);}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM purchase_orders po"+where,p,Long.class);
        int pg=Math.max(0,page),sz=Math.max(1,Math.min(size,100));p.addValue("limit",sz).addValue("offset",(long)pg*sz);
        List<Long> ids=jdbc.query("SELECT po.id FROM purchase_orders po"+where+" ORDER BY po.order_date DESC,po.id DESC LIMIT :limit OFFSET :offset",p,(rs,row)->rs.getLong(1));
        return new PageImpl<>(ids.stream().map(this::purchase).toList(),PageRequest.of(pg,sz),total);
    }

    public Long createSale(String number,Long customerId,long warehouseId,String notes){
        KeyHolder key=new GeneratedKeyHolder();
        jdbc.getJdbcTemplate().update(connection->{PreparedStatement ps=connection.prepareStatement("INSERT INTO sales_orders(order_number,customer_id,warehouse_id,notes) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,number);
            ps.setObject(2,customerId);ps.setLong(3,warehouseId);ps.setString(4,notes);return ps;},key);
        return key.getKey().longValue();
    }
    public void addSaleItem(long orderId,long productId,int quantity,BigDecimal price,BigDecimal discount){
        jdbc.update("INSERT INTO sales_order_items(sales_order_id,product_id,quantity,unit_price,discount_rate) VALUES(:order,:product,:quantity,:price,:discount)",new MapSqlParameterSource().addValue("order",orderId).addValue("product",productId).addValue("quantity",quantity).addValue("price",price).addValue("discount",discount));
    }
    public Map<String,Object> saleForUpdate(long id){return jdbc.queryForMap("SELECT id,status,warehouse_id FROM sales_orders WHERE id=:id FOR UPDATE",Map.of("id",id));}
    public List<Map<String,Object>> saleLines(long id){return jdbc.queryForList("SELECT soi.id,soi.product_id,soi.quantity,p.sku,p.name,p.selling_price FROM sales_order_items soi JOIN products p ON p.id=soi.product_id WHERE soi.sales_order_id=:id ORDER BY soi.product_id",Map.of("id",id));}
    public Map<Long,Integer> lockedStock(long warehouseId,List<Long> productIds){
        return jdbc.query("SELECT product_id,quantity_on_hand FROM inventory WHERE warehouse_id=:warehouse AND product_id IN (:products) ORDER BY product_id FOR UPDATE",new MapSqlParameterSource().addValue("warehouse",warehouseId).addValue("products",productIds),rs->{Map<Long,Integer> result=new java.util.HashMap<>();while(rs.next())result.put(rs.getLong(1),rs.getInt(2));return result;});
    }
    public int changeSaleStatus(long id,String status){return jdbc.update("UPDATE sales_orders SET status=:status WHERE id=:id",Map.of("status",status,"id",id));}
    public void addCompletedPayment(long orderId,String method,String reference){
        jdbc.update("INSERT INTO payments(sales_order_id,amount,payment_method,status,payment_reference,paid_at) VALUES(:order,calculate_sales_order_total(:order),:method,'COMPLETED',:reference,CURRENT_TIMESTAMP)",new MapSqlParameterSource().addValue("order",orderId).addValue("method",method).addValue("reference",reference));
    }
    public SalesOrderResponse sale(long id){
        SalesOrderResponse header=jdbc.query("SELECT so.id,so.order_number,so.customer_id,c.name customer_name,so.warehouse_id,w.name warehouse_name,so.status,so.order_date,so.notes,calculate_sales_order_total(so.id) total FROM sales_orders so LEFT JOIN customers c ON c.id=so.customer_id JOIN warehouses w ON w.id=so.warehouse_id WHERE so.id=:id",Map.of("id",id),(rs,row)->new SalesOrderResponse(rs.getLong("id"),rs.getString("order_number"),nullableLong(rs,"customer_id"),rs.getString("customer_name"),rs.getLong("warehouse_id"),rs.getString("warehouse_name"),rs.getString("status"),rs.getTimestamp("order_date").toInstant(),rs.getString("notes"),rs.getBigDecimal("total"),List.of(),List.of())).stream().findFirst().orElse(null);
        if(header==null)return null;
        List<SalesItemResponse> lines=jdbc.query("SELECT soi.id,soi.product_id,p.sku,p.name,soi.quantity,soi.unit_price,soi.discount_rate,(soi.quantity*soi.unit_price*(1-soi.discount_rate))::numeric(14,2) line_total FROM sales_order_items soi JOIN products p ON p.id=soi.product_id WHERE soi.sales_order_id=:id ORDER BY soi.id",Map.of("id",id),(rs,row)->new SalesItemResponse(rs.getLong("id"),rs.getLong("product_id"),rs.getString("sku"),rs.getString("name"),rs.getInt("quantity"),rs.getBigDecimal("unit_price"),rs.getBigDecimal("discount_rate"),rs.getBigDecimal("line_total")));
        List<PaymentResponse> payments=jdbc.query("SELECT id,amount,payment_method,status,payment_reference,paid_at FROM payments WHERE sales_order_id=:id ORDER BY created_at,id",Map.of("id",id),(rs,row)->new PaymentResponse(rs.getLong("id"),rs.getBigDecimal("amount"),rs.getString("payment_method"),rs.getString("status"),rs.getString("payment_reference"),rs.getTimestamp("paid_at")==null?null:rs.getTimestamp("paid_at").toInstant()));
        return new SalesOrderResponse(header.id(),header.orderNumber(),header.customerId(),header.customerName(),header.warehouseId(),header.warehouseName(),header.status(),header.orderDate(),header.notes(),header.total(),lines,payments);
    }
    public Page<SalesOrderResponse> sales(Long customerId,String status,Instant from,Instant to,int page,int size){
        MapSqlParameterSource p=new MapSqlParameterSource();StringBuilder where=new StringBuilder(" WHERE 1=1 ");
        if(customerId!=null){where.append(" AND so.customer_id=:customer");p.addValue("customer",customerId);}
        if(status!=null&&!status.isBlank()){where.append(" AND so.status=:status");p.addValue("status",status.toUpperCase(java.util.Locale.ROOT));}
        if(from!=null){where.append(" AND so.order_date>=:from");p.addValue("from",from);}
        if(to!=null){where.append(" AND so.order_date<:to");p.addValue("to",to);}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM sales_orders so"+where,p,Long.class);
        int pg=Math.max(0,page),sz=Math.max(1,Math.min(size,100));p.addValue("limit",sz).addValue("offset",(long)pg*sz);
        List<Long> ids=jdbc.query("SELECT so.id FROM sales_orders so"+where+" ORDER BY so.order_date DESC,so.id DESC LIMIT :limit OFFSET :offset",p,(rs,row)->rs.getLong(1));
        return new PageImpl<>(ids.stream().map(this::sale).toList(),PageRequest.of(pg,sz),total);
    }
    public boolean hasSupplierProduct(long supplierId,long productId){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM supplier_products WHERE supplier_id=:supplier AND product_id=:product)",new MapSqlParameterSource().addValue("supplier",supplierId).addValue("product",productId),Boolean.class));}
    private Long nullableLong(java.sql.ResultSet rs,String column)throws java.sql.SQLException{long x=rs.getLong(column);return rs.wasNull()?null:x;}
}
