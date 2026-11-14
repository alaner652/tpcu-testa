package tw.testa.unit;

/**
 * Roles within a unit. The MVP UI combines them into two:
 * officer = OWNER + PROCTOR, teacher = AUTHOR + GRADER. They are stored separately, so splitting them later needs no schema change.
 */
public enum Role {
    /** Unit owner: create exams, import rosters, assign roles, publish results */
    OWNER,
    /** Author: create and edit question banks */
    AUTHOR,
    /** Proctor: login slips, live status, reset login codes */
    PROCTOR,
    /** Grader: only the anonymous papers assigned to them */
    GRADER
}
