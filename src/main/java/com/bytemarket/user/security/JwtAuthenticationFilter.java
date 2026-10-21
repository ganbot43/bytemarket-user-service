package com.bytemarket.user.security;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        String jwt = null;
        final String userEmail;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        } else {
            // Check if cookie exists
            if (request.getCookies() != null) {
                for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                    if ("jwt_token".equals(cookie.getName())) {
                        jwt = cookie.getValue();
                        break;
                    }
                }
            }
        }

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            userEmail = jwtUtils.extractUsername(jwt);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        /*
         * Token de servicio, no de persona.
         *
         * Lo firman los otros microservicios para leer la matriz de permisos,
         * con el mismo secreto compartido y rol SERVICE (ver
         * ServiceTokenProvider en order-service). Su subject es el nombre del
         * servicio y no un correo, así que buscarlo en la tabla de usuarios no
         * encontraría nada y la llamada moriría en un 403 sin explicación.
         *
         * Se autentica desde el claim, sin tocar la base. La firma ya quedó
         * verificada arriba: extractUsername parsea el token con la clave, y
         * un token manipulado habría salido por el catch.
         */
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null
                && esTokenDeServicio(jwt)) {
            var authToken = new UsernamePasswordAuthenticationToken(
                    userEmail, null,
                    java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SERVICE")));
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
            filterChain.doFilter(request, response);
            return;
        }

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                
                // Un token emitido antes de la suspension sigue siendo criptograficamente
                // valido: hay que comprobar el estado de la cuenta en cada peticion.
                if (!userDetails.isEnabled()) {
                    System.out.println("Token de cuenta suspendida: " + userEmail + ". Ignorando token.");
                    filterChain.doFilter(request, response);
                    return;
                }

                if (jwtUtils.validateToken(jwt, userDetails.getUsername())) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
                // If user is not found (e.g. old cookie from a different database), ignore token
                System.out.println("Token contains unknown user: " + userEmail + ". Ignoring token.");
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean esTokenDeServicio(String jwt) {
        try {
            return "SERVICE".equals(jwtUtils.extractClaim(jwt, c -> c.get("role", String.class)));
        } catch (Exception e) {
            return false;
        }
    }
}
