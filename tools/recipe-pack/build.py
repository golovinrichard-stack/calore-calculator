#!/usr/bin/env python3
"""Builds app/src/main/assets/recipes/pack-cz.json, the big Czech recipe pack, from source/*.jsonl.

Usage (from the repository root):
    python3 tools/recipe-pack/build.py --version 1            # check and write the asset
    python3 tools/recipe-pack/build.py --check                # check only
    python3 tools/recipe-pack/build.py --foods-list foods.txt # write the food list for recipe writers

Python 3, standard library only. Every ingredient must be a basic food key from
app/src/main/assets/foods/generic-cz.json, so nutrition is always calculated from real data.
"""
import argparse
import glob
import json
import os
import re
import sys
import unicodedata

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FOODS = os.path.join(ROOT, "app/src/main/assets/foods/generic-cz.json")
STARTER = os.path.join(ROOT, "app/src/main/assets/recipes/starter-cz.json")
OUT = os.path.join(ROOT, "app/src/main/assets/recipes/pack-cz.json")
SOURCES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "source", "*.jsonl")

MEALS = {"BREAKFAST", "LUNCH", "DINNER", "SNACK"}
CUISINES = {"czech", "italian", "mediterranean", "asian", "indian", "mexican", "middle_eastern", "american", None}
EQUIPMENT = {"OVEN", "AIR_FRYER", "SLOW_COOKER", "RICE_COOKER", "MICROWAVE", "BLENDER"}
STEP_EQUIPMENT = EQUIPMENT | {"STOVE", None}
# The app's own test allows 150-1,200 kcal per serving for every recipe.
KCAL_RANGE = (150, 1200)


def load_foods():
    with open(FOODS, encoding="utf-8") as f:
        data = json.load(f)
    return {food["key"][len("generic:"):]: food for food in data["foods"]}


