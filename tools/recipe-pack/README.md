# Big recipe pack

Builds `app/src/main/assets/recipes/pack-cz.json`: about a thousand recipes cooked from food sold in
ordinary Czech supermarkets (Lidl, Albert, Tesco, Kaufland, Billa, Penny, Globus). The app imports it
like the 30 starter recipes (M3.4): by `key`, versioned, never touching a recipe the user edited or
deleted. Every ingredient is a basic food from `assets/foods/generic-cz.json`, so all nutrition is
calculated from USDA / Open Food Facts data; nothing in the pack has its own nutrition values.

| File | What it is |
|---|---|
| `BRIEF.md` | The instructions the recipe writers followed (format, calorie ranges, food safety). |
| `source/*.jsonl` | The recipes, one per line, by topic (20 topics × 50). Written with AI helpers from the brief, then checked by `build.py`. |
| `build.py` | Checks every recipe (known foods, 150–1,200 kcal per serving, both languages, enums), leaves out duplicates (the same dish twice, or one already in the starter pack) and writes the asset. Python 3, standard library only. |

## Rebuild

```bash
python3 tools/recipe-pack/build.py --check
python3 tools/recipe-pack/build.py --version 2
```

Raise `--version` whenever the pack changes; phones import a newer version on the next start.
`--foods-list foods.txt` writes the list of allowed foods for whoever writes new recipes.

The texts are original. Czech wording: please report anything that sounds wrong.
