package com.bytemarket.user.service;

import com.bytemarket.user.model.RolePermission;
import com.bytemarket.user.repository.IRolePermissionRepository;
import com.bytemarket.user.security.PanelModules;
import com.bytemarket.user.service.PermissionService.Permiso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * La pirámide: quién puede repartir permisos a quién.
 *
 * Una matriz de permisos editable es una vía de escalada de privilegios si
 * no se la sujeta. Lo que se comprueba aquí es que no se la pueda usar para
 * subir: ni hacia arriba, ni a uno mismo, ni para tocar al gerente.
 *
 * Es el mismo razonamiento de ApiAdminUserControllerRolesTest —que la
 * pantalla filtre no es un control— aplicado a los permisos en vez de a los
 * puestos.
 */
@DisplayName("Permisos — la pirámide no se puede escalar")
class PermissionServiceTest {

    private IRolePermissionRepository repository;
    private PermissionService servicio;
    private List<RolePermission> guardadas;

    @BeforeEach
    void setUp() {
        repository = mock(IRolePermissionRepository.class);
        guardadas = new ArrayList<>();

        when(repository.findByRoleAndModule(any(), any())).thenReturn(Optional.empty());
        when(repository.findByRole(any())).thenReturn(List.of());
        when(repository.save(any(RolePermission.class))).thenAnswer(i -> {
            guardadas.add(i.getArgument(0));
            return i.getArgument(0);
        });

        servicio = new PermissionService(repository);
    }

    private Map<String, Map<String, Permiso>> cambio(String rol, String modulo, Permiso p) {
        return Map.of(rol, Map.of(modulo, p));
    }

    /**
     * Deja a los roles con sus permisos por defecto en la base simulada.
     *
     * Hace falta desde que existe la regla de delegación: un editor sin
     * ningún permiso no puede conceder nada, así que sin esto los tests de
     * "sí puede editar hacia abajo" fallarían por el motivo equivocado.
     */
    private void conLosPermisosPorDefecto() {
        servicio.sembrar();
        List<RolePermission> sembradas = List.copyOf(guardadas);
        guardadas.clear();
        for (String rol : PanelModules.ROLES_CONFIGURABLES) {
            when(repository.findByRole(rol)).thenReturn(
                    sembradas.stream().filter(f -> rol.equals(f.getRole())).toList());
        }
    }

    // ── Hacia arriba y a uno mismo ───────────────────────────────

    @Test
    @DisplayName("el encargado no puede editarse a sí mismo")
    void nadieSeEditaASiMismo() {
        var r = servicio.guardar(PanelModules.ENCARGADO,
                cambio(PanelModules.ENCARGADO, "configuracion", new Permiso(true, true)));

        assertEquals(0, r.aplicados(), "no debió guardar nada");
        assertTrue(guardadas.isEmpty());
        assertFalse(r.rechazos().isEmpty(), "y debe decir por qué");
    }

    @Test
    @DisplayName("el jefe de ventas no puede ampliar al encargado, que está por encima")
    void noSePuedeEditarHaciaArriba() {
        var r = servicio.guardar(PanelModules.JEFE_VENTAS,
                cambio(PanelModules.ENCARGADO, "reportes", new Permiso(false, false)));

        assertEquals(0, r.aplicados());
        assertTrue(guardadas.isEmpty());
    }

    @Test
    @DisplayName("al gerente no lo toca nadie, ni el propio gerente")
    void elGerenteNoEstaEnLaMatriz() {
        // Ni siquiera él mismo: no es una cuestión de permisos sino de que
        // sus permisos no viven en la tabla.
        var r = servicio.guardar(PanelModules.GERENTE,
                cambio(PanelModules.GERENTE, "caja", new Permiso(false, false)));

        assertEquals(0, r.aplicados());
        assertTrue(guardadas.isEmpty());
    }

    // ── Hacia abajo sí ───────────────────────────────────────────

