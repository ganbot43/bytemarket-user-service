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
    /**
     * Qué acción pide esta petición, según el verbo.
     *
     * GET y HEAD consultan, POST da de alta, PUT y PATCH modifican, DELETE da
     * de baja. Sustituye a tener una regla por verbo en el SecurityConfig,
     * que es de donde salían los errores de "esta ruta se la llevó por
     * delante el comodín".
     *
     * Un POST que no crea nada —abrir la caja, cuadrar un turno— cuenta como
     * crear, y es lo correcto: son actos que dejan un registro nuevo, no
     * consultas.
     */
    public static String accion(HttpServletRequest request) {
        /*
         * Inhabilitar una cuenta es dar de baja, no corregir un dato.
         *
         * /users/{id}/status es un PUT, así que por el verbo pedía "editar".
         * Pero quien deja a alguien sin acceso al sistema está haciendo lo
         * mismo que un borrado —la pantalla lo llama "Inhabilitar" y la
         * acción Eliminar se explica como "dar de baja registros"—, y con la
         * regla del verbo bastaba el permiso de corregir un teléfono para
         * dejar al jefe de ventas fuera del panel.
         *
         * Vale para las dos direcciones. Reactivar con el permiso de editar
         * sería incoherente: quien no puede dar de baja tampoco debería poder
         * deshacer la baja que decidió otro.
         */
        if (request.getRequestURI().matches("/api/admin/users/[^/]+/status")) {
            return "delete";
        }
        return switch (request.getMethod()) {
            case "POST" -> "create";
            case "PUT", "PATCH" -> "edit";
            case "DELETE" -> "delete";
            default -> "view";
        };
    }
}
