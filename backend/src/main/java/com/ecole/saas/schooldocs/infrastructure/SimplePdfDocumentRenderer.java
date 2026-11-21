package com.ecole.saas.schooldocs.infrastructure;

import com.ecole.saas.schooldocs.domain.DocumentRenderPayload;
import com.ecole.saas.schooldocs.domain.DocumentSecurityPolicy;
import com.ecole.saas.schooldocs.domain.OfficialDocumentRenderer;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Générateur déterministe PDF 1.4 en Java pur (zéro dépendance externe non vérifiée).
 * Produit un document A4 valide ({@code %PDF-1.4 ... %%EOF}) encodé en {@code WinAnsiEncoding}
 * pour le support complet des accents français, avec en-tête officiel, mention DUPLICATA,
 * corps mis en pages multi-lignes et bloc de vérification d'authenticité par jeton opaque.
 */
@Component
public class SimplePdfDocumentRenderer implements OfficialDocumentRenderer {

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN_X = 50;
    private static final int TOP_Y = 785;
    private static final int BOTTOM_Y = 75;
    private static final int MAX_CHARS_BODY = 82;
    private static final int MAX_CHARS_TITLE = 58;

    @Override
    public byte[] renderPdf(DocumentRenderPayload payload) {
        List<PdfLine> lines = buildLines(payload);
        List<List<PdfLine>> pages = paginate(lines);
        byte[] pdfBytes = assemblePdf(pages);
        DocumentSecurityPolicy.assertValidPdfBinary(pdfBytes);
        return pdfBytes;
    }

