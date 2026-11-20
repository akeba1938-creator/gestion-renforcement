package com.ecole.saas.finance.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * Ligne d'une facture élève portant le snapshot du frais et du montant entier FCFA facturé.
 */
@Entity
@Table(name = "student_invoice_lines")
public class StudentInvoiceLine extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "fee_item_id", nullable = false, updatable = false)
    private UUID feeItemId;

    @Column(name = "line_order", nullable = false)
    private int lineOrder;

    @Column(name = "fee_code", nullable = false, length = 32)
    private String feeCode;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private FeeCategory category;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_amount_xof", nullable = false)
    private long unitAmountXof;

    @Column(name = "line_total_xof", nullable = false)
    private long lineTotalXof;

    protected StudentInvoiceLine() {
    }

    public StudentInvoiceLine(
            UUID invoiceId,
            UUID feeItemId,
            int lineOrder,
            String feeCode,
            String label,
            FeeCategory category,
            int quantity,
            long unitAmountXof) {
        this.invoiceId = Objects.requireNonNull(invoiceId, "L'identifiant de la facture est obligatoire.");
        this.feeItemId = Objects.requireNonNull(feeItemId, "L'identifiant du frais est obligatoire.");
        if (lineOrder <= 0) {
            throw new IllegalArgumentException("L'ordre de ligne doit être strictement positif.");
        }
        if (quantity <= 0 || quantity > 1000) {
            throw new IllegalArgumentException("La quantité doit être comprise entre 1 et 1000.");
        }
        this.lineOrder = lineOrder;
        this.feeCode = FinanceCodeFormatter.normalizeFeeCode(feeCode);
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Le libellé de ligne est obligatoire.");
        }
        this.label = label.trim();
        this.category = Objects.requireNonNull(category, "La catégorie de frais est obligatoire.");
        this.quantity = quantity;
        this.unitAmountXof = MoneyMath.requireNonNegative(unitAmountXof, "Le montant unitaire");
        this.lineTotalXof = MoneyMath.multiplyExact(this.unitAmountXof, this.quantity);
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getFeeItemId() {
        return feeItemId;
    }

    public int getLineOrder() {
        return lineOrder;
    }

    public String getFeeCode() {
        return feeCode;
    }

    public String getLabel() {
        return label;
    }

    public FeeCategory getCategory() {
        return category;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitAmountXof() {
        return unitAmountXof;
    }

    public long getLineTotalXof() {
        return lineTotalXof;
    }
}
