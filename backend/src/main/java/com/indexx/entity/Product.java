package com.indexx.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String sku;
    @Column(nullable = false, length = 180)
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, foreignKey = @ForeignKey(name = "products_category_id_fkey"))
    private Category category;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id", foreignKey = @ForeignKey(name = "products_category_id_subcategory_id_fkey"))
    private Subcategory subcategory;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", foreignKey = @ForeignKey(name = "products_brand_id_fkey"))
    private Brand brand;
    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;
    @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice;
    @Column(name = "reorder_level", nullable = false)
    private Integer reorderLevel;
    @Column(nullable = false)
    private boolean active = true;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {}

    public Product(String sku, String name, String description, Category category, Subcategory subcategory,
                   Brand brand, BigDecimal sellingPrice, BigDecimal costPrice, Integer reorderLevel, boolean active) {
        update(sku, name, description, category, subcategory, brand, sellingPrice, costPrice, reorderLevel, active);
    }

    public void update(String sku, String name, String description, Category category, Subcategory subcategory,
                       Brand brand, BigDecimal sellingPrice, BigDecimal costPrice, Integer reorderLevel, boolean active) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.category = category;
        this.subcategory = subcategory;
        this.brand = brand;
        this.sellingPrice = sellingPrice;
        this.costPrice = costPrice;
        this.reorderLevel = reorderLevel;
        this.active = active;
    }

    public void deactivate() { this.active = false; }
    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public Subcategory getSubcategory() { return subcategory; }
    public Brand getBrand() { return brand; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public BigDecimal getCostPrice() { return costPrice; }
    public Integer getReorderLevel() { return reorderLevel; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
