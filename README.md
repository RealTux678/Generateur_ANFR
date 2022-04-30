# Generateur_ANFR
Télécharge l'open data auprès de l'ANFR (data.anfr.fr) et génère les bases de données utilisées par le projet eNB Analytics + analyse des différences entre 2 datasets. 4G et 5G uniquement.

Il faut manuellement récupérer et placer le fichier SUP_ANTENNE.txt disponible sur data.gouv.fr (Données sur les installations radioélectriques de plus de 5 watts) dans le dossier /input

Diffusion/Usage/Modifications librement autorisés à condition de citer la source et publier les modifications/améliorations

Usage

./Generateur_ANFR.sh anfr
Téléchargement + traitement + analyse des différences 

./Generateur_ANFR.sh anfr-local
Traitement + analyse des différences. Nécéssite au préalable d'avoir manuellement téléchargé l'open-data et placé le fichier (qui doit être nommé ANFR.csv) dans le dossier /input

./Generateur_ANFR.sh diff-only
Analyse des différences uniquement
