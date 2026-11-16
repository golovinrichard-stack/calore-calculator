#!/usr/bin/env python3
"""Step 3 of the shop products seed: writes app/src/main/assets/seed/cz/store-products.json.

Reads the products from fetch.py (by food or by brand) and the reviewed matches (review/*.json: {"generic:key": ["barcode", …]})
and keeps, per shop and basic food, at most three reviewed products (the most scanned first, so it
becomes the shop's preferred product until the user picks another). No prices: Open Food Facts has
none, and we don't copy shop price lists (DATA_SOURCES section 4).

Usage (from the repository root):
    python3 tools/store-products/build.py --version 1 \
        --candidates tools/store-products/candidates.json tools/store-products/brand-products.json
"""
import argparse
import glob
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
REVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), "review", "*.json")
STORES = os.path.join(ROOT, "app/src/main/assets/seed/cz/stores.json")
FOODS = os.path.join(ROOT, "app/src/main/assets/foods/generic-cz.json")
OUT = os.path.join(ROOT, "app/src/main/assets/seed/cz/store-products.json")
# The final check: reviewed products left out after all, with the reason ({"barcode": "reason"}).
EXCLUDED = os.path.join(os.path.dirname(os.path.abspath(__file__)), "excluded.json")
PER_STORE_AND_FOOD = 3
# Letters of other languages' alphabets (Romanian, Polish, Hungarian, German …): a product whose only
# name on Open Food Facts is in another language would look wrong on a Czech shopping list.
FOREIGN_LETTERS = set("ăâîșşțţąęłńśżźőűäöüßñ")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--candidates", required=True, nargs="+")
    parser.add_argument("--version", type=int, required=True)
    args = parser.parse_args()
    # Every fetched product by barcode, from both kinds of fetch.py output.
    pool = {}
    for path in args.candidates:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        found = data["products"] if "products" in data else [c for v in data.values() for c in v["candidates"]]
        for product in found:
            pool.setdefault(product["code"], product)
    with open(STORES, encoding="utf-8") as f:
        stores = {s["key"] for s in json.load(f)["stores"]}
    with open(FOODS, encoding="utf-8") as f:
        foods = {food["key"] for food in json.load(f)["foods"]}
    approved = {}
    for path in sorted(glob.glob(REVIEW)):
        with open(path, encoding="utf-8") as f:
            for food, codes in json.load(f).items():
                approved.setdefault(food, set()).update(codes)

    with open(EXCLUDED, encoding="utf-8") as f:
        excluded = json.load(f)

    products, seen = [], set()
    for food, codes in sorted(approved.items()):
        if food not in foods:
            print(f"skipping unknown food {food}", file=sys.stderr)
            continue
        chosen = [pool[code] for code in codes if code in pool and code not in excluded and pool[code]["store"] in stores
                  and not FOREIGN_LETTERS & set(pool[code]["name"].lower())]
        chosen.sort(key=lambda c: (-c["scans"], c["name"]))
        per_store = {}
        for c in chosen:
            if per_store.get(c["store"], 0) >= PER_STORE_AND_FOOD or (c["store"], c["code"]) in seen:
                continue
            per_store[c["store"]] = per_store.get(c["store"], 0) + 1
            seen.add((c["store"], c["code"]))
            products.append({
                "key": f"{c['store']}:off-{c['code']}",
                "store": c["store"],
                "food": food,
                "name": c["name"],
                "brand": c["brand"],
                "barcode": c["code"],
                "packAmount": round(float(c["pack"]), 1),
                "packUnit": c["unit"],
                "priceCzk": None,
                "priceDate": None,
                "source": f"off:{c['code']}",
            })
    products.sort(key=lambda p: (p["store"], p["food"], p["name"]))
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"version": args.version, "country": "CZ",
                   "note": "Shops' own-brand products from Open Food Facts (ODbL 1.0, product labels), matched to basic foods. No prices.",
                   "products": products}, f, ensure_ascii=False, indent=1)
        f.write("\n")
    by_store = {}
    for p in products:
        by_store[p["store"]] = by_store.get(p["store"], 0) + 1
    print(f"wrote {len(products)} products for {len({p['food'] for p in products})} foods: {by_store}")


if __name__ == "__main__":
    sys.exit(main())
