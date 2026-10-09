package com.indexx.repository;

import com.indexx.dto.InventoryItemResponse;
import com.indexx.dto.InventoryTransactionResponse;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class InventoryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public InventoryRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

    private final RowMapper<InventoryItemResponse> stockMapper=(rs,row)->new InventoryItemResponse(
        rs.getLong("product_id"),rs.getString("sku"),rs.getString("product_name"),
        rs.getLong("warehouse_id"),rs.getString("warehouse_code"),rs.getString("warehouse_name"),
        rs.getInt("quantity_on_hand"),rs.getInt("reorder_level"),rs.getBoolean("low_stock"));
    private final RowMapper<InventoryTransactionResponse> transactionMapper=(rs,row)->new InventoryTransactionResponse(
        rs.getLong("id"),rs.getLong("product_id"),rs.getString("sku"),rs.getString("product_name"),
        rs.getLong("warehouse_id"),rs.getString("warehouse_code"),rs.getString("transaction_type"),
        rs.getInt("quantity_delta"),nullableLong(rs,"purchase_order_id"),nullableLong(rs,"sales_order_id"),
        nullableLong(rs,"return_id"),nullableLong(rs,"created_by"),rs.getString("note"),
        rs.getTimestamp("created_at").toInstant());

    public Page<InventoryItemResponse> search(Long productId,Long warehouseId,Boolean lowStock,int page,int size,String sort,String direction){
        MapSqlParameterSource params=new MapSqlParameterSource();
        StringBuilder where=new StringBuilder(" WHERE 1=1 ");
        if(productId!=null){where.append(" AND i.product_id=:productId");params.addValue("productId",productId);}
        if(warehouseId!=null){where.append(" AND i.warehouse_id=:warehouseId");params.addValue("warehouseId",warehouseId);}
        if(Boolean.TRUE.equals(lowStock)) where.append(" AND i.quantity_on_hand <= COALESCE(i.reorder_level,p.reorder_level)");
        String sql=" FROM inventory i JOIN products p ON p.id=i.product_id JOIN warehouses w ON w.id=i.warehouse_id"+where;
        long total=jdbc.queryForObject("SELECT COUNT(*)"+sql,params,Long.class);
        String column=switch(sort==null?"sku":sort){case "sku"->"p.sku";case "quantityOnHand"->"i.quantity_on_hand";case "warehouseCode"->"w.code";default->throw new IllegalArgumentException("Unsupported inventory sort field.");};
        if(direction!=null && !direction.equalsIgnoreCase("ASC") && !direction.equalsIgnoreCase("DESC"))
            throw new IllegalArgumentException("Sort direction must be ASC or DESC.");
        String dir="DESC".equalsIgnoreCase(direction)?"DESC":"ASC";
        int safePage=Math.max(0,page),safeSize=Math.max(1,Math.min(size,100));
        params.addValue("limit",safeSize).addValue("offset",(long)safePage*safeSize);
        String query="SELECT p.id product_id,p.sku,p.name product_name,w.id warehouse_id,w.code warehouse_code,w.name warehouse_name,"
                +"i.quantity_on_hand,COALESCE(i.reorder_level,p.reorder_level) reorder_level,"
                +"(i.quantity_on_hand <= COALESCE(i.reorder_level,p.reorder_level)) low_stock"+sql+
                " ORDER BY "+column+" "+dir+",w.id,p.id LIMIT :limit OFFSET :offset";
        List<InventoryItemResponse> rows=jdbc.query(query,params,stockMapper);
        Pageable pageable=PageRequest.of(safePage,safeSize);
        return new PageImpl<>(rows,pageable,total);
    }

    public List<InventoryItemResponse> byProduct(Long productId){return jdbc.query("SELECT p.id product_id,p.sku,p.name product_name,w.id warehouse_id,w.code warehouse_code,w.name warehouse_name,i.quantity_on_hand,COALESCE(i.reorder_level,p.reorder_level) reorder_level,(i.quantity_on_hand<=COALESCE(i.reorder_level,p.reorder_level)) low_stock FROM inventory i JOIN products p ON p.id=i.product_id JOIN warehouses w ON w.id=i.warehouse_id WHERE p.id=:id ORDER BY w.code",new MapSqlParameterSource("id",productId),stockMapper);}
    public List<InventoryItemResponse> byWarehouse(Long warehouseId){return jdbc.query("SELECT p.id product_id,p.sku,p.name product_name,w.id warehouse_id,w.code warehouse_code,w.name warehouse_name,i.quantity_on_hand,COALESCE(i.reorder_level,p.reorder_level) reorder_level,(i.quantity_on_hand<=COALESCE(i.reorder_level,p.reorder_level)) low_stock FROM inventory i JOIN products p ON p.id=i.product_id JOIN warehouses w ON w.id=i.warehouse_id WHERE w.id=:id ORDER BY p.name,p.id",new MapSqlParameterSource("id",warehouseId),stockMapper);}
    public Page<InventoryTransactionResponse> history(Long productId,Long warehouseId,String type,Instant from,Instant to,int page,int size){
        MapSqlParameterSource params=new MapSqlParameterSource(); StringBuilder where=new StringBuilder(" WHERE 1=1 ");
        if(productId!=null){where.append(" AND it.product_id=:productId");params.addValue("productId",productId);}
        if(warehouseId!=null){where.append(" AND it.warehouse_id=:warehouseId");params.addValue("warehouseId",warehouseId);}
        if(type!=null&&!type.isBlank()){where.append(" AND it.transaction_type=:type");params.addValue("type",type.toUpperCase(java.util.Locale.ROOT));}
        if(from!=null){where.append(" AND it.created_at>=:from");params.addValue("from",Timestamp.from(from));}
        if(to!=null){where.append(" AND it.created_at<:to");params.addValue("to",Timestamp.from(to));}
        String joins=" FROM inventory_transactions it JOIN products p ON p.id=it.product_id JOIN warehouses w ON w.id=it.warehouse_id"+where;
        long total=jdbc.queryForObject("SELECT COUNT(*)"+joins,params,Long.class);
        int safePage=Math.max(0,page),safeSize=Math.max(1,Math.min(size,100));
        params.addValue("limit",safeSize).addValue("offset",(long)safePage*safeSize);
        List<InventoryTransactionResponse> rows=jdbc.query("SELECT it.id,it.product_id,p.sku,p.name product_name,it.warehouse_id,w.code warehouse_code,it.transaction_type,it.quantity_delta,it.purchase_order_id,it.sales_order_id,it.return_id,it.created_by,it.note,it.created_at"+joins+" ORDER BY it.created_at DESC,it.id DESC LIMIT :limit OFFSET :offset",params,transactionMapper);
        return new PageImpl<>(rows,PageRequest.of(safePage,safeSize),total);
    }
    public int postAdjustment(Long productId,Long warehouseId,int quantityDelta,String note,Long actor){
        return jdbc.update("INSERT INTO inventory_transactions(product_id,warehouse_id,transaction_type,quantity_delta,created_by,note) VALUES(:product,:warehouse,'ADJUSTMENT',:delta,:actor,:note)",new MapSqlParameterSource().addValue("product",productId).addValue("warehouse",warehouseId).addValue("delta",quantityDelta).addValue("actor",actor).addValue("note",note));
    }
    private Long nullableLong(java.sql.ResultSet rs,String col)throws java.sql.SQLException {long value=rs.getLong(col);return rs.wasNull()?null:value;}
}