    @Test
    @DisplayName("el gerente reparte a los tres de abajo")
    void elGerenteEditaATodosLosDemas() {
        for (String rol : PanelModules.ROLES_CONFIGURABLES) {
            assertTrue(PanelModules.puedeEditar(PanelModules.GERENTE, rol),
                    "el gerente debe poder editar a " + rol);
        }
        assertEquals(List.of(PanelModules.CAJERO, PanelModules.JEFE_VENTAS, PanelModules.ENCARGADO),
                PanelModules.editablesPor(PanelModules.GERENTE));
    }

    @Test
    @DisplayName("el encargado reparte a jefe de ventas y cajero, y a nadie más")
    void elEncargadoEditaSoloHaciaAbajo() {
        assertEquals(List.of(PanelModules.CAJERO, PanelModules.JEFE_VENTAS),
                PanelModules.editablesPor(PanelModules.ENCARGADO));

        conLosPermisosPorDefecto();
        var r = servicio.guardar(PanelModules.ENCARGADO,
                cambio(PanelModules.CAJERO, "reportes", new Permiso(true, false)));

        assertEquals(1, r.aplicados());
        assertTrue(r.rechazos().isEmpty());
        assertEquals("cajero", guardadas.get(0).getRole());
        assertEquals("reportes", guardadas.get(0).getModule());
    }

    @Test
    @DisplayName("el cajero está al fondo: no reparte nada")
    void elCajeroNoEditaANadie() {
        assertTrue(PanelModules.editablesPor(PanelModules.CAJERO).isEmpty());
    }

    // ── El gerente lo puede todo, por código ─────────────────────

    @Test
    @DisplayName("el gerente lo tiene todo aunque la tabla diga lo contrario")
    void elGerenteNoDependeDeLaTabla() {
        // Una fila guardada a mano que intente recortarle algo: aunque exista
        // en base, para el gerente no se consulta. Es lo que impide que un
        // guardado mal hecho lo deje fuera de su propio sistema.
        RolePermission maliciosa = new RolePermission();
        maliciosa.setRole(PanelModules.GERENTE);
        maliciosa.setModule("configuracion");
        maliciosa.setCanView(false);
        maliciosa.setCanEdit(false);
        when(repository.findByRole(PanelModules.GERENTE)).thenReturn(List.of(maliciosa));

        Map<String, Permiso> suyos = servicio.efectivos(PanelModules.GERENTE);

        assertEquals(PanelModules.ORDEN_MODULOS.size(), suyos.size());
        suyos.forEach((modulo, p) -> {
            assertTrue(p.ver(), modulo + ": el gerente debe poder verlo");
            assertTrue(p.editar(), modulo + ": el gerente debe poder editarlo");
        });
    }

    // ── Coherencia de lo que se guarda ───────────────────────────

    @Test
    @DisplayName("conceder editar implica ver: lo contrario es un permiso inalcanzable")
    void editarImplicaVer() {
        servicio.guardar(PanelModules.GERENTE,
                cambio(PanelModules.CAJERO, "inventario", new Permiso(false, true)));

        assertEquals(1, guardadas.size());
        assertTrue(guardadas.get(0).isCanView(),
                "sin ver no se abre la pantalla, así que el editar no se podría usar");
        assertTrue(guardadas.get(0).isCanEdit());
    }

    @Test
    @DisplayName("un lote mixto aplica los válidos y solo rechaza los que no")
    void loteMixtoNoPierdeLosBuenos() {
        // El encargado manda un cambio legítimo para el cajero y otro,
        // inválido, para sí mismo. Tirar el lote entero haría perder el bueno
        // sin que nadie se enterara de cuál era el malo.
        conLosPermisosPorDefecto();
        var r = servicio.guardar(PanelModules.ENCARGADO, Map.of(
                PanelModules.CAJERO, Map.of("pedidos", new Permiso(true, false)),
                PanelModules.ENCARGADO, Map.of("configuracion", new Permiso(true, true))));

        assertEquals(1, r.aplicados(), "el del cajero sí se aplica");
        assertEquals(1, r.rechazos().size(), "el propio se rechaza");
        assertEquals(PanelModules.CAJERO, guardadas.get(0).getRole());
    }

