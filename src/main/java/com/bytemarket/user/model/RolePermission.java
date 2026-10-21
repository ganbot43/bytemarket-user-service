package com.bytemarket.user.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Qué módulos del panel puede ver y tocar cada rol.
 *
 * Antes esto estaba escrito a mano en tres sitios a la vez —los
 * SecurityConfig de cuatro servicios, los middleware de Nuxt y la lista del
 * menú—, así que mover un permiso era editar código y volver a desplegar.
 * Esta tabla es la única fuente de verdad y el gerente la edita desde el
 * panel.
 *
 * El rol superadmin NO tiene filas aquí, y es a propósito: el gerente lo
 * puede todo por código y no por datos. Si estuviera en la tabla, un guardado
 * mal hecho podría dejarlo fuera de su propio sistema sin forma de volver a
 * entrar.
 *
 * Ver y editar van separados porque hay permisos que ya existen así: el jefe
 * de ventas consulta el inventario para cuadrar, pero dar de alta un
 * movimiento es de quien reabastece.
 */
@Data
@Entity
@Table(
        name = "role_permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_role_module", columnNames = {"role", "module"}))
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 20, nullable = false)
    private String role;

    /** Clave del módulo; las válidas están en PanelModules. */
    @Column(length = 40, nullable = false)
    private String module;

    @Column(name = "can_view", nullable = false)
    private boolean canView;

    @Column(name = "can_edit", nullable = false)
    private boolean canEdit;
}
