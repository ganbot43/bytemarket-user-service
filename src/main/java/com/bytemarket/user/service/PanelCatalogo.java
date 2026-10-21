package com.bytemarket.user.service;

import com.bytemarket.user.model.PanelModule;
import com.bytemarket.user.model.PanelRole;
import com.bytemarket.user.repository.IPanelModuleRepository;
import com.bytemarket.user.repository.IPanelRoleRepository;
import com.bytemarket.user.security.PanelModules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * El catálogo de puestos y módulos del panel, leído de la base.
 *
 * Antes era una constante compilada: crear un puesto nuevo pedía recompilar
 * y redesplegar. Ahora son dos tablas y esto es lo único que las lee, así
 * que la pirámide se calcula en un solo sitio por muchos puestos que haya.
 *
 * ── Por qué hay caché ──
 *
 * Esto se consulta en CADA petición a /api/admin —el portero traduce la ruta
 * a su módulo y pregunta el nivel de quien llama—, y el catálogo cambia
 * cuando alguien crea un puesto, que no es cada segundo. Sin caché serían
 * dos consultas por petición para una respuesta que no cambia.
 *
 * Se invalida al escribir, no por tiempo: aquí el que escribe y el que lee
 * son el mismo servicio, así que no hace falta adivinar cuándo caducó.
 *
 * ── Lo que NO vive aquí ──
 *
 * El gerente. Su clave es una constante en código (PanelModules.GERENTE) y
 * `esGerente` la compara contra ella, no contra la tabla. Si dependiera de
 * los datos, una fila mal guardada —o borrada— lo dejaría fuera de su propio
 * sistema sin forma de volver a entrar. La tabla dice quién hay; el código
 * dice quién manda pase lo que pase.
 */
@Service
@RequiredArgsConstructor
public class PanelCatalogo {

    private final IPanelRoleRepository roleRepository;
    private final IPanelModuleRepository moduleRepository;

    private volatile List<PanelRole> puestos;
    private volatile List<PanelModule> modulos;

    // ── Valores de fábrica ───────────────────────────────────────

    /**
     * Los cuatro puestos y los diecisiete módulos con los que nació el panel.
     *
     * Solo se usan para sembrar lo que falte. Son los mismos que estaban
     * compilados, así que pasar a tabla no cambia nada de lo que el sistema
     * hacía: una migración que además cambia el comportamiento no se puede
     * depurar, porque cualquier cosa rara puede ser del cambio o de ella.
     */
    private record Fabrica(String clave, String etiqueta, String grupo) {}

    private static final List<Fabrica> MODULOS_DE_FABRICA = List.of(
            new Fabrica("dashboard", "Dashboard", "Operación"),
            new Fabrica("caja", "Caja", "Operación"),
            new Fabrica("cajas", "Cajas", "Operación"),
            new Fabrica("reportes", "Reportes", "Operación"),
            new Fabrica("pedidos", "Pedidos", "Operación"),
            new Fabrica("inventario", "Inventario", "Operación"),
            new Fabrica("productos", "Productos", "Tienda"),
            new Fabrica("categorias", "Categorías", "Tienda"),
            new Fabrica("subcategorias", "Subcategorías", "Tienda"),
            new Fabrica("banners", "Banners", "Tienda"),
            new Fabrica("cupones", "Cupones", "Tienda"),
            new Fabrica("resenas", "Reseñas", "Tienda"),
            new Fabrica("reclamaciones", "Libro de Reclamaciones", "Tienda"),
            new Fabrica("usuarios", "Usuarios", "Sistema"),
            new Fabrica("roles", "Roles y permisos", "Sistema"),
            new Fabrica("metodos_pago", "Métodos de Pago", "Sistema"),
            new Fabrica("configuracion", "Configuración", "Sistema"));

    private record PuestoDeFabrica(String clave, String etiqueta, int nivel) {}

    /**
     * Los niveles van de diez en diez, no de uno en uno.
     *
     * Con 0, 1, 2, 3 no cabe un puesto ENTRE dos existentes, y poner "jefe de
     * almacén" entre el cajero y el jefe de ventas obligaría a renumerar a
     * todos —tocando la columna que sostiene la pirámide, que es lo último
     * que conviene mover—. Con huecos de diez entran nueve puestos entre cada
     * par sin reordenar nada, que es justo la libertad que esta tabla viene a
     * dar.
     */
    private static final List<PuestoDeFabrica> PUESTOS_DE_FABRICA = List.of(
            new PuestoDeFabrica(PanelModules.CAJERO, "Cajero", 10),
            new PuestoDeFabrica(PanelModules.JEFE_VENTAS, "Jefe de ventas", 20),
            new PuestoDeFabrica(PanelModules.ENCARGADO, "Encargado", 30),
            new PuestoDeFabrica(PanelModules.GERENTE, "Gerente", 40));

