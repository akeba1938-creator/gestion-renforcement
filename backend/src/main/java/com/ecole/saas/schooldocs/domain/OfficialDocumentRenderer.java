package com.ecole.saas.schooldocs.domain;

/**
 * Contrat de domaine pur pour générer la représentation binaire PDF officielle d'un document.
 */
public interface OfficialDocumentRenderer {

    byte[] renderPdf(DocumentRenderPayload payload);
}
