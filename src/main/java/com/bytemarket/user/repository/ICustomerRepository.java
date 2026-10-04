package com.bytemarket.user.repository;

import com.bytemarket.user.model.Customer;
import com.bytemarket.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ICustomerRepository extends JpaRepository<Customer, Integer> {

    Optional<Customer> findByUser(User user);

    Optional<Customer> findByUserId(Integer userId);

    Optional<Customer> findByUserEmail(String email);

    Optional<Customer> findByDni(String dni);

    boolean existsByDni(String dni);

    java.util.List<Customer> findByUserIdIn(java.util.Collection<Integer> userIds);

    @org.springframework.transaction.annotation.Transactional
    void deleteByUserId(Integer userId);
}
