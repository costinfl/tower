package dev.tower.portability;

/**
 * What to do when an incoming record collides with one already present.
 *
 * <p>ADR-010 forbids automatic merging: deciding which version of a Release
 * Pack is correct is a human judgement, and a merge would make the result of an
 * import unpredictable. The user chooses, and Tower does exactly what was
 * chosen.
 */
public enum ConflictStrategy {

    /** Keep what is here. The incoming record is reported and discarded. */
    SKIP,

    /** Take the incoming record, replacing the local one entirely. */
    REPLACE,

    /**
     * Keep both, giving the incoming record a fresh identity and a
     * disambiguated name.
     *
     * <p>Useful when two developers independently prepared the same release and
     * want to compare rather than choose.
     */
    DUPLICATE
}
