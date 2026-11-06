# M3: Recipes and dishes

**Goal:** recipes (the user's own and a starter pack), and logging a dish by hand.
**Users get:** a recipe book with calories and macros per serving, and a fast way to log "a plate of my goulash".
**Depends on:** M2.
**Read first:** [data-model.md](data-model.md) (M3), [algorithms.md](algorithms.md) A9 and A15 (hard filters).
**New dependencies:** none.

## Tasks

### M3.1 Recipe data (database version 5)

- [ ] Tables `recipes`, `recipe_ingredients` and `recipe_steps` (data-model M3).
- [ ] Domain: `Recipe`, `RecipeIngredient`, `RecipeStep`, and `RecipeNutrition` with the total, per serving, and per 100 g cooked (only when `learnedYield` is known).
- [ ] Allergens of a recipe = all allergens of its ingredients. Diet check = A15 hard filters.

**Done when:** unit tests cover the nutrition totals (with unknown values staying unknown), the allergen union, and diet compatibility for each `Diet`.

### M3.2 Recipe builder

- [ ] Name, servings, short code (suggested from the name).
- [ ] Ingredients: food search (basic foods first) or the scanner, grams, note, optional.
- [ ] Steps: text, minutes, "needs no attention" (passive), equipment.
- [ ] Storage: fridge days (default 3), freezable, freezer months (default 3), reheating note.
- [ ] Meal types, cuisine, tags, equipment, "good for meal prep".
- [ ] A nutrition summary that updates live (total and per serving), plus allergens.
- [ ] Validation: a name, at least one ingredient, grams > 0.

**Done when:** a UI test creates, edits and deletes a recipe.

### M3.3 Recipes in the Plan tab

- [ ] Bottom bar: **Today · Plan · Me**. For now, Plan shows the recipe list (the week plan joins in M5).
- [ ] Search, and filters for meal type and "fits my diet".
- [ ] Recipes with one of my allergens are hidden by default, with a "Show all" switch.
- [ ] Recipe detail: ingredients, steps, nutrition per serving, allergens, storage. Buttons: **Log a portion** (M3.5), **Cook** (M4).

### M3.4 Starter pack

The owner asked for "some random food". Write **30 recipes**, a mix of Czech home cooking and meal-prep classics. All texts must be original (don't copy recipe websites). Every ingredient must reference a basic food `key` from M2.2, so nutrition is calculated.

| # | Czech name | English name | Meal |
|---|---|---|---|
| 1 | Ovesná kaše s tvarohem a ovocem | Oat porridge with quark and fruit | Breakfast |
| 2 | Overnight oats s jogurtem | Overnight oats with yoghurt | Breakfast |
| 3 | Zeleninová omeleta | Vegetable omelette | Breakfast |
| 4 | Tvarohová pomazánka s celozrnným chlebem | Quark spread on wholegrain bread | Breakfast |
| 5 | Proteinové palačinky | Protein pancakes | Breakfast |
| 6 | Kuře s rýží a brokolicí | Chicken, rice and broccoli (cooked as separate parts) | Lunch, dinner |
| 7 | Hovězí guláš s rýží | Beef goulash with rice | Lunch, dinner |
| 8 | Kuře na paprice s těstovinami | Chicken paprikash with pasta | Lunch, dinner |
| 9 | Čočka na kyselo s vejcem | Sour lentils with egg | Lunch, dinner |
| 10 | Fazolový guláš | Bean goulash (vegan) | Lunch, dinner |
| 11 | Kuřecí rizoto se zeleninou | Chicken risotto with vegetables | Lunch, dinner |
| 12 | Těstoviny s tuňákem a rajčaty | Tuna and tomato pasta | Lunch, dinner |
| 13 | Špagety bolognese | Spaghetti bolognese | Lunch, dinner |
| 14 | Chili con carne s rýží | Chili con carne with rice | Lunch, dinner |
| 15 | Kuřecí kari s rýží | Chicken curry with rice | Lunch, dinner |
| 16 | Krůtí ragú s těstovinami | Turkey ragù with pasta | Lunch, dinner |
| 17 | Pečená kuřecí stehna s bramborami a zeleninou | Roast chicken thighs with potatoes and vegetables | Lunch, dinner |
| 18 | Losos s bramborem a fazolkami | Salmon with potatoes and green beans | Lunch, dinner |
| 19 | Cizrnové kari | Chickpea curry (vegan) | Lunch, dinner |
| 20 | Vepřové na kmíně s rýží | Caraway pork with rice | Lunch, dinner |
| 21 | Špenát s bramborem a vejcem | Spinach with potatoes and egg | Lunch, dinner |
| 22 | Houbové rizoto | Mushroom risotto | Lunch, dinner |
| 23 | Burrito bowl s mletým hovězím | Beef burrito bowl | Lunch, dinner |
| 24 | Kuskusový salát s kuřetem | Couscous salad with chicken | Lunch |
| 25 | Zapečené těstoviny se šunkou a sýrem | Pasta bake with ham and cheese | Lunch, dinner |
| 26 | Tvaroh s ovocem | Quark with fruit | Snack |
| 27 | Řecký jogurt s ořechy a medem | Greek yoghurt with nuts and honey | Snack |
| 28 | Hummus se zeleninou | Hummus with vegetables | Snack |
| 29 | Vejce natvrdo | Hard-boiled eggs | Snack |
| 30 | Cottage s rajčaty a pečivem | Cottage cheese with tomatoes and bread | Snack |

- [ ] Format: `app/src/main/assets/recipes/starter-cz.json`:

```json
{ "version": 1, "recipes": [ {
  "key": "starter:cz:chili", "nameCs": "Chili con carne s rýží", "nameEn": "Chili con carne with rice",
  "shortCode": "CH", "servings": 6, "mealTypes": ["LUNCH","DINNER"], "cuisine": "international",
  "tags": ["beef","spicy"], "equipment": [], "fridgeDays": 3, "freezable": true, "freezerMonths": 3,
  "mealPrepFriendly": true,
  "ingredients": [ { "food": "generic:beef_minced_raw", "grams": 800 } ],
  "stepsCs": [ { "text": "…", "minutes": 10, "passive": false } ],
  "stepsEn": [ { "text": "…", "minutes": 10, "passive": false } ]
} ] }
```

- [ ] Import like the basic foods: by `key`, versioned, storing both languages (`nameCs` / `nameEn`, `textCs` / `textEn`) so switching the phone's language just works. Never overwrite a recipe the user has edited (`userEdited`).
- [ ] Ask the owner to look through the list.

**Done when:** all 30 import and show plausible nutrition (a unit test checks each recipe has kcal per serving between 150 and 1,200), and every ingredient key resolves.

### M3.5 Log a dish by hand

- [ ] Add food gets a **Dishes** tab: pick a recipe, then enter **servings** (default 1) or **grams** (only when the recipe has a learned yield).
- [ ] Saved as `entryType = DISH` with `recipeId` and a nutrient snapshot. Editing changes servings or grams and scales the snapshot, like portions today.

**Done when:** logging 1.5 servings of a recipe adds exactly 1.5 × the per-serving values.

### M3.6 Scale and swap

- [ ] **Scale** a recipe for one cook (× factor, or "for N servings"). This doesn't change the saved recipe.
- [ ] **Swap** an ingredient for another food (same grams by default), with nutrition shown before and after. **Save as a copy** keeps the original.

## Out of scope

Importing recipes from websites (an idea for later), per-member ratings (M6).
