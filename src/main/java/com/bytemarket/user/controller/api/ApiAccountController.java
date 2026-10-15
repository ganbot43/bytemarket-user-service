package com.bytemarket.user.controller.api;

import com.bytemarket.user.service.S3Service;
import lombok.RequiredArgsConstructor;
import com.bytemarket.user.model.BusinessConfig;
import com.bytemarket.user.model.Customer;
import com.bytemarket.user.model.User;
import com.bytemarket.user.repository.IBusinessConfigRepository;
import com.bytemarket.user.repository.ICustomerRepository;
import com.bytemarket.user.repository.IUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Perfil del cliente ("Mis datos") y configuración pública del negocio.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiAccountController {

    private final IUserRepository userRepository;
    private final ICustomerRepository customerRepository;
    private final IBusinessConfigRepository businessConfigRepository;
    private final S3Service s3Service;

    private final ObjectMapper mapper = new ObjectMapper();

    // ─── MI PERFIL ──────────────────────────────────────────────

    @GetMapping("/mi-perfil")
    public ResponseEntity<?> getPerfil(Principal principal) {
        Optional<User> userOpt = currentUser(principal);
        if (userOpt.isEmpty()) return error(401, "No autenticado");
        User user = userOpt.get();
        Customer c = customerRepository.findByUserId(user.getId()).orElse(null);
        return ResponseEntity.ok(perfilDTO(user, c));
    }

    @PutMapping("/mi-perfil")
    @Transactional
    public ResponseEntity<?> updatePerfil(@RequestBody Map<String, Object> body, Principal principal) {
        Optional<User> userOpt = currentUser(principal);
        if (userOpt.isEmpty()) return error(401, "No autenticado");
        User user = userOpt.get();

        if (!"customer".equals(user.getRole())) {
            return error(403, "Solo las cuentas de cliente pueden editar este perfil");
        }

        String name = limpiar(body.get("name"));
        if (name == null || name.length() < 2) return error(400, "Escribe tu nombre completo");
        if (name.length() > 120) return error(400, "El nombre es demasiado largo");

        String phone = limpiar(body.get("phone"));
        if (phone != null && phone.replaceAll("\\D", "").length() < 9) {
            return error(400, "El teléfono debe tener al menos 9 dígitos");
        }
        String address = limpiar(body.get("address"));
        String reference = limpiar(body.get("addressReference"));
        if ((address != null && address.length() > 255) || (reference != null && reference.length() > 255)) {
            return error(400, "La dirección es demasiado larga");
        }

        user.setName(name);
        userRepository.save(user);

        Customer c = customerRepository.findByUserId(user.getId()).orElseGet(() -> {
            Customer nuevo = new Customer();
            nuevo.setUser(user);
            return nuevo;
        });
        c.setPhone(phone);
        c.setAddress(address);
        c.setAddressReference(reference);
        customerRepository.save(c);

        Map<String, Object> dto = perfilDTO(user, c);
        dto.put("role", user.getRole());
        return ResponseEntity.ok(dto);
    }

    // ─── CONFIGURACIÓN DEL NEGOCIO (pública) ────────────────────

    @GetMapping("/business-config")
    public ResponseEntity<?> getBusinessConfig() {
        return businessConfigRepository.findAll().stream().findFirst()
                .<ResponseEntity<?>>map(c -> ResponseEntity.ok(BusinessConfigMapper.toDTO(c, mapper, s3Service)))
                .orElseGet(() -> error(404, "Configuración no encontrada"));
    }

    // ─── helpers ────────────────────────────────────────────────

    private Optional<User> currentUser(Principal principal) {
        if (principal == null) return Optional.empty();
        return userRepository.findByEmail(principal.getName());
    }

    private Map<String, Object> perfilDTO(User user, Customer c) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", user.getId());
        dto.put("name", user.getName());
        dto.put("email", user.getEmail());
        dto.put("phone", c != null ? c.getPhone() : null);
        dto.put("dni", c != null ? c.getDni() : null);
        dto.put("address", c != null ? c.getAddress() : null);
        dto.put("addressReference", c != null ? c.getAddressReference() : null);
        // El bucket es privado: la URL se firma aquí, al leer.
        dto.put("profilePicture", s3Service.getPresignedUrl(user.getProfilePicture()));
        return dto;
    }

    static String limpiar(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    static ResponseEntity<Map<String, Object>> error(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("statusCode", status);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    /** Conversión entidad ⇄ JSON del panel (flags como booleanos, redes como objeto). */
    static final class BusinessConfigMapper {
        private BusinessConfigMapper() {}

        static Map<String, Object> toDTO(BusinessConfig c, ObjectMapper mapper,
                                        com.bytemarket.user.service.S3Service s3) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", c.getId());
            dto.put("name", c.getName());
            dto.put("ruc", c.getRuc());
            dto.put("email", c.getEmail());
            // El bucket es privado: el logo se firma aquí, al leer.
            dto.put("logoUrl", s3 == null ? c.getLogoUrl() : s3.getPresignedUrl(c.getLogoUrl()));
            dto.put("whatsapp", c.getWhatsapp());
            dto.put("address", c.getAddress());
            dto.put("plan", c.getPlan());
            Object social = null;
            if (c.getSocialLinks() != null && !c.getSocialLinks().isBlank()) {
                try { social = mapper.readValue(c.getSocialLinks(), Map.class); } catch (Exception ignored) {}
            }
            dto.put("socialLinks", social);
            dto.put("stockEnabled", isOn(c.getStockEnabled()));
            dto.put("autoPaymentEnabled", isOn(c.getAutoPaymentEnabled()));
            dto.put("couponsEnabled", isOn(c.getCouponsEnabled()));
            dto.put("multiuserEnabled", isOn(c.getMultiuserEnabled()));
            dto.put("updatedAt", c.getUpdatedAt());
            return dto;
        }

        static boolean isOn(Integer v) { return v != null && v != 0; }
    }
}
