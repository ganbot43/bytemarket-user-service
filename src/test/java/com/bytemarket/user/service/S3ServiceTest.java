package com.bytemarket.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La clave con la que se guarda un objeto en S3.
 *
 * Tiene que ser la misma convención que en catalog-service: las dos clases
 * son copias y ya habían divergido —esta metía el nombre original entero en
 * la clave y aquella no ponía fecha—. Estos casos son los mismos que los de
 * allá, a propósito.
 */
class S3ServiceTest {

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Service = new S3Service();
        ReflectionTestUtils.setField(s3Service, "rootPrefix", "bytemarket");
    }


    // ─── La clave del objeto: {prefijo}/{carpeta}/{aaaa}/{MM}/{dd}/{uuid}.{ext} ───

    @Test
    @DisplayName("la clave lleva carpeta y fecha: aaaa/MM/dd antes del archivo")
    void claveConFecha() {
        String clave = s3Service.construirClave("profiles", "pantalla.JPG");
        String hoy = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        assertTrue(clave.startsWith("bytemarket/profiles/" + hoy + "/"),
                "esperaba bytemarket/profiles/" + hoy + "/..., y salió " + clave);
        assertTrue(clave.endsWith(".jpg"), "la extensión se conserva en minúscula: " + clave);

        // prefijo + carpeta + aaaa + MM + dd + archivo
        assertEquals(6, clave.split("/").length, clave);
    }

    @Test
    @DisplayName("del nombre original solo sobrevive la extensión")
    void nombreLoPoneElServidor() {
        String clave = s3Service.construirClave("profiles", "Mi Foto Ñoña (1).png");

        assertFalse(clave.contains(" "), "un espacio quedaría en la clave para siempre: " + clave);
        assertFalse(clave.contains("Ñ"));
        assertTrue(clave.endsWith(".png"));
    }

    @Test
    @DisplayName("un nombre con ruta no crea carpetas que nadie pidió")
    void nombreConRuta() {
        String clave = s3Service.construirClave("profiles", "mis fotos/perfil.jpg");
        String hoy = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        assertEquals(6, clave.split("/").length,
                "la barra del nombre original habría añadido un nivel: " + clave);
        assertTrue(clave.startsWith("bytemarket/profiles/" + hoy + "/"));
    }

    @Test
    @DisplayName("sin extensión, o con una que no lo es, la clave se queda sin ella")
    void extensionDudosa() {
        assertFalse(s3Service.construirClave("profiles", "sinextension").contains("."));
        assertFalse(s3Service.construirClave("profiles", "raro.unaextensionlarguisima").contains("."));
    }

    @Test
    @DisplayName("dos subidas del mismo archivo no se pisan")
    void clavesUnicas() {
        assertNotEquals(s3Service.construirClave("banners", "foto.png"),
                s3Service.construirClave("banners", "foto.png"));
    }
}
