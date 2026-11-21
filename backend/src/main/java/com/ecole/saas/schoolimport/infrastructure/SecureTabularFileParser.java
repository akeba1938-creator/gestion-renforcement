package com.ecole.saas.schoolimport.infrastructure;

import com.ecole.saas.schoolimport.ImportSourceFormat;
import com.ecole.saas.schoolimport.ImportType;
import com.ecole.saas.schoolimport.domain.ImportColumnCatalog;
import com.ecole.saas.schoolimport.domain.ImportFileSecurityPolicy;
import com.ecole.saas.schoolimport.domain.ParsedTabularDataset;
import com.ecole.saas.schoolimport.domain.ParsedTabularRow;
import com.ecole.saas.schoolimport.domain.SchoolImportValidationException;
import com.ecole.saas.schoolimport.domain.TabularFileParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Parseur sécurisé pur Java 21 (zéro dépendance externe) pour fichiers CSV (RFC
 * 4180 UTF-8)
 * et classeurs Excel XLSX (OOXML) avec contrôle par type réel, garde
 * anti-ZIP-bomb et durcissement anti-XXE.
 */
@Component
public class SecureTabularFileParser implements TabularFileParser {

    @Override
    public ParsedTabularDataset parse(byte[] rawBytes, ImportType importType, Character preferredDelimiter) {
        ImportSourceFormat format = ImportFileSecurityPolicy.detectAndValidateFormat(rawBytes);
        List<List<String>> rawRows = format == ImportSourceFormat.XLSX
                ? parseXlsxMatrix(rawBytes)
                : parseCsvMatrix(ImportFileSecurityPolicy.decodeStrictUtf8(rawBytes), preferredDelimiter);

        if (rawRows.isEmpty()) {
            throw new SchoolImportValidationException(
                    "schoolimport-empty-dataset",
                    "Fichier sans données",
                    "Le fichier ne contient aucune ligne d'en-tête.");
        }

        List<String> rawHeaders = rawRows.getFirst();
        if (rawHeaders.size() > ImportFileSecurityPolicy.MAX_COLUMNS_PER_ROW) {
            throw new SchoolImportValidationException(
                    "schoolimport-too-many-columns",
                    "Trop de colonnes",
                    "Le fichier dépasse la limite maximale de "
                            + ImportFileSecurityPolicy.MAX_COLUMNS_PER_ROW + " colonnes.");
        }

        List<String> canonicalHeaders = new ArrayList<>(rawHeaders.size());
        boolean hasAnyHeader = false;
        for (String h : rawHeaders) {
            String canonical = ImportColumnCatalog.resolveCanonicalColumn(importType, h);
            if (!canonical.isEmpty()) {
                hasAnyHeader = true;
            }
            canonicalHeaders.add(canonical);
        }
        if (!hasAnyHeader) {
            throw new SchoolImportValidationException(
                    "schoolimport-missing-headers",
                    "En-têtes manquants",
                    "La première ligne du fichier doit contenir les en-têtes de colonnes.");
        }

        List<ParsedTabularRow> dataRows = new ArrayList<>();
        for (int i = 1; i < rawRows.size(); i++) {
            List<String> cells = rawRows.get(i);
            if (isBlankRow(cells)) {
                continue;
            }
            if (cells.size() > ImportFileSecurityPolicy.MAX_COLUMNS_PER_ROW) {
                throw new SchoolImportValidationException(
                        "schoolimport-too-many-columns",
                        "Trop de colonnes",
                        "La ligne " + (i + 1) + " dépasse le maximum autorisé de "
                                + ImportFileSecurityPolicy.MAX_COLUMNS_PER_ROW + " colonnes.");
            }
            if (dataRows.size() >= ImportFileSecurityPolicy.MAX_ROWS_PER_JOB) {
                throw new SchoolImportValidationException(
                        "schoolimport-too-many-rows",
                        "Limite de lignes dépassée",
                        "Le fichier dépasse la limite maximale autorisée de "
                                + ImportFileSecurityPolicy.MAX_ROWS_PER_JOB + " lignes par lot.");
            }

            Map<String, String> rowMap = new LinkedHashMap<>();
            for (int col = 0; col < canonicalHeaders.size(); col++) {
                String key = canonicalHeaders.get(col);
                if (key.isEmpty()) {
                    continue;
                }
                String value = col < cells.size() ? cells.get(col) : "";
                if (value != null && value.length() > ImportFileSecurityPolicy.MAX_CELL_LENGTH) {
                    throw new SchoolImportValidationException(
                            "schoolimport-cell-too-long",
                            "Cellule trop longue",
                            "La ligne " + (i + 1) + " (colonne '" + key + "') dépasse "
                                    + ImportFileSecurityPolicy.MAX_CELL_LENGTH + " caractères.");
                }
                if (value != null && !value.isBlank()) {
                    rowMap.putIfAbsent(key, value.trim());
                }
            }
            dataRows.add(new ParsedTabularRow(i + 1, Map.copyOf(rowMap)));
        }

        if (dataRows.isEmpty()) {
            throw new SchoolImportValidationException(
                    "schoolimport-no-data-rows",
                    "Aucune ligne de données",
                    "Le fichier ne contient aucune ligne de données exploitable après l'en-tête.");
        }

        List<String> nonEmptyHeaders = canonicalHeaders.stream().filter(h -> !h.isEmpty()).toList();
        return new ParsedTabularDataset(format, nonEmptyHeaders, List.copyOf(dataRows));
    }

