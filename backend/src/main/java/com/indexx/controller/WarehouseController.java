package com.indexx.controller;

import com.indexx.dto.WarehouseRequest;
import com.indexx.dto.WarehouseResponse;
import com.indexx.service.PartyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/warehouses")
public class WarehouseController {
    private final PartyService service;
    public WarehouseController(PartyService service){this.service=service;}
    @GetMapping public Page<WarehouseResponse> list(@RequestParam(required=false) String q,
            @RequestParam(required=false) Boolean active,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="code") String sort,
            @RequestParam(defaultValue="ASC") String direction){return service.warehouses(q,active,page,size,sort,direction);}
    @GetMapping("/{id}") public WarehouseResponse get(@PathVariable Long id){return service.warehouse(id);}
    @PostMapping public ResponseEntity<WarehouseResponse> create(@Valid @RequestBody WarehouseRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(service.createWarehouse(request));}
    @PutMapping("/{id}") public WarehouseResponse update(@PathVariable Long id,@Valid @RequestBody WarehouseRequest request){return service.updateWarehouse(id,request);}
    @DeleteMapping("/{id}") public ResponseEntity<Void> deactivate(@PathVariable Long id){service.deactivateWarehouse(id);return ResponseEntity.noContent().build();}
}
