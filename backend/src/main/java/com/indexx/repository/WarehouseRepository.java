package com.indexx.repository;

import com.indexx.entity.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    @Query("select w from Warehouse w where (:q is null or lower(w.code) like lower(concat('%',:q,'%')) or lower(w.name) like lower(concat('%',:q,'%'))) and (:active is null or w.active=:active)")
    Page<Warehouse> search(@Param("q") String query, @Param("active") Boolean active, Pageable pageable);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
