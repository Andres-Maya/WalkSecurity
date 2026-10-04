# Modelo de riesgo por zonas (Python + scikit-learn)

Estima qué barrios de Pasto tienen mayor riesgo a partir de **noticias** y genera las zonas que usa la app.

> Es una **estimación**: las noticias son una muestra pequeña y sesgada de lo que ocurre (los medios
> no cubren todos los hechos ni todos los barrios por igual). No mide el delito real ni garantiza que
> una persona esté o no en peligro.

## Pipeline

```bash
python -m venv .venv
.venv\Scripts\python -m pip install -r requirements.txt
python collect_news.py          # 1. titulares de Google News (≈5 min)
python fetch_articles.py        # 1b. lee el cuerpo de cada noticia para hallar el barrio (≈40 min)
python extract_incidents.py     # 2. data/incidents.csv (tipo, barrio, fecha)
.venv\Scripts\python train_model.py   # 3. output/risk_zones.json + copia a la app
```

| Archivo | Qué es | ¿Se versiona? |
|---|---|---|
| `data/barrios.csv` | Barrios de Pasto con coordenadas (© colaboradores de OpenStreetMap, ODbL) | Sí |
| `data/incidents.csv` | Incidentes derivados: fecha, barrio, tipo, gravedad, medio y enlace | Sí |
| `data/raw/` | Titulares y datos intermedios | No |
| `output/risk_zones.json` | Zonas con puntaje 0–1, umbrales y evaluación | Sí |

No se guarda el texto de las noticias: solo el hecho (tipo de delito, barrio y fecha) y el enlace a la fuente.

## Modelo

- **Estimación de densidad por kernel** (`sklearn.neighbors.KernelDensity`, distancia haversine) sobre
  los incidentes, ponderados por **gravedad** (homicidio 5 … riña 2) y **antigüedad** (vida media de 2 años).
- El puntaje de un barrio sale de la densidad en su centro, calibrada por posición relativa: solo el
  **5 %** de barrios con mayor densidad llega a alerta (`≥ 0.7`) y el **10 %** siguiente a precaución
  (`0.4–0.7`). Sin esa calibración, media ciudad quedaría marcada y el aviso no diría nada.
- **Evaluación temporal:** se entrena con el 75 % más antiguo y se mide cuántos incidentes del 25 % más
  reciente caen en el 20 % de barrios con mayor puntaje (al azar sería 20 %). El ancho del kernel se
  elige por verosimilitud en ese conjunto de prueba.
- **Día y hora:** se calculan multiplicadores solo si hay datos suficientes; con pocos datos quedan en
  1.0 para no inventar un patrón.

## Cómo llega a la app

`train_model.py` copia el resultado a `app/src/main/assets/risk_zones.json`. La app compara el GPS con
las zonas (`ZoneRiskEvaluator`), y al cambiar de nivel avisa al reloj (`ZoneDetector`).
