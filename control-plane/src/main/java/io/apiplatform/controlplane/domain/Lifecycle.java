package io.apiplatform.controlplane.domain;

public enum Lifecycle {
    DRAFT, ACTIVE, INACTIVE, DEPRECATED;

    /** Allowed transitions; DEPRECATED can only be deactivated. */
    public boolean canMoveTo(Lifecycle next) {
        return switch (this) {
            case DRAFT -> next == ACTIVE || next == INACTIVE;
            case ACTIVE -> next == INACTIVE || next == DEPRECATED;
            case INACTIVE -> next == ACTIVE;
            case DEPRECATED -> next == INACTIVE;
        };
    }
}
