package org.xvm.lib.jsondb;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Guards jsondb client transaction failure paths.
 */
public class JsondbClientRollbackFailureTest {
    @Test
    public void commitFailureDoesNotSwallowRollbackFailure() throws IOException {
        var source = readClient();
        var region = sourceBetween(source,
                "txManager.rollback(writeId_);",
                "                } finally {");

        assertFalse(region.contains("catch (Exception ignore) {}"),
                "rollback failure after commit failure must not be swallowed");
        assertTrue(region.contains("catch (Exception e2)"),
                "rollback failure must be captured separately from the commit failure");
        assertTrue(region.contains("rollback after failed commit"),
                "rollback failure log must identify the compensating rollback path");
        assertTrue(region.contains("commit failure: {e}"),
                "rollback failure log must preserve the original commit failure context");
    }

    private static String readClient() throws IOException {
        var path = Path.of("lib_jsondb/src/main/x/jsondb/Client.x");
        return Files.readString(Files.exists(path)
                ? path
                : Path.of("../lib_jsondb/src/main/x/jsondb/Client.x"));
    }

    private static String sourceBetween(String source, String start, String end) {
        var ofStart = source.indexOf(start);
        var ofEnd   = source.indexOf(end, ofStart);

        assertTrue(ofStart >= 0, "missing source start marker: " + start);
        assertTrue(ofEnd > ofStart, "missing source end marker: " + end);
        return source.substring(ofStart, ofEnd);
    }
}
