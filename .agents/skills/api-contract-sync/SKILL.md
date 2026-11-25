---
name: api-contract-sync
description: Procédure d'alignement strict du frontend Angular avec le backend Spring Boot /api/v1 (modèles TS calqués sur les DTOs Java, intercepteur RFC 9457 global, extraction requestId, gestion 401/refresh cookie, alignement des rôles ADMIN, CASHIER, TEACHER, TUTOR, PARENT, STUDENT).
---
# Synchronisation des Contrats API (`/api/v1`)

## Étapes
1. **Inspecter le contrat backend source de vérité** :
   - Lire les contrôleurs et DTOs (`record`) dans `backend/src/main/java/com/ecole/saas/<module>/api/` ainsi que `GlobalExceptionHandler` pour vérifier routes, paramètres de pagination/tri, enums, montants FCFA entiers (`long`) et dates UTC ISO-8601 (`Instant` / `LocalDate`).
2. **Aligner strictement les modèles TypeScript** :
   - Définir les interfaces DTO dans `frontend/src/app/core/models/` (ou `features/<module>/models/`) à l'identique des records Java (zéro `any`, champs nullables/optionnels explicites).
   - Conserver l'injection par `InjectionToken` (`STUDENT_API`, `FINANCE_API`, etc.) pour basculer proprement entre les adaptateurs `HttpClient` réels et les doubles de test.
3. **Intercepteur RFC 9457 global & `requestId`** :
   - Typer `ProblemDetail` RFC 9457 (`type`, `title`, `status`, `detail`, `instance`, `requestId`, erreurs de validation par champ).
   - Brancher un `HttpInterceptorFn` global qui transforme toute `HttpErrorResponse` en erreur applicative normalisée : message métier en français (`detail ?? title`), erreurs par champ propagées aux formulaires sans effacer la saisie, et extraction systématique du **`requestId`** (corps `ProblemDetail` puis fallback en-tête `X-Request-Id`).
4. **Authentification, `401` & Refresh Cookie** :
   - Conserver le JWT d'accès strictement en mémoire dans `AuthService` (Signals), **jamais** dans `localStorage` ou `sessionStorage`.
   - Garder `authInterceptor` scopé à `API_BASE_URL` (`/api/v1`) avec contrôle d'origine et sans aucun en-tête `X-Tenant-Id`.
   - Sur `401` (hors `/api/v1/auth/login` et `/api/v1/auth/refresh`), tenter un renouvellement unique via cookie `HttpOnly` (`withCredentials: true`) mutualisé entre requêtes concurrentes ; si le refresh échoue, appeler `authService.clearSession()` et rediriger vers `/login`.
5. **Alignement des rôles & espaces** :
   - Vérifier la correspondance exacte entre les `@PreAuthorize` backend et la visibilité frontend (routes `roleGuard`, menus et boutons d'action) pour l'ensemble des profils :
     - **Administration (`ADMIN`)** : `SAAS_ADMIN`, `PROMOTER`, `DIRECTOR`, `SECRETARY`, `CENSOR`.
     - **Caisse / Finance** : `CASHIER`.
     - **Pédagogie & Soutien** : `TEACHER`, `TUTOR`.
     - **Portails Famille / Élève** : `PARENT`, `STUDENT`.
   - Documenter que la visibilité frontend reste purement ergonomique (la sécurité est exclusivement serveur).
6. **Tests & validation** :
   - Couvrir chaque client HTTP et intercepteur avec `provideHttpClientTesting()` + `HttpTestingController` : réponses nominales, pagination/tri, erreurs RFC 9457 (`400`, `403`, `404`, `409`, `422`, `500`) avec extraction `requestId`, cycle `401` + refresh réussi/échoué, protection contre la fuite de Bearer hors origine.
   - Exécuter `cd frontend && npm test && npm run build`.
