package com.ecole.saas.schoolimport.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "schoolimport_sequences")
public class ImportSequence extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sequence_year", nullable = false)
    private int sequenceYear;

    @Column(name = "last_value", nullable = false)
    private int lastValue;

    protected ImportSequence() {
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
