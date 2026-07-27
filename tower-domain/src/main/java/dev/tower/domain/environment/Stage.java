package dev.tower.domain.environment;

/**
 * The delivery stage an Environment represents, independent of its name.
 *
 * <p>ADR-008 introduces Stage so that Release Pack state can be derived for any
 * user-defined Promotion Path. Environments named SIT1, UAT and QA may all
 * classify as {@link #VALIDATION}; deriving state from names would break the
 * moment a team renamed an Environment.
 *
 * <p>The rank is declared explicitly rather than relying on enum ordinal, so
 * that reordering the constants cannot silently change how state is derived.
 */
public enum Stage {

    DEVELOPMENT(1),
    VALIDATION(2),
    PRE_PRODUCTION(3),
    PRODUCTION(4);

    private final int rank;

    Stage(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    /**
     * Returns the more advanced of two Stages.
     *
     * <p>ADR-008 derives Release Pack state as the highest Stage at which any of
     * its Application Versions has been observed. A Promotion Path containing no
     * PRE_PRODUCTION Environment therefore reaches PRODUCTION directly, which is
     * what makes the Hotfix path in Scenarios.md legal without special handling.
     */
    public Stage max(Stage other) {
        return other != null && other.rank > this.rank ? other : this;
    }

    public boolean isAtLeast(Stage other) {
        return this.rank >= other.rank;
    }
}
