package com.ecole.saas.academic.api;

public record ActivateAcademicYearRequest(Boolean makeCurrent) {

    public boolean resolvedMakeCurrent() {
        return makeCurrent == null || makeCurrent;
    }
}
