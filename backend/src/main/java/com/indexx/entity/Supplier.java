package com.indexx.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity @Table(name = "suppliers")
public class Supplier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 160) private String name;
    @Column(unique = true, length = 254) private String email;
    @Column(length = 30) private String phone;
    @Column(nullable = false) private boolean active = true;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected Supplier() {}
    public Supplier(String name, String email, String phone, boolean active) { update(name, email, phone, active); }
    public void update(String name, String email, String phone, boolean active) { this.name=name; this.email=email; this.phone=phone; this.active=active; }
    public Long getId(){return id;} public String getName(){return name;} public String getEmail(){return email;}
    public String getPhone(){return phone;} public boolean isActive(){return active;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
