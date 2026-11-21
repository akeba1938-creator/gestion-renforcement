package com.ecole.saas.schoolimport.domain;

import com.ecole.saas.schoolimport.ImportSourceFormat;
import java.util.List;

/**
 * Jeu de données tabulaire extrait d'un fichier CSV ou XLSX contrôlé par type réel.
 */
public record ParsedTabularDataset(
        ImportSourceFormat detectedFormat,
        List<String> normalizedHeaders,
        List<ParsedTabularRow> rows) {
}
