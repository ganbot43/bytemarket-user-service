package com.bytemarket.user.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * El catálogo de módulos del panel y el orden jerárquico de los puestos.
 *
 * ── Una sola declaración ──
 *
 * Cada módulo y cada puesto se declaran UNA vez, y todo lo demás —las
 * etiquetas, el orden, los grupos, la pirámide— se deriva de ahí. Antes eran
 * tres listas paralelas de las mismas diecisiete claves escritas a mano
 * (MODULOS, ORDEN_MODULOS y GRUPO_DE_MODULO) y nada impedía que se
 * separaran: añadir un módulo a dos de ellas y olvidar la tercera lo dejaba
 * sin grupo, o sin sembrar, o rechazado al guardar, sin un solo error que
 * lo dijera.
 *
 * Es la razón de que añadir un módulo sea hoy una línea y no cuatro.
 *
 * ── La pirámide ──
 *
 * Gerente > Encargado > Jefe de ventas > Cajero, y el nivel sale de la
 * posición en la declaración. De ella salen las reglas que hacen que una
 * matriz de permisos editable no sea una vía de escalada de privilegios:
 *
 *   1. Cada rol solo edita los permisos de los que están ESTRICTAMENTE por
 *      debajo. Nunca los suyos, nunca los de arriba.
 *   2. El gerente no aparece en la tabla: lo puede todo por código, para que
 *      ninguna fila guardada pueda dejarlo fuera de su propio sistema.
 *   3. Nadie delega lo que no tiene (en PermissionService).
 *   4. Quitar un permiso cascadea hacia abajo (en PermissionService).
 *
 * Con esas reglas ningún módulo es peligroso de conceder y no hace falta
 * lista negra: dar "Roles y permisos" a un cajero no le sirve de nada,
 * porque debajo de él no hay nadie. La seguridad sale de la estructura, no
 * de acordarse de prohibir cosas una por una.
 */
public final class PanelModules {

    private PanelModules() {}

    // ── Puestos ──────────────────────────────────────────────────

    /** Un puesto del panel. El nivel es su posición en la pirámide. */
    public record Puesto(String clave, String etiqueta, int nivel, boolean configurable) {}

    public static final String CAJERO = "cajero";
    public static final String JEFE_VENTAS = "jefe_ventas";
    public static final String ENCARGADO = "admin";
    public static final String GERENTE = "superadmin";

    /**
     * La única declaración de puestos, de abajo arriba.
     *
     * El gerente está aquí para que `nivel()` sepa que está por encima de
     * todos, pero marcado como NO configurable: eso es lo que lo mantiene
     * fuera de la tabla de permisos.
     */
    private static final List<Puesto> PUESTOS = List.of(
            new Puesto(CAJERO, "Cajero", 0, true),
            new Puesto(JEFE_VENTAS, "Jefe de ventas", 1, true),
            new Puesto(ENCARGADO, "Encargado", 2, true),
            new Puesto(GERENTE, "Gerente", 3, false));

    /** Los que se guardan en la tabla. El gerente queda fuera por diseño. */
    public static final List<String> ROLES_CONFIGURABLES = PUESTOS.stream()
            .filter(Puesto::configurable).map(Puesto::clave).toList();

    /** De mayor a menor: para quedarse con el rol más alto de una sesión. */
    public static final List<String> DE_MAYOR_A_MENOR = PUESTOS.stream()
            .sorted((a, b) -> b.nivel() - a.nivel()).map(Puesto::clave).toList();

    public static final Map<String, String> ETIQUETA_ROL = PUESTOS.stream()
            .collect(LinkedHashMap::new, (m, p) -> m.put(p.clave(), p.etiqueta()), Map::putAll);

    // ── Módulos ──────────────────────────────────────────────────

    /** Un módulo del panel: su clave, cómo se llama y en qué grupo se lee. */
    public record Modulo(String clave, String etiqueta, String grupo) {}

    /**
     * La única declaración de módulos, en el orden en que se muestran.
     *
     * Los grupos son los del menú, y por el mismo motivo: diecisiete filas
     * seguidas obligan a buscar "Subcategorías" entre "Reseñas" y "Usuarios"
     * cada vez. Agrupados como la navegación —que es la organización que
     * quien administra ya tiene en la cabeza— cada bloque se abre, se
     * resuelve y se cierra.
     */
    private static final List<Modulo> CATALOGO = List.of(
            new Modulo("dashboard", "Dashboard", "Operación"),
            new Modulo("caja", "Caja", "Operación"),
            new Modulo("cajas", "Cajas", "Operación"),
            new Modulo("reportes", "Reportes", "Operación"),
            new Modulo("pedidos", "Pedidos", "Operación"),
            new Modulo("inventario", "Inventario", "Operación"),
            new Modulo("productos", "Productos", "Tienda"),
            new Modulo("categorias", "Categorías", "Tienda"),
            new Modulo("subcategorias", "Subcategorías", "Tienda"),
            new Modulo("banners", "Banners", "Tienda"),
            new Modulo("cupones", "Cupones", "Tienda"),
            new Modulo("resenas", "Reseñas", "Tienda"),
            new Modulo("reclamaciones", "Libro de Reclamaciones", "Tienda"),
            new Modulo("usuarios", "Usuarios", "Sistema"),
            new Modulo("roles", "Roles y permisos", "Sistema"),
            new Modulo("metodos_pago", "Métodos de Pago", "Sistema"),
            new Modulo("configuracion", "Configuración", "Sistema"));

    /** Clave → etiqueta, en el orden de la declaración. */
    public static final Map<String, String> MODULOS = CATALOGO.stream()
            .collect(LinkedHashMap::new, (m, x) -> m.put(x.clave(), x.etiqueta()), Map::putAll);

    /** Las claves, en orden. Derivado: no se mantiene a mano. */
    public static final List<String> ORDEN_MODULOS = CATALOGO.stream().map(Modulo::clave).toList();

    /** Los grupos, en el orden en que aparecen en el catálogo. */
    public static final List<String> GRUPOS = CATALOGO.stream()
            .map(Modulo::grupo).distinct().toList();

    private static final Map<String, String> GRUPO_DE_MODULO = CATALOGO.stream()
            .collect(LinkedHashMap::new, (m, x) -> m.put(x.clave(), x.grupo()), Map::putAll);

    public static String grupoDe(String modulo) {
        return GRUPO_DE_MODULO.getOrDefault(modulo, GRUPOS.get(GRUPOS.size() - 1));
    }

    // ── Acciones ─────────────────────────────────────────────────

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

    // ── Jerarquía ────────────────────────────────────────────────

    /** Nivel en la pirámide; -1 si no es un puesto de panel (p. ej. customer). */
    public static int nivel(String role) {
        return PUESTOS.stream().filter(p -> p.clave().equals(role))
                .findFirst().map(Puesto::nivel).orElse(-1);
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
        return editor >= 0 && destino >= 0 && editor > destino;
    }

    /** Los roles que este editor puede tocar, de menor a mayor. */
    public static List<String> editablesPor(String rolEditor) {
        return ROLES_CONFIGURABLES.stream().filter(r -> puedeEditar(rolEditor, r)).toList();
    }

    /** Los roles estrictamente por debajo de uno, de menor a mayor. */
    public static List<String> pordebajoDe(String role) {
        int n = nivel(role);
        if (n <= 0) return List.of();
        return ROLES_CONFIGURABLES.stream().filter(r -> nivel(r) < n).toList();
    }
}
