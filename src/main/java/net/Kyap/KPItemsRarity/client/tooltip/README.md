# Infobulles

Deux fenêtres, un seul moteur :

- **l'infobulle RPG**, pour tous les objets (il n'y a pas d'autre infobulle d'objet) ;
- **la carte de panoplie**, qui la remplace quand on maintient Alt sur une pièce.

Les deux sont des `Card` (en-tête + corps qui défile + pied) dessinées par le même `CardRenderer`.
Seuls l'en-tête et le contenu changent.

```
Minecraft veut dessiner une infobulle
  └─ mixin/GuiGraphicsTooltipMixin      remplace TOUJOURS le dessin vanilla pour un objet
       └─ item/RpgTooltip               entrée : Alt -> carte de panoplie, sinon :
            ├─ item/ItemCard            QUOI : construit la Card de l'objet
            │    ├─ item/ItemHeader       en-tête : nom, rareté, type, dégâts/armure, aperçu animé
            │    ├─ item/StatRows         lignes de stats et attributs vanilla, dans l'ordre des stats (+ aide avec Maj)
            │    ├─ item/PassiveRows      conditions et passifs
            │    ├─ item/ForeignLines     nourriture, lignes des autres mods
            │    └─ text/*                type d'objet, descriptions, lore
            └─ card/CardRenderer        OÙ et COMMENT : mesure, place, dessine, fait défiler

Alt maintenu sur une pièce
  └─ setcard/SetCard                    ouverture, molette, en-tête (cases des pièces), survol d'une pièce
       ├─ setcard/SetCardContent        QUOI : titre, pièces portées, bonus par palier, lore
       └─ card/CardRenderer             le même moteur
```

## Dossiers

| Dossier    | Rôle |
|------------|------|
| `card/`    | Le moteur commun : `Card` (une fenêtre), `Elem` (les éléments du corps : ligne, titre, texte, passif...), `CardRenderer` (mesure, place, dessine), `CardScroll` (molette), `RpgFrame` (cadre, couleurs, ornements). |
| `item/`    | L'infobulle d'un objet : `RpgTooltip` (entrée), `ItemCard` (contenu), `ItemHeader` (en-tête) et les sections du corps. |
| `setcard/` | La carte de panoplie : `SetCard` (ouverture, en-tête, survol) et `SetCardContent` (contenu). |
| `text/`    | Les textes affichés : `ItemTypes` ("Épée", "Bague"...), `ItemDescriptions`, `LoreText`, `Texts` (nombres, touches). |

## Ajouter quelque chose

- **Une nouvelle ligne dans l'infobulle d'un objet** : l'ajouter au corps dans `ItemCard` (ou dans la section qui
  s'en occupe), avec un `Elem` existant. Rien à mesurer ni à dessiner à la main.
- **Un nouveau genre d'élément** (une barre, une image...) : un nouveau `record` dans `Elem`, qui dit sa hauteur, sa
  largeur et se dessine ; il marche alors dans les deux fenêtres.
- **Une nouvelle fenêtre** : un `Card.Header` pour l'en-tête, une liste d'`Elem` pour le corps, puis
  `CardRenderer.render(...)`.

Si l'infobulle plante, l'erreur est écrite une fois dans le journal (`latest.log`) et rien ne s'affiche : il n'y a
pas de repli sur une infobulle texte.
