package com.bytemarket.user.repository;

import com.bytemarket.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IUserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
    org.springframework.data.domain.Page<User> findByRole(String role, org.springframework.data.domain.Pageable pageable);
}