    private List<PdfLine> buildLines(DocumentRenderPayload payload) {
        List<PdfLine> lines = new ArrayList<>();

        // En-tête officiel établissement
        lines.add(new PdfLine("F2", 14, 20, payload.schoolName().toUpperCase(Locale.FRANCE)));
        lines.add(new PdfLine(
                "F1",
                10,
                14,
                "Code établissement : " + payload.schoolCode()
                        + "   |   N° officiel : " + payload.documentNumber()));
        lines.add(new PdfLine(
                "F1",
                10,
                14,
                "Type : " + payload.documentType().defaultFrenchLabel()
                        + "   |   Émis le : " + DATE_FORMATTER.format(payload.issueDate())));
        if (payload.validUntilDate() != null) {
            lines.add(new PdfLine("F1", 10, 14, "Valide jusqu'au : " + DATE_FORMATTER.format(payload.validUntilDate())));
        }
        if (payload.duplicate()) {
            lines.add(PdfLine.spacer(6));
            lines.add(new PdfLine(
                    "F2",
                    11,
                    16,
                    "*** DUPLICATA — ÉDITION N°" + payload.editionNumber() + " ***"));
            if (payload.reissueReason() != null && !payload.reissueReason().isBlank()) {
                for (String wrapped : wrapText("Motif de réédition : " + payload.reissueReason(), MAX_CHARS_BODY)) {
                    lines.add(new PdfLine("F1", 9, 13, wrapped));
                }
            }
        }

        lines.add(new PdfLine("F1", 10, 14, "------------------------------------------------------------------------------------"));
        lines.add(PdfLine.spacer(8));

        // Titre principal
        for (String titleLine : wrapText(payload.title(), MAX_CHARS_TITLE)) {
            lines.add(new PdfLine("F2", 14, 20, titleLine));
        }
        lines.add(PdfLine.spacer(8));

        // Identification élève et contexte académique
        if (payload.studentFullName() != null && !payload.studentFullName().isBlank()) {
            String studentLine = "Élève : " + payload.studentFullName();
            if (payload.studentMatricule() != null && !payload.studentMatricule().isBlank()) {
                studentLine += " (Matricule : " + payload.studentMatricule() + ")";
            }
            for (String wrapped : wrapText(studentLine, MAX_CHARS_BODY)) {
                lines.add(new PdfLine("F2", 10, 15, wrapped));
            }
        }
        if (payload.academicYearLabel() != null && !payload.academicYearLabel().isBlank()) {
            String academicLine = "Année scolaire : " + payload.academicYearLabel();
            if (payload.classRoomName() != null && !payload.classRoomName().isBlank()) {
                academicLine += "   |   Classe : " + payload.classRoomName();
            }
            for (String wrapped : wrapText(academicLine, MAX_CHARS_BODY)) {
                lines.add(new PdfLine("F1", 10, 15, wrapped));
            }
        }

        lines.add(PdfLine.spacer(12));

        // Corps du document
        String[] paragraphs = payload.body().replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isBlank()) {
                lines.add(PdfLine.spacer(10));
            } else {
                for (String wrapped : wrapText(paragraph.trim(), MAX_CHARS_BODY)) {
                    lines.add(new PdfLine("F1", 11, 16, wrapped));
                }
            }
        }

        // Pied de page éventuel
        if (payload.footer() != null && !payload.footer().isBlank()) {
            lines.add(PdfLine.spacer(14));
            String[] footerLines = payload.footer().replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
            for (String fLine : footerLines) {
                if (!fLine.isBlank()) {
                    for (String wrapped : wrapText(fLine.trim(), MAX_CHARS_BODY)) {
                        lines.add(new PdfLine("F1", 10, 14, wrapped));
                    }
                }
            }
        }

        // Bloc officiel de vérification d'authenticité (jeton opaque uniquement, aucune donnée personnelle)
        lines.add(PdfLine.spacer(16));
        lines.add(new PdfLine("F1", 9, 13, "------------------------------------------------------------------------------------"));
        lines.add(new PdfLine("F2", 9, 13, "VÉRIFICATION D'AUTHENTICITÉ OFFICIELLE"));
        lines.add(new PdfLine("F1", 8, 12, "Numéro officiel : " + payload.documentNumber()));
        lines.add(new PdfLine("F1", 8, 12, "Jeton opaque de vérification : " + payload.verificationToken()));

        return lines;
    }

    private List<List<PdfLine>> paginate(List<PdfLine> allLines) {
        List<List<PdfLine>> pages = new ArrayList<>();
        List<PdfLine> current = new ArrayList<>();
        int y = TOP_Y;
        for (PdfLine line : allLines) {
            if (y - line.leading < BOTTOM_Y && !current.isEmpty()) {
                pages.add(current);
                current = new ArrayList<>();
                y = TOP_Y;
            }
            current.add(line);
            y -= line.leading;
        }
        if (!current.isEmpty()) {
            pages.add(current);
        }
        return pages;
    }

    private byte[] assemblePdf(List<List<PdfLine>> pages) {
        // Objets PDF :
        // 1: Catalog, 2: Pages, 3: Font F1 (Helvetica WinAnsi), 4: Font F2 (Helvetica-Bold WinAnsi)
        // Pour chaque page i (0..n-1): Page = 5 + 2*i, Content stream = 6 + 2*i
        int pageCount = pages.size();
        int totalObjects = 4 + (2 * pageCount);
        List<byte[]> objects = new ArrayList<>(totalObjects);

        objects.add("<< /Type /Catalog /Pages 2 0 R >>".getBytes(StandardCharsets.US_ASCII));

        StringBuilder kids = new StringBuilder("[");
        for (int i = 0; i < pageCount; i++) {
            if (i > 0) {
                kids.append(' ');
            }
            kids.append(5 + (2 * i)).append(" 0 R");
        }
        kids.append(']');
        objects.add(("<< /Type /Pages /Kids " + kids + " /Count " + pageCount + " >>")
                .getBytes(StandardCharsets.US_ASCII));

        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
                .getBytes(StandardCharsets.US_ASCII));
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"
                .getBytes(StandardCharsets.US_ASCII));

        for (int i = 0; i < pageCount; i++) {
            int contentObjNum = 6 + (2 * i);
            String pageDict = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                    + PAGE_WIDTH + " " + PAGE_HEIGHT
                    + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents "
                    + contentObjNum + " 0 R >>";
            objects.add(pageDict.getBytes(StandardCharsets.US_ASCII));

            byte[] streamBytes = renderPageContentStream(pages.get(i), i + 1, pageCount);
            ByteArrayOutputStream streamObj = new ByteArrayOutputStream();
            String header = "<< /Length " + streamBytes.length + " >>\nstream\n";
            streamObj.writeBytes(header.getBytes(StandardCharsets.US_ASCII));
            streamObj.writeBytes(streamBytes);
            streamObj.writeBytes("\nendstream".getBytes(StandardCharsets.US_ASCII));
            objects.add(streamObj.toByteArray());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n".getBytes(StandardCharsets.ISO_8859_1));

        int[] offsets = new int[totalObjects + 1];
        for (int idx = 0; idx < totalObjects; idx++) {
            int objNum = idx + 1;
            offsets[objNum] = out.size();
            out.writeBytes((objNum + " 0 obj\n").getBytes(StandardCharsets.US_ASCII));
            out.writeBytes(objects.get(idx));
            out.writeBytes("\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        }

        int xrefOffset = out.size();
        out.writeBytes(("xref\n0 " + (totalObjects + 1) + "\n").getBytes(StandardCharsets.US_ASCII));
        out.writeBytes("0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
        for (int objNum = 1; objNum <= totalObjects; objNum++) {
            String entry = String.format(Locale.ROOT, "%010d 00000 n \n", offsets[objNum]);
            out.writeBytes(entry.getBytes(StandardCharsets.US_ASCII));
        }
        String trailer = "trailer\n<< /Size " + (totalObjects + 1) + " /Root 1 0 R >>\nstartxref\n"
                + xrefOffset + "\n%%EOF\n";
        out.writeBytes(trailer.getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private byte[] renderPageContentStream(List<PdfLine> pageLines, int pageNumber, int pageCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("BT\n");
        int y = TOP_Y;
        for (PdfLine line : pageLines) {
            if (line.text != null && !line.text.isEmpty()) {
                sb.append('/').append(line.font).append(' ').append(line.fontSize).append(" Tf\n");
                sb.append("1 0 0 1 ").append(MARGIN_X).append(' ').append(y).append(" Tm\n");
                sb.append('(').append(escapeWinAnsiPdfString(line.text)).append(") Tj\n");
            }
            y -= line.leading;
        }
        // Pagination en bas de page
        sb.append("/F1 8 Tf\n");
        sb.append("1 0 0 1 ").append(MARGIN_X).append(" 36 Tm\n");
        sb.append('(')
                .append(escapeWinAnsiPdfString("Page " + pageNumber + " / " + pageCount))
                .append(") Tj\n");
        sb.append("ET");
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static List<String> wrapText(String text, int maxChars) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String cleaned = sanitizeControlChars(text);
        List<String> result = new ArrayList<>();
        String[] words = cleaned.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (line.isEmpty()) {
                appendPossiblyLongWord(result, line, word, maxChars);
            } else if (line.length() + 1 + word.length() <= maxChars) {
                line.append(' ').append(word);
            } else {
                result.add(line.toString());
                line.setLength(0);
                appendPossiblyLongWord(result, line, word, maxChars);
            }
        }
        if (!line.isEmpty()) {
            result.add(line.toString());
        }
        return result;
    }

    private static void appendPossiblyLongWord(List<String> result, StringBuilder line, String word, int maxChars) {
        if (word.length() <= maxChars) {
            line.append(word);
            return;
        }
        int start = 0;
        while (start < word.length()) {
            int end = Math.min(start + maxChars, word.length());
            String chunk = word.substring(start, end);
            if (end < word.length()) {
                result.add(chunk);
            } else {
                line.append(chunk);
            }
            start = end;
        }
    }

    private static String sanitizeControlChars(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= 0x20 || c == '\t') {
                sb.append(c == '\t' ? ' ' : c);
            }
        }
        return sb.toString();
    }

    private static String escapeWinAnsiPdfString(String text) {
        byte[] encoded = text.getBytes(WIN_ANSI);
        StringBuilder sb = new StringBuilder(encoded.length + 16);
        for (byte b : encoded) {
            int unsigned = b & 0xFF;
            if (unsigned == '\\' || unsigned == '(' || unsigned == ')') {
                sb.append('\\').append((char) unsigned);
            } else if (unsigned >= 0x20 && unsigned <= 0x7E) {
                sb.append((char) unsigned);
            } else {
                sb.append(String.format(Locale.ROOT, "\\%03o", unsigned));
            }
        }
        return sb.toString();
    }

    private record PdfLine(String font, int fontSize, int leading, String text) {
        static PdfLine spacer(int leading) {
            return new PdfLine("F1", 10, leading, null);
        }
    }
}
