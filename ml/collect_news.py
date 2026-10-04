"""
Paso 1: recolecta titulares de noticias de seguridad en Pasto desde Google News (RSS).

Solo se guardan titular, medio, fecha y enlace, en data/raw/ (no se versiona). Lo que entra al
repositorio son los incidentes derivados (tipo, barrio, fecha), no el texto de las noticias.

    python collect_news.py            # búsquedas generales + una por barrio
    python collect_news.py --quick    # solo búsquedas generales
"""
import json
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from email.utils import parsedate_to_datetime

from common import RAW, load_barrios

CRIME_TERMS = [
    "hurto", "atraco", "robo", "asalto", "homicidio", "asesinato", "sicariato", "riña",
    "fleteo", "raponazo", "extorsión", "herido arma blanca", "baleado", "inseguridad barrio",
    "capturado hurto barrio", "ladrones barrio", "microtráfico barrio",
]
CRIME_QUERY = "(hurto OR atraco OR robo OR homicidio OR asesinato OR riña OR sicariato OR herido)"
URL = "https://news.google.com/rss/search?hl=es-419&gl=CO&ceid=CO:es-419&q="
HEADERS = {"User-Agent": "WalkSecurity-research/1.0 (proyecto academico)"}


def fetch(query: str) -> list[dict]:
    request = urllib.request.Request(URL + urllib.parse.quote(query), headers=HEADERS)
    with urllib.request.urlopen(request, timeout=25) as response:
        root = ET.fromstring(response.read())
    items = []
    for item in root.iter("item"):
        try:
            published = parsedate_to_datetime(item.findtext("pubDate")).date().isoformat()
        except (TypeError, ValueError):
            continue
        items.append({
            "title": item.findtext("title") or "",
            "source": item.findtext("source") or "",
            "published": published,
            "link": item.findtext("link") or "",
            "query": query,
        })
    return items


def main() -> None:
    quick = "--quick" in sys.argv
    queries = [f"Pasto {term}" for term in CRIME_TERMS]
    if not quick:
        queries += [f'"{b["name"]}" Pasto {CRIME_QUERY}' for b in load_barrios()]

    RAW.mkdir(parents=True, exist_ok=True)
    seen, total, failures = set(), 0, 0
    with (RAW / "news.jsonl").open("w", encoding="utf-8") as out:
        for index, query in enumerate(queries, 1):
            try:
                items = fetch(query)
            except Exception as error:  # red caída, límite de peticiones, XML inválido...
                failures += 1
                print(f"[{index}/{len(queries)}] fallo: {error}", flush=True)
                if failures >= 15:
                    print("Demasiados fallos seguidos: se detiene la recolección.")
                    break
                time.sleep(5)
                continue
            new = 0
            for item in items:
                if item["link"] not in seen:
                    seen.add(item["link"])
                    out.write(json.dumps(item, ensure_ascii=False) + "\n")
                    new += 1
            total += new
            if index % 20 == 0 or index == len(queries):
                print(f"[{index}/{len(queries)}] titulares únicos: {total}", flush=True)
            time.sleep(1.0)  # trato cortés con el servicio
    print(f"Listo: {total} titulares en {RAW / 'news.jsonl'}")


if __name__ == "__main__":
    main()
