package com.bytemarket.user.controller.api;

import com.bytemarket.user.model.Customer;
import com.bytemarket.user.model.User;
import com.bytemarket.user.repository.ICustomerRepository;
import com.bytemarket.user.repository.IUserRepository;
import com.bytemarket.user.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class ApiProfileController {

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private ICustomerRepository customerRepository;

    @Autowired
    private S3Service s3Service;

    @PostMapping("/profile")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam(value = "dni", required = false) String dni,
            @RequestParam(value = "profilePicture", required = false) MultipartFile profilePicture,
            Principal principal) {
        
        Map<String, Object> response = new HashMap<>();

        if (principal == null) {
            response.put("error", "No autenticado");
            return ResponseEntity.status(401).body(response);
        }

        Optional<User> userOpt = userRepository.findByEmail(principal.getName());
        if (!userOpt.isPresent()) {
            response.put("error", "Usuario no encontrado");
            return ResponseEntity.status(404).body(response);
        }

        User user = userOpt.get();

        Customer customer = null;
        boolean tocaFicha = phone != null || address != null || dni != null;
        String nuevoDni = null;

        if (tocaFicha) {
            customer = customerRepository.findByUserId(user.getId()).orElse(null);
            if (dni != null) {
                nuevoDni = dni.trim().isEmpty() ? null : dni.trim();
                boolean cambia = nuevoDni != null
                        && (customer == null || !nuevoDni.equals(customer.getDni()));
                if (cambia && customerRepository.existsByDni(nuevoDni)) {
                    response.put("error", "El DNI ya está registrado por otro cliente");
                    return ResponseEntity.status(400).body(response);
                }
            }
        }

        String nuevaFoto = null;
        if (profilePicture != null && !profilePicture.isEmpty()) {
            try {
                nuevaFoto = s3Service.uploadFile(profilePicture, "profiles");
            } catch (Exception e) {
                e.printStackTrace();
                response.put("error", "Error subiendo la imagen");
                return ResponseEntity.status(500).body(response);
            }
        }

        if (name != null && !name.trim().isEmpty()) {
            user.setName(name.trim());
        }
        if (nuevaFoto != null) {
            user.setProfilePicture(nuevaFoto);
        }
        userRepository.save(user);

        if (tocaFicha) {
            if (customer == null) {
                customer = new Customer();
                customer.setUser(user);
            }
            if (phone != null) {
                customer.setPhone(phone.trim().isEmpty() ? null : phone.trim());
            }
            if (address != null) {
                customer.setAddress(address.trim().isEmpty() ? null : address.trim());
            }
            if (dni != null) {
                customer.setDni(nuevoDni);
            }
            customerRepository.save(customer);
        }

        response.put("message", "Perfil actualizado correctamente");
        return ResponseEntity.ok(response);
    }
}
