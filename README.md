# bytemarket-user-service

Cuentas, autenticación y perfiles. Es quien **firma** los tokens JWT que el resto del sistema verifica.

Parte del sistema **ByteMarket**, una tienda de repuestos y accesorios para
celulares construida con microservicios Spring Boot y un frontend Nuxt.

**Puerto 8081** · Base de datos `bytemarket_user`

## API

Todo entra por el gateway (`http://localhost:8085`), no directamente al 8081.

### Autenticación — público

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/api/auth/register` | Crea la cuenta. Body: `name`, `email`, `password`, `phone?`, `dni?` |
| `POST` | `/api/auth/login` | Devuelve `{user, token}` y deja la cookie `jwt_token` |
| `POST` | `/api/auth/logout` | Limpia la cookie |
| `GET` | `/api/auth/me` | Datos del usuario de la sesión |

### Cliente — requiere sesión

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/api/mi-perfil` | Perfil: nombre, teléfono, DNI, dirección |
| `PUT` | `/api/mi-perfil` | Actualiza el perfil. Solo cuentas con rol `customer` |
| `POST` | `/api/users/profile` | Actualiza perfil con foto (`multipart/form-data`) |
| `GET` | `/api/business-config` | Datos públicos del negocio (nombre, logo, WhatsApp) |

### Panel — requiere rol `admin` o `superadmin`

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/api/admin/users` | Lista de usuarios y clientes |
| `POST` | `/api/admin/users` | Crea un usuario |
| `PUT` | `/api/admin/users/{id}` | Edita un usuario |
| `PUT` `PATCH` | `/api/admin/users/{id}/status` | Activa o suspende la cuenta |
| `DELETE` | `/api/admin/users/{id}` | Elimina un usuario |
| `GET` | `/api/admin/business-config` | Configuración del negocio |
| `PUT` | `/api/admin/business-config` | Actualiza la configuración |

### Ejemplo

```bash
curl -X POST http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@bytemarket.com","password":"admin123"}'
```

```json
{ "message": "Login exitoso",
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": { "id": 2, "name": "Encargado de tienda",
            "email": "admin@bytemarket.com", "role": "admin" } }
```

## El JWT

El token es HS256 y lleva los claims **`uid`** y **`role`** además del `sub`
(el correo). Los demás servicios leen el rol de ahí y **no consultan esta
base**: por eso catalog, order y support autorizan sin depender de este
servicio.

Un token de una cuenta suspendida sigue siendo criptográficamente válido, así
que el filtro comprueba el estado de la cuenta en cada petición.

### Usuarios del sembrado

| Rol | Correo | Contraseña |
|---|---|---|
| Superadmin | `owner@bytemarket.com` | `admin123` |
| Admin | `admin@bytemarket.com` | `admin123` |
| Cliente | `cliente@bytemarket.com` | `cliente123` |

## Cómo levantarlo

Requisitos: **Java 17+**, **MySQL 8** en `localhost:3306` y el
`bytemarket-eureka-server` ya arrancado (salvo que este repo *sea* Eureka).

```bash
cp .env.example .env     # y rellena los valores
./mvnw spring-boot:run
```

Queda escuchando en el puerto **8081**. El esquema de base de datos se crea
solo al arrancar (`createDatabaseIfNotExist=true`).

## Configuración

Las credenciales se leen del `.env`, que **no se versiona**. Los
`application*.yml` solo traen marcadores: si falta el `.env`, los valores
sensibles quedan vacíos. Mira `.env.example` para saber qué rellenar.

> El `JWT_SECRET` debe ser **idéntico** en user, catalog, order y support:
> user-service firma el token y los demás verifican la firma. Si difieren,
> todas las peticiones autenticadas fallan con 401 sin dejar rastro en el log.

## El sistema completo

| Repositorio | Puerto | Función |
|---|---|---|
| `bytemarket-eureka-server` | 8761 | Registro de servicios |
| `bytemarket-api-gateway` | 8085 | Punto de entrada único; enruta a los demás |
| `bytemarket-user-service` | 8081 | Cuentas, autenticación JWT, perfiles |
| `bytemarket-catalog-service` | 8082 | Productos, categorías, banners, inventario |
| `bytemarket-order-service` | 8083 | Pedidos, métodos de pago, cupones, reportes |
| `bytemarket-support-service` | 8084 | Libro de reclamaciones |
| `frontend-bytemarket` | 3000 | Tienda y panel de administración (Nuxt 3) |

Orden de arranque: **Eureka primero**, luego los servicios de negocio, el
gateway al final y el frontend cuando el gateway responda.
