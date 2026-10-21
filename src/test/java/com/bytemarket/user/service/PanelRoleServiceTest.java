package com.bytemarket.user.service;

import com.bytemarket.user.model.PanelRole;
import com.bytemarket.user.repository.IPanelRoleRepository;
import com.bytemarket.user.repository.IRolePermissionRepository;
import com.bytemarket.user.repository.IUserRepository;
import com.bytemarket.user.security.PanelModules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Crear puestos sin poder ascenderse.
 *
 * Los puestos en tabla sirven de poco sin una API que los cree, y esa API es
 * peligrosa sin una regla: sin ella, cualquiera con el módulo de Roles se
 * inventa un puesto por encima de todos, se lo asigna y pasa por encima de
 * la jerarquía entera. Es la misma regla 1 de la matriz aplicada al reparto
 * de puestos.
 */
@DisplayName("Puestos — no se crean por encima de uno mismo")
class PanelRoleServiceTest {

    private IPanelRoleRepository repository;
    private IUserRepository userRepository;
    private PanelRoleService servicio;
    private List<PanelRole> filas;

    @BeforeEach
    void setUp() {
        repository = mock(IPanelRoleRepository.class);
        userRepository = mock(IUserRepository.class);
        IRolePermissionRepository permisos = mock(IRolePermissionRepository.class);
        PermissionService permissionService = mock(PermissionService.class);

        filas = new ArrayList<>(List.of(
                fila(PanelModules.CAJERO, "Cajero", 10, true),
                fila(PanelModules.JEFE_VENTAS, "Jefe de ventas", 20, true),
                fila(PanelModules.ENCARGADO, "Encargado", 30, true),
                fila(PanelModules.GERENTE, "Gerente", 40, true)));

        when(repository.findByClave(any())).thenAnswer(i ->
                filas.stream().filter(r -> r.getClave().equals(i.getArgument(0))).findFirst());
        when(repository.findAllByOrderByNivelAsc()).thenAnswer(i ->
                filas.stream().sorted(Comparator.comparingInt(PanelRole::getNivel)).toList());
        when(repository.save(any(PanelRole.class))).thenAnswer(i -> {
            PanelRole r = i.getArgument(0);
            filas.removeIf(x -> x.getClave().equals(r.getClave()));
            filas.add(r);
            return r;
        });
        when(userRepository.countByRole(any())).thenReturn(0L);
        when(permisos.findByRole(any())).thenReturn(List.of());

        servicio = new PanelRoleService(repository, userRepository, permisos,
                CatalogoDePrueba.deFabrica(), permissionService);
    }

    private static PanelRole fila(String clave, String etiqueta, int nivel, boolean sistema) {
        PanelRole r = new PanelRole();
        r.setClave(clave);
        r.setEtiqueta(etiqueta);
        r.setNivel(nivel);
        r.setDelSistema(sistema);
        return r;
    }

    private String motivo(Runnable accion) {
        try { accion.run(); return null; }
        catch (PanelRoleService.NoSePuede e) { return e.getMessage(); }
    }

    // ── La regla ─────────────────────────────────────────────────

    @Test
    @DisplayName("nadie crea un puesto por encima del suyo")
    void noSeCreaPorEncima() {
        /* El camino de la escalada: el encargado (30) inventa un puesto de
           nivel 99, se lo asigna a una cuenta suya y queda por encima del
           gerente en la pirámide. */
        String m = motivo(() -> servicio.crear(PanelModules.ENCARGADO, "jefazo", "Jefazo", 99));
        assertNotNull(m, "no debió dejarle");
        assertTrue(m.contains("por debajo del tuyo"), m);
    }

    @Test
    @DisplayName("ni uno a su propio nivel")
    void noSeCreaAlMismoNivel() {
        /* A la par tampoco: dos puestos al mismo nivel no se administran
           entre ellos, y el encargado tendría un igual que no controla. */
        assertNotNull(motivo(() -> servicio.crear(PanelModules.ENCARGADO, "copia", "Copia", 30)));
    }

