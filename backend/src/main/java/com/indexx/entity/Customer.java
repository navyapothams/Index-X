package com.indexx.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity @Table(name = "customers")
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 160) private String name;
    @Column(unique = true, length = 254) private String email;
    @Column(length = 30) private String phone;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected Customer() {}
    public Customer(String name, String email, String phone) { update(name,email,phone); }
    public void update(String name, String email, String phone) { this.name=name; this.email=email; this.phone=phone; }
    public Long getId(){return id;} public String getName(){return name;} public String getEmail(){return email;}
    public String getPhone(){return phone;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
