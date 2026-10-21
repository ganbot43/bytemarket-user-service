package com.bytemarket.user.controller.api;

import com.bytemarket.user.model.PanelRole;
import com.bytemarket.user.security.RolDeLaSesion;
import com.bytemarket.user.service.PanelRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Los puestos del panel: crearlos, renombrarlos y darlos de baja.
 *
 * Cuelga de /api/admin/panel-roles, así que el AuthorizationManager lo
 * resuelve al módulo "roles" y el verbo decide la acción: ver para el
 * listado, crear para el alta, editar para el renombre y el cambio de
 * nivel, eliminar para la baja. No hace falta una comprobación aquí porque
 * la puerta ya la guarda el mismo sitio que guarda todo /api/admin.
 *
 * Lo que sí vive dentro es la regla de jerarquía —solo puestos por debajo
 * del propio—, en PanelRoleService: no es "quién entra" sino "qué puede
 * hacer una vez dentro", y eso la matriz no lo sabe.
 */
@RestController
@RequestMapping("/api/admin/panel-roles")
@RequiredArgsConstructor
public class ApiPanelRoleController {

    private final PanelRoleService service;

    @GetMapping
    public ResponseEntity<?> listar() {
        return ResponseEntity.ok(Map.of("data", service.listar().stream()
                .map(ApiPanelRoleController::dto).toList()));
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> body) {
        return responder(() -> dto(service.crear(
                RolDeLaSesion.actual(),
                texto(body.get("clave")),
                texto(body.get("etiqueta")),
                entero(body.get("nivel")))));
    }

    @PutMapping("/{clave}")
    public ResponseEntity<?> renombrar(@PathVariable String clave,
                                       @RequestBody Map<String, Object> body) {
        return responder(() -> {
            String rol = RolDeLaSesion.actual();
            PanelRole r = service.renombrar(rol, clave, texto(body.get("etiqueta")));
            /* El nivel se mueve aparte y solo si viene: renombrar es lo
               corriente y mover de sitio es lo delicado, así que pedirlo
               explícito evita cambiar la jerarquía sin querer al corregir
               una tilde. */
            if (body.containsKey("nivel")) {
                r = service.moverDeNivel(rol, clave, entero(body.get("nivel")));
            }
            return dto(r);
        });
    }

    @DeleteMapping("/{clave}")
    public ResponseEntity<?> eliminar(@PathVariable String clave) {
        return responder(() -> {
            service.eliminar(RolDeLaSesion.actual(), clave);
            return Map.of("ok", true);
        });
    }

    // ── Auxiliares ───────────────────────────────────────────────

    private static ResponseEntity<?> responder(java.util.function.Supplier<Object> accion) {
        try {
            return ResponseEntity.ok(accion.get());
        } catch (PanelRoleService.NoSePuede e) {
            /* El motivo viaja tal cual: "hay 3 personas con este puesto" se
               puede enseñar y resolver; "no se pudo" obliga a adivinar. */
            return ResponseEntity.status(e.status)
                    .body(Map.of("statusCode", e.status, "message", e.getMessage()));
        }
    }

    private static Map<String, Object> dto(PanelRole r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clave", r.getClave());
        m.put("etiqueta", r.getEtiqueta());
        m.put("nivel", r.getNivel());
        m.put("delSistema", r.isDelSistema());
        return m;
    }

    private static String texto(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    private static Integer entero(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return v == null ? null : Integer.valueOf(String.valueOf(v).trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
