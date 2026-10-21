package com.bytemarket.user.controller.api;

import com.bytemarket.user.security.PanelModules;
import com.bytemarket.user.service.PermissionService;
import com.bytemarket.user.service.PermissionService.Permiso;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La matriz de permisos: la pantalla del gerente, lo que el panel necesita
 * para pintarse, y lo que los otros servicios consultan para decidir.
 *
 * Son cuatro rutas con tres públicos distintos a propósito:
 *
 *   /api/admin/permissions   la pantalla de edición (gerente y encargado)
 *   /api/me/permissions      lo que puede el que pregunta, para el menú
 *   /api/internal/permissions  la matriz entera, para los otros servicios
 *
 * /api/me/** es de cualquiera con sesión de panel: saber qué puedes hacer no
 * es un privilegio, y sin ella el menú tendría que adivinarlo.
 */
@RestController
@RequiredArgsConstructor
public class ApiPermissionController {

    private final PermissionService permissionService;

    // ── La pantalla de edición ───────────────────────────────────

    /**
     * Todo lo que la pantalla necesita para dibujarse: la matriz, el catálogo
     * de módulos con sus etiquetas, y —lo importante— qué roles puede tocar
     * quien pregunta.
     *
     * Eso último lo decide el backend y no el panel. Si la pantalla lo
     * dedujera por su cuenta, habría dos versiones de la regla piramidal y
     * tarde o temprano dirían cosas distintas.
     */
    @GetMapping("/api/admin/permissions")
    public ResponseEntity<?> matriz() {
        String rol = rolDelPanel();
        if (rol == null) return error(403, "No es una sesión de panel.");

        List<String> editables = PanelModules.editablesPor(rol);
        if (editables.isEmpty()) {
            return error(403, "Tu rol no administra permisos de nadie.");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        /* Las acciones y su explicación viajan con la matriz para que la
           pantalla no tenga que repetirlas: si mañana se añade una, aparece
           sola en la cabecera de la tabla. */
        out.put("actions", PanelModules.ACCIONES);
        out.put("groups", PanelModules.GRUPOS);
        out.put("modules", PanelModules.ORDEN_MODULOS.stream()
                .map(k -> Map.of("key", k,
                        "label", PanelModules.MODULOS.get(k),
                        "group", PanelModules.grupoDe(k)))
                .toList());
        out.put("roles", PanelModules.ROLES_CONFIGURABLES.stream()
                .map(r -> Map.of(
                        "key", r,
                        "label", PanelModules.ETIQUETA_ROL.getOrDefault(r, r),
                        "editable", editables.contains(r)))
                .toList());
        out.put("matrix", comoDTO(permissionService.matriz()));
        /* Para que la pantalla pueda explicar por qué el gerente no aparece
           en la tabla, en vez de dejar un hueco sin motivo. */
        out.put("gerenteLabel", PanelModules.ETIQUETA_ROL.get(PanelModules.GERENTE));
        return ResponseEntity.ok(out);
    }

    /**
     * Guarda la matriz.
     *
     * Cuerpo esperado:
     *   { "cajero": { "reportes": { "view": true, "edit": false } }, ... }
     *
     * Responde 200 con los rechazos dentro si hubo alguno, en vez de 403 a
     * secas: un lote puede traer cambios válidos e inválidos a la vez, y los
     * válidos sí se aplicaron.
     */
    @PutMapping("/api/admin/permissions")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> guardar(@RequestBody Map<String, Object> body) {
        String rol = rolDelPanel();
        if (rol == null) return error(403, "No es una sesión de panel.");
        Map<String, Map<String, Permiso>> cambios = new LinkedHashMap<>();
        for (var e : body.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> modulos)) continue;
            Map<String, Permiso> porModulo = new LinkedHashMap<>();
            for (var m : modulos.entrySet()) {
                if (!(m.getValue() instanceof Map<?, ?> flags)) continue;
                porModulo.put(String.valueOf(m.getKey()), new Permiso(
                        esSi(flags.get("view")), esSi(flags.get("create")),
                        esSi(flags.get("edit")), esSi(flags.get("delete"))));
            }
            cambios.put(e.getKey(), porModulo);
        }

        PermissionService.Resultado r = permissionService.guardar(rol, cambios);
        if (r.aplicados() == 0 && !r.rechazos().isEmpty()) {
            return ResponseEntity.status(403).body(Map.of(
                    "statusCode", 403,
                    "message", r.rechazos().get(0),
                    "rechazos", r.rechazos()));
        }
        return ResponseEntity.ok(Map.of(
                "aplicados", r.aplicados(),
                "rechazos", r.rechazos(),
                "matrix", comoDTO(permissionService.matriz())));
    }

    // ── Lo que puede el que pregunta ─────────────────────────────

    /** Para el menú y los middleware del panel. */
    @GetMapping("/api/me/permissions")
    public ResponseEntity<?> mios() {
        String rol = rolDelPanel();
        if (rol == null) return error(403, "No es una sesión de panel.");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", rol);
        out.put("isGerente", PanelModules.esGerente(rol));
        out.put("permissions", permisosDTO(permissionService.efectivos(rol)));
        out.put("editableRoles", PanelModules.editablesPor(rol));
        return ResponseEntity.ok(out);
    }

    // ── Para los otros servicios ─────────────────────────────────

    /**
     * La matriz entera para catalog, order y support, que la cachean y
     * deciden con ella en vez de con roles escritos a mano.
     *
     * Va sin el gerente, igual que la tabla: cada servicio sabe que el
     * gerente lo puede todo sin preguntarlo.
     */
    @GetMapping("/api/internal/permissions")
    public ResponseEntity<?> paraServicios() {
        return ResponseEntity.ok(Map.of("matrix", comoDTO(permissionService.matriz())));
    }

    // ── Auxiliares ───────────────────────────────────────────────

    /** El puesto de quien pregunta; la deducción vive en RolDeLaSesion. */
    private static String rolDelPanel() {
        return com.bytemarket.user.security.RolDeLaSesion.actual();
    }


    /*
     * El módulo "roles" no se comprueba aquí: lo hace
     * ModuloAuthorizationManager para toda la ruta /api/admin/**, con GET
     * pidiendo ver y el PUT pidiendo editar. Tenerlo también en el
     * controlador sería la misma regla en dos sitios, que es justo lo que
     * este cambio vino a quitar.
     */

    private static boolean esSi(Object v) {
        if (v instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    private static Map<String, Object> permisosDTO(Map<String, Permiso> permisos) {
        Map<String, Object> out = new LinkedHashMap<>();
        permisos.forEach((modulo, p) -> out.put(modulo, Map.of(
                "view", p.ver(), "create", p.crear(),
                "edit", p.editar(), "delete", p.eliminar())));
        return out;
    }

    private static Map<String, Object> comoDTO(Map<String, Map<String, Permiso>> matriz) {
        Map<String, Object> out = new LinkedHashMap<>();
        matriz.forEach((rol, permisos) -> out.put(rol, permisosDTO(permisos)));
        return out;
    }

    private static ResponseEntity<Map<String, Object>> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("statusCode", status, "message", message));
    }
}
