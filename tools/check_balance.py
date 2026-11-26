#!/usr/bin/env python3
"""
Contrôle d'équilibrage rapide, modèle du doc « Système de builds » (refonte de novembre 2026).

La source de vérité des objets est KPSets/tools/design.py (le générateur build.py calcule chaque ligne). Ce script
relit les JSON générés (kp_items, kp_sets, kp_stats) et vérifie qu'aucun objet ne sort de son budget :

    palier   armure   arme   bijou   relique
      1        10      14      6       8
      2        14      19,5    8,5     11
      3        19      26,5    11,5    15
      4        26      36      15,5    20,5
      5        35      49      21      27,5

Une ligne vaut la moyenne de sa fourchette × le poids de sa stat (kp_stats "weight"). Les lignes de base
(Dégâts, Dégâts des flèches) sont hors budget. Un malus rend 75 % de son coût. Le budget réel d'une pièce varie :
pièce libre ×1,15, objet vanilla ×0,7, armure d'Immersive Armors ×0,9, et une armure paie (ou gagne) sa défense
d'origine au-dessus (ou en dessous) du gabarit moyen, de −35 % à +35 %. D'où la tolérance large : 42 % (armure vanilla lourde : 0,7 × 0,65) à 160 %.

Bonus de panoplie : au complet, environ 25 % de la somme des budgets des pièces (le passif vaut le reste).

Usage : python tools/check_balance.py [dossiers resources...]   (par défaut src/main/resources, et ../KPSets s'il existe)
        --tout : affiche aussi les objets dans la tolérance
Code de sortie 1 si un objet sort de la tolérance ou utilise une stat retirée ("legacy").
"""
import glob, json, os, sys

BUDGET = {1: {"armor": 10, "weapon": 14, "jewel": 6, "relic": 8},
          2: {"armor": 14, "weapon": 19.5, "jewel": 8.5, "relic": 11},
          3: {"armor": 19, "weapon": 26.5, "jewel": 11.5, "relic": 15},
          4: {"armor": 26, "weapon": 36, "jewel": 15.5, "relic": 20.5},
          5: {"armor": 35, "weapon": 49, "jewel": 21, "relic": 27.5}}
LOW, HIGH = 0.42, 1.60
MALUS_REFUND = 0.75
BASE_LINES = {"damage", "arrow_damage"}
ARMOR_WORDS = ("helmet", "chestplate", "leggings", "boots")


def kind_of(d):
    slot = d.get("slot")
    if slot in ("amulet", "ring"):
        return "jewel"
    if slot == "relic":
        return "relic"
    names = " ".join(d.get("items", []))
    if any(w in names for w in ARMOR_WORDS) or slot in ARMOR_WORDS:
        return "armor"
    return "weapon"


def avg(v):
    return (v[0] + v[1]) / 2 if isinstance(v, list) else v


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    show_all = "--tout" in sys.argv
    roots = args or ["src/main/resources"] + (["../KPSets/src/main/resources"] if os.path.isdir("../KPSets/src/main/resources") else [])
    stats, items, sets = {}, {}, {}
    for root in roots:
        for f in glob.glob(os.path.join(root, "data", "*", "kp_stats", "*.json")):
            stats[os.path.basename(f)[:-5]] = json.load(open(f, encoding="utf-8"))
        for f in glob.glob(os.path.join(root, "data", "*", "kp_items", "*.json")):
            items[f] = json.load(open(f, encoding="utf-8"))
        for f in glob.glob(os.path.join(root, "data", "*", "kp_sets", "*.json")):
            sets[f] = json.load(open(f, encoding="utf-8"))
    bad = 0
    for f, d in sorted(items.items(), key=lambda kv: (kv[1].get("tier", 1), kv[0])):
        name = os.path.basename(f)[:-5]
        tier, kind = d.get("tier", 1), kind_of(d)
        pos = mal = 0.0
        legacy = []
        for sid, v in d.get("stats", {}).items():
            st = stats.get(sid, {})
            if sid in BASE_LINES:
                continue
            if st.get("legacy"):
                legacy.append(sid)
            x = avg(v) * st.get("weight", 1.0)
            pos, mal = (pos + x, mal) if x >= 0 else (pos, mal - x)
        expected = BUDGET[tier][kind] + MALUS_REFUND * mal
        ratio = pos / expected
        ok = LOW <= ratio <= HIGH and not legacy
        bad += not ok
        if show_all or not ok:
            print(f"{'  ' if ok else '!!'} P{tier} {kind:6} {name:30} {pos:5.1f} / {expected:5.1f}  {ratio * 100:4.0f} %"
                  + (f"   stat retirée : {', '.join(legacy)}" if legacy else ""))
    for f, d in sorted(sets.items()):
        for k, line in d.get("bonuses", {}).items():
            for sid in line:
                if stats.get(sid, {}).get("legacy"):
                    bad += 1
                    print(f"!! panoplie {os.path.basename(f)[:-5]} ({k} pièces) : stat retirée {sid}")
    print(f"\n{len(items)} objets, {len(sets)} panoplies : " + (f"{bad} problème(s)." if bad else "tout est dans la tolérance."))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
