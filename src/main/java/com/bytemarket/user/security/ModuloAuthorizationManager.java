package com.bytemarket.user.security;

import com.bytemarket.user.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * Autoriza /api/admin/** consultando la matriz de permisos.
 *
 * Antes eran reglas hasAnyRole en el SecurityConfig, así que quitarle
 * "Usuarios" al jefe de ventas desde el panel no le cerraba
 * /api/admin/users/**: la matriz decía una cosa y el filtro otra, y mandaba
 * el filtro. Una pantalla de permisos cuyos cambios no cierran la API no
 * sirve de nada.
 *
 * Deniega por omisión. Una ruta de /api/admin que no esté declarada en
 * RutasDeModulo no pasa: si mañana se añade un controlador y nadie lo
 * declara, quedarse fuera es el error seguro —se nota en la primera prueba—
 * mientras que pasar sin comprobar nada no se nota hasta que es tarde.
 */
@Component
@RequiredArgsConstructor
public class ModuloAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final PermissionService permissionService;

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                       RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated()) return new AuthorizationDecision(false);

        String rol = rolDelPanel(auth);
        if (rol == null) return new AuthorizationDecision(false);

        // El gerente lo puede todo por código, no por datos.
        if (PanelModules.esGerente(rol)) return new AuthorizationDecision(true);

        String modulo = RutasDeModulo.modulo(context.getRequest());
        if (modulo == null) return new AuthorizationDecision(false);

        var permiso = permissionService.efectivos(rol).get(modulo);
        if (permiso == null) return new AuthorizationDecision(false);

        boolean ok = RutasDeModulo.escribe(context.getRequest())
                ? permiso.editar()
                : permiso.ver();
        return new AuthorizationDecision(ok);
    }

    /** El más alto de la pirámide que traiga la sesión. */
    private static String rolDelPanel(Authentication auth) {
        List<String> authorities = auth.getAuthorities().stream().map(Object::toString).toList();
        for (String rol : List.of(PanelModules.GERENTE, PanelModules.ENCARGADO,
                PanelModules.JEFE_VENTAS, PanelModules.CAJERO)) {
            if (authorities.contains("ROLE_" + rol.toUpperCase())) return rol;
        }
        return null;
    }
}
