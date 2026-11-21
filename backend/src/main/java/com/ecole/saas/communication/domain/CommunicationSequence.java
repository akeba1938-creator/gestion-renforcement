package com.ecole.saas.communication.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "communication_sequences")
public class CommunicationSequence extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sequence_year", nullable = false)
    private int sequenceYear;

    @Column(name = "last_value", nullable = false)
    private int lastValue;

    protected CommunicationSequence() {
    }

    public CommunicationSequence(int sequenceYear) {
        if (sequenceYear < CommunicationReferenceFormatter.MIN_YEAR
                || sequenceYear > CommunicationReferenceFormatter.MAX_YEAR) {
            throw new CommunicationValidationException("L'année de séquence est hors bornes.");
        }
        this.sequenceYear = sequenceYear;
        this.lastValue = 0;
    }

    public int nextValue() {
        this.lastValue = Math.addExact(this.lastValue, 1);
        return this.lastValue;
    }

    public UUID getId() {
        return id;
    }

    public int getSequenceYear() {
        return sequenceYear;
    }

    public int getLastValue() {
        return lastValue;
    }
}
