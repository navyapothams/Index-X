package com.indexx.repository;

import com.indexx.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query("select c from Customer c where (:q is null or lower(c.name) like lower(concat('%',:q,'%')) or lower(c.email) like lower(concat('%',:q,'%')) or c.phone like concat('%',:q,'%'))")
    Page<Customer> search(@Param("q") String query, Pageable pageable);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
