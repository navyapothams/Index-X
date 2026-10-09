package com.indexx.service;

import com.indexx.dto.*;
import com.indexx.entity.Product;
import com.indexx.exception.ConflictException;
import com.indexx.exception.ResourceNotFoundException;
import com.indexx.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final SupplierRepository suppliers;
    private final WarehouseRepository warehouses;
    private final CustomerRepository customers;
    private final ProductRepository products;
    public OrderService(OrderRepository orders,SupplierRepository suppliers,WarehouseRepository warehouses,
                        CustomerRepository customers,ProductRepository products){
        this.orders=orders;this.suppliers=suppliers;this.warehouses=warehouses;this.customers=customers;this.products=products;
    }

    @Transactional
    public PurchaseOrderResponse createPurchase(PurchaseOrderRequest request){
        if(!suppliers.existsById(request.supplierId()))throw new ResourceNotFoundException("Supplier "+request.supplierId()+" was not found.");
        if(!warehouses.existsById(request.warehouseId()))throw new ResourceNotFoundException("Warehouse "+request.warehouseId()+" was not found.");
        ensureUniqueProducts(request.items().stream().map(PurchaseItemRequest::productId).toList());
        for(PurchaseItemRequest item:request.items()){
            Product product=products.findById(item.productId()).orElseThrow(()->new ResourceNotFoundException("Product "+item.productId()+" was not found."));
            if(!product.isActive())throw new ConflictException("Inactive products cannot be added to a purchase order.");
            if(!orders.hasSupplierProduct(request.supplierId(),item.productId()))throw new ConflictException("Product "+product.getSku()+" is not in this supplier's catalog.");
        }
        Long id=orders.createPurchase(orderNumber("PO"),request.supplierId(),request.warehouseId(),request.expectedDate(),request.notes());
        request.items().forEach(item->orders.addPurchaseItem(id,item));
        return orders.purchase(id);
    }
    @Transactional
    public PurchaseOrderResponse addPurchaseItems(Long id,List<PurchaseItemRequest> items){
        String status=purchaseStatus(id,true); if(!"DRAFT".equals(status))throw new ConflictException("Purchase items can only be changed while the order is DRAFT.");
        if(items==null||items.isEmpty())throw new IllegalArgumentException("At least one purchase item is required.");
        ensureUniqueProducts(items.stream().map(PurchaseItemRequest::productId).toList());
        Long supplierId=orders.purchase(id).supplierId();
        for(PurchaseItemRequest item:items){
            Product product=products.findById(item.productId()).orElseThrow(()->new ResourceNotFoundException("Product "+item.productId()+" was not found."));
            if(!product.isActive())throw new ConflictException("Inactive products cannot be added to a purchase order.");
            if(!orders.hasSupplierProduct(supplierId,item.productId()))throw new ConflictException("Product "+product.getSku()+" is not in this supplier's catalog.");
            orders.addPurchaseItem(id,item);
        }
        return orders.purchase(id);
    }
    @Transactional public PurchaseOrderResponse orderPurchase(Long id){
        String status=purchaseStatus(id,true);if(!"DRAFT".equals(status))throw new ConflictException("Only DRAFT purchase orders can be ordered.");
        orders.changePurchaseStatus(id,"ORDERED");return orders.purchase(id);
    }
    @Transactional public PurchaseOrderResponse receivePurchase(Long id,PurchaseReceiptRequest request){
        String status=purchaseStatus(id,true);
        if(!List.of("ORDERED","PARTIALLY_RECEIVED").contains(status))throw new ConflictException("Only ordered or partially received purchases can receive stock.");
        Set<Long> seen=new HashSet<>();
        for(PurchaseReceiptLine line:request.items()){
            if(!seen.add(line.itemId()))throw new IllegalArgumentException("A receipt cannot contain the same order item more than once.");
            if(orders.receivePurchaseLine(id,line.itemId(),line.quantity())!=1)
                throw new ConflictException("Receipt quantity exceeds the outstanding quantity or item does not belong to this purchase.");
        }
        return orders.purchase(id);
    }
    @Transactional public PurchaseOrderResponse cancelPurchase(Long id){
        String status=purchaseStatus(id,true);
        if(!List.of("DRAFT","ORDERED","PARTIALLY_RECEIVED").contains(status))throw new ConflictException("This purchase order can no longer be cancelled.");
        orders.changePurchaseStatus(id,"CANCELLED");return orders.purchase(id);
    }
    @Transactional(readOnly=true) public PurchaseOrderResponse purchase(Long id){PurchaseOrderResponse po=orders.purchase(id);if(po==null)throw new ResourceNotFoundException("Purchase order "+id+" was not found.");return po;}
    @Transactional(readOnly=true) public Page<PurchaseOrderResponse> purchases(Long supplierId,String status,Instant from,Instant to,int page,int size){
        if(supplierId!=null&&!suppliers.existsById(supplierId))throw new ResourceNotFoundException("Supplier "+supplierId+" was not found.");
        if(from!=null&&to!=null&&!from.isBefore(to))throw new IllegalArgumentException("from must be earlier than to.");
        return orders.purchases(supplierId,status,from,to,page,size);
    }

    @Transactional
    public SalesOrderResponse createSale(SalesOrderRequest request){
        if(!warehouses.existsById(request.warehouseId()))throw new ResourceNotFoundException("Warehouse "+request.warehouseId()+" was not found.");
        if(request.customerId()!=null&&!customers.existsById(request.customerId()))throw new ResourceNotFoundException("Customer "+request.customerId()+" was not found.");
        ensureUniqueProducts(request.items().stream().map(SalesItemRequest::productId).toList());
        Map<Long,Product> selected=new HashMap<>();
        for(SalesItemRequest item:request.items()){
            Product p=products.findById(item.productId()).orElseThrow(()->new ResourceNotFoundException("Product "+item.productId()+" was not found."));
            if(!p.isActive())throw new ConflictException("Inactive products cannot be added to a sale.");
            selected.put(p.getId(),p);
        }
        Long id=orders.createSale(orderNumber("SO"),request.customerId(),request.warehouseId(),request.notes());
        for(SalesItemRequest item:request.items()){
            BigDecimal discount=item.discountRate()==null?BigDecimal.ZERO:item.discountRate();
            orders.addSaleItem(id,item.productId(),item.quantity(),selected.get(item.productId()).getSellingPrice(),discount);
        }
        return orders.sale(id);
    }
    @Transactional public SalesOrderResponse addSalesItems(Long id,List<SalesItemRequest> items){
        Map<String,Object> order=saleStatus(id,true);if(!"DRAFT".equals(order.get("status")))throw new ConflictException("Sales items can only be changed while the order is DRAFT.");
        if(items==null||items.isEmpty())throw new IllegalArgumentException("At least one sales item is required.");
        ensureUniqueProducts(items.stream().map(SalesItemRequest::productId).toList());
        for(SalesItemRequest item:items){
            Product p=products.findById(item.productId()).orElseThrow(()->new ResourceNotFoundException("Product "+item.productId()+" was not found."));
            if(!p.isActive())throw new ConflictException("Inactive products cannot be added to a sale.");
            orders.addSaleItem(id,item.productId(),item.quantity(),p.getSellingPrice(),item.discountRate()==null?BigDecimal.ZERO:item.discountRate());
        }
        return orders.sale(id);
    }
    @Transactional public SalesOrderResponse completeSale(Long id,SalesCompleteRequest request){
        Map<String,Object> header=saleStatus(id,true);if(!"DRAFT".equals(header.get("status")))throw new ConflictException("Only DRAFT sales orders can be completed.");
        List<Map<String,Object>> lines=orders.saleLines(id);if(lines.isEmpty())throw new ConflictException("A sale needs at least one item before completion.");
        long warehouse=((Number)header.get("warehouse_id")).longValue();
        List<Long> ids=lines.stream().map(x->((Number)x.get("product_id")).longValue()).distinct().sorted().toList();
        Map<Long,Integer> stock=orders.lockedStock(warehouse,ids);
        for(Map<String,Object> line:lines){
            long productId=((Number)line.get("product_id")).longValue();int available=stock.getOrDefault(productId,0);int requested=((Number)line.get("quantity")).intValue();
            if(available<requested)throw new ConflictException("Insufficient stock for SKU "+line.get("sku")+": requested "+requested+", available "+available+".");
        }
        orders.changeSaleStatus(id,"COMPLETED");
        String reference=request.paymentReference()==null||request.paymentReference().isBlank()?"PAY-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT):request.paymentReference().trim();
        orders.addCompletedPayment(id,request.paymentMethod(),reference);
        return orders.sale(id);
    }
    @Transactional public SalesOrderResponse cancelSale(Long id){Map<String,Object> header=saleStatus(id,true);if(!"DRAFT".equals(header.get("status")))throw new ConflictException("Only DRAFT sales orders can be cancelled.");orders.changeSaleStatus(id,"CANCELLED");return orders.sale(id);}
    @Transactional(readOnly=true) public SalesOrderResponse sale(Long id){SalesOrderResponse s=orders.sale(id);if(s==null)throw new ResourceNotFoundException("Sales order "+id+" was not found.");return s;}
    @Transactional(readOnly=true) public Page<SalesOrderResponse> sales(Long customerId,String status,Instant from,Instant to,int page,int size){
        if(customerId!=null&&!customers.existsById(customerId))throw new ResourceNotFoundException("Customer "+customerId+" was not found.");
        if(from!=null&&to!=null&&!from.isBefore(to))throw new IllegalArgumentException("from must be earlier than to.");
        return orders.sales(customerId,status,from,to,page,size);
    }

    private String purchaseStatus(Long id,boolean lock){try{return orders.purchaseStatusForUpdate(id);}catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResourceNotFoundException("Purchase order "+id+" was not found.");}}
    private Map<String,Object> saleStatus(Long id,boolean lock){try{return orders.saleForUpdate(id);}catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResourceNotFoundException("Sales order "+id+" was not found.");}}
    private void ensureUniqueProducts(List<Long> ids){if(new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("An order may contain a product only once; combine duplicate quantities into one line.");}
    private String orderNumber(String prefix){return prefix+"-"+java.time.LocalDate.now()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);}
}
