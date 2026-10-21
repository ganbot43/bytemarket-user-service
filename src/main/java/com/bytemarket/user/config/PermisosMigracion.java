package com.bytemarket.user.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Reparte el antiguo "editar" entre crear, editar y eliminar.
 *
 * Los permisos nacieron con dos acciones: ver y editar, donde "editar"
 * gobernaba TODO lo que no fuera un GET. Al separarlas en cuatro, Hibernate
 * añade can_create y can_delete —ddl-auto: update añade columnas— pero las
 * deja en 0 en las filas que ya existen. Sin esta migración, al actualizar,
 * el encargado se quedaría sin poder dar de alta un producto ni borrar un
 * cupón: permisos que tenía ayer y que nadie le quitó.
 *
 * El guardián es una tabla propia y no una comprobación sobre los datos.
 * Estuve tentado de mirar "si nadie tiene crear, es que falta migrar", pero
 * eso es un estado que el gerente puede configurar a mano —quitarle crear y
 * eliminar a todo el mundo y dejar editar—, y entonces el siguiente reinicio
 * le devolvería los dos permisos que acababa de retirar. Una migración que
 * concede privilegios no puede depender de adivinar si ya corrió.
 *
 * Idempotente: arrancar cien veces deja lo mismo que arrancar una. Va por
 * JDBC puro y antes del seeder, para que las filas nuevas nazcan ya con las
 * cuatro acciones y no haya que migrarlas después.
 */
@Component
@Order(0)
@RequiredArgsConstructor
public class PermisosMigracion implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PermisosMigracion.class);

    private static final String CLAVE = "permisos_cuatro_acciones";

    private final DataSource dataSource;

    @Override
    public void run(String... args) {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS migraciones (
                      clave VARCHAR(100) NOT NULL PRIMARY KEY,
                      aplicada_en DATETIME(6) NOT NULL
                    )""");

            if (yaAplicada(st)) return;

            /*
             * El antiguo can_edit cubría POST, PUT, PATCH y DELETE por igual,
             * así que quien lo tenía podía crear y borrar. Repartirlo a las
             * tres es lo que conserva exactamente lo que cada puesto hacía.
             */
            int filas = st.executeUpdate(
                    "UPDATE role_permissions SET can_create = can_edit, can_delete = can_edit "
                            + "WHERE can_edit = 1 AND can_create = 0 AND can_delete = 0");

            st.executeUpdate("INSERT INTO migraciones (clave, aplicada_en) VALUES ('"
                    + CLAVE + "', NOW(6))");

            log.info("Permisos: repartido el antiguo 'editar' en crear/editar/eliminar ({} filas)", filas);
        } catch (Exception e) {
            /* Si esto falla, el servicio arranca igual: la tabla de permisos
               sigue siendo válida, solo que a los puestos les faltarían crear
               y eliminar hasta arreglarlo. Tumbar el arranque dejaría el
               sistema entero fuera por un reparto de columnas. */
            log.error("No se pudo migrar los permisos a cuatro acciones: {}", e.getMessage());
        }
    }

    private boolean yaAplicada(Statement st) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM migraciones WHERE clave = '" + CLAVE + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }
}
