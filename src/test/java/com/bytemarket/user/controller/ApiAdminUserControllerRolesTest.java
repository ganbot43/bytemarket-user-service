package com.bytemarket.user.controller;

import com.bytemarket.user.controller.api.ApiAdminUserController;
import com.bytemarket.user.model.User;
import com.bytemarket.user.repository.IBusinessConfigRepository;
import com.bytemarket.user.repository.ICustomerRepository;
import com.bytemarket.user.repository.IUserRepository;
import com.bytemarket.user.service.AuthService;
import com.bytemarket.user.service.S3Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Quién puede repartir qué puesto.
 *
 * Esto no es una comodidad de la pantalla. La pantalla ya filtra las
 * opciones que cada quien puede asignar, pero una pantalla no es un
 * control: basta una llamada a la API con role=superadmin. Lo que se
 * comprueba aquí es la puerta de verdad.
 */
@DisplayName("Usuarios — quién reparte qué puesto")
class ApiAdminUserControllerRolesTest {

    private IUserRepository userRepository;
    private ICustomerRepository customerRepository;
    private ApiAdminUserController controlador;

    private static final String ENCARGADO = "encargado@bytemarket.com";
    private static final String GERENTE = "gerente@bytemarket.com";

    @BeforeEach
    void setUp() {
        userRepository = mock(IUserRepository.class);
        customerRepository = mock(ICustomerRepository.class);
        AuthService authService = mock(AuthService.class);
        S3Service s3Service = mock(S3Service.class);

        when(authService.hashPassword(any())).thenReturn("hash");
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(customerRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        controlador = new ApiAdminUserController(userRepository, customerRepository,
                mock(IBusinessConfigRepository.class), authService, s3Service);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    /** Deja el contexto como lo dejaría el filtro del JWT. */
    private void entraComo(String correo, String rol) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(correo, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()))));
    }

    private User usuario(int id, String correo, String rol) {
        User u = new User();
        u.setId(id);
        u.setEmail(correo);
        u.setName(rol);
        u.setRole(rol);
        u.setIsActive(1);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private static int estado(ResponseEntity<?> r) {
        return r.getStatusCode().value();
    }

    private static String mensaje(ResponseEntity<?> r) {
        Object body = r.getBody();
        return body instanceof Map<?, ?> m ? String.valueOf(m.get("message")) : "";
    }

    private Map<String, Object> alta(String rol) {
        return Map.of("name", "Nuevo", "email", "nuevo@bytemarket.com",
                "password", "clave123", "role", rol);
    }

    // ── Fabricar un gerente ─────────────────────────────────────

    @Test
    @DisplayName("el encargado no puede crear un gerente")
    void elEncargadoNoFabricaGerentes() {
        entraComo(ENCARGADO, "admin");

        ResponseEntity<?> r = controlador.createUser(alta("superadmin"));

        assertEquals(403, estado(r), mensaje(r));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("el gerente sí puede crear otro gerente")
    void elGerenteSiReparteSuPuesto() {
        entraComo(GERENTE, "superadmin");
        assertEquals(200, estado(controlador.createUser(alta("superadmin"))));
    }

    @Test
    @DisplayName("el encargado sigue dando de alta a su gente")
    void elEncargadoDaDeAltaNormal() {
        entraComo(ENCARGADO, "admin");
        assertEquals(200, estado(controlador.createUser(alta("cajero"))));
        assertEquals(200, estado(controlador.createUser(alta("jefe_ventas"))));
    }

    // ── Ascenderse solo ─────────────────────────────────────────

    @Test
    @DisplayName("el encargado no se asciende a gerente")
    void nadieSeAsciendeSolo() {
        entraComo(ENCARGADO, "admin");
        usuario(5, ENCARGADO, "admin");

        ResponseEntity<?> r = controlador.updateUser(5, Map.of("role", "superadmin"));

        assertEquals(403, estado(r));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("tampoco asciende a un tercero")
    void niAsciendeAOtro() {
        entraComo(ENCARGADO, "admin");
        usuario(6, "otro@bytemarket.com", "jefe_ventas");

        assertEquals(403, estado(controlador.updateUser(6, Map.of("role", "superadmin"))));
    }

    @Test
    @DisplayName("nadie se cambia su propio rol, ni hacia abajo")
    void niSiquieraHaciaAbajo() {
        entraComo(GERENTE, "superadmin");
        usuario(1, GERENTE, "superadmin");

        // Si el único gerente se degrada, el sistema se queda sin nadie que
        // pueda repartir el puesto otra vez.
        ResponseEntity<?> r = controlador.updateUser(1, Map.of("role", "admin"));

        assertEquals(403, estado(r));
        assertTrue(mensaje(r).contains("propia"), mensaje(r));
    }

    // ── Tocar al gerente que ya existe ──────────────────────────

    @Test
    @DisplayName("el encargado no degrada al gerente")
    void noDegradaAlGerente() {
        entraComo(ENCARGADO, "admin");
        usuario(1, GERENTE, "superadmin");

        // Era la vuelta de la escalada: sin poder ascenderse, bastaba con
        // bajar al que manda y quedarse como la única autoridad.
        assertEquals(403, estado(controlador.updateUser(1, Map.of("role", "customer"))));
    }

    @Test
    @DisplayName("el encargado no le cambia el correo al gerente")
    void noSeQuedaConSuCuenta() {
        entraComo(ENCARGADO, "admin");
        usuario(1, GERENTE, "superadmin");

        // Cambiarle el correo es quedarse con la cuenta por la puerta del
        // "he olvidado mi contraseña".
        assertEquals(403, estado(controlador.updateUser(1, Map.of("email", ENCARGADO))));
    }

    @Test
    @DisplayName("el encargado no desactiva al gerente")
    void noApagaAlGerente() {
        entraComo(ENCARGADO, "admin");
        usuario(1, GERENTE, "superadmin");

        assertEquals(403, estado(controlador.updateStatus(1, Map.of("isActive", 0))));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("el encargado no borra al gerente")
    void noBorraAlGerente() {
        entraComo(ENCARGADO, "admin");
        usuario(1, GERENTE, "superadmin");

        assertEquals(403, estado(controlador.deleteUser(1)));
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("nadie se borra ni se desactiva a sí mismo")
    void nadieSeApagaSolo() {
        entraComo(GERENTE, "superadmin");
        usuario(1, GERENTE, "superadmin");

        assertEquals(403, estado(controlador.deleteUser(1)));
        assertEquals(403, estado(controlador.updateStatus(1, Map.of("isActive", 0))));
        verify(userRepository, never()).deleteById(any());
    }

    // ── Lo que el encargado sí sigue pudiendo ───────────────────

    @Test
    @DisplayName("el encargado administra a todos menos al gerente")
    void loDemasSigueIgual() {
        entraComo(ENCARGADO, "admin");
        usuario(7, "cajero@bytemarket.com", "cajero");

        assertEquals(200, estado(controlador.updateUser(7, Map.of("role", "jefe_ventas"))));
        assertEquals(200, estado(controlador.updateStatus(7, Map.of("isActive", 0))));
        assertEquals(200, estado(controlador.deleteUser(7)));
    }

    // ── Y el jefe de ventas, donde ya estaba ────────────────────

    @Test
    @DisplayName("el jefe de ventas sigue limitado a cajeros")
    void elJefeSigueEnLoSuyo() {
        entraComo("jefe@bytemarket.com", "jefe_ventas");
        usuario(1, GERENTE, "superadmin");

        ResponseEntity<?> r = controlador.deleteUser(1);
        assertEquals(403, estado(r));
        // El suyo es el mensaje de jefe, no el de gerente: la primera regla
        // que casa es la que explica.
        assertTrue(mensaje(r).contains("jefe de ventas"), mensaje(r));
    }
}
