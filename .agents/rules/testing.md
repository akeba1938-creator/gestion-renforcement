---
trigger: model_decision
description: "À appliquer quand on écrit ou modifie des tests, ou avant de déclarer une tâche terminée."
---
# Tests
- Domaine : JUnit 5 sans Spring, couverture visée ≥ 85 % sur notes et finance.
- Tranches : `@WebMvcTest`, `@DataJpaTest` avec Testcontainers PostgreSQL.
- Intégration : `@SpringBootTest` sur les parcours complets (inscription, paiement, bulletin, import).
- Isolation multi-tenant et permissions testées pour chaque ressource REST.
- Architecture : ArchUnit + Spring Modulith.
- Bout en bout : Playwright, version mobile.
- Données de test fictives uniquement.
