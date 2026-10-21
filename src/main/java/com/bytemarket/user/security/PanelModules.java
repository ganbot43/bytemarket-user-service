package com.bytemarket.user.security;

import java.util.List;
import java.util.Map;

/**
 * Lo del panel que NO puede vivir en la base de datos.
 *
 * Los puestos y los módulos se mudaron a `panel_roles` y `panel_modules`
 * —ver PanelCatalogo—, para que crear uno nuevo deje de ser recompilar. Aquí
 * solo queda lo que, si fuera dato, podría dejar el sistema sin dueño:
 *
 *   La clave del gerente. `esGerente` la compara contra esta constante y no
 *   contra la tabla, que es la regla 2 de la pirámide: el gerente lo puede
 *   todo por código. Si dependiera de una fila, borrarla o cambiarle el
 *   nivel lo dejaría fuera de su propio sistema sin forma de volver a
 *   entrar. La tabla dice qué puestos hay; el código dice quién manda pase
 *   lo que pase.
 *
 *   Las cuatro acciones. Son el vocabulario con el que los cuatro servicios
 *   traducen un verbo HTTP a un permiso, así que añadir una no es configurar
 *   nada: es cambiar cómo deciden todos.
 *
 * Las otras tres claves son constantes por comodidad —las usan el sembrado
 * y los tests—, no porque manden sobre la tabla.
 */
public final class PanelModules {

    private PanelModules() {}

    public static final String CAJERO = "cajero";
    public static final String JEFE_VENTAS = "jefe_ventas";
    public static final String ENCARGADO = "admin";

    /** El único puesto que no se consulta: lo puede todo por código. */
    public static final String GERENTE = "superadmin";

    public static boolean esGerente(String role) {
        return GERENTE.equals(role);
    }

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
}
