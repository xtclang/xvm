package org.xvm.runtime.template._native.fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards RawOSFileChannel.submit failure observation. The native API is non-blocking, but queued
 * Java write failures must still become host-visible runtime failures.
 */
public class RawOSFileChannelSubmitTest {
    /**
     * Master discarded the CompletableFuture returned by scheduleIO. A write failure on the IO
     * thread could therefore disappear after submit returned OK.
     */
    @Test
    public void submitObservesQueuedWriteFailures() throws IOException {
        var source = readString("org/xvm/runtime/template/_native/fs/xRawOSFileChannel.java");
        var submit = sourceBetween(source, "protected int invokeSubmit", "// ----- ObjectHandle");

        assertFalse(submit.contains("scheduleIO(task); // don't wait"),
                "submit must not discard the scheduled IO future");
        assertTrue(submit.contains("cfWrite") && submit.contains("container.scheduleIO(task)"),
                "submit must keep the scheduled write future");
        assertTrue(submit.contains("cfWrite.whenComplete("),
                "submit must observe asynchronous write completion");
        assertTrue(submit.contains("recordRuntimeFailure("),
                "queued write failure must be visible through the container failure channel");
        assertTrue(submit.contains("return frame.assignValue(iReturn, xInt64.makeHandle(frame, 0))"),
                "submit remains non-blocking and reports successful queueing immediately");
    }

    private static String readString(String source) throws IOException {
        var path = Path.of("src/main/java", source);
        return Files.readString(Files.exists(path)
                ? path
                : Path.of("javatools/src/main/java", source));
    }

    private static String sourceBetween(String source, String start, String end) {
        var ofStart = source.indexOf(start);
        var ofEnd   = source.indexOf(end, ofStart);

        assertTrue(ofStart >= 0, "missing source start marker: " + start);
        assertTrue(ofEnd > ofStart, "missing source end marker: " + end);
        return source.substring(ofStart, ofEnd);
    }
}
