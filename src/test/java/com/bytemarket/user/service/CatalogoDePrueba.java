package com.bytemarket.user.service;

import com.bytemarket.user.model.PanelModule;
import com.bytemarket.user.model.PanelRole;
import com.bytemarket.user.repository.IPanelModuleRepository;
import com.bytemarket.user.repository.IPanelRoleRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Un PanelCatalogo de verdad sobre dos repositorios en memoria.
 *
 * Desde que los puestos y los módulos viven en tabla, casi todo lo demás
 * necesita un catálogo para funcionar. Simularlo a mano en cada test llevaba
 * a simular también la pirámide, que es justo la lógica que se quiere
 * probar. Aquí se usa la clase real y solo se finge la base.
 *
 * Se siembra con los valores de fábrica, así que los tests siguen hablando
 * de cajero, jefe de ventas, encargado y gerente.
 */
public final class CatalogoDePrueba {

    private CatalogoDePrueba() {}

    /** Catálogo con los cuatro puestos y los diecisiete módulos de fábrica. */
    public static PanelCatalogo deFabrica() {
        return con(List.of());
    }

    /**
     * Igual, más los puestos que se le añadan.
     *
     * Es lo que permite probar lo que antes era imposible: que un puesto
     * creado desde el panel se comporte como los de fábrica.
     */
    public static PanelCatalogo con(List<PanelRole> extra) {
        IPanelRoleRepository roles = mock(IPanelRoleRepository.class);
        IPanelModuleRepository modulos = mock(IPanelModuleRepository.class);

        List<PanelRole> filasRol = new ArrayList<>();
        List<PanelModule> filasMod = new ArrayList<>();

        when(roles.findByClave(any())).thenAnswer(i ->
                filasRol.stream().filter(r -> r.getClave().equals(i.getArgument(0))).findFirst());
        when(modulos.findByClave(any())).thenAnswer(i ->
                filasMod.stream().filter(m -> m.getClave().equals(i.getArgument(0))).findFirst());
        when(roles.save(any(PanelRole.class))).thenAnswer(i -> {
            filasRol.add(i.getArgument(0)); return i.getArgument(0);
        });
        when(modulos.save(any(PanelModule.class))).thenAnswer(i -> {
            filasMod.add(i.getArgument(0)); return i.getArgument(0);
        });
        when(roles.findAllByOrderByNivelAsc()).thenAnswer(i ->
                filasRol.stream().sorted(Comparator.comparingInt(PanelRole::getNivel)).toList());
        when(modulos.findAllByOrderByOrdenAsc()).thenAnswer(i ->
                filasMod.stream().sorted(Comparator.comparingInt(PanelModule::getOrden)).toList());

        PanelCatalogo catalogo = new PanelCatalogo(roles, modulos);
        catalogo.sembrar();
        extra.forEach(r -> { filasRol.add(r); });
        catalogo.invalidar();
        return catalogo;
    }

    /** Un puesto nuevo, de los que el gerente podrá crear desde el panel. */
    public static PanelRole puesto(String clave, String etiqueta, int nivel) {
        PanelRole r = new PanelRole();
        r.setClave(clave);
        r.setEtiqueta(etiqueta);
        r.setNivel(nivel);
        r.setDelSistema(false);
        return r;
    }

    static Optional<PanelRole> nada() {
        return Optional.empty();
    }
}
