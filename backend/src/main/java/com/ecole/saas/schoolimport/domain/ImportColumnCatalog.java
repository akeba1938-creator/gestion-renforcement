package com.ecole.saas.schoolimport.domain;

import com.ecole.saas.schoolimport.ImportType;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catalogue pur domaine des colonnes canoniques, alias FR/EN insensibles aux accents/casse
 * et génération des modèles CSV téléchargeables pour les 6 types d'imports scolaires.
 */
public final class ImportColumnCatalog {

    public record ColumnSpec(
            String key,
            String labelFr,
            boolean required,
            String exampleValue,
            List<String> aliases) {
    }

    public record TemplateDescriptor(
            ImportType importType,
            String titleFr,
            String descriptionFr,
            List<ColumnSpec> columns) {
    }

    private static final Map<ImportType, TemplateDescriptor> CATALOG = buildCatalog();

    private ImportColumnCatalog() {
    }

    public static List<TemplateDescriptor> listAll() {
        return List.of(
                CATALOG.get(ImportType.STUDENTS),
                CATALOG.get(ImportType.FAMILIES_GUARDIANS),
                CATALOG.get(ImportType.TEACHERS),
                CATALOG.get(ImportType.ENROLLMENTS),
                CATALOG.get(ImportType.GRADES),
                CATALOG.get(ImportType.ATTENDANCE));
    }

    public static TemplateDescriptor descriptorFor(ImportType importType) {
        TemplateDescriptor descriptor = CATALOG.get(importType);
        if (descriptor == null) {
            throw new SchoolImportValidationException("Type d'import non pris en charge.");
        }
        return descriptor;
    }

    /**
     * Normalise un en-tête brut du fichier (suppression accents, espaces, casse) et le mappe
     * vers sa clé canonique si reconnue.
     */
    public static String resolveCanonicalColumn(ImportType importType, String rawHeader) {
        String normalized = normalizeHeaderToken(rawHeader);
        if (normalized.isEmpty()) {
            return "";
        }
        TemplateDescriptor descriptor = descriptorFor(importType);
        for (ColumnSpec col : descriptor.columns()) {
            if (col.key().equals(normalized)) {
                return col.key();
            }
            for (String alias : col.aliases()) {
                if (alias.equals(normalized)) {
                    return col.key();
                }
            }
        }
        return normalized;
    }

