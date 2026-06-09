# Beyond Blueprint & Longo — the longevity-corpus landscape

> **Status:** research note (2026-06-09). Not a commitment. Frames whether Idun should
> add a **third recipe set** and, if so, which one and how it lands in the current
> two-set architecture. Pairs with [COMMERCIAL-CLEARANCE.md](../COMMERCIAL-CLEARANCE.md).

Idun currently ships two corpuses, de-branded to **Protocol** (ex-Blueprint / Bryan
Johnson) and **Mediterranean** (ex-Longo / Valter Longo). The question: *is there more
longevity food guidance out there worth a third set?* Yes — the space is much bigger than
two branded protocols. This note surveys the credible candidates, scores them for Idun, and
sketches the engineering + clearance work a third set would take.

---

## 1. The landscape

Longevity-nutrition frameworks fall into three families:

**A. Population / observational** — "what verifiably long-lived people actually eat."
- **Blue Zones (Dan Buettner)** — Okinawa, Sardinia, Ikaria, Nicoya, Loma Linda. Plant-slant,
  beans as cornerstone, the "Power 9" lifestyle rules. Five dietary pillars: whole grains,
  greens, tubers, nuts, beans. Meat ~5×/month, 3–4 oz. Has a 100-recipe cookbook.
- **Okinawan diet** (a Blue Zone in close-up) — sweet potato, leafy/root greens, soy (miso,
  tofu), bitter melon (goya), seaweed; *hara hachi bu* (eat to 80% full → mild CR). Overlaps
  Idun's **no-fixed-slots / eating-window** design conceptually.

**B. Clinical / mechanism-driven patterns** — trial-backed, outcome-targeted.
- **MIND diet (Rush University, Martha Clare Morris, 2015)** — Mediterranean × DASH for the
  aging brain. 10 brain-healthy groups (leafy greens, berries, nuts, legumes, whole grains,
  olive oil, fish, poultry, wine, other veg) + 5 to limit. RCT-grade signal: top-tertile
  scorers had 53% lower Alzheimer's rate; ~7.5 "cognitive years" younger. Has an ~80-recipe book.
- **DASH** — cardiovascular/BP; longevity-adjacent via hard outcomes. Less distinctive vs our
  Mediterranean set.
- **Time-restricted eating / circadian (Satchin Panda)** — *when* not *what*; complements our
  meal-window model rather than being a recipe corpus.

**C. Named-popularizer programs** — branded, recipe-dense.
- **Greger "Daily Dozen" / How Not to Die (NutritionFacts.org)** — whole-food plant-based
  checklist: beans 2–3, berries, other fruit, cruciferous, greens 2, other veg, flax, nuts,
  spices, whole grains + water + exercise. Very recipe-rich; existing free app.
- **Peter Attia (*Outlive*)** — protein- and muscle-mass-forward; a genuine *counterweight* to
  Longo's protein-restriction stance. More a framework than a recipe corpus.
- **Rhonda Patrick / David Sinclair** — micronutrient- and fasting-centric; thin on recipes.

---

## 2. Sourced comparison (third-set candidates)

| Candidate | Pattern | Recipe density | Distinct from our 2 sets? | Brand/IP risk | Idun fit |
|---|---|---|---|---|---|
| **Blue Zones** | Plant-slant, beans-cornerstone, 5 pillars | High (100-recipe cookbook) | Strong — global/peasant, not protocol or supplement | **High** — "BLUE ZONES" is a registered TM (US 2005, EU+), owned by Blue Zones LLC, certification-mark + brand-usage-guide enforced | **Best** — pattern-based, recipe-dense, equal-weight friendly |
| **Greger Daily Dozen** | WFPB checklist | High; existing app | Strong — strict plant-based vs our omnivore sets | **High** — "Daily Dozen" branded; competing free app | Good, but most overlap-prone w/ a future "vegan" lens |
| **MIND diet** | Med×DASH, brain-targeted | Medium (~80-recipe book) | **Weak** — explicitly a Mediterranean derivative; would blur vs our Mediterranean set | Lower — "MIND diet" is academic/descriptive, not an aggressive consumer brand | Marginal — too close to existing set |
| **Okinawan (close-up)** | Plant + soy + mild CR | Medium | Medium — overlaps Blue Zones | Lower — geographic/descriptive term | Interesting as an *eating-window* tie-in, weak as standalone set |
| **Attia / Outlive** | Protein-forward | Low (framework, not corpus) | Strong (counterweight) | Trademarked title; thin recipes | Poor as a *recipe* set |

