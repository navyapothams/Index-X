package com.indexx.service;

import com.indexx.dto.BrandRequest;
import com.indexx.dto.BrandResponse;
import com.indexx.dto.CategoryRequest;
import com.indexx.dto.CategoryResponse;
import com.indexx.dto.ProductRequest;
import com.indexx.dto.ProductResponse;
import com.indexx.dto.SubcategoryRequest;
import com.indexx.dto.SubcategoryResponse;
import com.indexx.entity.Brand;
import com.indexx.entity.Category;
import com.indexx.entity.Product;
import com.indexx.entity.Subcategory;
import com.indexx.exception.ConflictException;
import com.indexx.exception.ResourceNotFoundException;
import com.indexx.repository.BrandRepository;
import com.indexx.repository.CategoryRepository;
import com.indexx.repository.ProductRepository;
import com.indexx.repository.SubcategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;

@Service
@Transactional(readOnly = true)
public class CatalogService {
    private final CategoryRepository categories;
    private final SubcategoryRepository subcategories;
    private final BrandRepository brands;
    private final ProductRepository products;

    public CatalogService(CategoryRepository categories, SubcategoryRepository subcategories,
                          BrandRepository brands, ProductRepository products) {
        this.categories = categories;
        this.subcategories = subcategories;
        this.brands = brands;
        this.products = products;
    }

    public Page<ProductResponse> listProducts(String query, Long categoryId, Long subcategoryId,
                                               Long brandId, BigDecimal minPrice, BigDecimal maxPrice,
                                               Boolean active, String sortField, String sortDirection,
                                               Pageable requestedPage) {
        int page = Math.max(0, requestedPage.getPageNumber());
        int size = Math.max(1, Math.min(requestedPage.getPageSize(), 100));
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        if (minPrice != null && minPrice.signum() < 0 || maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException("Price filters cannot be negative.");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("minPrice cannot be greater than maxPrice.");
        }
        String orderBy = sortField == null || sortField.isBlank() ? "name" : sortField;
        if (!List.of("name", "sku", "sellingPrice", "costPrice", "createdAt").contains(orderBy)) {
            throw new IllegalArgumentException("Unsupported product sort field.");
        }
        String orderDirection = sortDirection == null ? "ASC" : sortDirection.toUpperCase(java.util.Locale.ROOT);
        if (!List.of("ASC", "DESC").contains(orderDirection)) {
            throw new IllegalArgumentException("Sort direction must be ASC or DESC.");
        }
        return products.search(normalizedQuery, categoryId, subcategoryId, brandId, active,
                minPrice, maxPrice, orderBy, orderDirection,
                PageRequest.of(page, size)).map(this::toProductResponse);
    }

