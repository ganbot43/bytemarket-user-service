package com.bytemarket.user.security;

import com.bytemarket.user.model.RolePermission;
import com.bytemarket.user.repository.IRolePermissionRepository;
import com.bytemarket.user.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El portero de /api/admin: decide con la matriz, no con una lista de roles.
 *
 * Lo que se comprueba aquí es que la pantalla de permisos no mienta. Antes
 * las reglas eran hasAnyRole en el SecurityConfig, así que quitarle
 * "Usuarios" al jefe de ventas desde el panel no le cerraba
 * /api/admin/users/**: la tabla decía una cosa, el filtro otra, y mandaba el
 * filtro.
 */
@DisplayName("Permisos — el portero de /api/admin")
class ModuloAuthorizationManagerTest {

    private IRolePermissionRepository repository;
    private ModuloAuthorizationManager portero;

    @BeforeEach
    void setUp() {
        repository = mock(IRolePermissionRepository.class);
        when(repository.findByRoleAndModule(any(), any())).thenReturn(Optional.empty());
        when(repository.findByRole(any())).thenReturn(List.of());

        PermissionService servicio = new PermissionService(repository);
        portero = new ModuloAuthorizationManager(servicio);
    }

    /** Deja a los roles con sus permisos por defecto en la base simulada. */
    private void conLosPermisosPorDefecto() {
        List<RolePermission> filas = new java.util.ArrayList<>();
        when(repository.save(any(RolePermission.class))).thenAnswer(i -> {
            filas.add(i.getArgument(0));
            return i.getArgument(0);
        });
        new PermissionService(repository).sembrar();
        for (String rol : PanelModules.ROLES_CONFIGURABLES) {
            when(repository.findByRole(rol)).thenReturn(
                    filas.stream().filter(f -> rol.equals(f.getRole())).toList());
        }
    }

    private boolean permite(String rol, String metodo, String ruta) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "quien@bytemarket.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase())));
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, ruta);
        var decision = portero.check(() -> auth, new RequestAuthorizationContext(request));
        return decision != null && decision.isGranted();
    }

    @Test
    @DisplayName("el gerente entra a todo sin consultar la tabla")
    void elGerenteEntraATodo() {
        assertTrue(permite(PanelModules.GERENTE, "GET", "/api/admin/users"));
        assertTrue(permite(PanelModules.GERENTE, "PUT", "/api/admin/business-config"));
        assertTrue(permite(PanelModules.GERENTE, "PUT", "/api/admin/permissions"));
        // Ni siquiera se le pregunta a la base.
        verify(repository, never()).findByRole(PanelModules.GERENTE);
    }

    @Test
    @DisplayName("el jefe de ventas administra usuarios pero no la configuración")
    void elJefeSoloLoSuyo() {
        conLosPermisosPorDefecto();

        assertTrue(permite(PanelModules.JEFE_VENTAS, "GET", "/api/admin/users"));
        assertTrue(permite(PanelModules.JEFE_VENTAS, "POST", "/api/admin/users"));
        assertFalse(permite(PanelModules.JEFE_VENTAS, "GET", "/api/admin/business-config"));
        assertFalse(permite(PanelModules.JEFE_VENTAS, "PUT", "/api/admin/business-config"));
    }

    @Test
    @DisplayName("el cajero no entra a la gestión de usuarios")
    void elCajeroNoGestionaUsuarios() {
        conLosPermisosPorDefecto();
        assertFalse(permite(PanelModules.CAJERO, "GET", "/api/admin/users"));
    }

    @Test
    @DisplayName("ver no alcanza para escribir: el método decide cuál de los dos permisos hace falta")
    void elMetodoDecideQuePermisoHaceFalta() {
        // Al cajero se le concede solo VER usuarios. El GET pasa, el POST no.
        RolePermission soloVer = new RolePermission();
        soloVer.setRole(PanelModules.CAJERO);
        soloVer.setModule("usuarios");
        soloVer.setCanView(true);
        soloVer.setCanEdit(false);
        when(repository.findByRole(PanelModules.CAJERO)).thenReturn(List.of(soloVer));

        assertTrue(permite(PanelModules.CAJERO, "GET", "/api/admin/users"));
        assertFalse(permite(PanelModules.CAJERO, "POST", "/api/admin/users"),
                "con solo ver no se puede dar de alta a nadie");
        assertFalse(permite(PanelModules.CAJERO, "DELETE", "/api/admin/users/3"));
    }

    @Test
    @DisplayName("una ruta de /api/admin sin módulo declarado NO pasa")
    void deniegaPorOmision() {
        conLosPermisosPorDefecto();

        /* El caso que importa: mañana se añade un controlador nuevo y nadie
           lo declara en RutasDeModulo. Quedarse fuera se nota en la primera
           prueba; pasar sin comprobar nada no se nota hasta que es tarde. */
        assertFalse(permite(PanelModules.ENCARGADO, "GET", "/api/admin/modulo-que-nadie-declaro"));
        assertFalse(permite(PanelModules.ENCARGADO, "POST", "/api/admin/otra-cosa/7"));
    }

    @Test
    @DisplayName("un cliente de la tienda no es rol de panel y no entra")
    void elClienteNoEsDelPanel() {
        assertFalse(permite("customer", "GET", "/api/admin/users"));
    }

    @Test
    @DisplayName("sin sesión no se entra")
    void sinSesionNada() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        var decision = portero.check(() -> null, new RequestAuthorizationContext(request));
        assertFalse(decision.isGranted());
    }
}
