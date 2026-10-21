package com.bytemarket.user.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * El catálogo de módulos del panel y el orden jerárquico de los roles.
 *
 * Es la única lista de módulos del sistema: la tabla role_permissions guarda
 * claves de aquí, el panel pinta estas etiquetas y los demás servicios
 * traducen sus rutas a estas claves. Añadir un módulo al panel es añadirlo
 * aquí.
 *
 * ── La pirámide ──
 *
 * Gerente > Encargado > Jefe de ventas > Cajero. De ella salen las dos
 * reglas que hacen que una matriz de permisos editable no sea una vía de
 * escalada de privilegios:
 *
 *   1. Cada rol solo edita los permisos de los que están ESTRICTAMENTE por
 *      debajo. Nunca los suyos, nunca los de arriba. Así el encargado
 *      administra a jefe de ventas y cajero sin poder ampliarse a sí mismo.
 *   2. El gerente no aparece en la tabla. Lo puede todo por código, para que
 *      ninguna fila guardada pueda dejarlo fuera de su propio sistema.
 *
 * Con esas dos reglas ningún módulo es peligroso de conceder, y por eso no
 * hay lista negra: la seguridad sale de la estructura y no de acordarse de
 * prohibir cosas una por una. Conceder "Roles y permisos" a un cajero, por
 * ejemplo, no le sirve de nada: debajo de él no hay nadie a quien editar.
 */
public final class PanelModules {

    private PanelModules() {}

    // ── Roles ────────────────────────────────────────────────────

    public static final String CAJERO = "cajero";
    public static final String JEFE_VENTAS = "jefe_ventas";
    public static final String ENCARGADO = "admin";
    public static final String GERENTE = "superadmin";

    /** De abajo arriba: la posición en la lista ES el nivel jerárquico. */
    private static final List<String> PIRAMIDE =
            List.of(CAJERO, JEFE_VENTAS, ENCARGADO, GERENTE);

    /** Los que se guardan en la tabla. El gerente queda fuera por diseño. */
    public static final List<String> ROLES_CONFIGURABLES =
            List.of(CAJERO, JEFE_VENTAS, ENCARGADO);

    /** Cómo se llama cada rol para la gente. */
    public static final Map<String, String> ETIQUETA_ROL = Map.of(
            CAJERO, "Cajero",
            JEFE_VENTAS, "Jefe de ventas",
            ENCARGADO, "Encargado",
            GERENTE, "Gerente");

    // ── Módulos ──────────────────────────────────────────────────

    /** Clave → etiqueta, en el orden en que se muestran. */
    public static final Map<String, String> MODULOS = modulos();

