package com.bytemarket.user.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

@Service
public class S3Service {

    private AmazonS3 s3client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.access.key}")
    private String accessKey;

    @Value("${aws.secret.key}")
    private String secretKey;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.s3.root-prefix}")
    private String rootPrefix;

    @PostConstruct
    private void initializeAmazon() {
        AWSCredentials credentials = new BasicAWSCredentials(this.accessKey, this.secretKey);
        this.s3client = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(this.region).build();
    }

    public String uploadFile(MultipartFile file) throws IOException {
        return uploadFile(file, "");
    }

    public String uploadFile(MultipartFile file, String folder) throws IOException {
        String fileName = construirClave(folder, file.getOriginalFilename());

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        PutObjectRequest request = new PutObjectRequest(bucketName, fileName, file.getInputStream(), metadata);
        // Quitamos PublicRead porque usaremos URLs firmadas (más seguro y evita errores de AWS)
        
        s3client.putObject(request);
        // URL absoluta, no la key: es lo que se guarda en base y lo que
        // getPresignedUrl sabe volver a firmar al momento de leer.
        return s3client.getUrl(bucketName, fileName).toString();
    }

    /**
     * La clave del objeto en S3: {prefijo}/{carpeta}/{aaaa}/{MM}/{dd}/{uuid}.{ext}
     *
     * La fecha no es por rendimiento —S3 escala solo por prefijo desde 2018—
     * sino para poder operar: aplicar reglas de ciclo de vida por antigüedad,
     * listar lo subido un día concreto cuando algo salió mal, y no tener que
     * paginar una carpeta de decenas de miles de objetos para encontrar uno.
     *
     * Del nombre original se conserva SOLO la extensión. Meterlo entero deja
     * espacios y acentos dentro de la clave para siempre, y un nombre con
     * barra —"mis fotos/perfil.jpg"— crearía un nivel de carpeta que nadie
     * pidió. El nombre lo pone el servidor.
     */
    String construirClave(String folder, String originalFilename) {
        String prefijo = (rootPrefix == null || rootPrefix.isBlank()) ? "" : rootPrefix.trim() + "/";
        String carpeta = (folder == null || folder.isBlank()) ? "" : folder.trim() + "/";
        String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        return prefijo + carpeta + fecha + "/" + UUID.randomUUID() + extension(originalFilename);
    }

    /** La extensión, si es una extensión de verdad y no un nombre disfrazado. */
    private static String extension(String originalFilename) {
        if (originalFilename == null) return "";

        // Un nombre puede llegar con ruta; solo interesa la última parte.
        String base = originalFilename.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);

        int punto = base.lastIndexOf('.');
        if (punto < 0) return "";

        String ext = base.substring(punto).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,8}") ? ext : "";
    }

    public String generatePresignedUrl(String key) {
        Date expiration = new Date();
        long expTimeMillis = expiration.getTime();
        expTimeMillis += 1000 * 60 * 60; // 1 hora
        expiration.setTime(expTimeMillis);

        GeneratePresignedUrlRequest generatePresignedUrlRequest =
                new GeneratePresignedUrlRequest(bucketName, key)
                        .withMethod(HttpMethod.GET)
                        .withExpiration(expiration);

        return s3client.generatePresignedUrl(generatePresignedUrlRequest).toString();
    }
    

    /**
     * Devuelve la forma que debe guardarse en base: la URL del objeto sin la
     * firma.
     *
     * Hace falta porque el panel recibe las URLs ya firmadas y las reenvía al
     * guardar. Sin esto se persistiría una URL con firma, y al caducar la
     * imagen quedaría rota de forma permanente. Una URL firmada es la del
     * objeto más los parámetros X-Amz-*, así que basta cortar en el "?".
     */
    public String normalizeUrl(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains(".amazonaws.com/")) {
            return fileUrl;
        }
        int q = fileUrl.indexOf('?');
        return q < 0 ? fileUrl : fileUrl.substring(0, q);
    }

    /**
     * Convierte la URL guardada en una URL firmada que el navegador puede
     * abrir. El bucket es privado: una URL directa devuelve 403, así que se
     * firma al momento de leer. Lo que no sea de S3 se devuelve tal cual.
     */
    public String getPresignedUrl(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains(".amazonaws.com/")) {
            return fileUrl;
        }
        try {
            String key = fileUrl.substring(fileUrl.indexOf(".amazonaws.com/") + 15);
            Date expiration = new Date();
            expiration.setTime(expiration.getTime() + 1000L * 60 * 60 * 2); // 2 horas

            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(bucketName, key)
                            .withMethod(HttpMethod.GET)
                            .withExpiration(expiration);

            return s3client.generatePresignedUrl(request).toString();
        } catch (Exception e) {
            // Si no se puede firmar, mejor devolver la original que romper la página.
            return fileUrl;
        }
    }

    public S3Object getObject(String key) {
        if (!key.startsWith(rootPrefix)) {
            throw new IllegalArgumentException("Acceso denegado a la clave: " + key);
        }
        return s3client.getObject(bucketName, key);
    }
}
