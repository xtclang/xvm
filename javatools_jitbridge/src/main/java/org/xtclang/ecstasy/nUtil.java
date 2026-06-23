package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native utility helpers for generated JIT code.
 */
public final class nUtil {
    private nUtil() {
    }

    public static void appendText(StringBuilder buffer, String text) {
        if (!text.isEmpty() && buffer.length() <= MAX_LEN) {
            buffer.append(text);
        }
    }

    public static void appendTo(Ctx ctx, StringBuilder buffer, Object value) {
        if (buffer.length() > MAX_LEN) {
            return;
        }
        String text = value.toString(ctx).toString();
        if (text.length() > MAX_VAL) {
            buffer.append(text, 0, MAX_VAL)
                  .append("...");
        } else {
            buffer.append(text);
        }
        if (buffer.length() > MAX_LEN) {
            buffer.append("...");
        }
    }

    private static final int MAX_VAL = 2*1024;
    private static final int MAX_LEN = 16*1024;
}
