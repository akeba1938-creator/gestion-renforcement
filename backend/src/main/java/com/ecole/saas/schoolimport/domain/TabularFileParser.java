package com.ecole.saas.schoolimport.domain;

import com.ecole.saas.schoolimport.ImportType;

/**
 * Contrat domaine pur de parsing sécurisé de fichiers tabulaires (CSV / XLSX).
 */
public interface TabularFileParser {

    ParsedTabularDataset parse(byte[] rawBytes, ImportType importType, Character preferredDelimiter);
}
