package com.indexx.service;

import com.indexx.dto.InventoryAdjustmentRequest;
import com.indexx.dto.InventoryItemResponse;
import com.indexx.dto.InventoryTransactionResponse;
import com.indexx.exception.ConflictException;
import com.indexx.exception.ResourceNotFoundException;
import com.indexx.repository.InventoryRepository;
import com.indexx.repository.ProductRepository;
import com.indexx.repository.WarehouseRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
public class InventoryService {
    private final InventoryRepository inventory;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    public InventoryService(InventoryRepository inventory,ProductRepository products,WarehouseRepository warehouses){
        this.inventory=inventory;this.products=products;this.warehouses=warehouses;
    }
    @Transactional(readOnly=true)
    public Page<InventoryItemResponse> list(Long productId,Long warehouseId,Boolean lowStock,int page,int size,String sort,String direction){
        if(productId!=null&&!products.existsById(productId))throw new ResourceNotFoundException("Product "+productId+" was not found.");
        if(warehouseId!=null&&!warehouses.existsById(warehouseId))throw new ResourceNotFoundException("Warehouse "+warehouseId+" was not found.");
        return inventory.search(productId,warehouseId,lowStock,page,size,sort,direction);
    }
    @Transactional(readOnly=true)
    public List<InventoryItemResponse> byProduct(Long id){if(!products.existsById(id))throw new ResourceNotFoundException("Product "+id+" was not found.");return inventory.byProduct(id);}
    @Transactional(readOnly=true)
    public List<InventoryItemResponse> byWarehouse(Long id){if(!warehouses.existsById(id))throw new ResourceNotFoundException("Warehouse "+id+" was not found.");return inventory.byWarehouse(id);}
    @Transactional(readOnly=true)
    public Page<InventoryTransactionResponse> history(Long productId,Long warehouseId,String type,Instant from,Instant to,int page,int size){
        if(productId!=null&&!products.existsById(productId))throw new ResourceNotFoundException("Product "+productId+" was not found.");
        if(warehouseId!=null&&!warehouses.existsById(warehouseId))throw new ResourceNotFoundException("Warehouse "+warehouseId+" was not found.");
        if(from!=null&&to!=null&&!from.isBefore(to))throw new IllegalArgumentException("from must be earlier than to.");
        if(type!=null&&!List.of("PURCHASE","SALE","RETURN","ADJUSTMENT").contains(type.toUpperCase(java.util.Locale.ROOT)))throw new IllegalArgumentException("Invalid inventory transaction type.");
        return inventory.history(productId,warehouseId,type,from,to,page,size);
    }
    @Transactional
    public List<InventoryItemResponse> adjust(InventoryAdjustmentRequest request){
        if(!products.existsById(request.productId()))throw new ResourceNotFoundException("Product "+request.productId()+" was not found.");
        if(!warehouses.existsById(request.warehouseId()))throw new ResourceNotFoundException("Warehouse "+request.warehouseId()+" was not found.");
        try { inventory.postAdjustment(request.productId(),request.warehouseId(),request.quantityDelta(),request.note().trim(),null); }
        catch(DataIntegrityViolationException ex){throw new ConflictException("Adjustment rejected because it would violate stock or database constraints.");}
        return inventory.byProduct(request.productId()).stream().filter(row->row.warehouseId().equals(request.warehouseId())).toList();
    }
}
