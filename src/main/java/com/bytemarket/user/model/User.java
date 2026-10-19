package com.bytemarket.user.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(length = 191, nullable = false, unique = true)
    private String email;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "password_hash", nullable = false, columnDefinition = "TEXT")
    private String passwordHash;

    @Column(length = 20, nullable = false)
    private String role = "customer";

    @Column(name = "is_active", nullable = false)
    private Integer isActive = 1;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "profile_picture", columnDefinition = "TEXT")
    private String profilePicture;

    /**
     * El DNI de la gente del panel.
     *
     * Va impreso en el ticket de apertura de caja y en el papel de caja: un
     * papel firmado que solo dice "Cajero" no identifica a nadie cuando hay
     * dos con el mismo nombre de pila.
     *
     * El de los clientes sigue en su ficha de `customers` —donde está la
     * restricción de unicidad y el resto de sus datos de envío—, y al pasar
     * a un rol de panel esa ficha se borra. Por eso hace falta aquí: el
     * cajero no tiene ficha de cliente.
     */
    @Column(length = 20)
    private String dni;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public String getProfilePicture() { return profilePicture; }
    public void setProfilePicture(String profilePicture) { this.profilePicture = profilePicture; }
}