    public ProductResponse getProduct(Long id) {
        return toProductResponse(findProduct(id));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        String sku = request.sku().trim();
        if (products.existsBySkuIgnoreCase(sku)) throw new ConflictException("A product with this SKU already exists.");
        Product product = new Product(sku, request.name().trim(), request.description(),
                findCategory(request.categoryId()), findSubcategory(request.subcategoryId(), request.categoryId()),
                findBrand(request.brandId()), request.sellingPrice(), request.costPrice(),
                request.reorderLevel(), request.active());
        return toProductResponse(products.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findProduct(id);
        String sku = request.sku().trim();
        if (products.existsBySkuIgnoreCaseAndIdNot(sku, id)) throw new ConflictException("A product with this SKU already exists.");
        product.update(sku, request.name().trim(), request.description(),
                findCategory(request.categoryId()), findSubcategory(request.subcategoryId(), request.categoryId()),
                findBrand(request.brandId()), request.sellingPrice(), request.costPrice(),
                request.reorderLevel(), request.active());
        return toProductResponse(product);
    }

    @Transactional
    public void deactivateProduct(Long id) {
        findProduct(id).deactivate();
    }

    public List<CategoryResponse> listCategories() {
        return categories.findAll(org.springframework.data.domain.Sort.by("name")).stream().map(this::toCategoryResponse).toList();
    }

    public CategoryResponse getCategory(Long id) { return toCategoryResponse(findCategory(id)); }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) throw new ConflictException("A category with this name already exists.");
        return toCategoryResponse(categories.save(new Category(name, request.description())));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = findCategory(id);
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name) && !category.getName().equalsIgnoreCase(name)) {
            throw new ConflictException("A category with this name already exists.");
        }
        category.update(name, request.description());
        return toCategoryResponse(category);
    }

    @Transactional
    public void deleteCategory(Long id) { categories.delete(findCategory(id)); }

    public List<SubcategoryResponse> listSubcategories(Long categoryId) {
        List<Subcategory> rows = categoryId == null
                ? subcategories.findAll(org.springframework.data.domain.Sort.by("name"))
                : subcategories.findAllByCategoryIdOrderByNameAsc(categoryId);
        return rows.stream().map(this::toSubcategoryResponse).toList();
    }

    @Transactional
    public SubcategoryResponse createSubcategory(SubcategoryRequest request) {
        Category category = findCategory(request.categoryId());
        String name = request.name().trim();
        if (subcategories.existsByCategoryIdAndNameIgnoreCase(category.getId(), name)) {
            throw new ConflictException("A subcategory with this name already exists in the category.");
        }
        return toSubcategoryResponse(subcategories.save(new Subcategory(category, name)));
    }

    @Transactional
    public SubcategoryResponse updateSubcategory(Long id, SubcategoryRequest request) {
        Subcategory subcategory = subcategories.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory " + id + " was not found."));
        Category category = findCategory(request.categoryId());
        String name = request.name().trim();
        if (subcategories.existsByCategoryIdAndNameIgnoreCase(category.getId(), name)
                && !(subcategory.getCategory().getId().equals(category.getId())
                     && subcategory.getName().equalsIgnoreCase(name))) {
            throw new ConflictException("A subcategory with this name already exists in the category.");
        }
        subcategory.update(category, name);
        return toSubcategoryResponse(subcategory);
    }

    @Transactional
    public void deleteSubcategory(Long id) {
        Subcategory subcategory = subcategories.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory " + id + " was not found."));
        subcategories.delete(subcategory);
    }

    public List<BrandResponse> listBrands() {
        return brands.findAll(org.springframework.data.domain.Sort.by("name")).stream().map(this::toBrandResponse).toList();
    }

    @Transactional
    public BrandResponse createBrand(BrandRequest request) {
        String name = request.name().trim();
        if (brands.existsByNameIgnoreCase(name)) throw new ConflictException("A brand with this name already exists.");
        return toBrandResponse(brands.save(new Brand(name)));
    }

    @Transactional
    public BrandResponse updateBrand(Long id, BrandRequest request) {
        Brand brand = findBrand(id);
        String name = request.name().trim();
        if (brands.existsByNameIgnoreCase(name) && !brand.getName().equalsIgnoreCase(name)) {
            throw new ConflictException("A brand with this name already exists.");
        }
        brand.rename(name);
        return toBrandResponse(brand);
    }

    @Transactional
    public void deleteBrand(Long id) { brands.delete(findBrand(id)); }

    private Product findProduct(Long id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product " + id + " was not found."));
    }

    private Category findCategory(Long id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category " + id + " was not found."));
    }

    private Subcategory findSubcategory(Long subcategoryId, Long categoryId) {
        if (subcategoryId == null) return null;
        Subcategory subcategory = subcategories.findById(subcategoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory " + subcategoryId + " was not found."));
        if (!subcategory.getCategory().getId().equals(categoryId)) {
            throw new IllegalArgumentException("The selected subcategory does not belong to the selected category.");
        }
        return subcategory;
    }

    private Brand findBrand(Long id) {
        if (id == null) return null;
        return brands.findById(id).orElseThrow(() -> new ResourceNotFoundException("Brand " + id + " was not found."));
    }

    private ProductResponse toProductResponse(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(),
                p.getCategory().getId(), p.getCategory().getName(),
                p.getSubcategory() == null ? null : p.getSubcategory().getId(),
                p.getSubcategory() == null ? null : p.getSubcategory().getName(),
                p.getBrand() == null ? null : p.getBrand().getId(),
                p.getBrand() == null ? null : p.getBrand().getName(),
                p.getSellingPrice(), p.getCostPrice(), p.getReorderLevel(), p.isActive(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    private CategoryResponse toCategoryResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private SubcategoryResponse toSubcategoryResponse(Subcategory s) {
        return new SubcategoryResponse(s.getId(), s.getCategory().getId(), s.getCategory().getName(),
                s.getName(), s.getCreatedAt(), s.getUpdatedAt());
    }

    private BrandResponse toBrandResponse(Brand b) {
        return new BrandResponse(b.getId(), b.getName(), b.getCreatedAt(), b.getUpdatedAt());
    }
}
