package com.bytemarket.user.controller.api;

import com.bytemarket.user.service.S3Service;
import lombok.RequiredArgsConstructor;
import com.bytemarket.user.model.BusinessConfig;
import com.bytemarket.user.model.Customer;
import com.bytemarket.user.model.User;
import com.bytemarket.user.repository.IBusinessConfigRepository;
import com.bytemarket.user.repository.ICustomerRepository;
import com.bytemarket.user.repository.IUserRepository;
import com.bytemarket.user.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.bytemarket.user.controller.api.ApiAccountController.error;
import static com.bytemarket.user.controller.api.ApiAccountController.limpiar;

/**
 * Panel admin: configuración del negocio y gestión de usuarios/clientes.
 * Protegido por SecurityConfig (/api/admin/** → ADMIN / SUPERADMIN).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class ApiAdminUserController {

    /**
     * Los roles del sistema, de menos a más alcance.
     *
     *   cajero       vende y atiende; no cuadra ni cambia nada
     *   jefe_ventas  da fondos, arquea, cuadra y habilita cajeros
     *   admin        encargado de tienda: productos, pedidos, inventario
     *   superadmin   gerente: todo, incluida la configuración
     *
     * Están separados porque el control interno de una caja lo exige: quien
     * vende no cuadra, y quien cuadra no cambia precios. Con un solo rol de
     * panel, un faltante no se puede atribuir a nadie.
     */
    private static final Set<String> ROLES_VALIDOS =
            Set.of("customer", "cajero", "jefe_ventas", "admin", "superadmin");

    private final IUserRepository userRepository;
    private final ICustomerRepository customerRepository;
    private final IBusinessConfigRepository businessConfigRepository;
    private final AuthService authService;
    private final S3Service s3Service;

    private final ObjectMapper mapper = new ObjectMapper();

    // ─── CONFIGURACIÓN ──────────────────────────────────────────

    @GetMapping("/business-config")
    public ResponseEntity<?> getConfig() {
        BusinessConfig c = businessConfigRepository.findAll().stream().findFirst().orElse(null);
        if (c == null) return error(404, "Configuración no encontrada");
        return ResponseEntity.ok(ApiAccountController.BusinessConfigMapper.toDTO(c, mapper, s3Service));
    }

    @PutMapping("/business-config")
    public ResponseEntity<?> updateConfig(@RequestBody Map<String, Object> body) {
        BusinessConfig c = businessConfigRepository.findAll().stream().findFirst().orElseGet(BusinessConfig::new);

        if (body.containsKey("name")) {
            String name = limpiar(body.get("name"));
            if (name == null || name.length() < 2) return error(400, "El nombre debe tener al menos 2 caracteres");
            c.setName(name);
        }
        if (body.containsKey("ruc")) c.setRuc(limpiar(body.get("ruc")));
        if (body.containsKey("email")) c.setEmail(limpiar(body.get("email")));
        // Sin firma: el panel reenvía la URL firmada que recibió al leer, y
        // guardarla metería en base una URL que caduca en dos horas.
        if (body.containsKey("logoUrl")) c.setLogoUrl(s3Service.normalizeUrl(limpiar(body.get("logoUrl"))));
        if (body.containsKey("whatsapp")) c.setWhatsapp(Optional.ofNullable(limpiar(body.get("whatsapp"))).orElse(""));
        if (body.containsKey("address")) c.setAddress(limpiar(body.get("address")));
        if (body.get("socialLinks") instanceof Map<?, ?> social) {
            try { c.setSocialLinks(mapper.writeValueAsString(social)); } catch (Exception ignored) {}
        }
        if (body.containsKey("stockEnabled")) c.setStockEnabled(flag(body.get("stockEnabled")));
        if (body.containsKey("autoPaymentEnabled")) c.setAutoPaymentEnabled(flag(body.get("autoPaymentEnabled")));
        if (body.containsKey("couponsEnabled")) c.setCouponsEnabled(flag(body.get("couponsEnabled")));
        if (body.containsKey("multiuserEnabled")) c.setMultiuserEnabled(flag(body.get("multiuserEnabled")));
        if (c.getName() == null) c.setName("ByteMarket");
        if (c.getWhatsapp() == null) c.setWhatsapp("");

        BusinessConfig saved = businessConfigRepository.save(c);
        return ResponseEntity.ok(ApiAccountController.BusinessConfigMapper.toDTO(saved, mapper, s3Service));
    }

    // ─── USUARIOS / CLIENTES ────────────────────────────────────

    /**
     * Listado de usuarios del panel.
     *
     * `role` admite varios separados por coma ("admin,superadmin") para que
     * la pantalla de personal los vea en una sola lista paginada. Sigue
     * devolviendo clientes si no se pide nada, que es lo que hacía antes.
     */
    @GetMapping("/users")
    public ResponseEntity<?> getUsers(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size,
                                      @RequestParam(defaultValue = "customer") String role,
                                      @RequestParam(required = false) String q) {
        /* El jefe de ventas solo ve cajeros, aunque pida otra cosa en el
           parámetro: si no, desde la misma pantalla en la que da de alta a
           su gente podría listar los correos de la dirección. */
        if (soloPuedeCajeros()) role = "cajero";

        List<String> roles = Arrays.stream(role.split(","))
                .map(String::trim).filter(r -> !r.isBlank())
                .filter(ROLES_VALIDOS::contains)
                .toList();
        if (roles.isEmpty()) return error(400, "Rol inválido: " + role);

        String patron = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        Page<User> users = userRepository.buscarPorRoles(roles, patron,
                PageRequest.of(page, Math.max(1, size), Sort.by("createdAt").descending()));

        List<Integer> ids = users.getContent().stream().map(User::getId).toList();
        Map<Integer, Customer> fichas = ids.isEmpty() ? Map.of()
                : customerRepository.findByUserIdIn(ids).stream()
                    .collect(Collectors.toMap(c -> c.getUser().getId(), c -> c));
        List<Map<String, Object>> data = users.getContent().stream()
                .map(u -> toClienteDTO(u, fichas.get(u.getId()))).toList();
        return ResponseEntity.ok(Map.of("data", data, "total", users.getTotalElements()));
    }

    /**
     * Si quien llama es jefe de ventas, solo puede tocar cajeros.
     *
     * El permiso de la ruta le deja entrar a la gestión de usuarios —tiene
     * que poder habilitar a su gente— pero no a crearse un admin ni a
     * desactivar al gerente. Sin esto, "habilita cajeros" sería en la
     * práctica "habilita lo que quiera", y la separación de roles que
     * justifica todo esto se vendría abajo.
     */
    private static boolean soloPuedeCajeros() {
        return tieneRol("JEFE_VENTAS") && !tieneRol("ADMIN") && !tieneRol("SUPERADMIN");
    }

    private static ResponseEntity<Map<String, Object>> soloCajeros() {
        return ResponseEntity.status(403).body(Map.of("statusCode", 403,
                "message", "Como jefe de ventas solo puedes gestionar cajeros."));
    }

    private static boolean tieneRol(String rol) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().map(Object::toString).anyMatch(("ROLE_" + rol)::equals);
    }

    /**
     * El puesto de gerente solo lo reparte un gerente.
     *
     * Faltaba, y era una escalada en toda regla: el encargado podía crearse
     * un gerente, ascenderse él mismo a gerente, o —peor— degradar o borrar
     * al gerente que ya había y quedarse como la única autoridad del
     * sistema. La pantalla no le ofrecía la opción, pero la pantalla no es
     * el control: una llamada a la API con role=superadmin bastaba.
     *
     * Cubre las cuatro puertas —crear, editar, activar y borrar— y mira
     * tanto el rol que se quiere poner como el que la persona YA tiene: lo
     * primero impide fabricar un gerente, lo segundo tocar al que existe.
     */
    private static boolean esGerente() {
        return tieneRol("SUPERADMIN");
    }

    private static ResponseEntity<Map<String, Object>> soloElGerente() {
        return ResponseEntity.status(403).body(Map.of("statusCode", 403,
                "message", "Solo el gerente puede crear, modificar o dar de baja a otro gerente."));
    }

    /** El correo de quien llama: es como lo identifica el token. */
    private static String quienLlama() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    private static boolean esElMismo(User user) {
        String yo = quienLlama();
        return yo != null && yo.equalsIgnoreCase(user.getEmail());
    }

    /**
     * Nadie se cambia su propio rol, ni se desactiva, ni se borra.
     *
     * Hacia arriba sería ascenderse —ya lo impide {@link #esGerente()}— y
     * hacia abajo es quedarse fuera del panel sin que nadie pueda
     * devolverte: si el único gerente se degrada, el sistema se queda sin
     * quien reparta el puesto. La pantalla ya lo evitaba, pero solo en la
     * pantalla.
     */
    private static ResponseEntity<Map<String, Object>> contraTiMismo(String que) {
        return ResponseEntity.status(403).body(Map.of("statusCode", 403,
                "message", "No puedes " + que + " tu propia cuenta. Pídeselo a otro gerente."));
    }

    @PostMapping("/users")
    @Transactional
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) {
        String name = limpiar(body.get("name"));
        String email = limpiar(body.get("email"));
        String password = body.get("password") == null ? null : String.valueOf(body.get("password"));
        String role = Optional.ofNullable(limpiar(body.get("role"))).orElse("customer");
        String dni = limpiar(body.get("dni"));

        if (name == null || email == null || password == null || password.isBlank()) {
            return error(400, "Nombre, correo y contraseña son obligatorios");
        }
        if (password.length() < 6) return error(400, "La contraseña debe tener al menos 6 caracteres");
        if (!ROLES_VALIDOS.contains(role)) return error(400, "Rol inválido: " + role);
        if (soloPuedeCajeros() && !"cajero".equals(role)) return soloCajeros();
        if ("superadmin".equals(role) && !esGerente()) return soloElGerente();
        if (userRepository.findByEmail(email).isPresent()) return error(400, "El correo ya está registrado");
        if (dni != null && customerRepository.existsByDni(dni)) return error(400, "El DNI ya está registrado");

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(authService.hashPassword(password));
        user.setRole(role);
        if (!"customer".equals(role)) user.setDni(dni);
        if (body.get("isActive") instanceof Number n) user.setIsActive(n.intValue());
        userRepository.save(user);

        Customer ficha = null;
        if ("customer".equals(role)) {
            ficha = new Customer();
            ficha.setUser(user);
            ficha.setPhone(limpiar(body.get("phone")));
            ficha.setAddress(limpiar(body.get("address")));
            ficha.setDni(dni);
            ficha = customerRepository.save(ficha);
        }
        return ResponseEntity.ok(toClienteDTO(user, ficha));
    }

    @PutMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> updateUser(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Optional<User> found = userRepository.findById(id);
        if (found.isEmpty()) return error(404, "Usuario no encontrado");
        User user = found.get();

        String email = limpiar(body.get("email"));
        boolean cambiaEmail = email != null && !email.equals(user.getEmail());
        if (cambiaEmail && userRepository.findByEmail(email).isPresent()) return error(400, "El correo ya está registrado");

        String role = limpiar(body.get("role"));
        if (role != null && !ROLES_VALIDOS.contains(role)) return error(400, "Rol inválido: " + role);
        // Se mira el rol QUE TIENE y el que se le quiere poner: lo primero
        // impide editar a un admin, lo segundo ascender a un cajero.
        if (soloPuedeCajeros()
                && (!"cajero".equals(user.getRole()) || (role != null && !"cajero".equals(role)))) {
            return soloCajeros();
        }
        // Mismo criterio con el gerente: el rol que tiene y el que se le
        // quiere poner. Sin la primera mitad, un encargado degradaba al
        // gerente a cliente y se quedaba mandando él.
        if (!esGerente() && ("superadmin".equals(user.getRole()) || "superadmin".equals(role))) {
            return soloElGerente();
        }
        if (role != null && !role.equals(user.getRole()) && esElMismo(user)) {
            return contraTiMismo("cambiarle el rol a");
        }

        Customer ficha = customerRepository.findByUserId(user.getId()).orElse(null);
        String dni = null;
        if (body.containsKey("dni")) {
            dni = limpiar(body.get("dni"));
            boolean cambiaDni = dni != null && (ficha == null || !dni.equals(ficha.getDni()));
            if (cambiaDni && customerRepository.existsByDni(dni)) return error(400, "El DNI ya está registrado");
        }

        String name = limpiar(body.get("name"));
        if (name != null) user.setName(name);
        if (cambiaEmail) user.setEmail(email);
        if (role != null) user.setRole(role);
        if (body.containsKey("dni") && !"customer".equals(user.getRole())) user.setDni(dni);
        if (body.get("isActive") instanceof Number n) user.setIsActive(n.intValue());
        userRepository.save(user);

        if (!"customer".equals(user.getRole())) {
            if (ficha != null) { customerRepository.delete(ficha); ficha = null; }
        } else {
            if (ficha == null) { ficha = new Customer(); ficha.setUser(user); }
            if (body.containsKey("phone")) ficha.setPhone(limpiar(body.get("phone")));
            if (body.containsKey("address")) ficha.setAddress(limpiar(body.get("address")));
            if (body.containsKey("dni")) ficha.setDni(dni);
            ficha = customerRepository.save(ficha);
        }
        return ResponseEntity.ok(toClienteDTO(user, ficha));
    }

    @RequestMapping(value = "/users/{id}/status", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<?> updateStatus(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        // Esta ruta faltaba, y era la puerta de atrás: guardar crear, editar
        // y borrar no sirve de nada si activar y desactivar queda abierto.
        // Desactivar a alguien es dejarlo fuera igual que borrarlo, así que
        // lleva exactamente los mismos permisos que las otras tres.
        Optional<User> found = userRepository.findById(id);
        if (found.isEmpty()) return error(404, "Usuario no encontrado");
        User user = found.get();

        if (soloPuedeCajeros() && !"cajero".equals(user.getRole())) return soloCajeros();
        if (!esGerente() && "superadmin".equals(user.getRole())) return soloElGerente();
        if (esElMismo(user)) return contraTiMismo("desactivar");

        if (body.get("isActive") instanceof Number n) {
            user.setIsActive(n.intValue());
            userRepository.save(user);
        }
        return ResponseEntity.ok(toClienteDTO(user, customerRepository.findByUserId(user.getId()).orElse(null)));
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Integer id) {
        Optional<User> found = userRepository.findById(id);
        if (found.isEmpty()) return error(404, "Usuario no encontrado");
        User user = found.get();

        // Sin esto, el jefe podría borrar al gerente desde la misma pantalla
        // en la que da de alta a un cajero.
        if (soloPuedeCajeros() && !"cajero".equals(user.getRole())) return soloCajeros();
        if (!esGerente() && "superadmin".equals(user.getRole())) return soloElGerente();
        // Y el guardián del último gerente: como nadie puede borrarse a sí
        // mismo, siempre queda al menos uno con quien arreglar lo demás.
        if (esElMismo(user)) return contraTiMismo("borrar");

        customerRepository.deleteByUserId(id);
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private Map<String, Object> toClienteDTO(User user, Customer c) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", user.getId());
        dto.put("name", user.getName());
        dto.put("email", user.getEmail());
        dto.put("role", user.getRole());
        // Booleano, como declara types/index.ts del frontend: en base es 0/1.
        dto.put("isActive", user.getIsActive() == null || user.getIsActive() == 1);
        dto.put("createdAt", user.getCreatedAt());
        dto.put("profilePicture", s3Service.getPresignedUrl(user.getProfilePicture()));
        dto.put("customerId", c != null ? c.getId() : null);
        // La ficha manda si existe —es donde está la unicidad— y si no,
        // el del propio usuario, que es lo que tiene la gente del panel.
        dto.put("dni", c != null && c.getDni() != null ? c.getDni() : user.getDni());
        dto.put("phone", c != null ? c.getPhone() : null);
        dto.put("address", c != null ? c.getAddress() : null);
        dto.put("addressReference", c != null ? c.getAddressReference() : null);
        return dto;
    }

    private static Integer flag(Object v) {
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof Number n) return n.intValue() != 0 ? 1 : 0;
        return "true".equalsIgnoreCase(String.valueOf(v)) ? 1 : 0;
    }
}
