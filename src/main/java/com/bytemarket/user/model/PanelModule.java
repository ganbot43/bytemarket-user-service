package com.bytemarket.user.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Un módulo del panel: una pantalla cuyo acceso se reparte.
 *
 * Igual que los puestos, estaban compilados. En tabla, añadir un módulo deja
 * de ser recompilar: se inserta la fila, aparece en la matriz y el gerente
 * ya puede repartirlo.
 *
 * Ojo con lo que NO cambia: qué ruta de API pertenece a qué módulo sigue
 * viviendo en el RutasDeModulo de cada servicio, porque es conocimiento de
 * ese servicio y no del catálogo. Un módulo nuevo sin rutas declaradas se
 * puede conceder, pero no abre ninguna API: solo enseña su entrada del menú.
 */
@Data
@Entity
@Table(name = "panel_modules")
public class PanelModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 40, nullable = false, unique = true)
    private String clave;

    @Column(length = 80, nullable = false)
    private String etiqueta;

    /** Cómo se agrupa en la pantalla; los mismos bloques que el menú. */
    @Column(length = 40, nullable = false)
    private String grupo;

    /** En qué orden se muestra. */
    @Column(nullable = false)
    private int orden;
}
