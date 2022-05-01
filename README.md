# Generateur_ANFR
Télécharge l'open data auprès de l'ANFR (data.anfr.fr) et génère les bases de données utilisées par le projet eNB Analytics + analyse des différences entre 2 datasets. 4G et 5G uniquement.

Il faut manuellement récupérer et placer le fichier SUP_ANTENNE.txt disponible sur data.gouv.fr (Données sur les installations radioélectriques de plus de 5 watts) dans le dossier /input

Diffusion/Usage/Modifications librement autorisés à condition de citer la source et publier les modifications/améliorations

Nécessite Java 8 minimum. Compiler avec la commande :
```
javac Main
```
Usage<br />
./Generateur_ANFR.sh anfr<br />
Téléchargement + traitement + analyse des différences 

./Generateur_ANFR.sh anfr-local<br />
Traitement + analyse des différences. Nécéssite au préalable d'avoir manuellement téléchargé l'open-data et placé le fichier (qui doit être nommé ANFR.csv) dans le dossier /input

./Generateur_ANFR.sh diff-only<br />
Analyse des différences uniquement


Le fichier carto des différences, à utiliser avec Leaflet, se trouve dans /Generated/diff.js<br />
Chaque base générée se trouve dans /SQL/ANFR/yyyy-Sww_version.db<br />
La base utilisée par l'appli se trouve dans /SQL/ANFR_SQLite.db (à compresser au format XZ)


## Carte HTML
Les icônes de la carte sont dans le dossier html/assets/images/ et doivent au préalable être colorées/personnalisées.