    private List<List<String>> parseCsvMatrix(String content, Character preferredDelimiter) {
        char delimiter = resolveDelimiter(content, preferredDelimiter);
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder currentCell = new StringBuilder();
        boolean inQuotes = false;
        int len = content.length();

        for (int i = 0; i < len; i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && content.charAt(i + 1) == '"') {
                        currentCell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    currentCell.append(c);
                }
            } else {
                if (c == '"' && currentCell.isEmpty()) {
                    inQuotes = true;
                } else if (c == delimiter) {
                    currentRow.add(currentCell.toString());
                    currentCell.setLength(0);
                } else if (c == '\r') {
                    if (i + 1 < len && content.charAt(i + 1) == '\n') {
                        i++;
                    }
                    currentRow.add(currentCell.toString());
                    currentCell.setLength(0);
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                } else if (c == '\n') {
                    currentRow.add(currentCell.toString());
                    currentCell.setLength(0);
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                } else {
                    currentCell.append(c);
                }
            }
            if (rows.size() > ImportFileSecurityPolicy.MAX_ROWS_PER_JOB + 10) {
                throw new SchoolImportValidationException(
                        "schoolimport-too-many-rows",
                        "Limite de lignes dépassée",
                        "Le fichier dépasse la limite maximale autorisée de "
                                + ImportFileSecurityPolicy.MAX_ROWS_PER_JOB + " lignes par lot.");
            }
        }
        if (inQuotes) {
            throw new SchoolImportValidationException(
                    "schoolimport-malformed-csv",
                    "CSV mal formé",
                    "Guillemet non fermé détecté dans le fichier CSV.");
        }
        if (!currentCell.isEmpty() || !currentRow.isEmpty()) {
            currentRow.add(currentCell.toString());
            rows.add(currentRow);
        }
        return rows;
    }

    private char resolveDelimiter(String content, Character preferredDelimiter) {
        if (preferredDelimiter != null) {
            if (preferredDelimiter != ',' && preferredDelimiter != ';' && preferredDelimiter != '\t') {
                throw new SchoolImportValidationException(
                        "Délimiteur CSV non pris en charge (utilisez ';', ',' ou tabulation).");
            }
            return preferredDelimiter;
        }
        int endFirstLine = content.indexOf('\n');
        String firstLine = endFirstLine >= 0 ? content.substring(0, endFirstLine) : content;
        int semicolons = countChar(firstLine, ';');
        int commas = countChar(firstLine, ',');
        return semicolons >= commas && semicolons > 0 ? ';' : ',';
    }

    private static int countChar(String s, char target) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    private List<List<String>> parseXlsxMatrix(byte[] zipBytes) {
        Map<String, byte[]> entries = extractValidatedXlsxEntries(zipBytes);
        if (!entries.containsKey("[content_types].xml") || !entries.containsKey("xl/workbook.xml")) {
            throw new SchoolImportValidationException(
                    "schoolimport-invalid-xlsx",
                    "Classeur XLSX invalide",
                    "Le fichier ZIP fourni n'est pas un classeur Excel (.xlsx) OOXML valide.");
        }
        byte[] sheetBytes = resolveFirstWorksheet(entries);
        if (sheetBytes == null) {
            throw new SchoolImportValidationException(
                    "schoolimport-invalid-xlsx",
                    "Classeur XLSX invalide",
                    "Aucune feuille de calcul valide n'a été trouvée dans le classeur .xlsx.");
        }

        List<String> sharedStrings = entries.containsKey("xl/sharedstrings.xml")
                ? parseSharedStrings(entries.get("xl/sharedstrings.xml"))
                : List.of();
        return parseWorksheetXml(sheetBytes, sharedStrings);
    }

    private Map<String, byte[]> extractValidatedXlsxEntries(byte[] zipBytes) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long totalUncompressed = 0L;
        int entryCount = 0;
        byte[] buffer = new byte[8192];

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > ImportFileSecurityPolicy.MAX_XLSX_ENTRIES) {
                    throw new SchoolImportValidationException(
                            "schoolimport-unsafe-xlsx",
                            "Archive XLSX rejetée",
                            "Le classeur XLSX contient un nombre excessif d'entrées internes.");
                }
                String name = entry.getName();
                if (name == null || name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    throw new SchoolImportValidationException(
                            "schoolimport-unsafe-xlsx",
                            "Archive XLSX rejetée",
                            "Chemin d'entrée non autorisé détecté dans l'archive XLSX.");
                }
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    totalUncompressed += read;
                    if (totalUncompressed > ImportFileSecurityPolicy.MAX_XLSX_UNCOMPRESSED_BYTES) {
                        throw new SchoolImportValidationException(
                                "schoolimport-unsafe-xlsx",
                                "Archive XLSX rejetée",
                                "La taille décompressée du classeur XLSX dépasse la limite de sécurité (20 Mo).");
                    }
                    out.write(buffer, 0, read);
                }
                entries.put(name.replace('\\', '/').toLowerCase(Locale.ROOT), out.toByteArray());
                zis.closeEntry();
            }
        } catch (IOException ex) {
            throw new SchoolImportValidationException(
                    "schoolimport-invalid-xlsx",
                    "Classeur XLSX corrompu",
                    "Impossible de lire l'archive XLSX fournie.");
        }
        return entries;
    }

    private static byte[] resolveFirstWorksheet(Map<String, byte[]> entries) {
        if (entries.containsKey("xl/worksheets/sheet1.xml")) {
            return entries.get("xl/worksheets/sheet1.xml");
        }
        return entries.entrySet().stream()
                .filter(e -> e.getKey().startsWith("xl/worksheets/sheet") && e.getKey().endsWith(".xml"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private List<String> parseSharedStrings(byte[] xmlBytes) {
        Document doc = parseXmlSecurely(xmlBytes);
        NodeList siNodes = doc.getElementsByTagNameNS("*", "si");
        List<String> strings = new ArrayList<>(siNodes.getLength());
        for (int i = 0; i < siNodes.getLength(); i++) {
            Element si = (Element) siNodes.item(i);
            NodeList textNodes = si.getElementsByTagNameNS("*", "t");
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < textNodes.getLength(); j++) {
                String text = textNodes.item(j).getTextContent();
                if (text != null) {
                    sb.append(text);
                }
            }
            strings.add(sb.toString());
        }
        return strings;
    }

    private List<List<String>> parseWorksheetXml(byte[] xmlBytes, List<String> sharedStrings) {
        Document doc = parseXmlSecurely(xmlBytes);
        NodeList rowNodes = doc.getElementsByTagNameNS("*", "row");
        List<List<String>> result = new ArrayList<>();

        for (int i = 0; i < rowNodes.getLength(); i++) {
            if (result.size() > ImportFileSecurityPolicy.MAX_ROWS_PER_JOB + 10) {
                throw new SchoolImportValidationException(
                        "schoolimport-too-many-rows",
                        "Limite de lignes dépassée",
                        "Le classeur dépasse la limite maximale autorisée de "
                                + ImportFileSecurityPolicy.MAX_ROWS_PER_JOB + " lignes par lot.");
            }
            Element rowEl = (Element) rowNodes.item(i);
            TreeMap<Integer, String> cellsByCol = new TreeMap<>();
            NodeList children = rowEl.getChildNodes();
            int implicitCol = 0;
            for (int c = 0; c < children.getLength(); c++) {
                Node node = children.item(c);
                if (node.getNodeType() != Node.ELEMENT_NODE || !"c".equals(localName(node))) {
                    continue;
                }
                Element cellEl = (Element) node;
                String ref = cellEl.getAttribute("r");
                int colIndex = (ref != null && !ref.isBlank()) ? parseColumnIndex(ref) : implicitCol;
                if (colIndex >= ImportFileSecurityPolicy.MAX_COLUMNS_PER_ROW) {
                    throw new SchoolImportValidationException(
                            "schoolimport-too-many-columns",
                            "Trop de colonnes",
                            "Le classeur dépasse la limite maximale de "
                                    + ImportFileSecurityPolicy.MAX_COLUMNS_PER_ROW + " colonnes.");
                }
                implicitCol = colIndex + 1;
                cellsByCol.put(colIndex, extractCellValue(cellEl, sharedStrings));
            }
            if (cellsByCol.isEmpty()) {
                continue;
            }
            int maxCol = cellsByCol.lastKey();
            List<String> rowValues = new ArrayList<>(maxCol + 1);
            for (int col = 0; col <= maxCol; col++) {
                rowValues.add(cellsByCol.getOrDefault(col, ""));
            }
            result.add(rowValues);
        }
        return result;
    }

    private String extractCellValue(Element cellEl, List<String> sharedStrings) {
        String cellType = cellEl.getAttribute("t");
        if ("inlineStr".equals(cellType)) {
            NodeList tNodes = cellEl.getElementsByTagNameNS("*", "t");
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < tNodes.getLength(); j++) {
                String t = tNodes.item(j).getTextContent();
                if (t != null) {
                    sb.append(t);
                }
            }
            return sb.toString();
        }
        NodeList vNodes = cellEl.getElementsByTagNameNS("*", "v");
        if (vNodes.getLength() == 0) {
            return "";
        }
        String rawVal = vNodes.item(0).getTextContent();
        if (rawVal == null) {
            return "";
        }
        if ("s".equals(cellType)) {
            try {
                int idx = Integer.parseInt(rawVal.trim());
                if (idx >= 0 && idx < sharedStrings.size()) {
                    return sharedStrings.get(idx);
                }
            } catch (NumberFormatException ignored) {
                return "";
            }
            return "";
        }
        if ("b".equals(cellType)) {
            return "1".equals(rawVal.trim()) ? "true" : "false";
        }
        return rawVal.trim();
    }

    private Document parseXmlSecurely(byte[] xmlBytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xmlBytes));
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new SchoolImportValidationException(
                    "schoolimport-unsafe-xlsx",
                    "Contenu XML XLSX invalide ou non sécurisé",
                    "Le contenu XML du classeur XLSX est invalide ou contient des directives interdites.");
        }
    }

    private static int parseColumnIndex(String cellRef) {
        int col = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char c = cellRef.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                col = col * 26 + (c - 'A' + 1);
            } else if (c >= 'a' && c <= 'z') {
                col = col * 26 + (c - 'a' + 1);
            } else {
                break;
            }
        }
        return Math.max(0, col - 1);
    }

    private static String localName(Node node) {
        return node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
    }

    private static boolean isBlankRow(List<String> cells) {
        for (String cell : cells) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