    public static String normalizeHeaderToken(String rawHeader) {
        if (rawHeader == null) {
            return "";
        }
        String stripped = rawHeader.replace("\uFEFF", "").trim();
        String decomposed = Normalizer.normalize(stripped, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String lowered = decomposed.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return lowered;
    }

    public static byte[] buildCsvTemplate(ImportType importType) {
        TemplateDescriptor descriptor = descriptorFor(importType);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        char delimiter = ';';
        for (int i = 0; i < descriptor.columns().size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(ImportFileSecurityPolicy.escapeCsvCell(descriptor.columns().get(i).key(), delimiter));
        }
        sb.append("\r\n");
        for (int i = 0; i < descriptor.columns().size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(ImportFileSecurityPolicy.escapeCsvCell(descriptor.columns().get(i).exampleValue(), delimiter));
        }
        sb.append("\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Map<ImportType, TemplateDescriptor> buildCatalog() {
        Map<ImportType, TemplateDescriptor> map = new LinkedHashMap<>();

        map.put(ImportType.STUDENTS, new TemplateDescriptor(
                ImportType.STUDENTS,
                "Import massif des élèves",
                "Création ou mise à jour contrôlée des dossiers élèves (avec inscription optionnelle en classe).",
                List.of(
                        col("matricule", "Matricule (optionnel, requis pour mise à jour)", false, "",
                                "student_matricule", "code_eleve"),
                        col("first_name", "Prénom", true, "Awa", "prenom", "prenoms"),
                        col("last_name", "Nom", true, "Ndiaye", "nom", "nom_de_famille"),
                        col("gender", "Genre (MALE/FEMALE/OTHER ou M/F)", true, "FEMALE", "sexe", "genre"),
                        col("birth_date", "Date de naissance (YYYY-MM-DD ou DD/MM/YYYY)", true, "2014-05-12",
                                "date_naissance", "date_de_naissance"),
                        col("birth_place", "Lieu de naissance", false, "Dakar", "lieu_naissance", "lieu_de_naissance"),
                        col("nationality", "Nationalité", false, "Sénégalaise", "nationalite"),
                        col("address", "Adresse", false, "Mermoz, Dakar", "adresse"),
                        col("phone", "Téléphone E.164 (+221...)", false, "+221771110001", "telephone", "tel"),
                        col("email", "E-mail", false, "awa.ndiaye@exemple.sn", "mail", "courriel"),
                        col("medical_notes", "Notes médicales (masquées en aperçu)", false, "",
                                "notes_medicales", "observations_medicales"),
                        col("admission_date", "Date d'admission", false, "2026-10-01", "date_admission"),
                        col("class_code", "Code classe pour inscription (optionnel)", false, "6E-A",
                                "classe", "code_classe", "class_room_code"))));

        map.put(ImportType.FAMILIES_GUARDIANS, new TemplateDescriptor(
                ImportType.FAMILIES_GUARDIANS,
                "Import massif des familles, parents et tuteurs",
                "Création ou mise à jour contrôlée des familles, tuteurs légaux et rattachement aux élèves.",
                List.of(
                        col("family_code", "Code famille (optionnel)", false, "FAM-NDIAYE", "code_famille"),
                        col("family_name", "Nom de la famille", false, "Famille Ndiaye", "nom_famille"),
                        col("family_city", "Ville de la famille", false, "Dakar", "ville"),
                        col("guardian_first_name", "Prénom du parent/tuteur", true, "Fatou",
                                "prenom_tuteur", "prenom", "first_name"),
                        col("guardian_last_name", "Nom du parent/tuteur", true, "Ndiaye",
                                "nom_tuteur", "nom", "last_name"),
                        col("guardian_gender", "Genre (MALE/FEMALE/OTHER)", false, "FEMALE", "sexe", "genre"),
                        col("guardian_phone", "Téléphone E.164 (+221...)", true, "+221772220001",
                                "telephone", "phone", "telephone_tuteur"),
                        col("guardian_secondary_phone", "Téléphone secondaire E.164", false, "",
                                "telephone_secondaire", "secondary_phone"),
                        col("guardian_email", "E-mail du tuteur", false, "fatou.ndiaye@exemple.sn",
                                "email", "mail"),
                        col("profession", "Profession", false, "Commerçante", "metier"),
                        col("address", "Adresse", false, "Point E, Dakar", "adresse"),
                        col("national_id_number", "CNI / NIN (jamais journalisé)", false, "",
                                "cni", "nin", "numero_piece"),
                        col("preferred_language", "Langue préférée (fr/wo/en)", false, "fr", "langue"),
                        col("student_matricule", "Matricule élève à rattacher (optionnel)", false, "",
                                "matricule", "matricule_eleve"),
                        col("relationship_type", "Lien parental (MOTHER/FATHER/LEGAL_GUARDIAN/...)", false, "MOTHER",
                                "lien_parente", "relation"),
                        col("primary_contact", "Contact principal (true/false)", false, "true", "contact_principal"),
                        col("financial_responsible", "Responsable financier (true/false)", false, "true",
                                "responsable_financier"))));

        map.put(ImportType.TEACHERS, new TemplateDescriptor(
                ImportType.TEACHERS,
                "Import massif des enseignants et intervenants",
                "Création ou mise à jour contrôlée des enseignants/intervenants avec taux horaire en FCFA entiers.",
                List.of(
                        col("employee_code", "Code employé unique", true, "ENS-001", "code_employe", "matricule_employe"),
                        col("first_name", "Prénom", true, "Cheikh", "prenom"),
                        col("last_name", "Nom", true, "Fall", "nom"),
                        col("gender", "Genre (MALE/FEMALE/OTHER)", false, "MALE", "sexe", "genre"),
                        col("teacher_type", "Statut (PERMANENT/PART_TIME/TUTOR/SUBSTITUTE)", true, "PERMANENT",
                                "type_enseignant", "statut_enseignant"),
                        col("phone", "Téléphone E.164 (+221...)", true, "+221773330001", "telephone", "tel"),
                        col("secondary_phone", "Téléphone secondaire E.164", false, "", "telephone_secondaire"),
                        col("email", "E-mail", false, "cheikh.fall@exemple.sn", "mail"),
                        col("address", "Adresse", false, "Sacré-Cœur, Dakar", "adresse"),
                        col("national_id_number", "CNI / NIN (jamais journalisé)", false, "", "cni", "nin"),
                        col("hire_date", "Date d'embauche", false, "2026-09-15", "date_embauche"),
                        col("hourly_rate_xof", "Taux horaire en FCFA entiers (optionnel)", false, "5000",
                                "taux_horaire_fcfa", "taux_horaire"),
                        col("subject_code", "Code matière spécialité principale (optionnel)", false, "MATH",
                                "code_matiere", "matiere"),
                        col("notes", "Notes", false, "", "observations"))));

        map.put(ImportType.ENROLLMENTS, new TemplateDescriptor(
                ImportType.ENROLLMENTS,
                "Import massif des inscriptions aux classes",
                "Affectation d'élèves existants dans des classes d'une année scolaire avec contrôle de capacité.",
                List.of(
                        col("student_matricule", "Matricule élève", true, "ECO-2026-0001",
                                "matricule", "matricule_eleve"),
                        col("class_code", "Code de la classe", true, "6E-A", "classe", "code_classe", "class_room_code"),
                        col("enrollment_type", "Type (NEW_ADMISSION/RE_ENROLLMENT/TRANSFER_IN)", false, "NEW_ADMISSION",
                                "type_inscription"),
                        col("enrollment_date", "Date d'inscription", false, "2026-10-01", "date_inscription"),
                        col("repeating", "Redoublant (true/false)", false, "false", "redoublant"),
                        col("previous_school", "Établissement précédent", false, "", "ecole_precedente"),
                        col("notes", "Notes", false, "", "observations"))));

        map.put(ImportType.GRADES, new TemplateDescriptor(
                ImportType.GRADES,
                "Import massif des notes d'évaluation",
                "Saisie en lot des notes pour une évaluation cible sur période ouverte (OPEN).",
                List.of(
                        col("student_matricule", "Matricule élève", true, "ECO-2026-0001",
                                "matricule", "matricule_eleve"),
                        col("attendance_status", "Statut (PRESENT/ABSENT_UNJUSTIFIED/ABSENT_JUSTIFIED/EXEMPTED)",
                                false, "PRESENT", "statut_presence", "presence"),
                        col("score", "Note (obligatoire si PRESENT, dans [0, barème])", false, "15.50", "note"),
                        col("comment", "Appréciation / commentaire", false, "Bon travail", "commentaire", "appreciation"))));

        map.put(ImportType.ATTENDANCE, new TemplateDescriptor(
                ImportType.ATTENDANCE,
                "Import massif des présences / feuilles d'appel",
                "Pointage en lot des élèves pour une séance d'appel ouverte ou soumise.",
                List.of(
                        col("student_matricule", "Matricule élève", true, "ECO-2026-0001",
                                "matricule", "matricule_eleve"),
                        col("status", "Statut (PRESENT/LATE/ABSENT/EXCUSED)", true, "PRESENT",
                                "statut", "attendance_status"),
                        col("late_minutes", "Minutes de retard (1..300, obligatoire si LATE)", false, "",
                                "minutes_retard", "retard_minutes"),
                        col("notes", "Observations", false, "", "commentaire", "observations"))));

        return map;
    }

    private static ColumnSpec col(
            String key, String labelFr, boolean required, String example, String... aliases) {
        return new ColumnSpec(key, labelFr, required, example, List.of(aliases));
    }
}
