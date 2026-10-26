# TripleCrates (Paper 26.2 / Java 25)

Caisses où 3 coffres tombent du ciel : chaque coffre affiche en hologramme l'item, le nom et la rareté
d'une récompense différente. Un clic droit choisit le coffre ; le clic gauche sur la caisse (avant l'ouverture)
ouvre l'aperçu des récompenses.
L'animation (explosion, 3 coffres, hologrammes, particules, sons) n'est visible que par le joueur qui ouvre
la caisse (moins de paquets, moins de lag).

## Animation d'ouverture
1. La caisse **gonfle** (jusqu'à `inflate-scale`, 2x par défaut) en tremblant de plus en plus, puis **explose**.
2. Les 3 coffres **tombent** (accélération), **rebondissent** légèrement puis se stabilisent, bien droits.
   Les coffres de gauche et de droite sont légèrement tournés vers le joueur (`side-turn`).
3. **Machine à sous** : sur chaque coffre les récompenses défilent puis ralentissent et s'arrêtent sur la vraie
   récompense (gauche, puis milieu, puis droite). À l'arrêt, des éclats jaillissent du coffre, de la couleur de la
   rareté (`rarity-colors` dans config.yml : vert commun, bleu rare, violet épique, rouge mythique).
4. Le joueur choisit son coffre au clic droit.
Tous les réglages sont dans `default-animation` (config.yml) et modifiables par caisse dans l'éditeur.
Note : une caisse avec un modèle 3D (MythicMobs/ModelEngine) ne peut pas gonfler (le modèle reste tel quel
jusqu'à l'explosion) ; les caisses « bloc » gonflent normalement.

## Couleurs et formats de texte
Partout (noms de caisse/clé/récompense, lore, messages, titres d'aperçu, hologrammes, rarités, éditeur, chat) :
- codes Minecraft : `&3`, `&l`, `&r`... (le `§` marche aussi) ;
- couleurs HTML / hex : `#ff8800`, `&#ff8800`, `<#ff8800>`, `&x&f&f&8&8&0&0` ;
- MiniMessage : `<red>`, `<gradient:#ff0000:#0000ff>`, `<bold>`... ;
- tout se mélange. Comme en jeu, une couleur annule le gras/souligné précédent.

## Aperçu personnalisable (par caisse)
Section `preview` du fichier de la caisse (`crates/<id>.yml`). Tout est optionnel.

| Clé | Rôle |
|---|---|
| `title`, `rows` (1 à 6) | titre (glyphes Nexo ok, `%crate%`) et nombre de lignes |
| `reward-slots` | cases où se placent les récompenses sans `preview-slot`, dans l'ordre : `[10, 11, "19-25"]` |
| `rewards.<id>.preview-slot` | case fixe d'une récompense (affichée sur toutes les pages) |
| `reward-lore` | lignes ajoutées sous chaque récompense : `<rarity> <chance> <weight> <amount> <name>` |
| `filler`, `filler-area` | item de remplissage (matériau ou `nexo:<id>`) et zone : `bottom-row`, `all`, `none` |
| `items` | boutons et décor, voir ci-dessous (remplace ceux de config.yml) |

Les cases : numéro (0 = en haut à gauche), intervalle `"0-8"`, ou négatif pour compter depuis la fin (`-1` = dernière case).
Si les récompenses sont plus nombreuses que les cases disponibles, des pages sont créées (boutons `next-page` / `prev-page`).

Chaque entrée de `items` : `slot` ou `slots`, `item` (matériau, `nexo:<id>`, `base64:...`), `name`, `lore`, `amount`,
`glow`, `hide-tooltip`, `action` (`close`, `next-page`, `prev-page`, `command`, `none`),
`commands` (pour `command` ; `%player%`, préfixes `[player]` par défaut, `[console]`, `[close]`),
`show` (`always`, `has-next`, `has-prev`). Un exemple commenté se trouve dans `crates/example.yml`.

## Caisse avec modèle 3D (ModelEngine / MythicMobs)
- Une fois le modèle appliqué, le bloc vanilla de la caisse (coffre...) est remplacé par une **barrière** invisible
  (solide et cliquable) : seul le modèle se voit. Si le modèle n'a pas pu être appliqué, le bloc reste visible.
  Si tu retires le modèle de la caisse, le bloc d'origine revient.
- Pour retirer une caisse à modèle : Shift + casser la barrière en **créatif** (la barrière est incassable en survie).
- Le modèle ne prend jamais de dégâts (pas de teinte rouge au clic gauche / aperçu).

## Modèle des 3 coffres (Nexo, ModelEngine, MythicMobs)
- Coffre Nexo : `chest-item: nexo:storage_chest` dans `default-animation` (config.yml), ou par caisse dans
  l'éditeur (Animation > Item / modèle Nexo des coffres, en tenant l'item ou en tapant `nexo:storage_chest`).
  Le modèle de l'item est affiché sur chaque coffre ; ajuste `chest-scale`, `chest-y-offset` et
  `chest-item-transform` si besoin.
- Modèle tourné dans le mauvais sens : essaie `chest-yaw-offset: 90`, puis `-90` ou `180`.
- Modèle décentré par rapport à l'hologramme : `chest-offset-right` (0.2 = 0,2 bloc vers la droite du joueur,
  négatif = vers la gauche) et `chest-offset-forward`. Règle d'abord la rotation, puis le centrage.
  `/crates reload` puis `/crates open <joueur> <caisse>` permet de tester sans clé.
