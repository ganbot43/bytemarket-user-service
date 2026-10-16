package com.bytemarket.user.repository;

import com.bytemarket.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de la capa de acceso a datos del repositorio de usuarios.
 *
 * Se usa @DataJpaTest: levanta solo la capa JPA contra una base en memoria,
 * sin cargar controladores ni clientes REST, y envuelve cada prueba en una
 * transacción que se revierte al terminar. Por eso las pruebas no dependen
 * del orden en que se ejecuten ni dejan rastro en MySQL.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("IUserRepository — capa de acceso a datos")
class IUserRepositoryTest {

    @Autowired
    private IUserRepository repositorio;

    /* El TestEntityManager prepara los datos sin pasar por el repositorio que
       se está probando: si la inserción estuviera rota, no queremos que
       arrastre al resto de las pruebas. */
    @Autowired
    private TestEntityManager em;

    private User nuevoUsuario(String email, String rol) {
        User u = new User();
        u.setName("Usuario de prueba");
        u.setEmail(email);
        u.setPasswordHash("$2a$10$hashDePrueba");
        u.setRole(rol);
        u.setIsActive(1);
        return u;
    }

    @BeforeEach
    void limpiar() {
        repositorio.deleteAll();
        em.flush();
    }

    // ── INSERTAR ─────────────────────────────────────────────────────

    @Test
    @DisplayName("insertar: persiste el usuario y le asigna un id generado")
    void insertar() {
        User guardado = repositorio.save(nuevoUsuario("insertar@bytemarket.com", "customer"));
        em.flush();
        em.clear();

        assertNotNull(guardado.getId(), "la base debe asignar el id");

        /* Se relee desde la base en vez de confiar en el objeto devuelto:
           así se comprueba que los datos viajaron de verdad a la tabla. */
        User desdeLaBase = em.find(User.class, guardado.getId());
        assertNotNull(desdeLaBase);
        assertEquals("insertar@bytemarket.com", desdeLaBase.getEmail());
        assertEquals("customer", desdeLaBase.getRole());
        assertNotNull(desdeLaBase.getCreatedAt(), "@PrePersist debe fijar la fecha de creación");
    }

    @Test
    @DisplayName("insertar: el email es único y un duplicado no se persiste")
    void insertarDuplicadoFalla() {
        repositorio.save(nuevoUsuario("repetido@bytemarket.com", "customer"));
        em.flush();

        /* La restricción vive en la base, no en Java. Hibernate puede
           detectarla al persistir o al sincronizar según cuándo decida
           vaciar la sesión, así que se envuelven las dos operaciones. */
        assertThrows(DataIntegrityViolationException.class, () -> {
            repositorio.save(nuevoUsuario("repetido@bytemarket.com", "admin"));
            em.flush();
        });
    }

    // ── ACTUALIZAR ───────────────────────────────────────────────────

    @Test
    @DisplayName("actualizar: modifica los campos sin crear una fila nueva")
    void actualizar() {
        User original = repositorio.save(nuevoUsuario("actualizar@bytemarket.com", "customer"));
        em.flush();
        Integer id = original.getId();

        original.setName("Nombre corregido");
        original.setRole("admin");
        repositorio.save(original);
        em.flush();
        em.clear();

        User actualizado = em.find(User.class, id);
        assertEquals("Nombre corregido", actualizado.getName());
        assertEquals("admin", actualizado.getRole());
        assertEquals(1, repositorio.count(), "actualizar no debe insertar una fila adicional");
    }

    // ── ELIMINAR ─────────────────────────────────────────────────────

    @Test
    @DisplayName("eliminar: borra la fila y deja de encontrarse por id")
    void eliminar() {
        User u = repositorio.save(nuevoUsuario("eliminar@bytemarket.com", "customer"));
        em.flush();
        Integer id = u.getId();

        repositorio.deleteById(id);
        em.flush();
        em.clear();

        assertFalse(repositorio.findById(id).isPresent());
        assertNull(em.find(User.class, id));
        assertEquals(0, repositorio.count());
    }

    // ── LISTAR ───────────────────────────────────────────────────────

    @Test
    @DisplayName("listar: devuelve todos los usuarios persistidos")
    void listar() {
        repositorio.save(nuevoUsuario("uno@bytemarket.com", "customer"));
        repositorio.save(nuevoUsuario("dos@bytemarket.com", "customer"));
        repositorio.save(nuevoUsuario("tres@bytemarket.com", "admin"));
        em.flush();
        em.clear();

        List<User> todos = repositorio.findAll();
        assertEquals(3, todos.size());
        assertTrue(todos.stream().anyMatch(u -> "dos@bytemarket.com".equals(u.getEmail())));
    }

    @Test
    @DisplayName("listar: findByEmail recupera el usuario y distingue el inexistente")
    void listarPorEmail() {
        repositorio.save(nuevoUsuario("buscado@bytemarket.com", "admin"));
        em.flush();
        em.clear();

        Optional<User> encontrado = repositorio.findByEmail("buscado@bytemarket.com");
        assertTrue(encontrado.isPresent());
        assertEquals("admin", encontrado.get().getRole());

        assertTrue(repositorio.findByEmail("noexiste@bytemarket.com").isEmpty(),
                "un email ausente debe devolver vacío, no lanzar");
    }

    @Test
    @DisplayName("listar: findByRole pagina y filtra por rol")
    void listarPorRolPaginado() {
        repositorio.save(nuevoUsuario("cliente1@bytemarket.com", "customer"));
        repositorio.save(nuevoUsuario("cliente2@bytemarket.com", "customer"));
        repositorio.save(nuevoUsuario("admin1@bytemarket.com", "admin"));
        em.flush();
        em.clear();

        Page<User> primeraPagina = repositorio.findByRole("customer", PageRequest.of(0, 1));

        assertEquals(1, primeraPagina.getContent().size(), "la página pedida es de un elemento");
        assertEquals(2, primeraPagina.getTotalElements(), "pero el total filtrado son dos");
        assertEquals("customer", primeraPagina.getContent().get(0).getRole());
    }
}
