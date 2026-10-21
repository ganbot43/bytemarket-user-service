package com.bytemarket.user.repository;

import com.bytemarket.user.model.PanelModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IPanelModuleRepository extends JpaRepository<PanelModule, Integer> {

    Optional<PanelModule> findByClave(String clave);

    List<PanelModule> findAllByOrderByOrdenAsc();
}
