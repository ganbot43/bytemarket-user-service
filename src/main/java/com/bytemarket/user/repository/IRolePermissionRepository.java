package com.bytemarket.user.repository;

import com.bytemarket.user.model.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IRolePermissionRepository extends JpaRepository<RolePermission, Integer> {

    List<RolePermission> findByRole(String role);

    Optional<RolePermission> findByRoleAndModule(String role, String module);
}
