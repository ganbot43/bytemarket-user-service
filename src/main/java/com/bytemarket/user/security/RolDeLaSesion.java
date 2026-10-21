package com.bytemarket.user.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * Qué puesto de panel trae la sesión que está haciendo la petición.
 *
 * Existe porque estaba escrito dos veces: el AuthorizationManager lo deducía
 * para decidir si dejar pasar, y ApiAdminUserController lo deducía otra vez
 * para decidir a quién puede administrar. Las dos copias llevaban su propia
 * lista de puestos, así que añadir uno nuevo arreglaba la mitad del sistema
 * y dejaba la otra mitad decidiendo con la lista vieja — sin error, sin
 * aviso, y con la puerta abierta o cerrada de más según cuál se consultara.
 *
 * Es la razón de que ahora los dos pregunten aquí y de que la lista de
 * puestos viva solo en PanelModules.
 */
public final class RolDeLaSesion {

    private RolDeLaSesion() {}

    /**
     * El puesto más alto que traiga la sesión, o null si no es del panel.
     *
     * Se recorre de mayor a menor y se devuelve el primero que encaja: si una
     * sesión trajera dos roles, quedarse con el más alto es lo único
     * coherente con una jerarquía.
     */
    public static String actual() {
        return deAuth(SecurityContextHolder.getContext().getAuthentication());
    }

    /** Igual, pero sobre una autenticación concreta (la que recibe el manager). */
    public static String deAuth(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        List<String> authorities = auth.getAuthorities().stream().map(Object::toString).toList();
        for (String rol : PanelModules.DE_MAYOR_A_MENOR) {
            if (authorities.contains("ROLE_" + rol.toUpperCase())) return rol;
        }
        return null;
    }

    /** Si la sesión trae una autoridad concreta, sea o no de la pirámide. */
    public static boolean tieneRol(String rol) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().map(Object::toString)
                .anyMatch(("ROLE_" + rol)::equals);
    }
}
