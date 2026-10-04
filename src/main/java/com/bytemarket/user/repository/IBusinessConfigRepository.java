package com.bytemarket.user.repository;

import com.bytemarket.user.model.BusinessConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IBusinessConfigRepository extends JpaRepository<BusinessConfig, Integer> {
    // Al ser una tabla de 1 sola fila, usaremos findById(1)
}