- Hologrammes : fond transparent et texte ombré (`hologram.background`, `hologram.shadow` dans config.yml).
- Au choix de la récompense, les 3 coffres disparaissent immédiatement. `end-delay` ne règle plus que le
  délai avant que la caisse réapparaisse.
- Une caisse avec modèle 3D garde son modèle visible pendant toute l'animation (elle ne gonfle pas).

## Compilation
Prérequis : JDK 25 + Gradle.
    gradle build
Le jar est généré dans `build/libs/TripleCrates-1.0.0.jar` → dossier `plugins/`.

## Utilisation rapide
1. `/crates editor` : crée des raretés, une clé, une caisse, lie la clé à la caisse, ajoute des récompenses.
2. `/crates give <joueur> <caisse>` puis pose l'item : la caisse est active (Shift + casser pour la retirer, admin).
3. `/crates givekey <joueur> <clé>` : le joueur tient la clé en main et fait clic droit sur la caisse.
   Clic gauche sur la caisse (avant l'ouverture) = aperçu (récompenses, rareté, pourcentage).
   Pendant l'animation, le clic gauche ne fait rien : seul le clic droit sur un coffre est utilisé.

## Commandes (permission `triplecrates.admin`, sauf aperçu/ouverture : `triplecrates.use`)
/crates editor | give <joueur> <caisse> [n] | givekey <joueur> <clé> [n] | open <joueur> <caisse>
/crates preview <caisse> [joueur] | list | reload

## Récompenses
- ITEM : donne l'item (matériau, `nexo:<id>`, ou n'importe quel item tenu en main : outils spéciaux
  marteau 3x3 / houe auto-replante… sont conservés tels quels, NBT/PDC inclus via `base64:`).
- COMMAND : exécute les commandes (console par défaut, `[player]` pour exécuter en tant que joueur).
  Placeholders : %player% %uuid% %crate% %reward%. L'item sert d'icône.
- Nom / lore d'affichage modifiables (hologramme + aperçu), rareté, poids (chance = poids / total).

## Nexo / MythicMobs / ModelEngine (détection automatique, par réflexion)
- Nexo : `nexo:<id>` partout (récompenses, clés, coffres, filler/boutons de l'aperçu).
  GUI custom : colle le caractère du glyphe Nexo dans le titre de l'aperçu.
- Modèle 3D d'une caisse posée : éditeur → caisse → « Modèle 3D » (MYTHICMOBS = nom du mob,
  MODELENGINE = blueprint). Mets le bloc de la caisse sur BARRIER si le modèle remplace le bloc.
- Modèle 3D des coffres qui tombent : éditeur → Animation → « Modèle des coffres ».

## Fichiers
config.yml (hologramme, animations par défaut, aperçu), messages.yml, rarities.yml, keys.yml,
crates/<id>.yml (un fichier par caisse), data.yml (caisses posées).
L'éditeur réécrit les fichiers de caisses/clés/raretés (les commentaires YAML de ces fichiers sont perdus).
