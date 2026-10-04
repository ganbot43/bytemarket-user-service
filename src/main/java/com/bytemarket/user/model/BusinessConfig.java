package com.bytemarket.user.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "business_config")
public class BusinessConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(name = "ruc", columnDefinition = "TEXT")
    private String ruc;

    @Column(name = "email", columnDefinition = "TEXT")
    private String email;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String whatsapp;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 20, nullable = false)
    private String plan = "basic";

    @Column(name = "social_links", columnDefinition = "TEXT")
    private String socialLinks;

    @Column(name = "stock_enabled", nullable = false)
    private Integer stockEnabled = 1;

    @Column(name = "auto_payment_enabled", nullable = false)
    private Integer autoPaymentEnabled = 0;

    @Column(name = "coupons_enabled", nullable = false)
    private Integer couponsEnabled = 0;

    @Column(name = "multiuser_enabled", nullable = false)
    private Integer multiuserEnabled = 0;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