    @Test
    @DisplayName("sí uno por debajo, y queda encajado entre dos")
    void siPorDebajo() {
        PanelRole r = servicio.crear(PanelModules.ENCARGADO, "jefe_almacen", "Jefe de almacén", 15);

        assertEquals("jefe_almacen", r.getClave());
        assertEquals(15, r.getNivel());
        assertFalse(r.isDelSistema(), "lo creado desde el panel no es de fábrica");
    }

    @Test
    @DisplayName("el gerente no se reparte desde aquí")
    void elGerenteNoSeReparte() {
        String m = motivo(() -> servicio.crear(PanelModules.GERENTE, "superadmin", "Otro gerente", 35));
        assertNotNull(m);
        assertTrue(m.contains("gerente"), m);
    }

    @Test
    @DisplayName("el puesto más bajo sí puede crear uno aún más bajo")
    void elDeAbajoPuedeCrearDebajo() {
        /* Con los niveles de diez en diez queda sitio por debajo del cajero
           (10), y eso es correcto, no un agujero: el puesto nuevo nace por
           DEBAJO, y la matriz solo deja concederle lo que el cajero ya
           tiene. Delegar hacia abajo nunca amplía lo propio.
           Nota: con los niveles consecutivos de la primera versión esto era
           imposible, así que el comportamiento cambió al espaciarlos. */
        assertNull(motivo(() -> servicio.crear(PanelModules.CAJERO, "ayudante", "Ayudante", 5)));
    }

    @Test
    @DisplayName("pero no uno a su altura ni por encima")
    void elDeAbajoNoSubeDeNivel() {
        assertNotNull(motivo(() -> servicio.crear(PanelModules.CAJERO, "igual", "Igual", 10)));
        assertNotNull(motivo(() -> servicio.crear(PanelModules.CAJERO, "arriba", "Arriba", 25)));
    }

    // ── Integridad ───────────────────────────────────────────────

    @Test
    @DisplayName("dos puestos no comparten nivel")
    void elNivelNoSeComparte() {
        String m = motivo(() -> servicio.crear(PanelModules.GERENTE, "otro", "Otro", 20));
        assertNotNull(m);
        assertTrue(m.contains("ya lo ocupa"), m);
    }

    @Test
    @DisplayName("la clave tiene que sobrevivir a ser una autoridad de Spring")
    void laClaveSeValida() {
        for (String mala : List.of("Jefe Almacén", "a", "con-guion", "MAYUS", "1empieza")) {
            assertNotNull(motivo(() -> servicio.crear(PanelModules.GERENTE, mala, "X", 15)),
                    "debió rechazar la clave: " + mala);
        }
    }

    @Test
    @DisplayName("los puestos de fábrica no se eliminan ni cambian de nivel")
    void losDeFabricaNoSeTocan() {
        assertNotNull(motivo(() -> servicio.eliminar(PanelModules.GERENTE, PanelModules.CAJERO)));
        assertNotNull(motivo(() -> servicio.moverDeNivel(PanelModules.GERENTE, PanelModules.CAJERO, 5)));
        // Renombrarlos sí: "Encargado" puede llamarse "Jefe de tienda" en
        // otro negocio, y la clave —que es lo que usa la matriz— no se toca.
        assertNull(motivo(() -> servicio.renombrar(PanelModules.GERENTE, PanelModules.ENCARGADO, "Jefe de tienda")));
    }

    @Test
    @DisplayName("no se elimina un puesto con gente dentro")
    void noSeEliminaConGente() {
        servicio.crear(PanelModules.GERENTE, "jefe_almacen", "Jefe de almacén", 15);
        when(userRepository.countByRole("jefe_almacen")).thenReturn(3L);

        /* Borrarlo dejaría a esas tres personas con un rol que ya no existe:
           sin nivel, sin permisos y fuera del panel, sin un error que lo
           explicara. */
        String m = motivo(() -> servicio.eliminar(PanelModules.GERENTE, "jefe_almacen"));
        assertNotNull(m);
        assertTrue(m.contains("3 personas"), m);
    }

    @Test
    @DisplayName("sin gente dentro sí se elimina")
    void sinGenteSeElimina() {
        servicio.crear(PanelModules.GERENTE, "jefe_almacen", "Jefe de almacén", 15);
        assertNull(motivo(() -> servicio.eliminar(PanelModules.GERENTE, "jefe_almacen")));
        verify(repository).delete(any(PanelRole.class));
    }
}
