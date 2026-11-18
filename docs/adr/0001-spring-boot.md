# ADR 0001 — Spring Boot 4 plutôt que Jakarta EE pur
- Statut : accepté
## Contexte
Produit commercial, petite équipe, besoin d'avancer vite avec sécurité, batch et tests prêts à l'emploi.
## Décision
Spring Boot 4 sur Jakarta EE 11 (Servlet, Persistence, Validation), déploiement en JAR avec Tomcat embarqué.
## Conséquences
Écosystème large et recrutement plus simple. Pas de certification Jakarta EE de plateforme. Domaine métier en Java pur pour limiter la dépendance au framework.
## Alternatives écartées
Jakarta EE pur sur WildFly, Payara ou Open Liberty : retenu seulement si un client impose un serveur certifié.
