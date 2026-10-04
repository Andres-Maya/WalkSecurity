"""
Paso 2: convierte titulares en incidentes geolocalizados (data/incidents.csv).

Un titular cuenta como incidente si (a) habla de Pasto, (b) describe un delito y (c) menciona un
barrio conocido. Es una heurística: los titulares son una muestra pequeña y sesgada de lo que
realmente ocurre (los medios no cubren todo ni todos los barrios por igual).

    python extract_incidents.py
"""
import csv
import json
import re
from datetime import date

from common import DATA, RAW, load_barrios, normalize

# (patrón sobre el titular normalizado, categoría, gravedad 1-5). Gana la primera coincidencia.
CATEGORIES = [
    (r"homicid|asesin|sicari|muert[oa] a (bala|tiros|cuchill)|acribill|feminicid", "HOMICIDIO", 5),
    (r"baleado|herid[oa]|apunal|arma blanca|lesion|disparo|tiroteo|balacera", "LESIONES", 4),
    (r"atrac|asalt|mano armada|fleteo|a punta de", "ATRACO", 4),
    (r"hurt|rob[oóa]|robaron|ladron|rapon|delincuent", "HURTO", 3),
    (r"extorsi", "EXTORSION", 3),
    (r"rina|pelea|disturbi|vandal", "RINA", 2),
    (r"microtrafico|estupefaciente|expendio|olla", "DROGAS", 2),
]
# Noticias que mencionan delitos pero no son un hecho en un lugar (balances, campañas, capturas masivas)
NOT_AN_EVENT = re.compile(r"reduccion|disminu|balance|cifras|campana|consejo de seguridad|recompensa|tasa de")
HOURS = [(r"madrugada", 3), (r"\bnoche\b|anoche", 21), (r"mediodia", 12), (r"\btarde\b", 16), (r"en la manana|horas de la manana", 9)]
PLACE_WORDS = r"(?:barrio|sector|comuna|corregimiento|vereda|urbanizacion|conjunto)"
CENTER = {"key": "centro", "name": "Centro", "lat": 1.2136, "lng": -77.2811}  # Plaza de Nariño
DEDUPE_DAYS = 3


def find_barrio(text: str, barrios: list[dict]) -> dict | None:
    best = None
    for barrio in barrios:
        key = re.escape(barrio["key"])
        strong = re.search(rf"\b{PLACE_WORDS}\s+(?:de\s+)?(?:el |la |los |las )?{key}\b", text)
        # Sin "barrio X" delante solo se aceptan nombres de varias palabras (poco ambiguos)
        weak = len(barrio["key"].split()) >= 2 and len(barrio["key"]) >= 9 and re.search(rf"\b{key}\b", text)
        if (strong or weak) and (best is None or len(barrio["key"]) > len(best["key"])):
            best = barrio
    if best is None and re.search(r"centro de (pasto|la ciudad)|pleno centro|centro historico", text):
        best = CENTER
    return best


def load_article_facts() -> dict:
    """Datos derivados del cuerpo de las noticias (fetch_articles.py), si ya se generaron."""
    path = RAW / "article_facts.jsonl"
    if not path.exists():
        return {}
    with path.open(encoding="utf-8") as f:
        return {r["link"]: r for r in map(json.loads, f)}


def main() -> None:
    barrios = load_barrios()
    for b in barrios:   # "El Común" también aparece como "barrio Común"
        b["key"] = re.sub(r"^(el|la|los|las) ", "", b["key"])
    by_name = {b["name"]: b for b in barrios}
    articles = load_article_facts()
    rows, stats = [], {"titulares": 0, "de_pasto": 0, "con_delito": 0, "con_barrio": 0, "barrio_del_cuerpo": 0}
    with (RAW / "news.jsonl").open(encoding="utf-8") as f:
        for line in f:
            item = json.loads(line)
            stats["titulares"] += 1
            # El titular de Google News termina en " - Medio": se separa para no confundirlo con el texto
            title = normalize(item["title"].rsplit(" - ", 1)[0])
            if "pasto" not in title and "pasto" not in normalize(item["source"]):
                continue
            stats["de_pasto"] += 1
            if NOT_AN_EVENT.search(title):
                continue
            category = next(((c, s) for p, c, s in CATEGORIES if re.search(p, title)), None)
            if category is None:
                continue
            stats["con_delito"] += 1
            barrio = find_barrio(title, barrios)
            article = articles.get(item["link"], {})
            if barrio is None and article.get("barrio") and article.get("mentionsPasto"):
                barrio = by_name.get(article["barrio"])
                stats["barrio_del_cuerpo"] += barrio is not None
            if barrio is None:
                continue
            stats["con_barrio"] += 1
            hour = next((h for p, h in HOURS if re.search(p, title)), article.get("hour") or "")
            rows.append({
                "date": item["published"], "barrio": barrio["name"], "lat": barrio["lat"], "lng": barrio["lng"],
                "category": category[0], "severity": category[1], "hour": hour,
                "source": item["source"], "url": item["link"],
            })

    # Un mismo hecho sale en varios medios: se deja uno por (barrio, categoría, ventana de días)
    rows.sort(key=lambda r: r["date"])
    unique, last_seen = [], {}
    for row in rows:
        key = (row["barrio"], row["category"])
        day = date.fromisoformat(row["date"])
        if key in last_seen and (day - last_seen[key]).days <= DEDUPE_DAYS:
            continue
        last_seen[key] = day
        unique.append(row)

    with (DATA / "incidents.csv").open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(unique[0].keys()) if unique else ["date"])
        writer.writeheader()
        writer.writerows(unique)

    print(stats)
    print(f"incidentes únicos: {len(unique)} (de {len(rows)} menciones)")
    if unique:
        print("rango de fechas:", unique[0]["date"], "a", unique[-1]["date"])
        by_cat, by_barrio = {}, {}
        for r in unique:
            by_cat[r["category"]] = by_cat.get(r["category"], 0) + 1
            by_barrio[r["barrio"]] = by_barrio.get(r["barrio"], 0) + 1
        print("por categoría:", dict(sorted(by_cat.items(), key=lambda kv: -kv[1])))
        print("barrios con incidentes:", len(by_barrio))
        print("top 10:", sorted(by_barrio.items(), key=lambda kv: -kv[1])[:10])
        print("con hora del día:", sum(1 for r in unique if r["hour"] != ""))


if __name__ == "__main__":
    main()