    // ── Nadie delega lo que no tiene ─────────────────────────────

    @Test
    @DisplayName("el encargado no puede conceder al cajero algo que él no tiene")
    void nadieConcedeLoQueNoTiene() {
        conLosPermisosPorDefecto();

        /* El camino real de la escalada: el encargado no tiene
           Configuración, pero puede editar al cajero y puede CREAR cajeros.
           Si esto pasara, se concedía el módulo, se creaba una cuenta de
           cajero con su propia contraseña, entraba con ella y acababa
           teniendo lo que nunca tuvo. La pirámide aguanta y el privilegio
           se escapa por abajo. */
        var r = servicio.guardar(PanelModules.ENCARGADO,
                cambio(PanelModules.CAJERO, "configuracion", new Permiso(true, true)));

        assertEquals(0, r.aplicados(), "no debe poder concederlo");
        assertTrue(guardadas.isEmpty());
        assertFalse(r.rechazos().isEmpty());
    }

    @Test
    @DisplayName("pero sí puede QUITAR algo que él no tiene")
    void quitarNoRequiereTenerlo() {
        conLosPermisosPorDefecto();

        // Recortar a otro no amplía lo propio, así que no es escalada. Y un
        // encargado tiene que poder cerrarle una pantalla a un cajero sin
        // necesitar antes ese mismo permiso.
        var r = servicio.guardar(PanelModules.ENCARGADO,
                cambio(PanelModules.CAJERO, "configuracion", new Permiso(false, false)));

        assertEquals(1, r.aplicados());
        assertFalse(guardadas.get(0).isCanView());
        assertFalse(guardadas.get(0).isCanEdit());
    }

    @Test
    @DisplayName("al gerente no le limita la regla: lo tiene todo, no hay nada de más que dar")
    void elGerenteConcedeCualquierCosa() {
        conLosPermisosPorDefecto();

        var r = servicio.guardar(PanelModules.GERENTE,
                cambio(PanelModules.CAJERO, "configuracion", new Permiso(true, true)));

        assertEquals(1, r.aplicados());
        assertTrue(guardadas.get(0).isCanEdit());
    }

    @Test
    @DisplayName("el jefe de ventas tampoco amplía al cajero por encima de sí mismo")
    void elJefeTampocoAmplia() {
        conLosPermisosPorDefecto();

        // El jefe no tiene Productos; el cajero tampoco debe acabar con él.
        var r = servicio.guardar(PanelModules.JEFE_VENTAS,
                cambio(PanelModules.CAJERO, "productos", new Permiso(true, true)));

        assertEquals(0, r.aplicados());
        assertTrue(guardadas.isEmpty());
    }

    // ── Quitar cierra la puerta entera ───────────────────────────

    @Test
    @DisplayName("quitar un permiso arriba lo quita también abajo")
    void elRetiroCascadea() {
        conLosPermisosPorDefecto();

        /* El camino que quedaba abierto: el encargado tiene Reportes y se lo
           concede al cajero (legal). El gerente se lo quita después AL
           ENCARGADO. Si el cajero se quedara con él, el encargado —que puede
           crear cajeros— entraría con una cuenta de cajero y recuperaría lo
           que le acababan de retirar. */
        RolePermission delCajero = new RolePermission();
        delCajero.setRole(PanelModules.CAJERO);
        delCajero.setModule("reportes");
        delCajero.setCanView(true);
        delCajero.setCanEdit(false);
        when(repository.findByRoleAndModule(PanelModules.CAJERO, "reportes"))
                .thenReturn(Optional.of(delCajero));

        servicio.guardar(PanelModules.GERENTE,
                cambio(PanelModules.ENCARGADO, "reportes", new Permiso(false, false)));

        assertFalse(delCajero.isCanView(),
                "al cajero también se le cierra: si no, el encargado lo recupera por abajo");
        assertFalse(delCajero.isCanEdit());
    }

