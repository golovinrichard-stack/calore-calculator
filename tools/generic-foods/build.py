#!/usr/bin/env python3
"""Builds app/src/main/assets/foods/generic-cz.json, the basic foods bundled with Calore (M2.2).

Inputs:
  * USDA FoodData Central downloads (public domain, CC0), JSON format:
      - SR Legacy:        FoodData_Central_sr_legacy_food_json_2018-04.json
      - Foundation Foods: FoodData_Central_foundation_food_json_2025-12-18.json
    from https://fdc.nal.usda.gov/download-datasets (unzip them first).
  * selection.csv  - which USDA foods, with Czech and English names and shop data.
  * off-foods.json - Czech basic foods USDA doesn't have, from typical product labels on
                     Open Food Facts (ODbL).

Usage:
  python3 build.py --sr <SR Legacy json> --foundation <Foundation json> --version <N> [--out <path>]

Raise --version whenever the output changes: the app re-imports the basic foods when the bundled
datasetVersion is newer than the one it imported before. Uses only the Python standard library.
"""
import argparse
import csv
import json
import os
import sys

# USDA nutrient numbers -> our field names. Sugars are 269 in SR Legacy, sometimes 2000 in Foundation.
NUTRIENTS = {
    "208": "kcal", "203": "protein", "205": "carbs", "204": "fat", "291": "fiber",
    "269": "sugar", "2000": "sugar", "303": "ironMg", "304": "magnesiumMg", "401": "vitaminCMg",
}
REQUIRED = ("kcal", "protein", "carbs", "fat")
SECTIONS = {
    "VEGETABLES", "FRUIT", "MEAT", "FISH", "DAIRY", "EGGS", "BAKERY", "GRAINS", "LEGUMES", "CANNED", "FROZEN",
    "SPICES_SAUCES", "OILS", "SNACKS", "DRINKS", "SUPPLEMENTS", "OTHER",
}
ALLERGENS = {
    "GLUTEN", "CRUSTACEANS", "EGGS", "FISH", "PEANUTS", "SOY", "MILK", "NUTS", "CELERY", "MUSTARD", "SESAME",
    "SULPHITES", "LUPIN", "MOLLUSCS",
}
ANIMAL_FLAGS = {"DAIRY", "EGG", "FISH", "SHELLFISH", "MEAT", "PORK"}
STORAGE = {"FRIDGE", "FREEZER", "CUPBOARD"}

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "foods", "generic-cz.json")


def load_usda(path, list_key):
    """fdcId -> {field: amount per 100 g} for the nutrients we use."""
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    foods = {}
    for food in data[list_key]:
        values = {}
        for item in food.get("foodNutrients", []):
            nutrient = item.get("nutrient", {})
            field = NUTRIENTS.get(nutrient.get("number"))
            if field and "amount" in item and field not in values:
                values[field] = item["amount"]
        if "kcal" not in values:
            # Some Foundation foods give energy only as Atwater factors (958, then 957).
            for number in ("958", "957"):
                for item in food.get("foodNutrients", []):
                    nutrient = item.get("nutrient", {})
                    if nutrient.get("number") == number and nutrient.get("unitName") == "kcal" and "amount" in item:
                        values["kcal"] = item["amount"]
                        break
                if "kcal" in values:
                    break
        foods[str(food["fdcId"])] = values
    return foods


def split_list(text, allowed, what, key):
    items = [x for x in (text or "").split(";") if x]
    for item in items:
        if item not in allowed:
            sys.exit(f"{key}: unknown {what} {item!r}")
    return items


def number(text, kind):
    if text is None or text == "":
        return None
    return kind(text)


def per100g(values):
    out = {}
    for field in ("kcal", "protein", "carbs", "fat", "fiber", "sugar", "ironMg", "magnesiumMg", "vitaminCMg"):
        if values.get(field) is not None:
            out[field] = round(float(values[field]), 3)
    return out


