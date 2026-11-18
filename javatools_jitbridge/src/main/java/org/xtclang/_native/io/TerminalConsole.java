package org.xtclang._native.io;

import org.xtclang.ecstasy.io.Console;
import org.xtclang.ecstasy.io.IOException;

import org.xtclang.ecstasy.Object;
import org.xtclang.ecstasy.nService;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

import org.xvm.runtime.template._native.io.xTerminalConsole;

/**
 * Native implementation for _native.TerminalConsole.
 */
public class TerminalConsole
        extends nService
        implements Console {

    public TerminalConsole() {
        super(null);
    }

    @Override
    public TypeConstant $xvmType(Ctx ctx, boolean async) {
        return ctx.pool().ensureEcstasyTypeConstant("io.Console");
    }

    /**
     * {@code void print(Object object = "", Boolean suppressNewline = False)}
     */
    public void print$p(Ctx ctx, boolean async, Object object, boolean suppressNewline,
            boolean dfltSuppressNewline) {
        if (object == null) {
            object = String.EmptyString;
        }
        if (dfltSuppressNewline) {
            suppressNewline = false;
        }

        xTerminalConsole.CONSOLE_OUT.print(object.toString(ctx, false));
        if (!suppressNewline) {
            xTerminalConsole.CONSOLE_OUT.println();
        }
        xTerminalConsole.CONSOLE_OUT.flush();
    }

    /**
     * {@code String readLine(String prompt = "", Boolean suppressEcho = False)}
     */
    public String readLine$p(Ctx ctx, boolean async, String prompt, boolean suppressEcho,
            boolean dfltSuppressEcho) {
        if (prompt == null) {
            prompt = String.EmptyString;
        }
        if (dfltSuppressEcho) {
            suppressEcho = false;
        }
        if (prompt.size$get$p(ctx, false) != 0) {
            xTerminalConsole.CONSOLE_OUT.print(prompt);
            xTerminalConsole.CONSOLE_OUT.flush();
        }

        try {
            if (suppressEcho) {
                return String.of(ctx, false,  xTerminalConsole.CONSOLE_IN.readLine());
            } else {
                char[] achLine = xTerminalConsole.CONSOLE.readPassword();
                return String.of(ctx, false, new java.lang.String(achLine));
            }
        } catch (java.io.IOException e) {
            throw new IOException(ctx).$init(ctx, false, e.getMessage(), e);
        }
    }

    // ------ injection support --------------------------------------------------------------------

    /**
     * Create a TerminalConsole.
     */
    public static Console $create(java.lang.Object opts) {
        return new TerminalConsole();
    }
}
