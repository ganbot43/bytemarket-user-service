package com.bytemarket.user.service;

import com.bytemarket.user.model.RolePermission;
import com.bytemarket.user.repository.IRolePermissionRepository;
import com.bytemarket.user.security.PanelModules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee y escribe la matriz de permisos, haciendo cumplir la pirámide.
 *
 * Las reglas no están en el controlador porque también las necesita el
 * endpoint interno que consultan los otros servicios: si viven aquí, no hay
 * dos caminos para llegar a la misma decisión.
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    /** Ver y editar, que no siempre van juntos. */
    public record Permiso(boolean ver, boolean editar) {
        static final Permiso NADA = new Permiso(false, false);
        static final Permiso SOLO_VER = new Permiso(true, false);
        static final Permiso COMPLETO = new Permiso(true, true);
    }

    private final IRolePermissionRepository repository;

    /**
     * Los valores por defecto, que son EXACTAMENTE lo que hoy está escrito a
     * mano en los SecurityConfig y en el menú del panel.
     *
     * Es deliberado: al encender esto, nadie gana ni pierde un permiso hasta
     * que el gerente toque algo. Una migración que además cambia el
     * comportamiento es imposible de depurar, porque cualquier cosa rara
     * puede ser del cambio o de la migración.
     *
     * Y son piramidales por construcción: cada nivel tiene lo del de abajo
     * más lo suyo. El jefe de ventas consulta el inventario pero no lo
     * mueve —eso ya era así en catalog, donde solo el GET le está
     * permitido—, y de ahí viene que ver y editar sean dos cosas.
     */
    private static final Map<String, Map<String, Permiso>> BASE = base();

    private static Map<String, Map<String, Permiso>> base() {
        Map<String, Permiso> cajero = new LinkedHashMap<>();
        cajero.put("caja", Permiso.COMPLETO);

        Map<String, Permiso> jefe = new LinkedHashMap<>(cajero);
        jefe.put("dashboard", Permiso.SOLO_VER);
        jefe.put("cajas", Permiso.COMPLETO);
        jefe.put("reportes", Permiso.SOLO_VER);
        /* Solo ver: hoy el jefe hace GET de pedidos, pero cambiar el estado
           de uno cae en el comodín de order-service y es de gestión. */
        jefe.put("pedidos", Permiso.SOLO_VER);
        jefe.put("inventario", Permiso.SOLO_VER);
        jefe.put("usuarios", Permiso.COMPLETO);

        Map<String, Permiso> encargado = new LinkedHashMap<>(jefe);
        encargado.put("reportes", Permiso.COMPLETO);
        encargado.put("inventario", Permiso.COMPLETO);
        encargado.put("productos", Permiso.COMPLETO);
        encargado.put("categorias", Permiso.COMPLETO);
        encargado.put("subcategorias", Permiso.COMPLETO);
        encargado.put("banners", Permiso.COMPLETO);
        encargado.put("cupones", Permiso.COMPLETO);
        encargado.put("resenas", Permiso.COMPLETO);
        encargado.put("reclamaciones", Permiso.COMPLETO);
        /* El encargado administra permisos igual que el gerente, pero la
           pirámide le deja solo jefe de ventas y cajero. */
        encargado.put("roles", Permiso.COMPLETO);

        return Map.of(
                PanelModules.CAJERO, Map.copyOf(cajero),
                PanelModules.JEFE_VENTAS, Map.copyOf(jefe),
                PanelModules.ENCARGADO, Map.copyOf(encargado));
    }

    /** Crea las filas que falten. Idempotente: no pisa lo ya configurado. */
    @Transactional
    public void sembrar() {
        for (String rol : PanelModules.ROLES_CONFIGURABLES) {
            Map<String, Permiso> porDefecto = BASE.getOrDefault(rol, Map.of());
            for (String modulo : PanelModules.ORDEN_MODULOS) {
                if (repository.findByRoleAndModule(rol, modulo).isPresent()) continue;
                Permiso p = porDefecto.getOrDefault(modulo, Permiso.NADA);
                RolePermission fila = new RolePermission();
                fila.setRole(rol);
                fila.setModule(modulo);
                fila.setCanView(p.ver());
                fila.setCanEdit(p.editar());
                repository.save(fila);
            }
        }
    }

    /**
     * Los permisos de un rol.
     *
     * El gerente no se consulta en la tabla: devuelve todo en true. Es la
     * regla 2 de la pirámide y vive aquí para que da igual por qué camino se
     * pregunte —el panel, el endpoint interno o otro servicio— la respuesta
     * sea la misma.
     */
    public Map<String, Permiso> efectivos(String rol) {
        if (PanelModules.esGerente(rol)) {
            Map<String, Permiso> todo = new LinkedHashMap<>();
            PanelModules.ORDEN_MODULOS.forEach(m -> todo.put(m, Permiso.COMPLETO));
            return todo;
        }
        Map<String, Permiso> out = new LinkedHashMap<>();
        PanelModules.ORDEN_MODULOS.forEach(m -> out.put(m, Permiso.NADA));
        for (RolePermission fila : repository.findByRole(rol)) {
            if (!PanelModules.MODULOS.containsKey(fila.getModule())) continue;
            out.put(fila.getModule(), new Permiso(fila.isCanView(), fila.isCanEdit()));
        }
        return out;
    }

    /** La matriz completa de los roles configurables. */
    public Map<String, Map<String, Permiso>> matriz() {
        Map<String, Map<String, Permiso>> out = new LinkedHashMap<>();
        for (String rol : PanelModules.ROLES_CONFIGURABLES) {
            out.put(rol, efectivos(rol));
        }
        return out;
    }

    /** Lo que devuelve guardar(): qué se aplicó y qué se rechazó. */
    public record Resultado(int aplicados, List<String> rechazos) {}

    /**
     * Guarda cambios en nombre de quien los pide.
     *
     * Cada cambio se valida contra la pirámide por separado en vez de
     * comprobar el lote entero: así un cambio inválido mezclado con válidos
     * no tira los buenos, y el que llama se entera exactamente de qué no
     * pasó. Rechazar todo el lote en silencio sería peor.
     */
    @Transactional
    public Resultado guardar(String rolEditor, Map<String, Map<String, Permiso>> cambios) {
        int aplicados = 0;
        List<String> rechazos = new java.util.ArrayList<>();

        /*
         * Regla 3: nadie delega lo que no tiene.
         *
         * Las dos reglas de la pirámide no bastan. El encargado no tiene
         * Configuración, pero sí puede editar al cajero y sí puede crear
         * cajeros: le concedía Configuración al cajero, se creaba una cuenta
         * de cajero con su propia contraseña, entraba con ella y acababa
         * teniendo lo que nunca tuvo. La jerarquía aguantaba y el privilegio
         * se escapaba por abajo.
         *
         * El gerente no se limita: lo tiene todo, así que no hay nada que
         * pueda conceder de más.
         *
         * QUITAR sí está permitido aunque no tengas el permiso: reducir lo
         * de otro no amplía lo tuyo, y un encargado tiene que poder recortar
         * a un cajero sin necesitar antes el permiso que le recorta.
         */
        Map<String, Permiso> propios = PanelModules.esGerente(rolEditor)
                ? null : efectivos(rolEditor);

        for (var porRol : cambios.entrySet()) {
            String rol = porRol.getKey();

            if (PanelModules.esGerente(rol)) {
                rechazos.add("Los permisos del gerente no se editan: los tiene todos siempre.");
                continue;
            }
            if (!PanelModules.puedeEditar(rolEditor, rol)) {
                rechazos.add("No puedes editar los permisos de "
                        + PanelModules.ETIQUETA_ROL.getOrDefault(rol, rol)
                        + ": solo los de quienes están por debajo de ti.");
                continue;
            }

            for (var porModulo : porRol.getValue().entrySet()) {
                String modulo = porModulo.getKey();
                if (!PanelModules.MODULOS.containsKey(modulo)) {
                    rechazos.add("Módulo desconocido: " + modulo);
                    continue;
                }
                Permiso p = porModulo.getValue();

                if (propios != null) {
                    Permiso mio = propios.getOrDefault(modulo, Permiso.NADA);
                    if ((p.ver() && !mio.ver()) || (p.editar() && !mio.editar())) {
                        rechazos.add("No puedes conceder "
                                + PanelModules.MODULOS.getOrDefault(modulo, modulo)
                                + ": tú no lo tienes.");
                        continue;
                    }
                }

                RolePermission fila = repository.findByRoleAndModule(rol, modulo)
                        .orElseGet(() -> {
                            RolePermission nueva = new RolePermission();
                            nueva.setRole(rol);
                            nueva.setModule(modulo);
                            return nueva;
                        });
                /* Editar sin ver no significa nada: la pantalla no se abre,
                   así que el permiso de escritura sería inalcanzable. */
                fila.setCanView(p.ver() || p.editar());
                fila.setCanEdit(p.editar());
                repository.save(fila);
                aplicados++;

                aplicados += retirarHaciaAbajo(rol, modulo, fila.isCanView(), fila.isCanEdit());
            }
        }
        return new Resultado(aplicados, List.copyOf(rechazos));
    }

    /**
     * Quitar un permiso a un rol se lo quita también a los de abajo.
     *
     * Sin esto, la regla 3 solo valía en el instante de escribir. El
     * encargado tenía Reportes y se lo concedía al cajero —legal—; el
     * gerente se lo quitaba después AL ENCARGADO, y el cajero se quedaba
     * con él. Como el encargado puede crear cajeros, entraba con una cuenta
     * de cajero y recuperaba justo lo que le acababan de retirar.
     *
     * Solo cascadea el retiro, nunca la concesión: dar un permiso a un rol
     * no tiene por qué dárselo a los de abajo —para eso está la pantalla—,
     * pero quitárselo sí tiene que cerrar la puerta entera.
     */
    private int retirarHaciaAbajo(String rol, String modulo, boolean ver, boolean editar) {
        if (ver && editar) return 0;

        int tocados = 0;
        for (String abajo : PanelModules.pordebajoDe(rol)) {
            var opcional = repository.findByRoleAndModule(abajo, modulo);
            if (opcional.isEmpty()) continue;
            RolePermission fila = opcional.get();

            boolean nuevoVer = fila.isCanView() && ver;
            boolean nuevoEditar = fila.isCanEdit() && editar;
            if (nuevoVer == fila.isCanView() && nuevoEditar == fila.isCanEdit()) continue;

            fila.setCanView(nuevoVer);
            fila.setCanEdit(nuevoEditar);
            repository.save(fila);
            tocados++;
        }
        return tocados;
    }
}
