package com.bytemarket.user.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Un puesto del panel.
 *
 * Estaban compilados en PanelModules, así que crear un puesto nuevo —"jefe
 * de almacén", "auditor"— pedía recompilar y redesplegar los siete
 * servicios. En tabla, el gerente puede inventarlos sobre la marcha, que es
 * lo que hace falta para que esto sirva de base a un ERP.
 *
 * ── El nivel es la pirámide ──
 *
 * De él salen las dos reglas que impiden que la matriz sea una vía de
 * escalada: cada puesto solo edita a los de nivel estrictamente menor, y un
 * retiro cascadea hacia abajo. Por eso es ÚNICO: dos puestos al mismo nivel
 * no podrían editarse entre ellos y la jerarquía dejaría un hueco.
 *
 * ── Lo que no se puede tocar ──
 *
 * `delSistema` marca los cuatro puestos de fábrica. No se borran ni se les
 * cambia el nivel, y es lo que impide el final tonto: alguien edita el nivel
 * del gerente, se queda por encima, y ya no hay quien reparta permisos. El
 * gerente además sigue siendo una constante en código (PanelModules.GERENTE)
 * y no una fila: su poder no depende de que esta tabla esté bien.
 */
@Data
@Entity
@Table(name = "panel_roles")
public class PanelRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** La que se guarda en users.role y en role_permissions.role. */
    @Column(length = 20, nullable = false, unique = true)
    private String clave;

    @Column(length = 60, nullable = false)
    private String etiqueta;

    @Column(nullable = false, unique = true)
    private int nivel;

    @Column(name = "del_sistema", nullable = false)
    private boolean delSistema;
}
