# TripleCrates (Paper 26.2 / Java 25)

Caisses où 3 coffres tombent du ciel : chaque coffre affiche en hologramme l'item, le nom et la rareté
d'une récompense différente. Un clic droit choisit le coffre ; le clic gauche sur la caisse (avant l'ouverture)
ouvre l'aperçu des récompenses.
L'animation (explosion, 3 coffres, hologrammes, particules, sons) n'est visible que par le joueur qui ouvre
la caisse (moins de paquets, moins de lag).

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
