package com.indexx.repository;

import com.indexx.entity.Subcategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubcategoryRepository extends JpaRepository<Subcategory, Long> {
    List<Subcategory> findAllByCategoryIdOrderByNameAsc(Long categoryId);
    boolean existsByCategoryIdAndNameIgnoreCase(Long categoryId, String name);
}
