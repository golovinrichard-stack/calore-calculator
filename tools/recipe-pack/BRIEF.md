# Recipe writing brief (Calore app, Czech Republic)

You write home-cooking recipes for a Czech calorie and meal-prep app. People buy the ingredients in
ordinary Czech supermarkets (Lidl, Albert, Tesco, Kaufland, Billa, Penny, Globus). The app calculates
nutrition from the ingredients, so the ingredient list and the gram amounts must be realistic.

## Ingredients: ONLY these foods

Every ingredient must be one `key` from the file `foods.txt` (first column; `build.py --foods-list foods.txt` writes it). Read the whole
file before you start. Do not invent keys, do not use a key that is not in the file, do not add water
(mention water only in the steps). If a dish needs something that is not in the list, choose a different
dish or leave that item out.

- Use the **raw / dry** form for anything cooked in the recipe: `rice_white_raw`, `pasta_dry`,
  `lentils_red`, `chicken_breast_raw`, `potato_raw`, `oats`, `quinoa_raw`, `couscous_dry` … Use the
  `_cooked` / `_boiled` / `_roasted` forms only when the recipe really starts from leftovers.
- Grams are the amount for the WHOLE recipe (all servings together), as a number. Typical amounts:
  meat 120–200 g per serving, dry rice/pasta 60–90 g per serving, potatoes 200–300 g per serving,
  oil 5–10 g per serving, salt 1–3 g per serving, a spice 1–3 g in total, garlic 5–15 g in total,
  1 egg = 50 g, 1 onion = 110 g.
- Optional ingredients (garnish, toppings) get `"optional"` as a third element.

## Calories per serving (check with the kcal column of foods.txt)

- BREAKFAST: 300–650 kcal per serving
- LUNCH / DINNER: 450–900 kcal per serving
- SNACK: 150–400 kcal per serving

Add up roughly: sum of (grams × kcal per 100 g ÷ 100) for all ingredients, divided by servings.

## Output format

Write **JSON Lines**: one recipe per line, one JSON object per line, nothing else in the file (no
markdown, no comments, no trailing commas). Example line (shown on several lines here only for reading):

{"id": "kure_na_paprice", "cs": "Kuře na paprice s těstovinami", "en": "Chicken paprikash with pasta",
 "code": "KP", "servings": 4, "meals": ["LUNCH", "DINNER"], "cuisine": "czech", "equipment": [],
 "fridgeDays": 3, "freezable": true, "mealPrep": true,
 "ing": [["chicken_thigh_raw", 700], ["onion_raw", 220], ["paprika_spice", 12], ["sour_cream", 200],
         ["flour_wheat", 20], ["sunflower_oil", 20], ["salt", 6], ["pasta_dry", 320], ["parsley", 10, "optional"]],
 "steps": [[8, false, "STOVE", "Cibuli nakrájejte nadrobno a na oleji ji osmahněte dozlatova.", "Finely chop the onion and fry it in the oil until golden."],
           [5, false, "STOVE", "Přidejte kuřecí maso nakrájené na kousky a opečte ho ze všech stran.", "Add the chicken, cut into pieces, and brown it on all sides."],
           [25, true, "STOVE", "Vmíchejte papriku, osolte, podlijte trochou vody a duste pod pokličkou doměkka.", "Stir in the paprika, season with salt, add a little water and simmer covered until tender."],
           [12, true, "STOVE", "Mezitím uvařte těstoviny podle návodu na obalu.", "Meanwhile cook the pasta as the packet says."],
           [3, false, "STOVE", "Zakysanou smetanu rozmíchejte s moukou, vmíchejte do omáčky a krátce povařte.", "Mix the sour cream with the flour, stir it into the sauce and simmer briefly."]]}

Fields:
- `id`: unique, lowercase ASCII with underscores, no diacritics (e.g. `cesnecka_s_chlebem`).
- `cs`: Czech name (correct Czech with diacritics). `en`: English name.
- `code`: 2 capital letters, a short label for food boxes (e.g. initials of the Czech name).
- `servings`: 1–8.
- `meals`: one or more of `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`.
- `cuisine`: one of `czech`, `italian`, `mediterranean`, `asian`, `indian`, `mexican`, `middle_eastern`,
  `american`, or null.
- `equipment`: special equipment the recipe NEEDS, from `OVEN`, `AIR_FRYER`, `SLOW_COOKER`,
  `RICE_COOKER`, `MICROWAVE`, `BLENDER`. A normal stove/hob is not listed here. Usually `[]` or `["OVEN"]`.
- `fridgeDays`: how many days the cooked dish keeps in the fridge: 1–3 (salads with dressing,
  fish, rice dishes: 1–2; stews, sauces, roasts: 3).
- `freezable`: true for stews, soups, sauces, meatballs, goulash, curries; false for salads, eggs,
  fried or crispy food, pasta salads, fresh dishes.
- `mealPrep`: true if it travels well in a lunch box and reheats or is eaten cold well.
- `ing`: list of [key, grams] or [key, grams, "optional"].
- `steps`: 2–8 steps, each [minutes, passive, equipment, Czech text, English text].
  `minutes` = how long the step takes (integer, can be 0 for "serve"). `passive` = true when the cook
  can do something else meanwhile (simmering, baking, soaking, resting, cooling). `equipment` =
  `"STOVE"`, one of the equipment names above, or null. The Czech and English text say the same thing.
  Write your own original instructions in a plain, friendly style; do not copy recipe websites.
  Czech uses the polite plural form ("nakrájejte", "vařte"), like the example.

## Quality rules

- Real dishes that Czech people cook and like. Use Czech names Czech people know.
- No duplicates: not within your file, not of the names in `existing.txt`, and not of names in your
  topic that another helper obviously owns (stay in your topic).
- Correct food safety: meat and fish fully cooked; no raw eggs in cold dishes.
- Breakfasts and snacks are often 1 serving; family dishes 4; meal-prep batches 4–6.
- Vary ingredients: do not put the same 5 foods in every recipe.
