#!/usr/bin/env python3
"""
Contrôle d'équilibrage de KPItemsRarity (voir le plan de game design).

Pour chaque objet de data/*/kp_items : somme des jets MAXIMUMS x poids de chaque stat (kp_stats "weight"),
comparée au budget de son emplacement, de son palier et de sa rareté. Même chose pour les bonus de panoplie.
Une ligne négative (malus) rembourse 60 % de son poids (MALUS_REFUND), dans la limite de la moitié du budget.
Les lignes de base (Dégâts, "base": true) sont hors budget.

Usage : python tools/check_balance.py [dossiers de resources...]   (par défaut src/main/resources)
Code de sortie 1 si un objet sort de la fourchette 75 % - 115 %.
"""
import glob, json, os, sys

TIER_BUDGET = {1: 40, 2: 70, 3: 110, 4: 160, 5: 230}
SHARE = {"armor": 0.09, "weapon": 0.13, "offhand": 0.07, "amulet": 0.06, "ring": 0.045, "relic": 0.04,
         "set": 0.18, "parure": 0.07}
# La rareté dépend maintenant des jets (Commun -> Épique) et des crans de forge (Légendaire, Mythique).
# Le champ "rarity" d'une définition n'est qu'une rareté MINIMUM : un objet de boss "rare" a un budget plus grand.
RARITY_MULT = {"common": 1.0, "uncommon": 1.1, "rare": 1.2, "epic": 1.3, "legendary": 1.4, "mythic": 1.5}
LOW, HIGH = 0.75, 1.15
MALUS_REFUND = 0.60
PARURE_SHARE = 0.10  # un malus ne rend pas tout son poids : il doit rester un vrai choix
ARMOR_WORDS = ("helmet", "chestplate", "leggings", "boots", "_cap", "_tunic", "_pants", "_hood", "_mask")
OFFHAND_WORDS = ("shield",)


def slot_of(item_def, item_ids):
    if item_def.get("slot"):
        return item_def["slot"]
    ids = " ".join(item_ids)
    if any(w in ids for w in ARMOR_WORDS):
        return "armor"
    if any(w in ids for w in OFFHAND_WORDS):
        return "offhand"
    return "weapon"


def load(roots):
    stats, items, sets = {}, {}, {}
    for root in roots:
        for f in glob.glob(os.path.join(root, "data", "*", "kp_stats", "*.json")):
            stats[os.path.splitext(os.path.basename(f))[0]] = json.load(open(f, encoding="utf-8"))
        for f in glob.glob(os.path.join(root, "data", "*", "kp_items", "*.json")):
            items[f] = json.load(open(f, encoding="utf-8"))
        for f in glob.glob(os.path.join(root, "data", "*", "kp_sets", "*.json")):
            ns = f.split(os.sep)[-3]
            sets[ns + ":" + os.path.splitext(os.path.basename(f))[0]] = json.load(open(f, encoding="utf-8"))
    return stats, items, sets


def cost(lines, stats, budget):
    total, refund = 0.0, 0.0
    for sid, v in lines.items():
        st = stats.get(sid)
        if st is None or st.get("base"):
            continue
        w = st.get("weight", 1.0)
        if isinstance(v, list):
            v = max(v) if max(v) > 0 else min(v)
        if v >= 0:
            total += v * w
        else:
            refund += -v * w
    return total - min(refund * MALUS_REFUND, budget / 2)


def main():
    roots = sys.argv[1:] or ["src/main/resources"]
    stats, items, sets = load(roots)
    bad = 0
    rows = []
    set_meta = {}
    for f, d in sorted(items.items()):
        tier, rarity = d.get("tier", 1), d.get("rarity", "common")
        slot = slot_of(d, d.get("items", []))
        budget = TIER_BUDGET[tier] * SHARE[slot] * RARITY_MULT.get(rarity, 1.0)
        c = cost(d.get("stats", {}), stats, budget)
        ratio = c / budget if budget else 0
        ok = LOW <= ratio <= HIGH
        bad += not ok
        rows.append((("  " if ok else "!!"), os.path.basename(f), f"P{tier}", rarity, slot, f"{c:5.1f}/{budget:5.1f}", f"{ratio*100:4.0f} %"))
        if d.get("set"):
            set_meta.setdefault(d["set"], (tier, rarity, slot))
    for r in rows:
        print(" ".join(str(x).ljust(w) for x, w in zip(r, (3, 26, 4, 10, 8, 13, 7))))
    print()
    for sid, d in sorted(sets.items()):
        tier, rarity, slot = set_meta.get(sid, (1, "common", "armor"))
        # Parure (panoplie d'accessoires) : budget plus petit, 3 pièces seulement
        share = PARURE_SHARE if slot in ("amulet", "ring", "relic") else SHARE["set"]
        budget = TIER_BUDGET[tier] * share * RARITY_MULT.get(rarity, 1.0)
        th = {int(k): v for k, v in d.get("bonuses", {}).items()}
        full = th[max(th)] if th else {}
        c = cost(full, stats, budget)
        ratio = c / budget if budget else 0
        ok = LOW <= ratio <= HIGH
        bad += not ok
        print(f"{'  ' if ok else '!!'} panoplie {sid:28s} P{tier} {rarity:9s} {c:5.1f}/{budget:5.1f}  {ratio*100:4.0f} %")
    print(f"\n{bad} hors budget" if bad else "\nTout est dans le budget.")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
