# Sécurité et modèle de menaces
## Principes
Authentification courte durée, autorisations côté serveur, moindre privilège, journalisation des opérations sensibles, secrets hors dépôt, sauvegardes testées.

## Menaces principales
| Menace | Impact | Parade |
|---|---|---|
| Lecture ou modification inter-établissements | Fuite de données d'élèves | Tenant côté serveur, RLS, tests d'isolation |
| Falsification de paiement ou de reçu | Pertes financières | Registre non modifiable, signature des notifications, idempotence, QR opaque |
| Fichier importé malveillant | Compromission serveur | Type réel, limites, traitement isolé, analyse |
| Vol de compte directeur ou caissier | Fraude, fuite | Multifacteur, limitation des tentatives, audit |
| Abus des liens d'accès parent | Fuite sur un enfant | Jetons longs, courts, révocables |
| Dépendance vulnérable | Faille exploitable | Dependabot, analyse, revue des ajouts |
| Perte de données | Arrêt d'activité | Sauvegardes externalisées, test de restauration |
| Données de mineurs mal protégées | Atteinte à la vie privée, risque juridique | Minimisation, durées de conservation, avis juridique CDP |
