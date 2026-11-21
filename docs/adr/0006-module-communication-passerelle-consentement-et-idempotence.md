# ADR 0006 — Module `communication` : graphe acyclique, passerelle abstraite, consentement et idempotence

- Statut : accepté

## Contexte
Le module `communication` doit orchestrer des campagnes et notifications multicanaux (`IN_APP`, `SMS`, `WHATSAPP`, `EMAIL`) vers toutes les populations d'un établissement (familles/tuteurs, élèves, classes, cohortes centre, enseignants/intervenants, personnel) tout en :
1. Respectant le graphe acyclique Spring Modulith (`ApplicationModules.verify()`) sans jamais créer de cycle avec `school`, `subscription`, `academic`, `student`, `family`, `teacher`, `center`, `identity` et `audit`.
2. Isolant le domaine métier de tout fournisseur externe SMS/WhatsApp/Email et en garantissant l'absence totale d'appel réseau dans les tests.
3. Assurant la traçabilité immuable des remises, l'idempotence contre les doubles envois/coûts FCFA et le respect strict du consentement/opt-out.

## Décision
1. **Positionnement dans le DAG Spring Modulith** :
   - `communication` consomme unidirectionnellement les ports publics en lecture seule (`SchoolDirectory`, `SubscriptionAccessPort`, `AcademicStructurePort`, `StudentDirectoryPort`, `FamilyDirectoryPort`, `TeacherDirectoryPort`, `CenterDirectoryPort`, `UserDirectoryPort`) et `AuditLogPort`.
   - `communication` expose `CommunicationPort` (`com.ecole.saas.communication`) avec des commandes typées (`AbsenceNotificationCommand`, `PaymentReceiptNotificationCommand`, `ReportCardPublishedNotificationCommand`, `PaymentDueReminderNotificationCommand`, `TriggerNotificationCommand`) pour les orchestrateurs, tâches planifiées et modules situés en aval (ex. `reporting`, `schooldocs`).
   - Les modules situés en amont (`attendance`, `finance`, `grade`) n'importent jamais `CommunicationPort` directement (ce qui créerait un cycle) : leur intégration automatique future passera par des événements applicatifs publiés par ces modules et consommés unidirectionnellement par `communication`.
2. **Adaptateur abstrait de passerelle (`ChannelDeliveryGateway` dans `domain`)** :
   - Le contrat `ChannelDeliveryGateway` réside dans `domain` en Java pur ; les implémentations par canal (`InAppDeliveryGateway`, `LocalStubSmsGateway`, `LocalStubWhatsAppGateway`, `LocalStubEmailGateway`) vivent dans `infrastructure`.
   - Tous les coûts unitaires et totaux sont calculés en entiers FCFA (`long` / `BIGINT`) avec calcul pur des segments SMS (`SmsSegmentCalculator`, GSM-7 vs Unicode).
3. **Consentement, idempotence et journal de remise immuable** :
   - Chaque envoi exige une `idempotency_key` unique par tenant avec contrôle d'empreinte SHA-256 : rejeu identique (`200 OK` + `X-Idempotent-Replay: true`) sans doublon ni surcoût ; payload différent → `409 Conflict`.
   - Les destinataires sont dédupliqués par `(campaign_id, recipient_type, recipient_id, channel)` et filtrés par `ConsentPolicy` en domaine pur (traçant explicitement `CHANNEL_OPTED_OUT`, `MARKETING_OPTED_OUT`, `MISSING_CONTACT_ADDRESS` sans relance automatique).
   - Les transitions `QUEUED / SENT / DELIVERED / FAILED / READ` sont consignées dans `communication_delivery_events` (ajout seul protégé par trigger SQL et droits PostgreSQL `SELECT, INSERT` uniquement pour `ecole_app`).

## Conséquences
- Aucun cycle de dépendance dans Spring Modulith ; couche `domain` 100 % Java pur sans Spring.
- Tests d'intégration et unitaires rapides, déterministes et totalement hors réseau.
- Remplacement futur d'un adaptateur local SMS/WhatsApp/Email par un connecteur opérateur (Orange, Twilio, Meta, SMTP) sans impact sur `domain`, `application` ni `api`.
