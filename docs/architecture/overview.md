# Vue d'ensemble
Monolithe modulaire en couches. Backend Spring Boot 4 (Jakarta EE 11), frontend Angular 22 (PWA), PostgreSQL, stockage d'objets compatible S3.

```text
Angular PWA  →  Nginx  →  Spring Boot (modules)  →  PostgreSQL (RLS)
                                   ↘ stockage S3   ↘ SMS / WhatsApp / agrégateur de paiement
```

Couches par module : `api` → `application` → `domain` ← `infrastructure`.
Tâches de fond : Spring Batch (imports), `@Scheduled` (relances), `@Async`.
Observabilité : Actuator, Micrometer, OpenTelemetry, journaux JSON.
Choix justifiés : voir `docs/adr/`.
