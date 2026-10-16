package com.bytemarket.user.repository;

import com.bytemarket.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IUserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
    org.springframework.data.domain.Page<User> findByRole(String role, org.springframework.data.domain.Pageable pageable);

    /**
     * Varios roles a la vez y con búsqueda por texto.
     *
     * findByRole solo acepta uno, y la pantalla de personal necesita ver
     * admin y superadmin juntos: separarlos en dos listas haría que la
     * paginación no significara nada. El texto busca por nombre y correo,
     * que es por lo que se busca a una persona.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT u FROM User u
            WHERE u.role IN :roles
              AND (:patron IS NULL
                   OR LOWER(u.name) LIKE :patron
                   OR LOWER(u.email) LIKE :patron)
            """)
    org.springframework.data.domain.Page<User> buscarPorRoles(
            @org.springframework.data.repository.query.Param("roles") java.util.List<String> roles,
            @org.springframework.data.repository.query.Param("patron") String patron,
            org.springframework.data.domain.Pageable pageable);
}
