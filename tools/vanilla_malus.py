#!/usr/bin/env python3
"""Contreparties (malus) des panoplies vanilla, même règle que KP Sets (KPSets/tools/gen_sets.py) :
chaque pièce porte une ligne négative qui coûte MALUS_SHARE de son budget ; 60 % de ce coût est rendu en bonus.
Les jets d'origine (sans malus) sont dans tools/vanilla_base_stats.json : modifie-les là, puis relance
  python tools/vanilla_malus.py [dossiers kp_stats en plus]
pour réécrire data/itemsrarity/kp_items/*.json."""
import glob, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources", "data")
MALUS_SHARE, MALUS_REFUND = 0.60, 0.60
TIER_BUDGET = {1: 40, 2: 70, 3: 110, 4: 160, 5: 230}
SHARE = {"armor": 0.09, "weapon": 0.13, "offhand": 0.07}
RARITY_MULT = {"common": 1.0, "uncommon": 1.1, "rare": 1.2, "epic": 1.3, "legendary": 1.4}
# Plafond par pièce au palier 5, comme KP Sets (un défaut doit gêner, pas casser le jeu)
PIECE_CAP = {"jump": 8, "attack_speed": 6, "agility": 5, "healing": 10, "fire_resistance": 30, "fall": 1,
             "vitality": 2, "resistance": 2, "strength": 8, "wisdom": 30, "arrow_damage": 15}
# Défaut de chaque panoplie (une pièce qui donne déjà cette stat n'en reçoit pas)
MALUS = {
    "itemsrarity:rodeur":      "vitality",         # cuir fin : on vise, on ne tient pas
    "itemsrarity:eclaireur":   "vitality",         # mailles légères, éclaireur exposé
    "itemsrarity:iron_guard":  "agility",          # le fer pèse
    "itemsrarity:orpailleur":  "resistance",       # l'or est mou
    "itemsrarity:champion":    "agility",          # le diamant ne court pas
    "itemsrarity:nether_lord": "healing",          # la netherite brûle aussi son porteur
}

def weights(extra):
    out = {}
    for d in glob.glob(os.path.join(HERE, "..", "..", "*", "src", "main", "resources", "data", "*", "kp_stats")) + \
             [os.path.join(RES, "itemsrarity", "kp_stats")] + extra:
        for f in glob.glob(os.path.join(d, "*.json")):
            out[os.path.basename(f)[:-5]] = json.load(open(f, encoding="utf-8")).get("weight", 1.0)
    return out

def cost(lines, W):
    return sum(max(v) * W.get(k, 1) for k, v in lines.items() if k != "damage" and max(v) > 0)

def main():
    W = weights(sys.argv[1:])  # dossiers kp_stats en plus (ex. KPBlockStamina), si pas à côté
    base = json.load(open(os.path.join(HERE, "vanilla_base_stats.json"), encoding="utf-8"))
    for name, stats in base.items():
        path = os.path.join(RES, "itemsrarity", "kp_items", name + ".json")
        d = json.load(open(path, encoding="utf-8"))
        piece = {k: list(v) for k, v in stats.items()}
        stat = MALUS.get(d.get("set"))
        if stat and stat not in piece:
            ids = " ".join(d["items"])
            slot = "armor" if any(w in ids for w in ("helmet", "chestplate", "leggings", "boots")) else ("offhand" if "shield" in ids else "weapon")
            budget = TIER_BUDGET[d["tier"]] * SHARE[slot] * RARITY_MULT[d.get("rarity", "common")]
            worst = max(1, round(budget * MALUS_SHARE / W[stat]))
            if stat in PIECE_CAP:
                worst = min(worst, max(1, round(PIECE_CAP[stat] * d["tier"] / 5)))
            best = round(worst * 0.4)
            refund = min(worst * W[stat] * MALUS_REFUND, budget / 2)
            pos = {k: v for k, v in piece.items() if k != "damage" and max(v) > 0}
            f = (cost(pos, W) + refund) / cost(pos, W)
            for k, v in pos.items():
                piece[k] = [max(0, round(v[0] * f)), max(1, round(v[1] * f))]
            piece[stat] = [-worst, -best]
            for _ in range(40):
                c = cost(piece, W) - refund
                ups = [k for k in piece if k not in ("damage", stat) and max(piece[k]) > 0]
                if c > budget * 1.10:
                    k = max(ups, key=lambda x: W[x] * piece[x][1])
                    if piece[k][1] <= 1: break
                    piece[k][1] -= 1; piece[k][0] = min(piece[k][0], piece[k][1])
                elif c < budget * 0.85:
                    k = min(ups, key=lambda x: W[x]); piece[k][1] += 1
                else:
                    break
        d["stats"] = piece
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(d, fh, indent=2, ensure_ascii=False); fh.write("\n")
    print(len(base), "pièces vanilla réécrites")

if __name__ == "__main__":
    main()
