---
trigger: glob
globs: "backend/**"
description: "Conventions du backend Spring Boot : couches, API, erreurs, injection."
---
# Backend (Spring Boot 4, Java 21)
- Modules : `identity, school, subscription, student, family, teacher, academic, grade, finance, attendance, communication, center, schooldocs, schoolimport, reporting, audit` sous `com.ecole.saas`.
- Sous-paquets : `api` (contrôleurs, DTO en records, MapStruct), `application` (services, `@Transactional`), `domain` (Java pur, annotations `jakarta.persistence` autorisées, aucune annotation Spring), `infrastructure` (dépôts Spring Data, adaptateurs).
- Injection par constructeur uniquement. Pas de `@Autowired` sur champ.
- Frontières entre modules vérifiées par ArchUnit et Spring Modulith. Communication par interface publique ou événement applicatif.
- API : préfixe `/api/v1`, OpenAPI à jour, erreurs `ProblemDetail` (RFC 9457) en français, pagination et tri obligatoires avec taille maximale.
- `spring.jpa.open-in-view=false`. Surveiller les requêtes N+1.
- Configuration par profils (dev, test, preprod, prod). Secrets par variables d'environnement.
- Journaux structurés avec identifiant de tenant et de requête, sans donnée personnelle.
