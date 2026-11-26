---
name: frontend-visual-audit
description: Procédure réutilisable d'audit visuel réel dans Chrome avant toute livraison UI : démarrage auto du serveur Angular local, connexion /dev-login, inspection DOM & visuelle sur 1440x900, 1280x800 et 390x844 en modes clair et sombre (zéro débordement, colonnes/boutons, icônes SVG, cibles tactiles >= 44px) puis nettoyage.
---
# Audit Visuel Réel Frontend (`frontend-visual-audit`)

Procédure obligatoire de contrôle visuel dans un navigateur réel avant toute livraison d'écran ou de refonte UI dans `frontend/`. Elle complète `.agents/rules/frontend-quality-gate.md`, `.agents/rules/design-system-constitution.md` et `.agents/skills/frontend-design/SKILL.md`.

## Étapes

### 1. Démarrage du serveur local
- Vérifier si `http://localhost:4200` est déjà disponible.
- S'il ne répond pas, démarrer le serveur Angular en arrière-plan (`cd frontend && npm start`) et attendre que la compilation soit prête (sans jamais commiter ni laisser le processus orphelin à la fin).

### 2. Authentification de développement
- Ouvrir `http://localhost:4200/dev-login` dans le navigateur.
- Cliquer sur le bouton de connexion de démonstration (rôle Direction / Admin dev) pour initialiser la session en mémoire, puis naviguer vers la route cible (ex. `/dashboard`).

### 3. Inspection multi-viewports (Mode Clair & Mode Sombre)
Tester systématiquement les **3 viewports** en **mode clair** (`data-theme="light"`) puis en **mode sombre** (`data-theme="dark"`) via le sélecteur de thème de la barre supérieure :
- **Desktop large** : `1440×900`
- **Laptop standard** : `1280×800`
- **Mobile** : `390×844`

Pour chaque combinaison *(viewport × thème)*, exécuter les vérifications DOM et visuelles suivantes :
1. **Zéro débordement horizontal global et tableau** :
   - `document.documentElement.scrollWidth <= window.innerWidth`
   - Pour chaque tableau desktop visible (`table` non `sr-only`) : `table.scrollWidth <= table.parentElement.clientWidth` et `table.getBoundingClientRect().right <= card.getBoundingClientRect().right + 1`.
2. **Boutons d'action & colonnes de tableau** :
   - Vérifier que chaque bouton d'action (`Reçu`, `Vérifier`, etc.) reste strictement contenu dans sa cellule `<td>` (`btnRect.left >= tdRect.left - 1` et `btnRect.right <= tdRect.right + 1`), ne retourne pas à la ligne (`whitespace-nowrap`) et conserve une hauteur/largeur harmonieuse.
3. **Intégrité des icônes SVG** :
   - Vérifier que chaque icône Lucide visible (`svg.lucide`) possède une boîte englobante non nulle (`width > 0 && height > 0`), un ratio propre (`Math.abs(width - height) <= 1`, aucun écrasement), un `stroke-width` cohérent (`1.75`) et des tracés internes rendus (`querySelectorAll('path, circle, rect, line, polyline, polygon').length > 0`).
4. **Responsive mobile (`390×844`)** :
   - Vérifier que les tableaux larges basculent en cartes empilées propres (`table` masqué sur mobile) sans aucun débordement horizontal.
   - Vérifier que les boutons et contrôles interactifs principaux respectent des zones tactiles `>= 44px` (`height >= 44` ou `min-h-11`).
5. **Cohérence visuelle clair / sombre** :
   - Contrôler l'absence de texte illisible, de badge rogné, de chiffre KPI coloré criard ou d'infobulle graphique tronquée.

### 4. Correction, nettoyage & rapport
- Corriger immédiatement toute anomalie détectée puis re-vérifier le viewport concerné.
- Arrêter le serveur Angular temporaire si cette procédure l'a démarré.
- Restituer un rapport concis indiquant :
  - Statut par viewport (`1440×900`, `1280×800`, `390×844`) en clair et en sombre.
  - Métriques clés (`scrollWidth`, tableaux, icônes, cibles tactiles).
  - Liste des anomalies corrigées (ou « Aucune anomalie »).
