package com.ecole.saas.center.domain;

public enum CenterAttendanceStatus {
    PRESENT,
    LATE,
    ABSENT,
    EXCUSED;

    public boolean countsAsAttended() {
        return this == PRESENT || this == LATE;
    }
}