def food_entry(key, name_cs, name_en, section, allergens, flags, pack, storage, shelf, piece, source_ref, nutrients):
    if not key.startswith("generic:"):
        sys.exit(f"{key}: keys start with 'generic:'")
    if section not in SECTIONS:
        sys.exit(f"{key}: unknown section {section!r}")
    if storage and storage not in STORAGE:
        sys.exit(f"{key}: unknown storage {storage!r}")
    missing = [field for field in REQUIRED if field not in nutrients]
    if missing:
        sys.exit(f"{key}: missing {missing}")
    entry = {"key": key, "nameCs": name_cs, "nameEn": name_en, "section": section, "sourceRef": source_ref}
    if allergens:
        entry["allergens"] = allergens
    if flags:
        entry["animalFlags"] = flags
    for name, value in (("defaultPackGrams", pack), ("storage", storage or None), ("shelfLifeDays", shelf), ("gramsPerPiece", piece)):
        if value is not None:
            entry[name] = value
    entry["per100g"] = nutrients
    return entry


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--sr", required=True, help="SR Legacy JSON file")
    parser.add_argument("--foundation", required=True, help="Foundation Foods JSON file")
    parser.add_argument("--version", required=True, type=int, help="datasetVersion of the output")
    parser.add_argument("--out", default=DEFAULT_OUT)
    args = parser.parse_args()

    usda = load_usda(args.sr, "SRLegacyFoods")
    usda.update(load_usda(args.foundation, "FoundationFoods"))

    foods = []
    with open(os.path.join(HERE, "selection.csv"), encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            key = row["key"]
            values = usda.get(row["fdcId"])
            if values is None:
                sys.exit(f"{key}: fdcId {row['fdcId']} not found in the USDA files")
            foods.append(food_entry(
                key, row["nameCs"], row["nameEn"], row["section"],
                split_list(row["allergens"], ALLERGENS, "allergen", key),
                split_list(row["animalFlags"], ANIMAL_FLAGS, "animal flag", key),
                number(row["defaultPackGrams"], float), row["storage"], number(row["shelfLifeDays"], int),
                number(row["gramsPerPiece"], float), f"usda:{row['fdcId']}", per100g(values),
            ))

    with open(os.path.join(HERE, "off-foods.json"), encoding="utf-8") as f:
        for item in json.load(f)["foods"]:
            key = item["key"]
            foods.append(food_entry(
                key, item["nameCs"], item["nameEn"], item["section"],
                split_list(";".join(item.get("allergens", [])), ALLERGENS, "allergen", key),
                split_list(";".join(item.get("animalFlags", [])), ANIMAL_FLAGS, "animal flag", key),
                item.get("defaultPackGrams"), item.get("storage"), item.get("shelfLifeDays"), item.get("gramsPerPiece"),
                f"off:{item['barcode']}", per100g(item["per100g"]),
            ))

    keys = [food["key"] for food in foods]
    duplicates = sorted({k for k in keys if keys.count(k) > 1})
    if duplicates:
        sys.exit(f"duplicate keys: {duplicates}")

    output = {
        "format": "calore-generic-foods",
        "datasetVersion": args.version,
        "country": "CZ",
        "sources": [
            {"name": "USDA FoodData Central, SR Legacy (April 2018)", "licence": "CC0 1.0 (public domain)",
             "url": "https://fdc.nal.usda.gov/"},
            {"name": "USDA FoodData Central, Foundation Foods (December 2025)", "licence": "CC0 1.0 (public domain)",
             "url": "https://fdc.nal.usda.gov/"},
            {"name": "Open Food Facts product labels (see sourceRef off:<barcode>)", "licence": "ODbL 1.0",
             "url": "https://world.openfoodfacts.org/"},
        ],
        "foods": foods,
    }
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"{len(foods)} foods written to {os.path.relpath(args.out)} (datasetVersion {args.version})")


if __name__ == "__main__":
    main()