    private static Map<String, String> modulos() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("dashboard", "Dashboard");
        m.put("caja", "Caja");
        m.put("cajas", "Cajas");
        m.put("reportes", "Reportes");
        m.put("pedidos", "Pedidos");
        m.put("productos", "Productos");
        m.put("inventario", "Inventario");
        m.put("categorias", "Categorías");
        m.put("subcategorias", "Subcategorías");
        m.put("banners", "Banners");
        m.put("cupones", "Cupones");
        m.put("resenas", "Reseñas");
        m.put("reclamaciones", "Libro de Reclamaciones");
        m.put("usuarios", "Usuarios");
        m.put("metodos_pago", "Métodos de Pago");
        m.put("configuracion", "Configuración");
        m.put("roles", "Roles y permisos");
        return Map.copyOf(m);
    }

    /** El orden de MODULOS, que Map.copyOf no conserva. */
    public static final List<String> ORDEN_MODULOS = List.of(
            "dashboard", "caja", "cajas", "reportes", "pedidos", "productos",
            "inventario", "categorias", "subcategorias", "banners", "cupones",
            "resenas", "reclamaciones", "usuarios", "metodos_pago",
            "configuracion", "roles");

    // ── Acciones y agrupación ────────────────────────────────────

    /**
     * Las cuatro acciones, en el orden en que se leen, con su explicación.
     *
     * Viajan al panel en vez de estar escritas en la pantalla: la cabecera de
     * la tabla se dibuja desde aquí, así que añadir una acción mañana no pide
     * tocar el front. La ayuda importa porque "Editar" y "Crear" se confunden
     * cuando se reparten de corrido.
     */
    public static final List<Map<String, String>> ACCIONES = List.of(
            Map.of("key", "view", "label", "Ver",
                    "help", "Entrar al módulo y consultarlo"),
            Map.of("key", "create", "label", "Crear",
                    "help", "Dar de alta registros nuevos"),
            Map.of("key", "edit", "label", "Editar",
                    "help", "Modificar lo que ya existe"),
            Map.of("key", "delete", "label", "Eliminar",
                    "help", "Dar de baja registros"));

    /**
     * Cómo se agrupan los módulos en la pantalla.
     *
     * Por el mismo motivo por el que se agrupan en el menú: diecisiete filas
     * seguidas obligan a buscar "Subcategorías" entre "Reseñas" y "Usuarios"
     * cada vez. Agrupados como la navegación —que es la organización que
     * quien administra ya tiene en la cabeza— cada bloque se abre, se
     * resuelve y se cierra.
     */
    public static final List<String> GRUPOS = List.of("Operación", "Tienda", "Sistema");

    private static final Map<String, String> GRUPO_DE_MODULO = Map.ofEntries(
            Map.entry("dashboard", "Operación"),
            Map.entry("caja", "Operación"),
            Map.entry("cajas", "Operación"),
            Map.entry("pedidos", "Operación"),
            Map.entry("reportes", "Operación"),
            Map.entry("inventario", "Operación"),
            Map.entry("productos", "Tienda"),
            Map.entry("categorias", "Tienda"),
            Map.entry("subcategorias", "Tienda"),
            Map.entry("banners", "Tienda"),
            Map.entry("cupones", "Tienda"),
            Map.entry("resenas", "Tienda"),
            Map.entry("reclamaciones", "Tienda"),
            Map.entry("usuarios", "Sistema"),
            Map.entry("roles", "Sistema"),
            Map.entry("metodos_pago", "Sistema"),
            Map.entry("configuracion", "Sistema"));

    public static String grupoDe(String modulo) {
        return GRUPO_DE_MODULO.getOrDefault(modulo, "Sistema");
    }

    // ── Jerarquía ────────────────────────────────────────────────

    /** Nivel en la pirámide; -1 si no es un rol de panel (p. ej. customer). */
    public static int nivel(String role) {
        return PIRAMIDE.indexOf(role);
    }

    public static boolean esGerente(String role) {
        return GERENTE.equals(role);
    }

    /**
     * Regla 1 de la pirámide: solo hacia abajo, y nunca a uno mismo.
     *
     * Lo de "estrictamente" es el punto: con >= en vez de >, un encargado
     * podría reescribir los permisos del encargado —es decir, los suyos— y
     * la jerarquía dejaría de significar nada.
     */
    public static boolean puedeEditar(String rolEditor, String rolDestino) {
        if (!ROLES_CONFIGURABLES.contains(rolDestino)) return false;
        int editor = nivel(rolEditor);
        int destino = nivel(rolDestino);
        return editor > 0 && destino >= 0 && editor > destino;
    }

    /** Los roles estrictamente por debajo de uno, de menor a mayor. */
    public static List<String> pordebajoDe(String role) {
        int n = nivel(role);
        if (n <= 0) return List.of();
        return ROLES_CONFIGURABLES.stream().filter(r -> nivel(r) < n).toList();
    }

    /** Los roles que este editor puede tocar, de menor a mayor. */
    public static List<String> editablesPor(String rolEditor) {
        return ROLES_CONFIGURABLES.stream()
                .filter(r -> puedeEditar(rolEditor, r))
                .toList();
    }
}
