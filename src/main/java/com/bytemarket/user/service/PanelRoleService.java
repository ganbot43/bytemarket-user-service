package com.bytemarket.user.service;

import com.bytemarket.user.model.PanelRole;
import com.bytemarket.user.repository.IPanelRoleRepository;
import com.bytemarket.user.repository.IRolePermissionRepository;
import com.bytemarket.user.repository.IUserRepository;
import com.bytemarket.user.security.PanelModules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Crear, renombrar y dar de baja puestos del panel.
 *
 * Es la pieza por la que los puestos pasaron a vivir en tabla: sin esto, el
 * gerente seguiría necesitando un INSERT a mano para inventar un "jefe de
 * almacén".
 *
 * ── La regla que lo hace seguro ──
 *
 * Solo se crean y se tocan puestos ESTRICTAMENTE por debajo del propio. Sin
 * ella, cualquiera con el módulo de Roles se inventaba un puesto de nivel 99
 * —por encima de todos—, se lo asignaba a una cuenta suya y pasaba por
 * encima de la jerarquía entera. Es la misma regla 1 de la matriz aplicada a
 * los puestos: la que gobierna los permisos no sirve de nada si el reparto
 * de puestos no la respeta.
 *
 * El gerente no se limita por nivel —está en lo alto—, pero tampoco puede
 * crear otro gerente por aquí: su clave es una constante de código y esta
 * tabla no la reparte.
 */
@Service
@RequiredArgsConstructor
public class PanelRoleService {

    /**
     * La clave acaba siendo una autoridad de Spring (`ROLE_<CLAVE>`) y una
     * columna en users.role, así que se limita a lo que sobrevive a ese
     * viaje: minúsculas, dígitos y guion bajo.
     */
    private static final Pattern CLAVE_VALIDA = Pattern.compile("^[a-z][a-z0-9_]{2,19}$");

    private final IPanelRoleRepository repository;
    private final IUserRepository userRepository;
    private final IRolePermissionRepository permisoRepository;
    private final PanelCatalogo catalogo;
    private final PermissionService permissionService;

    /** Lo que sale mal, con su motivo y su código. */
    public static class NoSePuede extends RuntimeException {
        public final int status;
        public NoSePuede(int status, String mensaje) { super(mensaje); this.status = status; }
    }

    public List<PanelRole> listar() {
        return repository.findAllByOrderByNivelAsc();
    }

    @Transactional
    public PanelRole crear(String rolEditor, String clave, String etiqueta, Integer nivel) {
        String c = clave == null ? "" : clave.trim().toLowerCase();
        if (!CLAVE_VALIDA.matcher(c).matches()) {
            throw new NoSePuede(400, "La clave debe ser de 3 a 20 letras minúsculas, dígitos o guion bajo.");
        }
        if (PanelModules.esGerente(c)) {
            throw new NoSePuede(403, "El puesto de gerente no se reparte desde aquí.");
        }
        if (repository.findByClave(c).isPresent()) {
            throw new NoSePuede(409, "Ya existe un puesto con la clave " + c + ".");
        }
        String nombre = etiqueta == null ? "" : etiqueta.trim();
        if (nombre.length() < 2) throw new NoSePuede(400, "Ponle un nombre al puesto.");
        if (nivel == null) throw new NoSePuede(400, "Falta el nivel del puesto.");

        exigirPorDebajo(rolEditor, nivel);
        exigirNivelLibre(nivel, null);

        PanelRole r = new PanelRole();
        r.setClave(c);
        r.setEtiqueta(nombre);
        r.setNivel(nivel);
        r.setDelSistema(false);
        PanelRole guardado = repository.save(r);

        catalogo.invalidar();
        /* Le crea sus filas en la matriz, todas vacías. Sin esto el puesto
           existiría pero no aparecería en la pantalla de permisos, que es
           donde se le da lo suyo. */
        permissionService.sembrar();
        return guardado;
    }

