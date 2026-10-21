package com.bytemarket.user.service;

import com.bytemarket.user.security.PanelModules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El catálogo de puestos y módulos, ahora leído de la base.
 *
 * Estaba compilado y esto comprobaba que sus tres listas paralelas no se
 * separaran. Al pasar a tabla esa clase de error desaparece —hay una fila
 * por módulo, con su etiqueta y su grupo dentro— y aparece otra: que la
 * pirámide dependa de datos que alguien puede editar.
 *
 * Lo que se comprueba aquí es que sembrar deja el sistema exactamente como
 * estaba, que la jerarquía sigue siendo estricta, y —lo nuevo— que un
 * puesto creado desde el panel se comporta como los de fábrica.
 */
@DisplayName("Catálogo — puestos y módulos en tabla")
class PanelCatalogoTest {

    @Test
    @DisplayName("sembrar deja los cuatro puestos y los diecisiete módulos")
    void sembrarDejaLoDeSiempre() {
        PanelCatalogo c = CatalogoDePrueba.deFabrica();

        assertEquals(17, c.ordenDeModulos().size());
        assertEquals(List.of("Operación", "Tienda", "Sistema"), c.grupos());
        assertEquals(List.of(PanelModules.CAJERO, PanelModules.JEFE_VENTAS, PanelModules.ENCARGADO),
                c.rolesConfigurables(), "el gerente no se configura");

        for (String m : c.ordenDeModulos()) {
            assertFalse(c.modulosConEtiqueta().get(m).isBlank(), m + " sin etiqueta");
            assertTrue(c.grupos().contains(c.grupoDe(m)), m + " en un grupo que no existe");
        }
    }

    @Test
    @DisplayName("sembrar dos veces no duplica nada")
    void sembrarEsIdempotente() {
        PanelCatalogo c = CatalogoDePrueba.deFabrica();
        int modulos = c.ordenDeModulos().size();
        c.sembrar();
        assertEquals(modulos, c.ordenDeModulos().size());
    }

    @Test
    @DisplayName("la pirámide es estricta: sin empates y ordenada")
    void laPiramideEsEstricta() {
        PanelCatalogo c = CatalogoDePrueba.deFabrica();

        /* Un empate rompería la regla 1: con dos puestos al mismo nivel,
           ninguno podría editar al otro y la jerarquía dejaría un hueco. La
           columna es UNIQUE en base, y esto lo comprueba del lado del
           código. */
        List<Integer> niveles = c.deMayorAMenor().stream().map(c::nivel).toList();
        assertEquals(niveles.size(), niveles.stream().distinct().count(), "niveles repetidos");
        for (int i = 1; i < niveles.size(); i++) {
            assertTrue(niveles.get(i - 1) > niveles.get(i), "deMayorAMenor no viene ordenada");
        }
    }

    @Test
    @DisplayName("el gerente manda por código, aunque su fila diga otra cosa")
    void elGerenteNoDependeDeLaTabla() {
        /* La regla 2. Si su poder saliera de la tabla, bastaría editarle el
           nivel —o borrar la fila— para dejar el sistema sin quien reparta
           permisos. */
        PanelCatalogo c = CatalogoDePrueba.con(List.of(
                CatalogoDePrueba.puesto("impostor", "Impostor", 99)));

        assertFalse(c.rolesConfigurables().contains(PanelModules.GERENTE));
        for (String otro : c.rolesConfigurables()) {
            assertFalse(c.puedeEditar(otro, PanelModules.GERENTE), otro + " no toca al gerente");
        }
        // Ni el puesto inventado con nivel 99.
        assertFalse(c.puedeEditar("impostor", PanelModules.GERENTE));
    }

    @Test
    @DisplayName("un puesto creado desde el panel entra en la pirámide como los demás")
    void unPuestoNuevoSeComportaIgual() {
        /* Esto es lo que los puestos en tabla vienen a permitir, y antes era
           imposible: "jefe de almacén" ENTRE el cajero (10) y el jefe de
           ventas (20), sin recompilar y sin renumerar a nadie. Los niveles
           van de diez en diez justo para que quepa. */
        PanelCatalogo c = CatalogoDePrueba.con(List.of(
                CatalogoDePrueba.puesto("jefe_almacen", "Jefe de almacén", 15)));

        assertEquals(15, c.nivel("jefe_almacen"));
        assertTrue(c.nivel(PanelModules.CAJERO) < c.nivel("jefe_almacen"));
        assertTrue(c.nivel("jefe_almacen") < c.nivel(PanelModules.JEFE_VENTAS),
                "queda encajado entre los dos, que es lo que no se podía hacer antes");
        assertTrue(c.rolesConfigurables().contains("jefe_almacen"),
                "un puesto nuevo se puede configurar");
        assertTrue(c.puedeEditar(PanelModules.GERENTE, "jefe_almacen"),
                "y el gerente lo administra");
        assertTrue(c.puedeEditar("jefe_almacen", PanelModules.CAJERO),
                "y él administra a los de abajo");
        assertFalse(c.puedeEditar(PanelModules.CAJERO, "jefe_almacen"));
        assertFalse(c.puedeEditar("jefe_almacen", PanelModules.JEFE_VENTAS),
                "y no administra a quien tiene por encima");
        assertTrue(c.pordebajoDe("jefe_almacen").contains(PanelModules.CAJERO));
    }

    @Test
    @DisplayName("editablesPor y pordebajoDe dicen lo mismo")
    void lasDosVistasCoinciden() {
        /* Una decide qué puede editar la pantalla y la otra hasta dónde
           cascadea un retiro. Si dijeran cosas distintas, se podría conceder
           a alguien a quien luego la cascada no alcanza. */
        PanelCatalogo c = CatalogoDePrueba.con(List.of(
                CatalogoDePrueba.puesto("jefe_almacen", "Jefe de almacén", 5)));
        for (String rol : c.deMayorAMenor()) {
            assertEquals(c.pordebajoDe(rol), c.editablesPor(rol), "no coinciden para " + rol);
        }
    }

    @Test
    @DisplayName("quien no es del panel no tiene nivel ni administra a nadie")
    void losDeFueraNoEstanEnLaPiramide() {
        PanelCatalogo c = CatalogoDePrueba.deFabrica();
        assertEquals(-1, c.nivel("customer"));
        assertTrue(c.editablesPor("customer").isEmpty());
        assertTrue(c.pordebajoDe("customer").isEmpty());
    }

    @Test
    @DisplayName("las cuatro acciones siguen siendo de código, no de la tabla")
    void lasAccionesNoSonConfigurables() {
        /* Añadir una acción no es configurar: es cambiar cómo los cuatro
           servicios traducen un verbo HTTP a un permiso. */
        assertEquals(4, PanelModules.ACCIONES.size());
        for (var a : PanelModules.ACCIONES) {
            for (String campo : List.of("key", "label", "help")) {
                assertNotNull(a.get(campo), "falta " + campo);
                assertFalse(a.get(campo).isBlank());
            }
        }
    }
}
