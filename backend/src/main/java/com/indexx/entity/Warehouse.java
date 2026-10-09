package com.indexx.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity @Table(name = "warehouses")
public class Warehouse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 30) private String code;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false) private boolean active = true;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected Warehouse() {}
    public Warehouse(String code, String name, boolean active) { update(code,name,active); }
    public void update(String code, String name, boolean active) { this.code=code; this.name=name; this.active=active; }
    public Long getId(){return id;} public String getCode(){return code;} public String getName(){return name;}
    public boolean isActive(){return active;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