    /** Crea lo que falte. Idempotente: no pisa lo que ya esté configurado. */
    @Transactional
    public void sembrar() {
        for (PuestoDeFabrica p : PUESTOS_DE_FABRICA) {
            if (roleRepository.findByClave(p.clave()).isPresent()) continue;
            PanelRole r = new PanelRole();
            r.setClave(p.clave());
            r.setEtiqueta(p.etiqueta());
            r.setNivel(p.nivel());
            r.setDelSistema(true);
            roleRepository.save(r);
        }
        /* Mismo criterio que con los niveles: de diez en diez para poder
           colocar un módulo nuevo entre dos sin renumerar la lista. */
        int orden = 0;
        for (Fabrica m : MODULOS_DE_FABRICA) {
            orden += 10;
            if (moduleRepository.findByClave(m.clave()).isPresent()) continue;
            PanelModule x = new PanelModule();
            x.setClave(m.clave());
            x.setEtiqueta(m.etiqueta());
            x.setGrupo(m.grupo());
            x.setOrden(orden);
            moduleRepository.save(x);
        }
        invalidar();
    }

    /** Lo llama quien escriba en las tablas, para que la caché no mienta. */
    public void invalidar() {
        puestos = null;
        modulos = null;
    }

    // ── Lectura ──────────────────────────────────────────────────

    private List<PanelRole> puestos() {
        List<PanelRole> p = puestos;
        if (p == null) {
            p = roleRepository.findAllByOrderByNivelAsc();
            puestos = p;
        }
        return p;
    }

    private List<PanelModule> modulos() {
        List<PanelModule> m = modulos;
        if (m == null) {
            m = moduleRepository.findAllByOrderByOrdenAsc();
            modulos = m;
        }
        return m;
    }

    /** Clave → etiqueta, en el orden en que se muestran. */
    public Map<String, String> modulosConEtiqueta() {
        Map<String, String> out = new LinkedHashMap<>();
        modulos().forEach(m -> out.put(m.getClave(), m.getEtiqueta()));
        return out;
    }

    public List<String> ordenDeModulos() {
        return modulos().stream().map(PanelModule::getClave).toList();
    }

    public boolean existeModulo(String clave) {
        return modulos().stream().anyMatch(m -> m.getClave().equals(clave));
    }

    /** Los grupos, en el orden en que aparecen los módulos. */
    public List<String> grupos() {
        return modulos().stream().map(PanelModule::getGrupo).distinct().toList();
    }

    public String grupoDe(String modulo) {
        return modulos().stream().filter(m -> m.getClave().equals(modulo))
                .findFirst().map(PanelModule::getGrupo).orElse("Sistema");
    }

    // ── La pirámide ──────────────────────────────────────────────

    /** Nivel del puesto; -1 si no es un puesto de panel (p. ej. customer). */
    public int nivel(String rol) {
        return puestos().stream().filter(p -> p.getClave().equals(rol))
                .findFirst().map(PanelRole::getNivel).orElse(-1);
    }

    /** Los puestos que se guardan en la matriz: todos menos el gerente. */
    public List<String> rolesConfigurables() {
        return puestos().stream().map(PanelRole::getClave)
                .filter(c -> !PanelModules.esGerente(c)).toList();
    }

    /** De mayor a menor: para quedarse con el puesto más alto de una sesión. */
    public List<String> deMayorAMenor() {
        return puestos().stream()
                .sorted((a, b) -> b.getNivel() - a.getNivel())
                .map(PanelRole::getClave).toList();
    }

    public Map<String, String> etiquetasDeRol() {
        Map<String, String> out = new LinkedHashMap<>();
        puestos().forEach(p -> out.put(p.getClave(), p.getEtiqueta()));
        return out;
    }

    public String etiquetaDeRol(String rol) {
        return etiquetasDeRol().getOrDefault(rol, rol);
    }

    public boolean esDelSistema(String rol) {
        return puestos().stream().filter(p -> p.getClave().equals(rol))
                .findFirst().map(PanelRole::isDelSistema).orElse(false);
    }

    /**
     * Regla 1 de la pirámide: solo hacia abajo, y nunca a uno mismo.
     *
     * Lo de "estrictamente" es el punto: con >= en vez de >, un encargado
     * podría reescribir los permisos del encargado —es decir, los suyos— y
     * la jerarquía dejaría de significar nada.
     */
    public boolean puedeEditar(String rolEditor, String rolDestino) {
        if (PanelModules.esGerente(rolDestino)) return false;
        int editor = nivel(rolEditor);
        int destino = nivel(rolDestino);
        return editor >= 0 && destino >= 0 && editor > destino;
    }

    public List<String> editablesPor(String rolEditor) {
        return rolesConfigurables().stream().filter(r -> puedeEditar(rolEditor, r)).toList();
    }

    /** Los puestos estrictamente por debajo de uno, de menor a mayor. */
    public List<String> pordebajoDe(String rol) {
        int n = nivel(rol);
        if (n < 0) return List.of();
        return rolesConfigurables().stream().filter(r -> nivel(r) < n).toList();
    }
}
