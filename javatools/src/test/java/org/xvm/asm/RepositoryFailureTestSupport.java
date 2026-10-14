package org.xvm.asm;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.joining;

/**
 * Collects repository diagnostics without redirecting process-wide output.
 */
abstract class RepositoryFailureTestSupport {
    String output() {
        return reports.stream().map(Object::toString).collect(joining("\n"));
    }

    void clearOutput() {
        reports.clear();
    }

    final List<ErrorListener.ErrorInfo> reports = new ArrayList<>();
    final ErrorListener errors = ErrorListener.collecting(reports::add);
}
