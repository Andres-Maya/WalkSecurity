"""
Paso 3: entrena el modelo de riesgo y genera las zonas (output/risk_zones.json).

Modelo: estimación de densidad por kernel (scikit-learn KernelDensity, distancia haversine) sobre
los incidentes, ponderados por gravedad y antigüedad. El puntaje de cada barrio es la densidad en
su centro, normalizada a [0, 1]. Es una ESTIMACIÓN a partir de noticias, no una medición del delito.

Evaluación honesta: se entrena con los incidentes más antiguos y se mide cuántos de los más
recientes caen en los barrios que el modelo marcó como de mayor riesgo.

    python train_model.py
"""
import csv
import json
import math
from datetime import date

import numpy as np
from sklearn.neighbors import KernelDensity

from common import DATA, OUTPUT, load_barrios

EARTH_RADIUS_M = 6_371_000
BANDWIDTHS_M = [250, 400, 600, 900]
HALF_LIFE_DAYS = 730          # un incidente de hace 2 años pesa la mitad
ZONE_RADIUS_M = 300
MIN_SCORE = 0.4               # solo se publican zonas de precaución o alerta
ALERT_SHARE, CAUTION_SHARE = 0.05, 0.10   # fracción de barrios en cada nivel
CAUTION, ALERT = 0.4, 0.7     # mismos umbrales que RiskLevel en la app
TOP_SHARE = 0.2               # "barrios de mayor riesgo" = 20 % superior
MIN_INCIDENTS = 25


