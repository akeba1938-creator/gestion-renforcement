package com.ecole.saas.schoolimport;

public enum ImportJobEventType {
    UPLOADED,
    ANALYZED,
    EXECUTION_STARTED,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    CANCELLED
}
