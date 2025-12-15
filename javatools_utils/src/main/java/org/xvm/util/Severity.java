package org.xvm.util;


import java.util.Locale;

/**
 * Severity levels.
 */
public enum Severity {
    NONE, INFO, WARNING, ERROR, FATAL;

    public String desc() {
        final var sName = name();
        return sName.charAt(0) + sName.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * Check if this severity is at least as severe as the specified severity.
     *
     * @param sev  the severity to compare against
     *
     * @return true if this severity is at least as severe as the specified severity
     */
    public boolean isAtLeast(Severity sev) {
        return compareTo(sev) >= 0;
    }

    public boolean isWorseThan(Severity sev) {
        return compareTo(sev) > 0;
    }

    /**
     * Update the specified severity to be at least as severe as this severity.
     *
     * @return the updated worst severity
     */
    public static Severity worstOf(Severity oldSev, Severity newSev) {
        return oldSev.isWorseThan(newSev) ? oldSev : newSev;
    }
}
