package com.indexx.controller;

import com.indexx.dto.InventoryAdjustmentRequest;
import com.indexx.dto.InventoryItemResponse;
import com.indexx.dto.InventoryTransactionResponse;
import com.indexx.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

@RestController @RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService service;
    public InventoryController(InventoryService service){this.service=service;}
    @GetMapping public Page<InventoryItemResponse> list(@RequestParam(required=false) Long productId,
            @RequestParam(required=false) Long warehouseId,@RequestParam(required=false) Boolean lowStock,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="sku") String sort,@RequestParam(defaultValue="ASC") String direction){
        return service.list(productId,warehouseId,lowStock,page,size,sort,direction);
    }
    @GetMapping("/by-product/{productId}") public List<InventoryItemResponse> byProduct(@PathVariable Long productId){return service.byProduct(productId);}
    @GetMapping("/by-warehouse/{warehouseId}") public List<InventoryItemResponse> byWarehouse(@PathVariable Long warehouseId){return service.byWarehouse(warehouseId);}
    @GetMapping("/transactions") public Page<InventoryTransactionResponse> history(@RequestParam(required=false) Long productId,
            @RequestParam(required=false) Long warehouseId,@RequestParam(required=false) String type,
            @RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return service.history(productId,warehouseId,type,from,to,page,size);
    }
    @PostMapping("/adjustments") public ResponseEntity<List<InventoryItemResponse>> adjust(@Valid @RequestBody InventoryAdjustmentRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adjust(request));
    }
}
