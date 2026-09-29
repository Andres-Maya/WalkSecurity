# WalkSecurity API

Spring Boot 4 · Java 21 · PostgreSQL 17 · Flyway · JWT (HS256)

```bash
docker compose up -d     # PostgreSQL en localhost:5433
./gradlew bootRun        # API en http://localhost:8080
```

Variables de entorno (todas tienen valor por defecto para desarrollo):

| Variable      | Por defecto                                        |
|---------------|----------------------------------------------------|
| `DB_URL`      | `jdbc:postgresql://localhost:5433/walksecurity`    |
| `DB_USER`     | `walksecurity`                                     |
| `DB_PASSWORD` | `walksecurity`                                     |
| `JWT_SECRET`  | secreto de desarrollo — **cámbialo en producción** |
| `PORT`        | `8080`                                             |

## Endpoints

Todos los errores responden `{"message": "..."}`. Salvo `/api/auth/**`, todo requiere
`Authorization: Bearer <token>`.

| Método | Ruta                  | Descripción                                    |
|--------|-----------------------|------------------------------------------------|
| POST   | `/api/auth/register`  | `{name, email, phone, password}` → `{token, user}` (201; 409 si el correo existe) |
| POST   | `/api/auth/login`     | `{email, password}` → `{token, user}` (401 si falla) |
| GET    | `/api/users/me`       | Usuario autenticado                            |
| GET    | `/api/contacts`       | Contactos de confianza                         |
| POST   | `/api/contacts`       | `{name, phone, relationship?}` (máx. 10; 409 si el número se repite) |
| DELETE | `/api/contacts/{id}`  | 204                                            |
| POST   | `/api/alerts`         | `{type: SOS\|RISK_ZONE, latitude?, longitude?, accuracyMeters?, message?}` (201) |
| GET    | `/api/alerts`         | Últimas 50 alertas del usuario                 |
| POST   | `/api/locations`      | `{latitude, longitude, accuracyMeters?, recordedAt?}` (204) — fase 2 |
| GET    | `/actuator/health`    | Estado del servicio                            |

## Esquema

Ver `src/main/resources/db/migration/V1__initial_schema.sql`. Ya incluye `risk_zones` (fase 2) e
`incidents` (datos de entrenamiento para el modelo de la fase 4).

## Ejemplo rápido

```bash
curl -s -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"name":"Ana","email":"ana@example.com","phone":"+573001234567","password":"12345678"}'
```
