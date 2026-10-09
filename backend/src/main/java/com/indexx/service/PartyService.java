package com.indexx.service;

import com.indexx.dto.*;
import com.indexx.entity.Customer;
import com.indexx.entity.Supplier;
import com.indexx.entity.Warehouse;
import com.indexx.exception.ConflictException;
import com.indexx.exception.ResourceNotFoundException;
import com.indexx.repository.CustomerRepository;
import com.indexx.repository.SupplierRepository;
import com.indexx.repository.WarehouseRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PartyService {
    private final SupplierRepository suppliers;
    private final CustomerRepository customers;
    private final WarehouseRepository warehouses;

    public PartyService(SupplierRepository suppliers, CustomerRepository customers, WarehouseRepository warehouses) {
        this.suppliers=suppliers; this.customers=customers; this.warehouses=warehouses;
    }

    public Page<SupplierResponse> suppliers(String q, Boolean active, int page, int size, String sort, String direction) {
        Sort order = sort("name", sort, direction, "name", "email", "createdAt", "active");
        return suppliers.search(clean(q), active, PageRequest.of(Math.max(page,0), limit(size), order)).map(this::supplierDto);
    }
    public SupplierResponse supplier(Long id) { return supplierDto(findSupplier(id)); }
    @Transactional public SupplierResponse createSupplier(SupplierRequest r) {
        String email=clean(r.email());
        if(email!=null && suppliers.existsByEmailIgnoreCase(email)) throw new ConflictException("Supplier email already exists.");
        return supplierDto(suppliers.save(new Supplier(r.name().trim(), email, clean(r.phone()), r.active()==null || r.active())));
    }
    @Transactional public SupplierResponse updateSupplier(Long id, SupplierRequest r) {
        Supplier s=findSupplier(id); String email=clean(r.email());
        if(email!=null && suppliers.existsByEmailIgnoreCaseAndIdNot(email,id)) throw new ConflictException("Supplier email already exists.");
        s.update(r.name().trim(),email,clean(r.phone()),r.active()==null?s.isActive():r.active()); return supplierDto(s);
    }
    @Transactional public void deactivateSupplier(Long id) { Supplier s=findSupplier(id); s.update(s.getName(),s.getEmail(),s.getPhone(),false); }

    public Page<CustomerResponse> customers(String q, int page, int size, String sort, String direction) {
        return customers.search(clean(q), PageRequest.of(Math.max(page,0), limit(size), sort("name",sort,direction,"name","email","createdAt"))).map(this::customerDto);
    }
    public CustomerResponse customer(Long id) { return customerDto(findCustomer(id)); }
    @Transactional public CustomerResponse createCustomer(CustomerRequest r) {
        String email=clean(r.email());
        if(email!=null && customers.existsByEmailIgnoreCase(email)) throw new ConflictException("Customer email already exists.");
        return customerDto(customers.save(new Customer(r.name().trim(),email,clean(r.phone()))));
    }
    @Transactional public CustomerResponse updateCustomer(Long id, CustomerRequest r) {
        Customer c=findCustomer(id); String email=clean(r.email());
        if(email!=null && customers.existsByEmailIgnoreCaseAndIdNot(email,id)) throw new ConflictException("Customer email already exists.");
        c.update(r.name().trim(),email,clean(r.phone())); return customerDto(c);
    }
    @Transactional public void deleteCustomer(Long id) { customers.delete(findCustomer(id)); }

    public Page<WarehouseResponse> warehouses(String q, Boolean active, int page, int size, String sort, String direction) {
        return warehouses.search(clean(q),active,PageRequest.of(Math.max(page,0),limit(size),sort("code",sort,direction,"code","name","createdAt","active"))).map(this::warehouseDto);
    }
    public WarehouseResponse warehouse(Long id) { return warehouseDto(findWarehouse(id)); }
    @Transactional public WarehouseResponse createWarehouse(WarehouseRequest r) {
        String code=r.code().trim(); if(warehouses.existsByCodeIgnoreCase(code)) throw new ConflictException("Warehouse code already exists.");
        return warehouseDto(warehouses.save(new Warehouse(code,r.name().trim(),r.active()==null || r.active())));
    }
    @Transactional public WarehouseResponse updateWarehouse(Long id, WarehouseRequest r) {
        Warehouse w=findWarehouse(id); String code=r.code().trim();
        if(warehouses.existsByCodeIgnoreCaseAndIdNot(code,id)) throw new ConflictException("Warehouse code already exists.");
        w.update(code,r.name().trim(),r.active()==null?w.isActive():r.active()); return warehouseDto(w);
    }
    @Transactional public void deactivateWarehouse(Long id) { Warehouse w=findWarehouse(id); w.update(w.getCode(),w.getName(),false); }

    private Supplier findSupplier(Long id){return suppliers.findById(id).orElseThrow(()->new ResourceNotFoundException("Supplier "+id+" was not found."));}
    private Customer findCustomer(Long id){return customers.findById(id).orElseThrow(()->new ResourceNotFoundException("Customer "+id+" was not found."));}
    private Warehouse findWarehouse(Long id){return warehouses.findById(id).orElseThrow(()->new ResourceNotFoundException("Warehouse "+id+" was not found."));}
    private SupplierResponse supplierDto(Supplier s){return new SupplierResponse(s.getId(),s.getName(),s.getEmail(),s.getPhone(),s.isActive(),s.getCreatedAt(),s.getUpdatedAt());}
    private CustomerResponse customerDto(Customer c){return new CustomerResponse(c.getId(),c.getName(),c.getEmail(),c.getPhone(),c.getCreatedAt(),c.getUpdatedAt());}
    private WarehouseResponse warehouseDto(Warehouse w){return new WarehouseResponse(w.getId(),w.getCode(),w.getName(),w.isActive(),w.getCreatedAt(),w.getUpdatedAt());}
    private Sort sort(String fallback,String field,String direction,String... allowed){
        String f=field==null||field.isBlank()?fallback:field;
        if(!java.util.List.of(allowed).contains(f)) throw new IllegalArgumentException("Unsupported sort field: "+f);
        Sort.Direction d;
        try { d=Sort.Direction.fromString(direction==null?"ASC":direction); }
        catch(IllegalArgumentException ex){throw new IllegalArgumentException("Sort direction must be ASC or DESC.");}
        return Sort.by(d,f);
    }
    private int limit(int size){return Math.max(1,Math.min(size,100));}
    private String clean(String s){return s==null||s.isBlank()?null:s.trim();}
}
