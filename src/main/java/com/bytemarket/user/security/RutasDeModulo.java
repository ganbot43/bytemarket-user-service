package com.bytemarket.user.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * A qué módulo del panel pertenece cada ruta de /api/admin de ESTE servicio.
 *
 * Cada servicio tiene su propia copia con sus propias rutas, igual que
 * RabbitConfig está en order y en catalog: compartir la clase ataría los
 * servicios al mismo jar, y las rutas de uno no le sirven de nada a otro.
 *
 * El método decide si hace falta ver o editar: GET y HEAD consultan,
 * cualquier otra cosa escribe. Eso sustituye a tener una regla por verbo en
 * el SecurityConfig, que es de donde salían los errores de "esta ruta se la
 * llevó por delante el comodín".
 *
 * El orden importa: gana la coincidencia más específica, así que las rutas
 * exactas van antes que los prefijos.
 */
public final class RutasDeModulo {

    private RutasDeModulo() {}

    /** Prefijo o ruta exacta → módulo. */
    private record Regla(String patron, boolean exacta, String modulo) {}

    private static final List<Regla> REGLAS = List.of(
            new Regla("/api/admin/users", false, "usuarios"),
            new Regla("/api/admin/business-config", false, "configuracion"),
            new Regla("/api/admin/permissions", false, "roles"));

    /** El módulo de la petición, o null si la ruta no está declarada. */
    public static String modulo(HttpServletRequest request) {
        String path = request.getRequestURI();
        String mejor = null;
        int largo = -1;
        for (Regla r : REGLAS) {
            boolean casa = r.exacta()
                    ? path.equals(r.patron())
                    : path.equals(r.patron()) || path.startsWith(r.patron() + "/");
            if (casa && r.patron().length() > largo) {
                mejor = r.modulo();
                largo = r.patron().length();
            }
        }
        return mejor;
    }

    /** true si la petición escribe, y entonces hace falta el permiso de editar. */
    public static boolean escribe(HttpServletRequest request) {
        String m = request.getMethod();
        return !("GET".equals(m) || "HEAD".equals(m) || "OPTIONS".equals(m));
    }
}