**Read:** the two corpus-grade candidates are **Blue Zones** and **Greger's Daily Dozen**.
Blue Zones wins on distinctiveness and pattern-based framing (fits "equal-weight sets, don't
lead with one"); Daily Dozen wins on recipe volume but risks becoming a redundant "plant-based
lens" rather than a peer set. MIND is too close to our existing Mediterranean set to justify a
third chip.

**Recommendation:** if a third set is wanted, **Blue Zones** is the strongest candidate — but
treat it exactly like the Blueprint/Longo clearance: concept yes, **brand label no**, original
recipe prose only. "BLUE ZONES" is a *more* aggressively protected mark than "Blueprint" (it's a
registered certification mark with a published brand-usage guide), so a de-branded set label
(e.g. **"Heritage"** or **"Traditional"**) plus name-credit on Credits only is mandatory, not
optional.

---

## 3. Architecture sketch — adding a third set

Grounded in the current code. The set is modeled as a **field on each recipe**
(`"source": "blueprint"|"longo"`), not separate-file-as-identity, and merged at load. The
forcing function is a Kotlin enum with **exhaustive `when`** — the compiler will flag every
site that assumes two sets.

**Where the abstraction lives**
- Enum: [Recipe.kt:38](../../app/src/main/java/com/idun/app/data/Recipe.kt#L38) — `enum class RecipeSource { BLUEPRINT, LONGO }`
- Parse: [RecipeRepository.kt:70](../../app/src/main/java/com/idun/app/data/RecipeRepository.kt#L70) — `RecipeSource.valueOf(r.getString("source").uppercase())`
- Load list (hardcoded 2 files): [RecipeRepository.kt:29](../../app/src/main/java/com/idun/app/data/RecipeRepository.kt#L29)
- Labels: `source_blueprint` / `source_longo` in `res/values*/strings.xml` (EN/ES/CA/FR)

**Two-set assumptions to touch**

1. **Enum** — add a third value to `RecipeSource` ([Recipe.kt:38](../../app/src/main/java/com/idun/app/data/Recipe.kt#L38)). This *breaks compilation* at every exhaustive `when`, which is what we want — a guided checklist.
2. **JSON asset** — add `app/src/main/assets/recipes_<set>.json` (same schema; `"source": "<set>"`).
3. **Repository load list** — add the filename at [RecipeRepository.kt:29](../../app/src/main/java/com/idun/app/data/RecipeRepository.kt#L29). (`values()` iteration, `bySource()` already scale to N.)
4. **Filter chip (XML)** — add a `<Chip>` to [activity_main.xml:42-61](../../app/src/main/res/layout/activity_main.xml#L42) (and the picker layout). Currently 3 hardcoded chips: All / Protocol / Mediterranean.
5. **Chip→enum routing** — extend the `when` at [MainActivity.kt:67](../../app/src/main/java/com/idun/app/MainActivity.kt#L67) and the matching site in `PickRecipeActivity`.
6. **Label `when`s** — [MainActivity.kt:199](../../app/src/main/java/com/idun/app/MainActivity.kt#L199), `PickRecipeActivity.kt:144`, [RecipeDetailActivity.kt:66](../../app/src/main/java/com/idun/app/RecipeDetailActivity.kt#L66).
7. **Strings (×4 locales)** — `source_<set>` label + credits strings.
8. **Credits** — add a third card in `activity_credits.xml` (currently two hardcoded cards). Worth refactoring to data-driven if we go past three.
9. **KB + JSON in tandem** — there is still **no recipe md→JSON generator** ([CLAUDE.md](../../CLAUDE.md), [COMMERCIAL-CLEARANCE.md](../COMMERCIAL-CLEARANCE.md)). New recipes must be written to the Miam KB *and* the JSON by hand, with schema parity, then versionCode bumped.

**Difficulty: moderate.** No data-model migration; the enum + exhaustive `when` make the
compiler the to-do list. Real friction is editorial (re-derived, de-branded recipes + 4-locale
translations), not structural. The one nicety worth doing first: make the **chip group and
credits cards data-driven** off `RecipeSource.values()` so set #3 (and #4) stop touching XML.

---

## 4. Commercial-clearance implications

Idun's clearance posture (v0.3.1) already says: concept yes, upstream prose no, brand label no
(credit by name on Credits only). Every candidate here **inherits that discipline**, and Blue
Zones raises the bar:

- **"BLUE ZONES" is a registered trademark / certification mark** (Buettner trademarked "blue
  zone" in the US in 2005; Blue Zones LLC holds registrations in the US, EU and beyond, with a
  published brand-usage guide). → never a user-facing set label; de-brand it (e.g. "Heritage").
- **"Daily Dozen"** is similarly branded with a competing app. → same treatment, and watch for
  it reading as a clone of that app.
- **MIND / Okinawan** terms are more descriptive/academic — lower mark risk — but MIND fails on
  *distinctiveness*, not IP.
- Recipe **methods must be re-derived** (original technique, not cookbook prose), exactly as
  done for sets 1–2.

---

## 5. Suggested next step

If you want to proceed, the lowest-risk, highest-distinctiveness path is a **Blue Zones-derived
"Heritage" set**: pattern-based, recipe-dense, clearly a peer to Protocol/Mediterranean, and it
reinforces the eating-window story via the Okinawan *hara hachi bu* angle. Before any recipes:
(1) decide the de-branded label, (2) data-drive the chip group + credits cards, (3) write the
KB-first re-derived recipes with EN canon + ES/CA/FR. None of this is committed — flag it and
I'll scope a branch.

---

## Sources

- [Power 9® — Blue Zones](https://www.bluezones.com/2016/11/power-9/)
- [Blue Zones Diet: Food Secrets — Blue Zones](https://www.bluezones.com/2020/07/blue-zones-diet-food-secrets-of-the-worlds-longest-lived-people/)
- [The Blue Zones Kitchen (100 recipes) — Amazon](https://www.amazon.com/Blue-Zones-Kitchen-Recipes-Live/dp/1426220138)
- [Diet of Dan Buettner — CNBC](https://www.cnbc.com/2024/03/16/diet-of-dan-buettner-longevity-expert-who-coined-term-blue-zones.html)
- [BLUE ZONES Brand Usage Guide](https://www.bluezones.com/blue-zones-brand-usage-guide/)
- [Blue Zones Founder's Statement (TM ownership)](https://www.bluezones.com/founders-statement/)
- [Dr. Greger's Daily Dozen — NutritionFacts.org](https://nutritionfacts.org/daily-dozen/)
- [Foods for Health and Longevity: Daily Dozen — Prama](https://prama.org/blog/foods-for-health-and-longevity-dr-gregers-daily-dozen/)
- [Diet Review: MIND Diet — Harvard Nutrition Source](https://nutritionsource.hsph.harvard.edu/healthy-weight/diet-reviews/mind-diet/)
- [MIND diet slows cognitive decline — PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC4581900/)
- [MIND Diet Linked to Better Cognitive Performance — Rush](https://www.rush.edu/news/mind-diet-linked-better-cognitive-performance)
- [Okinawa diet — Wikipedia](https://en.wikipedia.org/wiki/Okinawa_diet)
- [Why the traditional Okinawan diet is the recipe for a long life — National Geographic](https://www.nationalgeographic.com/science/article/okinawa-diet-benefits-blue-zones)