def norm(text):
    text = unicodedata.normalize("NFKD", text.lower())
    text = "".join(c for c in text if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", " ", text).strip()


def problems_of(recipe, foods):
    p = []
    for field in ("id", "cs", "en", "code", "servings", "meals", "ing", "steps"):
        if field not in recipe:
            return [f"missing {field}"]
    if not re.fullmatch(r"[a-z0-9_]+", recipe["id"]):
        p.append("bad id")
    if not re.fullmatch(r"[A-Z]{1,2}", recipe["code"]):
        p.append("bad code")
    if not isinstance(recipe["servings"], int) or not 1 <= recipe["servings"] <= 8:
        p.append("bad servings")
    if not recipe["meals"] or any(m not in MEALS for m in recipe["meals"]):
        p.append("bad meals")
    if recipe.get("cuisine") not in CUISINES:
        p.append("bad cuisine")
    if any(e not in EQUIPMENT for e in recipe.get("equipment", [])):
        p.append("bad equipment")
    if recipe.get("fridgeDays", 3) not in (1, 2, 3):
        p.append("bad fridgeDays")
    keys = [i[0] for i in recipe["ing"]]
    if len(keys) != len(set(keys)):
        p.append("food listed twice")
    kcal = 0.0
    for ing in recipe["ing"]:
        if ing[0] not in foods:
            p.append(f"unknown food {ing[0]}")
            continue
        if not isinstance(ing[1], (int, float)) or not 0 < ing[1] <= 5000:
            p.append(f"bad grams {ing}")
        if len(ing) == 3 and ing[2] != "optional":
            p.append(f"bad ingredient {ing}")
        if len(ing) == 2:
            kcal += ing[1] * foods[ing[0]]["per100g"]["kcal"] / 100
    for step in recipe["steps"]:
        if len(step) != 5 or not isinstance(step[0], int) or not isinstance(step[1], bool) or step[2] not in STEP_EQUIPMENT:
            p.append(f"bad step {step[:3]}")
        elif not step[3].strip() or not step[4].strip() or step[3] == step[4]:
            p.append("step text missing")
    if not 2 <= len(recipe["steps"]) <= 10:
        p.append("2-10 steps")
    per = kcal / recipe["servings"] if isinstance(recipe["servings"], int) and recipe["servings"] > 0 else 0
    if not KCAL_RANGE[0] <= per <= KCAL_RANGE[1]:
        p.append(f"{per:.0f} kcal per serving")
    return p


def to_asset(recipe):
    """The starter-recipe format the app imports (StarterRecipe in StarterRecipes.kt)."""
    step_gear = {s[2] for s in recipe["steps"]} - {None, "STOVE"}
    equipment = sorted(set(recipe.get("equipment", [])) | step_gear)
    freezable = bool(recipe.get("freezable", False))
    key = re.sub(r"^b\d\d_", "", recipe["id"])

    def steps(lang):
        out = []
        for minutes, passive, gear, cs, en in recipe["steps"]:
            step = {"text": (cs if lang == "cs" else en).strip(), "minutes": minutes, "passive": passive}
            if gear is not None:
                step["equipment"] = gear
            out.append(step)
        return out

    return {
        "key": "pack:cz:" + key,
        "nameCs": recipe["cs"].strip(),
        "nameEn": recipe["en"].strip(),
        "shortCode": recipe["code"],
        "servings": recipe["servings"],
        "mealTypes": recipe["meals"],
        "cuisine": recipe.get("cuisine"),
        "tags": [],
        "equipment": equipment,
        "fridgeDays": recipe.get("fridgeDays", 3),
        "freezable": freezable,
        "freezerMonths": 3,
        "mealPrepFriendly": bool(recipe.get("mealPrep", False)),
        "ingredients": [
            {"food": "generic:" + i[0], "grams": float(i[1]), **({"optional": True} if len(i) == 3 else {})} for i in recipe["ing"]
        ],
        "stepsCs": steps("cs"),
        "stepsEn": steps("en"),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", type=int)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--foods-list")
    args = parser.parse_args()
    foods = load_foods()

    if args.foods_list:
        with open(args.foods_list, "w", encoding="utf-8") as f:
            f.write("key | Czech name | English name | kcal per 100 g | protein g per 100 g | notes\n")
            for key, food in foods.items():
                notes = []
                if food.get("animalFlags"):
                    notes.append("animal:" + ",".join(food["animalFlags"]))
                if food.get("allergens"):
                    notes.append("allergens:" + ",".join(food["allergens"]))
                if food.get("gramsPerPiece"):
                    notes.append("1 piece = %g g" % food["gramsPerPiece"])
                f.write(f"{key} | {food['nameCs']} | {food['nameEn']} | {round(food['per100g']['kcal'])} | {food['per100g'].get('protein', 0)} | {'; '.join(notes)}\n")
        return 0

    with open(STARTER, encoding="utf-8") as f:
        taken_names = {norm(r["nameCs"]) for r in json.load(f)["recipes"]}
    recipes, errors, dropped = [], 0, []
    taken_keys = set()
    taken_en = set()
    for path in sorted(glob.glob(SOURCES)):
        with open(path, encoding="utf-8") as f:
            for n, line in enumerate(f, 1):
                if not line.strip():
                    continue
                recipe = json.loads(line)
                problems = problems_of(recipe, foods)
                if problems:
                    errors += 1
                    print(f"{os.path.basename(path)}:{n} {recipe.get('id')}: {'; '.join(problems)}")
                    continue
                asset = to_asset(recipe)
                # The same dish twice (from two writers, or one already in the starter pack): keep the first.
                if norm(asset["nameCs"]) in taken_names or norm(asset["nameEn"]) in taken_en or asset["key"] in taken_keys:
                    dropped.append(asset["nameCs"])
                    continue
                taken_names.add(norm(asset["nameCs"]))
                taken_en.add(norm(asset["nameEn"]))
                taken_keys.add(asset["key"])
                recipes.append(asset)
    print(f"{len(recipes)} recipes, {len(dropped)} duplicates left out, {errors} with problems")
    if errors:
        return 1
    if args.check:
        return 0
    if not args.version:
        parser.error("--version is required to write the asset")
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"format": "calore-starter-recipes", "version": args.version, "country": "CZ", "recipes": recipes}, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    print(f"wrote {os.path.relpath(OUT, ROOT)} ({os.path.getsize(OUT) // 1024} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