    @Test
    @DisplayName("quitar solo 'editar' arriba deja el 'ver' de abajo en pie")
    void elRetiroCascadeaSoloLoQuitado() {
        conLosPermisosPorDefecto();

        RolePermission delCajero = new RolePermission();
        delCajero.setRole(PanelModules.CAJERO);
        delCajero.setModule("pedidos");
        delCajero.setCanView(true);
        delCajero.setCanEdit(true);
        when(repository.findByRoleAndModule(PanelModules.CAJERO, "pedidos"))
                .thenReturn(Optional.of(delCajero));

        // El encargado conserva el ver, así que el cajero también puede.
        servicio.guardar(PanelModules.GERENTE,
                cambio(PanelModules.ENCARGADO, "pedidos", new Permiso(true, false)));

        assertTrue(delCajero.isCanView(), "el ver no se tocó arriba, no se toca abajo");
        assertFalse(delCajero.isCanEdit(), "el editar sí");
    }

    @Test
    @DisplayName("conceder no cascadea: dar algo arriba no lo reparte abajo")
    void laConcesionNoCascadea() {
        conLosPermisosPorDefecto();

        RolePermission delCajero = new RolePermission();
        delCajero.setRole(PanelModules.CAJERO);
        delCajero.setModule("configuracion");
        delCajero.setCanView(false);
        delCajero.setCanEdit(false);
        when(repository.findByRoleAndModule(PanelModules.CAJERO, "configuracion"))
                .thenReturn(Optional.of(delCajero));

        servicio.guardar(PanelModules.GERENTE,
                cambio(PanelModules.ENCARGADO, "configuracion", new Permiso(true, true)));

        assertFalse(delCajero.isCanView(),
                "para eso está la pantalla; conceder en cadena daría permisos que nadie pidió");
    }

    // ── Los valores por defecto ──────────────────────────────────

    @Test
    @DisplayName("los valores por defecto son piramidales: cada nivel contiene al de abajo")
    void losDefaultsSonPiramidales() {
        servicio.sembrar();

        Map<String, Map<String, Boolean>> ver = new java.util.LinkedHashMap<>();
        for (RolePermission f : guardadas) {
            ver.computeIfAbsent(f.getRole(), k -> new java.util.LinkedHashMap<>())
               .put(f.getModule(), f.isCanView());
        }

        for (String modulo : PanelModules.ORDEN_MODULOS) {
            boolean cajero = ver.get(PanelModules.CAJERO).get(modulo);
            boolean jefe = ver.get(PanelModules.JEFE_VENTAS).get(modulo);
            boolean encargado = ver.get(PanelModules.ENCARGADO).get(modulo);

            if (cajero) assertTrue(jefe, modulo + ": si lo ve el cajero, lo ve su jefe");
            if (jefe) assertTrue(encargado, modulo + ": si lo ve el jefe, lo ve el encargado");
        }

        // Y el reparto de hoy, que es lo que no debe cambiar al encender esto.
        assertTrue(ver.get(PanelModules.CAJERO).get("caja"), "el cajero cobra");
        assertFalse(ver.get(PanelModules.CAJERO).get("reportes"), "pero no ve los números del negocio");
        assertFalse(ver.get(PanelModules.JEFE_VENTAS).get("productos"), "el catálogo es de gestión");
        assertFalse(ver.get(PanelModules.ENCARGADO).get("configuracion"), "la configuración es del gerente");
    }

    @Test
    @DisplayName("sembrar no pisa lo que el gerente ya configuró")
    void sembrarEsIdempotente() {
        RolePermission yaExiste = new RolePermission();
        yaExiste.setRole(PanelModules.CAJERO);
        yaExiste.setModule("caja");
        when(repository.findByRoleAndModule(PanelModules.CAJERO, "caja"))
                .thenReturn(Optional.of(yaExiste));

        servicio.sembrar();

        assertTrue(guardadas.stream()
                        .noneMatch(f -> PanelModules.CAJERO.equals(f.getRole()) && "caja".equals(f.getModule())),
                "la fila que ya existía no debe reescribirse");
    }
}
