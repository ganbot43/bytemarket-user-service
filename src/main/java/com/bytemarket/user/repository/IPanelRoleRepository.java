package com.bytemarket.user.repository;

import com.bytemarket.user.model.PanelRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IPanelRoleRepository extends JpaRepository<PanelRole, Integer> {

    Optional<PanelRole> findByClave(String clave);

    List<PanelRole> findAllByOrderByNivelAsc();
}
