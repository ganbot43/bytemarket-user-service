package com.bytemarket.user.config;

import com.bytemarket.user.model.BusinessConfig;
import com.bytemarket.user.model.Customer;
import com.bytemarket.user.model.User;
import com.bytemarket.user.repository.IBusinessConfigRepository;
import com.bytemarket.user.repository.ICustomerRepository;
import com.bytemarket.user.repository.IUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeder de ByteMarket (adaptado del seed.ts de CelParts).
 * Es idempotente: solo crea lo que falta y migra datos antiguos de "CelParts".
 */
@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ICustomerRepository customerRepository;

    @Autowired
    private IBusinessConfigRepository businessConfigRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        System.out.println("🌱 Iniciando seed de ByteMarket (user-service)...");
        migrateLegacyEmails();
        seedUsers();
        seedCustomers();
        seedBusinessConfig();
        System.out.println("✅ Seed de user-service completado.");
    }

    /** Renombra los usuarios antiguos @celparts.com a @bytemarket.com */
    private void migrateLegacyEmails() {
        for (String prefix : List.of("owner", "admin")) {
            userRepository.findByEmail(prefix + "@celparts.com").ifPresent(u -> {
                if (userRepository.findByEmail(prefix + "@bytemarket.com").isEmpty()) {
                    u.setEmail(prefix + "@bytemarket.com");
                    userRepository.save(u);
                    System.out.println("🔁 Migrado " + prefix + "@celparts.com -> " + prefix + "@bytemarket.com");
                }
            });
        }
    }

    private User ensureUser(String name, String email, String rawPassword, String role) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setName(name);
            u.setEmail(email);
            u.setPasswordHash(passwordEncoder.encode(rawPassword));
            u.setRole(role);
            u.setIsActive(1);
            System.out.println("👤 Usuario creado: " + email + " (" + role + ")");
            return userRepository.save(u);
        });
    }

    private void seedUsers() {
        ensureUser("Gerente de tienda", "owner@bytemarket.com", "admin123", "superadmin");
        ensureUser("Encargado de tienda", "admin@bytemarket.com", "admin123", "admin");
    }

    private void seedCustomers() {
        Object[][] demo = {
            {"Cliente Demo", "cliente@bytemarket.com", "987654321", "71234567", "Av. Javier Prado Este 1234, San Isidro, Lima"},
            {"María López", "maria.lopez@bytemarket.com", "912345678", "72345678", "Jr. de la Unión 456, Cercado de Lima"},
        };
        for (Object[] d : demo) {
            User u = ensureUser((String) d[0], (String) d[1], "cliente123", "customer");
            if (customerRepository.findByUserId(u.getId()).isEmpty()) {
                Customer c = new Customer();
                c.setUser(u);
                c.setPhone((String) d[2]);
                c.setDni((String) d[3]);
                c.setAddress((String) d[4]);
                customerRepository.save(c);
            }
        }
    }

    private void seedBusinessConfig() {
        BusinessConfig config = businessConfigRepository.findAll().stream().findFirst().orElseGet(BusinessConfig::new);
        boolean isNew = config.getId() == null;
        boolean isLegacy = config.getName() != null && config.getName().toLowerCase().replace(" ", "").contains("celparts");

        if (isNew || isLegacy) {
            config.setName("ByteMarket");
            config.setRuc("20612345678");
            config.setEmail("ventas@bytemarket.com");
            config.setWhatsapp("+51923821520");
            config.setAddress("Av. Garcilaso de la Vega 1348, Galería Wilson, Lima");
            config.setPlan("basic");
            config.setSocialLinks("{\"instagram\": \"bytemarket.pe\", \"facebook\": \"bytemarket.pe\", \"tiktok\": \"bytemarket.pe\"}");
            config.setStockEnabled(1);
            config.setCouponsEnabled(1);
            businessConfigRepository.save(config);
            System.out.println(isNew ? "🏪 BusinessConfig creado (ByteMarket)." : "🔁 BusinessConfig migrado a ByteMarket.");
        }
    }
}
