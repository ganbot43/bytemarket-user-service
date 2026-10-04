package com.bytemarket.user.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "customers")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 20)
    private String phone;

    @Column(length = 20)
    private String dni;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "address_reference", columnDefinition = "TEXT")
    private String addressReference;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getAddressReference() { return addressReference; }
    public void setAddressReference(String addressReference) { this.addressReference = addressReference; }
}
