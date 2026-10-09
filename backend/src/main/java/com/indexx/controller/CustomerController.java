package com.indexx.controller;

import com.indexx.dto.CustomerRequest;
import com.indexx.dto.CustomerResponse;
import com.indexx.service.PartyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/customers")
public class CustomerController {
    private final PartyService service;
    public CustomerController(PartyService service){this.service=service;}
    @GetMapping public Page<CustomerResponse> list(@RequestParam(required=false) String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="name") String sort,@RequestParam(defaultValue="ASC") String direction){return service.customers(q,page,size,sort,direction);}
    @GetMapping("/{id}") public CustomerResponse get(@PathVariable Long id){return service.customer(id);}
    @PostMapping public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(service.createCustomer(request));}
    @PutMapping("/{id}") public CustomerResponse update(@PathVariable Long id,@Valid @RequestBody CustomerRequest request){return service.updateCustomer(id,request);}
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id){service.deleteCustomer(id);return ResponseEntity.noContent().build();}
}
