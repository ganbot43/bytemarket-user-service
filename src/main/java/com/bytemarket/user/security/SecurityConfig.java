package com.bytemarket.user.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * El filtro llega como parámetro del @Bean y no como campo inyectado:
     * una clase @Configuration que además produce beans puede entrar en
     * conflicto de creación si se inyecta por constructor, y de este modo
     * la dependencia queda donde se usa.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthFilter,
                                                   AuthenticationProvider authenticationProvider) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Sonda de salud: la consulta el healthcheck de Docker, que
                // no tiene con qué autenticarse. Solo expone UP o DOWN.
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(
                    "/api/auth/login", 
                    "/api/auth/register", 
                    "/api/_auth/session",
                    "/api/landing/**",
                    "/api/products/**",
                    "/api/categories/**",
                    "/api/subcategories/**",
                    "/api/payment-methods/**",
                    "/api/banners/**",
                    "/api/business-config/**",
                    "/api/media/**",
                    "/api/reviews/**",
                    "/",
                    "/css/**",
                    "/images/**",
                    "/js/**",
                    "/favicon.ico",
                    "/error",
                    "/login",
                    "/registro",
                    "/register",
                    "/productos",
                    "/productos/**",
                    "/contacto",
                    "/nosotros",
                    "/como-comprar",
                    "/metodos-de-pago",
                    "/zonas-de-envio",
                    "/politica-de-devoluciones",
                    "/privacidad",
                    "/terminos",
                    "/libro-de-reclamaciones",
                    "/api/reclamaciones",
                    "/checkout",
                    "/mi-cuenta",
                    "/mi-cuenta/**"
                ).permitAll()
                .requestMatchers("/admin", "/admin/**", "/api/admin/**", "/api/upload/**").hasAnyRole("ADMIN", "SUPERADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                    } else {
                        response.sendRedirect("/login");
                    }
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.sendError(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN, "Forbidden");
                    } else {
                        response.sendRedirect("/");
                    }
                })
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsServiceImpl userDetailsService,
                                                         PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
