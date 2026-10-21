package com.bytemarket.user.service;

import com.bytemarket.user.model.RolePermission;
import com.bytemarket.user.repository.IRolePermissionRepository;
import com.bytemarket.user.security.PanelModules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    /**
     * Las cuatro acciones de un módulo.
     *
     * Antes eran dos —ver y editar— y eso metía crear y eliminar en el mismo
     * saco, con lo que "registra pedidos pero no los borra" era inexpresable.
     * Separar las cuatro es lo que permite repartir responsabilidades de
     * verdad y no solo abrir o cerrar pantallas.
     */
    public record Permiso(boolean ver, boolean crear, boolean editar, boolean eliminar) {

        static final Permiso NADA = new Permiso(false, false, false, false);
        static final Permiso SOLO_VER = new Permiso(true, false, false, false);
        static final Permiso TODO = new Permiso(true, true, true, true);

        /** Si concede algo, sea lo que sea. */
        public boolean algo() {
            return ver || crear || editar || eliminar;
        }

        /**
         * Hacer algo exige poder verlo.
         *
         * Conceder crear sin ver sería un permiso inalcanzable: la pantalla no
         * se abre, así que no hay desde dónde usarlo.
         */
        Permiso normalizado() {
            return new Permiso(ver || crear || editar || eliminar, crear, editar, eliminar);
        }

        /** Lo que este permiso tiene y el otro no: lo que NO se puede delegar. */
        boolean excedeA(Permiso tope) {
            return (ver && !tope.ver()) || (crear && !tope.crear())
                    || (editar && !tope.editar()) || (eliminar && !tope.eliminar());
        }

        /** Recorta este permiso a lo que el de arriba conserve. */
        Permiso recortadoA(Permiso tope) {
            return new Permiso(ver && tope.ver(), crear && tope.crear(),
                    editar && tope.editar(), eliminar && tope.eliminar());
        }
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
     * más lo suyo. Donde hoy un rol solo puede consultar, va SOLO_VER —el
     * jefe de ventas con el inventario es el caso—; donde puede operar, van
     * las cuatro.
     */
    private static final Map<String, Map<String, Permiso>> BASE = base();

    private static Map<String, Map<String, Permiso>> base() {
        Map<String, Permiso> cajero = new LinkedHashMap<>();
        cajero.put("caja", Permiso.TODO);

        Map<String, Permiso> jefe = new LinkedHashMap<>(cajero);
        jefe.put("dashboard", Permiso.SOLO_VER);
        jefe.put("cajas", Permiso.TODO);
        jefe.put("reportes", Permiso.SOLO_VER);
        /* Solo ver: hoy el jefe hace GET de pedidos, pero cambiar el estado
           de uno cae en el comodín de order-service y es de gestión. */
        jefe.put("pedidos", Permiso.SOLO_VER);
        jefe.put("inventario", Permiso.SOLO_VER);
        jefe.put("usuarios", Permiso.TODO);

        Map<String, Permiso> encargado = new LinkedHashMap<>(jefe);
        encargado.put("reportes", Permiso.TODO);
        encargado.put("inventario", Permiso.TODO);
        /* Hereda "pedidos" del jefe, que es solo ver, y hay que subirlo:
           cambiar el estado de un pedido cae hoy en el comodín de ADMIN de
           order-service, así que dejarlo en ver le quitaría algo que ya
           hace. */
        encargado.put("pedidos", Permiso.TODO);
        encargado.put("productos", Permiso.TODO);
        encargado.put("categorias", Permiso.TODO);
        encargado.put("subcategorias", Permiso.TODO);
        encargado.put("banners", Permiso.TODO);
        encargado.put("cupones", Permiso.TODO);
        encargado.put("resenas", Permiso.TODO);
        encargado.put("reclamaciones", Permiso.TODO);
        /* El encargado administra permisos igual que el gerente, pero la
           pirámide le deja solo jefe de ventas y cajero. */
        encargado.put("roles", Permiso.TODO);

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
                repository.save(fila(new RolePermission(), rol, modulo,
                        porDefecto.getOrDefault(modulo, Permiso.NADA)));
            }
        }
    }

    private static RolePermission fila(RolePermission f, String rol, String modulo, Permiso p) {
        f.setRole(rol);
        f.setModule(modulo);
        f.setCanView(p.ver());
        f.setCanCreate(p.crear());
        f.setCanEdit(p.editar());
        f.setCanDelete(p.eliminar());
        return f;
    }

    private static Permiso desdeFila(RolePermission f) {
        return new Permiso(f.isCanView(), f.isCanCreate(), f.isCanEdit(), f.isCanDelete());
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
        Map<String, Permiso> out = new LinkedHashMap<>();
        if (PanelModules.esGerente(rol)) {
            PanelModules.ORDEN_MODULOS.forEach(m -> out.put(m, Permiso.TODO));
            return out;
        }
        PanelModules.ORDEN_MODULOS.forEach(m -> out.put(m, Permiso.NADA));
        for (RolePermission f : repository.findByRole(rol)) {
            if (!PanelModules.MODULOS.containsKey(f.getModule())) continue;
            out.put(f.getModule(), desdeFila(f));
        }
        return out;
    }

    /** La matriz completa de los roles configurables. */
    public Map<String, Map<String, Permiso>> matriz() {
        Map<String, Map<String, Permiso>> out = new LinkedHashMap<>();
        for (String rol : PanelModules.ROLES_CONFIGURABLES) out.put(rol, efectivos(rol));
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
        List<String> rechazos = new ArrayList<>();

        /*
         * Regla 3: nadie delega lo que no tiene.
         *
         * Las dos primeras reglas de la pirámide no bastan. El encargado no
         * tiene Configuración, pero sí puede editar al cajero y sí puede CREAR
         * cajeros: le concedía el módulo, se creaba una cuenta de cajero con su
         * propia contraseña, entraba con ella y acababa teniendo lo que nunca
         * tuvo. La jerarquía aguantaba y el privilegio se escapaba por abajo.
         *
         * Se compara acción por acción: quien solo consulta el inventario no
         * puede conceder a otro que lo mueva.
         *
         * El gerente no se limita: lo tiene todo, así que no hay nada que
         * pueda conceder de más.
         */
        Map<String, Permiso> propios = PanelModules.esGerente(rolEditor) ? null : efectivos(rolEditor);

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

                Permiso p = porModulo.getValue().normalizado();

                if (propios != null && p.excedeA(propios.getOrDefault(modulo, Permiso.NADA))) {
                    rechazos.add("No puedes conceder más de lo que tú tienes en "
                            + PanelModules.MODULOS.getOrDefault(modulo, modulo) + ".");
                    continue;
                }

                RolePermission destino = repository.findByRoleAndModule(rol, modulo)
                        .orElseGet(RolePermission::new);
                repository.save(fila(destino, rol, modulo, p));
                aplicados++;

                aplicados += retirarHaciaAbajo(rol, modulo, p);
            }
        }
        return new Resultado(aplicados, List.copyOf(rechazos));
    }

    /**
     * Quitar un permiso a un rol se lo quita también a los de abajo.
     *
     * Sin esto, la regla 3 solo valía en el instante de escribir. El
     * encargado tenía Reportes y se lo concedía al cajero —legal—; el gerente
     * se lo quitaba después AL ENCARGADO, y el cajero se quedaba con él. Como
     * el encargado puede crear cajeros, entraba con una cuenta de cajero y
     * recuperaba justo lo que le acababan de retirar.
     *
     * Solo cascadea el retiro, nunca la concesión: dar un permiso a un rol no
     * tiene por qué dárselo a los de abajo —para eso está la pantalla—, pero
     * quitárselo sí tiene que cerrar la puerta entera.
     */
    private int retirarHaciaAbajo(String rol, String modulo, Permiso tope) {
        if (tope.equals(Permiso.TODO)) return 0;

        int tocados = 0;
        for (String abajo : PanelModules.pordebajoDe(rol)) {
            var opcional = repository.findByRoleAndModule(abajo, modulo);
            if (opcional.isEmpty()) continue;
            RolePermission f = opcional.get();

            Permiso actual = desdeFila(f);
            Permiso recortado = actual.recortadoA(tope);
            if (recortado.equals(actual)) continue;

            repository.save(fila(f, abajo, modulo, recortado));
            tocados++;
        }
        return tocados;
    }
}