    @Transactional
    public PanelRole renombrar(String rolEditor, String clave, String etiqueta) {
        PanelRole r = buscar(clave);
        /* Renombrar sí se permite en los puestos del sistema: "Encargado" se
           puede llamar "Jefe de tienda" en otro negocio, y la clave —que es
           lo que usan la matriz y los permisos— no se toca. */
        exigirPorDebajo(rolEditor, r.getNivel());

        String nombre = etiqueta == null ? "" : etiqueta.trim();
        if (nombre.length() < 2) throw new NoSePuede(400, "Ponle un nombre al puesto.");
        r.setEtiqueta(nombre);
        PanelRole guardado = repository.save(r);
        catalogo.invalidar();
        return guardado;
    }

    @Transactional
    public PanelRole moverDeNivel(String rolEditor, String clave, Integer nivel) {
        PanelRole r = buscar(clave);
        if (r.isDelSistema()) {
            throw new NoSePuede(403,
                    "Los puestos de fábrica no cambian de nivel: la jerarquía entera se apoya en ellos.");
        }
        if (nivel == null) throw new NoSePuede(400, "Falta el nivel.");

        // Hay que poder con el puesto donde está Y donde se le quiere poner:
        // sin lo primero se movería un puesto que no se administra.
        exigirPorDebajo(rolEditor, r.getNivel());
        exigirPorDebajo(rolEditor, nivel);
        exigirNivelLibre(nivel, r.getClave());

        r.setNivel(nivel);
        PanelRole guardado = repository.save(r);
        catalogo.invalidar();
        return guardado;
    }

    @Transactional
    public void eliminar(String rolEditor, String clave) {
        PanelRole r = buscar(clave);
        if (r.isDelSistema()) {
            throw new NoSePuede(403, "Los puestos de fábrica no se eliminan.");
        }
        exigirPorDebajo(rolEditor, r.getNivel());

        long cuantos = userRepository.countByRole(r.getClave());
        if (cuantos > 0) {
            /* Borrarlo dejaría a esa gente con un rol que ya no existe: sin
               nivel, sin permisos y fuera del panel, sin un error que lo
               explicara. Primero se les cambia el puesto. */
            throw new NoSePuede(409, "Hay " + cuantos + " "
                    + (cuantos == 1 ? "persona" : "personas") + " con este puesto. "
                    + "Cámbiales el puesto antes de eliminarlo.");
        }

        permisoRepository.deleteAll(permisoRepository.findByRole(r.getClave()));
        repository.delete(r);
        catalogo.invalidar();
    }

    // ── Las reglas ───────────────────────────────────────────────

    private PanelRole buscar(String clave) {
        return repository.findByClave(clave == null ? "" : clave.trim().toLowerCase())
                .orElseThrow(() -> new NoSePuede(404, "No existe ese puesto."));
    }

    /**
     * El nivel tiene que quedar por debajo del de quien administra.
     *
     * El gerente pasa siempre: está en lo alto por código, no por nivel, así
     * que compararlo con la tabla sería preguntarle a los datos quién manda.
     */
    private void exigirPorDebajo(String rolEditor, int nivel) {
        if (PanelModules.esGerente(rolEditor)) return;

        int mio = catalogo.nivel(rolEditor);
        if (mio < 0) throw new NoSePuede(403, "Tu sesión no tiene un puesto de panel.");
        if (nivel >= mio) {
            throw new NoSePuede(403,
                    "Solo puedes crear o administrar puestos por debajo del tuyo.");
        }
    }

    /** Dos puestos al mismo nivel no podrían editarse entre ellos. */
    private void exigirNivelLibre(int nivel, String exceptoClave) {
        repository.findAllByOrderByNivelAsc().stream()
                .filter(r -> r.getNivel() == nivel)
                .filter(r -> !r.getClave().equals(exceptoClave))
                .findFirst()
                .ifPresent(r -> { throw new NoSePuede(409,
                        "El nivel " + nivel + " ya lo ocupa " + r.getEtiqueta()
                        + ". Los niveles van de diez en diez para que quepan puestos entre medias."); });
    }
}
