package com.indexx.controller;

import com.indexx.dto.SupplierRequest;
import com.indexx.dto.SupplierResponse;
import com.indexx.service.PartyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/suppliers")
public class SupplierController {
    private final PartyService service;
    public SupplierController(PartyService service){this.service=service;}
    @GetMapping public Page<SupplierResponse> list(@RequestParam(required=false) String q,
            @RequestParam(required=false) Boolean active,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="name") String sort,
            @RequestParam(defaultValue="ASC") String direction){return service.suppliers(q,active,page,size,sort,direction);}
    @GetMapping("/{id}") public SupplierResponse get(@PathVariable Long id){return service.supplier(id);}
    @PostMapping public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(service.createSupplier(request));}
    @PutMapping("/{id}") public SupplierResponse update(@PathVariable Long id,@Valid @RequestBody SupplierRequest request){return service.updateSupplier(id,request);}
    @DeleteMapping("/{id}") public ResponseEntity<Void> deactivate(@PathVariable Long id){service.deactivateSupplier(id);return ResponseEntity.noContent().build();}
}
