package com.indexx.repository;

import com.indexx.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    @Query("select s from Supplier s where (:q is null or lower(s.name) like lower(concat('%',:q,'%')) or lower(s.email) like lower(concat('%',:q,'%'))) and (:active is null or s.active=:active)")
    Page<Supplier> search(@Param("q") String query, @Param("active") Boolean active, Pageable pageable);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
