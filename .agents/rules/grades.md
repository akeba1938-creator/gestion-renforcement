---
trigger: model_decision
description: "À appliquer pour notes, moyennes, bulletins, classements et matricules."
---
# Notes, bulletins, matricules
- Moyenne = somme(coefficient × note) / somme(coefficients) sur les matières notées. Règles configurables : absent, non saisi, arrondi, mentions, passage, ex æquo.
- Bulletin : brouillon → validé → verrouillé. Modification après verrouillage : autorisation, motif, historique.
- Calculs dans `domain`, sans Spring, avec tests unitaires exhaustifs et jeux de données validés par un enseignant.
- Matricule `CODE_ECOLE-ANNEE-NUMERO`, année = première inscription, séquence par école et année, unicité en base, stable toute la scolarité.
- Réinscription = nouvelle `StudentEnrollment`, jamais un nouvel élève. Détection de doublons avant création.
- Règle ambiguë (format de bulletin, classement, arrondi) : poser la question, ne pas deviner.
