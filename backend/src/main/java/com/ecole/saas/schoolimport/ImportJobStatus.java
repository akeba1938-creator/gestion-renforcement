package com.ecole.saas.schoolimport;

public enum ImportJobStatus {
    PREVIEW_READY,
    VALIDATION_FAILED,
    PROCESSING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPLETED_WITH_ERRORS || this == FAILED || this == CANCELLED;
    }

    public boolean isExecutable() {
        return this == PREVIEW_READY;
    }

    public boolean isCancellable() {
        return this == PREVIEW_READY || this == VALIDATION_FAILED;
    }
}
