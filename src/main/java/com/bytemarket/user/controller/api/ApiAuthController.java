package com.bytemarket.user.controller.api;

import lombok.RequiredArgsConstructor;
import com.bytemarket.user.model.Customer;
import com.bytemarket.user.model.User;
import com.bytemarket.user.repository.ICustomerRepository;
import com.bytemarket.user.repository.IUserRepository;
import com.bytemarket.user.service.AuthService;
import com.bytemarket.user.security.JwtUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class ApiAuthController {

    private final IUserRepository userRepository;

    private final ICustomerRepository customerRepository;

    private final AuthService authService;

    private final JwtUtils jwtUtils;

    // Endpoint genérico de Login que Nuxt podría usar
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials, jakarta.servlet.http.HttpServletResponse responseServlet) {
        String email = credentials.get("email");
        String password = credentials.get("password");

        Map<String, Object> response = new HashMap<>();

        if (email == null || password == null) {
            response.put("message", "Faltan credenciales");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent() && authService.verifyPassword(password, userOpt.get().getPasswordHash())) {
            User user = userOpt.get();

            if (user.getIsActive() != null && user.getIsActive() == 0) {
                response.put("message", "Tu cuenta ha sido suspendida. Contáctanos si crees que es un error.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            // Generamos el token JWT
            String token = jwtUtils.generateToken(user.getEmail(), user.getId(), user.getRole(), user.getName());

            // Configurar Cookie
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("jwt_token", token);
            cookie.setHttpOnly(true);
            cookie.setSecure(false); // set to true in production with HTTPS
            cookie.setPath("/");
            cookie.setMaxAge(24 * 60 * 60); // 1 día
            responseServlet.addCookie(cookie);

            // Retornamos el objeto usuario y el token para el frontend
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("name", user.getName());
            userData.put("email", user.getEmail());
            userData.put("role", user.getRole());

            response.put("user", userData);
            response.put("token", token);
            response.put("message", "Login exitoso");
            return ResponseEntity.ok(response);
        }

        response.put("message", "Email o contraseña incorrectos");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(jakarta.servlet.http.HttpServletResponse responseServlet) {
        // En JWT el logout se maneja usualmente en el cliente eliminando el token, pero ahora también limpiamos la cookie
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("jwt_token", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        responseServlet.addCookie(cookie);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Sesión cerrada exitosamente");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(java.security.Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<User> userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userOpt.get();
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("name", user.getName());
        userData.put("email", user.getEmail());
        userData.put("role", user.getRole());

        Map<String, Object> response = new HashMap<>();
        response.put("user", userData);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> data) {
        String name = data.get("name");
        String email = data.get("email");
        String password = data.get("password");
        String phone = data.get("phone");
        String dni = data.get("dni");

        Map<String, Object> response = new HashMap<>();
        
        // Validaciones básicas
        if (name == null || name.trim().isEmpty() || 
            email == null || email.trim().isEmpty() || 
            password == null || password.trim().isEmpty()) {
            response.put("message", "Todos los campos son obligatorios");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            response.put("message", "Formato de correo inválido");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (password.length() < 6) {
            response.put("message", "La contraseña debe tener al menos 6 caracteres");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        
        if (userRepository.findByEmail(email).isPresent()) {
            response.put("message", "El correo ya está registrado");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (dni != null && !dni.trim().isEmpty() && customerRepository.existsByDni(dni.trim())) {
            response.put("message", "El DNI ya está registrado");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // 1) La cuenta: solo credenciales y rol
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(authService.hashPassword(password));
        user.setRole("customer"); // Por defecto para nuevos registros
        userRepository.save(user);

        // 2) El cliente: datos personales, 1:1 con la cuenta recién creada
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setPhone(phone != null && !phone.trim().isEmpty() ? phone.trim() : null);
        customer.setDni(dni != null && !dni.trim().isEmpty() ? dni.trim() : null);
        customerRepository.save(customer);

        response.put("message", "Usuario registrado con éxito");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
