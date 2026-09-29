# WalkSecurity

Aplicación de seguridad personal: el reloj avisa (vibración + pantalla) cuando entras en una zona
estimada como riesgosa, y un botón SOS envía tu ubicación a tus contactos de confianza.

> **Aviso:** las zonas de riesgo son **estimaciones basadas en datos históricos**, no una garantía
> de que una persona esté o no en peligro. La app no sustituye a la línea de emergencias (123 en Colombia).

## Arquitectura

```
Smartwatch (Wear OS)  ──Data Layer──►  App Android (teléfono)  ──REST/JWT──►  API Spring Boot  ──►  PostgreSQL
  estados + SOS                         GPS, mapa, geofencing                  usuarios, contactos,      ▲
  vibración                             SMS de emergencia                      alertas, zonas            │
                                                                                     │                   │
                                                                                     └──► Modelo IA (Python / scikit-learn)
                                                                                          estima risk_score por zona
```

Monolito modular cliente-servidor (sin microservicios por ahora):

| Carpeta    | Tecnología                           | Responsabilidad                                   |
|------------|--------------------------------------|---------------------------------------------------|
| `app/`     | Kotlin, Jetpack Compose, Maps, Fused Location | App del teléfono: componente principal     |
| `backend/` | Java 21, Spring Boot 4, JPA, Flyway  | API REST, autenticación JWT, persistencia          |
| `wear/`    | Kotlin, Compose for Wear OS          | *(Fase 3)* estados seguro/precaución/alerta + SOS  |
| `ml/`      | Python, scikit-learn                 | *(Fase 4)* modelo de riesgo por zona               |

### Decisiones clave

- **El SOS sale por SMS desde el teléfono**, antes que por el servidor: funciona sin datos móviles
  y sin depender de que el backend esté arriba. El servidor solo registra la alerta (historial y
  futuros canales push). Por eso la app guarda una copia local de los contactos.
- **Cuenta regresiva de 5 s** antes de enviar el SOS para evitar falsas alarmas; se puede enviar
  de inmediato o cancelar.
- El envío del SOS corre en un scope de aplicación: no se cancela si el usuario cambia de pantalla.
- El GPS continuo solo está activo mientras la pantalla principal es visible (batería).
  El seguimiento en segundo plano llegará con el geofencing (fase 2) vía un Foreground Service.

### Estructura de la app

```
app/src/main/java/com/andres/walksecurity/
├── AppContainer.kt          # inyección de dependencias manual
├── core/
│   ├── location/            # FusedLocationProvider → corrutinas/Flow
│   ├── model/               # modelos, validaciones, texto del SMS
│   └── sms/                 # envío de SMS
├── data/
│   ├── local/SessionStore   # DataStore: token, usuario, contactos (caché)
│   ├── remote/              # Retrofit + kotlinx.serialization
│   └── repository/          # Auth, Contacts, Alert (SOS)
└── ui/
    ├── auth/  home/  contacts/  navigation/  components/  theme/
```

## Roadmap

- [x] **Fase 1** – Registro/login, GPS, mapa, contactos de confianza, botón SOS (SMS + registro en API)
- [ ] **Fase 2** – Zonas de riesgo (API + mapa), Geofencing API en segundo plano, notificaciones y
      umbral configurable (seguro < 0.4 ≤ precaución < 0.7 ≤ alerta)
- [ ] **Fase 3** – Módulo `wear/`: 3 estados + SOS, vibración, comunicación con el teléfono (Wearable Data Layer)
- [ ] **Fase 4** – Modelo scikit-learn (lat/lng, hora, día, histórico de incidentes) que actualiza `risk_zones.risk_score`

## Cómo ejecutar y probar en tu teléfono (sin emulador)

### 1. Backend

Requisitos: Docker Desktop **abierto** y JDK 21+.

```bash
cd backend
docker compose up -d
./gradlew bootRun
```

Comprueba: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.

### 2. Conectar el teléfono al backend por USB

Con el teléfono conectado y la depuración USB activa:

```bash
adb reverse tcp:8080 tcp:8080
```

Así `http://127.0.0.1:8080` dentro del teléfono apunta al backend de tu PC. Hay que repetirlo
cada vez que desconectes el cable. *(Alternativa por Wi-Fi: `API_BASE_URL=http://<IP-de-tu-PC>:8080/`
en `local.properties`; solo funciona en builds debug.)*

### 3. Google Maps API key

1. En [Google Cloud Console](https://console.cloud.google.com/) habilita **Maps SDK for Android** y crea una API key.
2. Restríngela a apps Android con el paquete `com.andres.walksecurity` y el SHA-1 de tu keystore de debug
   (`./gradlew signingReport`).
3. Añádela a `local.properties` (no se sube a git):

```properties
MAPS_API_KEY=tu_api_key
```

Sin la key la app funciona igual, pero muestra un aviso en lugar del mapa.

### 4. Instalar la app

Desde Android Studio (▶ Run con el teléfono seleccionado) o:

```bash
./gradlew installDebug
```

### 5. Prueba de humo

1. Regístrate → llegas a la pantalla principal y se piden permisos de ubicación y SMS.
2. Agrega un contacto de confianza (usa **tu propio número** u otro teléfono tuyo para probar).
3. Pulsa **SOS** → cuenta regresiva → llega un SMS con el enlace de Google Maps.
4. En la consola del backend aparece `Alerta SOS #...` y el registro queda en la tabla `alerts`.

> Los SMS los cobra tu operador según tu plan.

## Tests

```bash
./gradlew testDebugUnitTest      # app
cd backend && ./gradlew test     # backend
```
