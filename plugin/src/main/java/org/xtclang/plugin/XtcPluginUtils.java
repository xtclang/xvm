package org.xtclang.plugin;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * XTC Plugin Helper methods in a utility class.
 * <p>
 * TODO: Move the state independent/reentrant stuff from the ProjectDelegate and its subclassses to here.
 */
public final class XtcPluginUtils {
    private XtcPluginUtils() {
    }

    public static List<String> argumentArrayToList(final Object... args) {
        return Arrays.stream(ensureNotNull(args)).map(String::valueOf).toList();
    }

    private static Object[] ensureNotNull(final Object... array) {
        Arrays.stream(array).forEach(e -> Objects.requireNonNull(e, "Arguments must never be null."));
        return array;
    }
}