def load_incidents() -> list[dict]:
    with (DATA / "incidents.csv").open(encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    for r in rows:
        r["lat"], r["lng"], r["severity"] = float(r["lat"]), float(r["lng"]), int(r["severity"])
        r["day"] = date.fromisoformat(r["date"])
    return sorted(rows, key=lambda r: r["day"])


def radians(points) -> np.ndarray:
    return np.radians(np.array(points, dtype=float))


def weights(rows: list[dict], today: date) -> np.ndarray:
    age = np.array([(today - r["day"]).days for r in rows], dtype=float)
    severity = np.array([r["severity"] for r in rows], dtype=float)
    return severity * 0.5 ** (age / HALF_LIFE_DAYS)


def fit(rows: list[dict], bandwidth_m: float, today: date) -> KernelDensity:
    model = KernelDensity(kernel="gaussian", metric="haversine", bandwidth=bandwidth_m / EARTH_RADIUS_M)
    model.fit(radians([(r["lat"], r["lng"]) for r in rows]), sample_weight=weights(rows, today))
    return model


def scores(model: KernelDensity, barrios: list[dict]) -> np.ndarray:
    density = np.exp(model.score_samples(radians([(b["lat"], b["lng"]) for b in barrios])))
    # Calibración por posición relativa: la densidad cruda marcaría media ciudad en alerta.
    # Solo el ALERT_SHARE superior de barrios llega a alerta y el CAUTION_SHARE siguiente a precaución.
    rank = density.argsort().argsort() / (len(density) - 1)       # 0 = menor densidad, 1 = mayor
    alert_from, caution_from = 1 - ALERT_SHARE, 1 - ALERT_SHARE - CAUTION_SHARE
    return np.where(
        rank >= alert_from, ALERT + (rank - alert_from) / ALERT_SHARE * (1 - ALERT),
        np.where(rank >= caution_from, CAUTION + (rank - caution_from) / CAUTION_SHARE * (ALERT - CAUTION),
                 rank / caution_from * CAUTION),
    )


def evaluate(train: list[dict], test: list[dict], barrios: list[dict], bandwidth_m: float) -> dict:
    """¿Los incidentes recientes caen en los barrios que el modelo marcó como de mayor riesgo?"""
    model = fit(train, bandwidth_m, train[-1]["day"])
    barrio_scores = scores(model, barrios)
    top = set(np.argsort(-barrio_scores)[: max(1, int(len(barrios) * TOP_SHARE))])
    coords = radians([(b["lat"], b["lng"]) for b in barrios])
    hits = 0
    for r in test:
        point = radians([(r["lat"], r["lng"])])[0]
        nearest = int(np.argmin(np.hypot(coords[:, 0] - point[0], (coords[:, 1] - point[1]) * math.cos(point[0]))))
        hits += nearest in top
    hit_rate = hits / len(test)
    return {
        "bandwidthM": bandwidth_m,
        "testIncidents": len(test),
        "hitRateTop20": round(hit_rate, 3),
        "randomBaseline": TOP_SHARE,
        "lift": round(hit_rate / TOP_SHARE, 2),
        "logLikelihood": round(float(model.score(radians([(r["lat"], r["lng"]) for r in test]))) / len(test), 3),
    }


def multipliers(counts: list[int], minimum: int) -> list[float]:
    """Factor relativo por franja (1.0 = promedio). Con pocos datos se queda en 1.0 para no inventar."""
    total = sum(counts)
    if total < minimum:
        return [1.0] * len(counts)
    mean, smoothing = total / len(counts), 10
    return [round(min(1.25, max(0.8, (c + smoothing) / (mean + smoothing))), 2) for c in counts]


def main() -> None:
    barrios, rows = load_barrios(), load_incidents()
    if len(rows) < MIN_INCIDENTS:
        raise SystemExit(f"Solo hay {len(rows)} incidentes: no alcanza ni para un modelo de demostración.")
    confidence = "baja" if len(rows) < 100 else "media" if len(rows) < 400 else "alta"

    split = int(len(rows) * 0.75)
    train, test = rows[:split], rows[split:]
    evaluations = [evaluate(train, test, barrios, bw) for bw in BANDWIDTHS_M]
    best = max(evaluations, key=lambda e: e["logLikelihood"])
    print("Evaluación temporal (entrena con el 75 % más antiguo, prueba con el 25 % más reciente):")
    for e in evaluations:
        print("  ", e, "<- elegido" if e is best else "")

    today = date.today()
    model = fit(rows, best["bandwidthM"], today)   # modelo final: todos los datos
    barrio_scores = scores(model, barrios)

    counts: dict[str, int] = {}
    for r in rows:
        counts[r["barrio"]] = counts.get(r["barrio"], 0) + 1
    zones = [
        {
            "name": b["name"], "latitude": b["lat"], "longitude": b["lng"], "radiusMeters": ZONE_RADIUS_M,
            "riskScore": round(float(s), 3), "incidents": counts.get(b["name"], 0),
        }
        for b, s in zip(barrios, barrio_scores) if s >= MIN_SCORE
    ]
    zones.sort(key=lambda z: -z["riskScore"])

    by_weekday, by_hour = [0] * 7, [0] * 24
    for r in rows:
        by_weekday[r["day"].weekday()] += 1
        if r["hour"] != "":
            by_hour[int(r["hour"])] += 1

    result = {
        "generatedAt": today.isoformat(),
        "city": "Pasto, Nariño",
        "source": "Titulares de noticias (Google News) + barrios de OpenStreetMap",
        "disclaimer": "Estimación basada en noticias: no es una medición del delito ni garantiza que una persona esté o no en peligro.",
        "model": {"type": "KernelDensity (scikit-learn), haversine", "bandwidthMeters": best["bandwidthM"],
                  "halfLifeDays": HALF_LIFE_DAYS, "incidents": len(rows),
                  # Con menos de 100 incidentes el mapa es orientativo: sirve para demostrar, no para confiar
                  "confidence": confidence,
                  "dateRange": [rows[0]["date"], rows[-1]["date"]]},
        "evaluation": best,
        "thresholds": {"caution": CAUTION, "alert": ALERT},
        # Lunes = 0. La fecha es la de publicación (aprox. el día del hecho o el siguiente)
        "weekdayMultipliers": multipliers(by_weekday, minimum=70),
        # Pocas noticias dicen la hora: si no hay suficientes, queda en 1.0 (sin ajuste)
        "hourMultipliers": multipliers(by_hour, minimum=120),
        "zones": zones,
    }
    OUTPUT.mkdir(exist_ok=True)
    text = json.dumps(result, ensure_ascii=False, indent=1)
    (OUTPUT / "risk_zones.json").write_text(text, encoding="utf-8")
    # La app lleva el modelo empaquetado: detecta zonas sin depender del servidor
    assets = OUTPUT.parent.parent / "app" / "src" / "main" / "assets"
    if assets.parent.exists():
        assets.mkdir(exist_ok=True)
        (assets / "risk_zones.json").write_text(text, encoding="utf-8")

    alert = sum(z["riskScore"] >= ALERT for z in zones)
    caution = sum(CAUTION <= z["riskScore"] < ALERT for z in zones)
    print(f"\nIncidentes: {len(rows)} · zonas publicadas: {len(zones)} (alerta {alert}, precaución {caution})")
    print("Top 10:", [(z["name"], z["riskScore"], z["incidents"]) for z in zones[:10]])
    print("Multiplicadores por día:", result["weekdayMultipliers"])
    print("Con hora:", sum(by_hour), "-> multiplicadores por hora", "aprendidos" if sum(by_hour) >= 120 else "sin ajuste (1.0)")


if __name__ == "__main__":
    main()
