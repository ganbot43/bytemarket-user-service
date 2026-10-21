package com.bytemarket.user.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El catálogo no se puede separar de sí mismo.
 *
 * Antes las mismas diecisiete claves estaban escritas en tres listas
 * paralelas —etiquetas, orden y grupos— y nada impedía que se separaran:
 * añadir un módulo a dos y olvidar la tercera lo dejaba sin grupo, o sin
 * sembrar, o rechazado al guardar, sin un solo error que lo dijera. Ahora se
 * derivan de una sola declaración, y esto lo comprueba para que nadie
 * reintroduzca la copia "por comodidad".
 */
@DisplayName("Permisos — el catálogo de módulos y puestos es coherente")
class PanelModulesTest {

    @Test
    @DisplayName("las tres vistas de los módulos hablan de las mismas claves")
    void lasVistasNoSeSeparan() {
        assertEquals(PanelModules.ORDEN_MODULOS.size(), PanelModules.MODULOS.size());
        assertEquals(PanelModules.ORDEN_MODULOS, List.copyOf(PanelModules.MODULOS.keySet()),
                "el orden declarado y el del mapa tienen que ser el mismo");

        for (String clave : PanelModules.ORDEN_MODULOS) {
            assertNotNull(PanelModules.MODULOS.get(clave), clave + " no tiene etiqueta");
            assertFalse(PanelModules.MODULOS.get(clave).isBlank(), clave + " tiene etiqueta vacía");
            assertTrue(PanelModules.GRUPOS.contains(PanelModules.grupoDe(clave)),
                    clave + " cae en un grupo que no existe: " + PanelModules.grupoDe(clave));
        }
    }

    @Test
    @DisplayName("no hay claves repetidas")
    void sinClavesRepetidas() {
        assertEquals(PanelModules.ORDEN_MODULOS.size(),
                PanelModules.ORDEN_MODULOS.stream().distinct().count(),
                "una clave repetida haría que un módulo pisara los permisos de otro");
    }

    @Test
    @DisplayName("la pirámide es estricta: un nivel por puesto y sin empates")
    void laPiramideEsEstricta() {
        /* Un empate rompería la regla 1: con dos puestos al mismo nivel,
           ninguno podría editar al otro y la jerarquía dejaría un hueco. */
        List<Integer> niveles = PanelModules.DE_MAYOR_A_MENOR.stream()
                .map(PanelModules::nivel).toList();
        assertEquals(niveles.size(), niveles.stream().distinct().count(), "niveles repetidos");

        for (int i = 1; i < niveles.size(); i++) {
            assertTrue(niveles.get(i - 1) > niveles.get(i),
                    "DE_MAYOR_A_MENOR tiene que venir ordenada de verdad");
        }
    }

    @Test
    @DisplayName("el gerente está por encima de todos y fuera de la tabla")
    void elGerenteMandaYNoSeConfigura() {
        assertFalse(PanelModules.ROLES_CONFIGURABLES.contains(PanelModules.GERENTE),
                "si entrara en la tabla, un guardado mal hecho podría dejarlo fuera del sistema");

        for (String otro : PanelModules.ROLES_CONFIGURABLES) {
            assertTrue(PanelModules.nivel(PanelModules.GERENTE) > PanelModules.nivel(otro),
                    "el gerente debe estar por encima de " + otro);
            assertTrue(PanelModules.puedeEditar(PanelModules.GERENTE, otro));
            assertFalse(PanelModules.puedeEditar(otro, PanelModules.GERENTE),
                    otro + " no puede tocar al gerente");
        }
    }

    @Test
    @DisplayName("editablesPor y pordebajoDe dicen lo mismo")
    void lasDosVistasDeLaPiramideCoinciden() {
        /* Una decide qué puede editar la pantalla y la otra hasta dónde
           cascadea un retiro. Si dijeran cosas distintas, se podría conceder
           a alguien a quien luego la cascada no alcanza. */
        for (String rol : PanelModules.DE_MAYOR_A_MENOR) {
            assertEquals(PanelModules.pordebajoDe(rol), PanelModules.editablesPor(rol),
                    "no coinciden para " + rol);
        }
    }

    @Test
    @DisplayName("un rol que no es del panel no tiene nivel ni edita a nadie")
    void losDeFueraNoEstanEnLaPiramide() {
        assertEquals(-1, PanelModules.nivel("customer"));
        assertTrue(PanelModules.editablesPor("customer").isEmpty());
        assertTrue(PanelModules.pordebajoDe("customer").isEmpty());
    }

    @Test
    @DisplayName("las cuatro acciones vienen con clave, etiqueta y ayuda")
    void lasAccionesEstanCompletas() {
        assertEquals(4, PanelModules.ACCIONES.size());
        for (var a : PanelModules.ACCIONES) {
            for (String campo : List.of("key", "label", "help")) {
                assertNotNull(a.get(campo), "falta " + campo + " en " + a);
                assertFalse(a.get(campo).isBlank());
            }
        }
    }
}
