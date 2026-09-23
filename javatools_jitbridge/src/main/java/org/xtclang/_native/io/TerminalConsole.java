package org.xtclang._native.io;

import java.io.PrintWriter;

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
        this(null);
    }

    /**
     * Create an output console backed by a caller-owned writer, or the terminal when null.
     */
    public TerminalConsole(PrintWriter output) {
        super(null);
        this.output = output;
    }

    private final PrintWriter output;

    @Override
    public TypeConstant $xvmType(Ctx ctx) {
        return ctx.pool().ensureEcstasyTypeConstant("io.Console");
    }

    /**
     * {@code void print(Object object = "", Boolean suppressNewline = False)}
     */
    public void print$p(Ctx ctx, Object object, boolean suppressNewline, boolean dfltSuppressNewline) {
        if (object == null) {
            object = String.EmptyString;
        }
        if (dfltSuppressNewline) {
            suppressNewline = false;
        }

        PrintWriter writer = output == null ? xTerminalConsole.CONSOLE_OUT : output;
        writer.print(object.toString(ctx));
        if (!suppressNewline) {
            writer.println();
        }
        writer.flush();
    }

    /**
     * {@code String readLine(String prompt = "", Boolean suppressEcho = False)}
     */
    public String readLine$p(Ctx ctx, String prompt, boolean suppressEcho, boolean dfltSuppressEcho) {
        if (output != null) {
            throw new IOException(ctx).$init(ctx, "Embedded console input is not supported", null);
        }
        if (prompt == null) {
            prompt = String.EmptyString;
        }
        if (dfltSuppressEcho) {
            suppressEcho = false;
        }
        if (prompt.size$get$p(ctx) != 0) {
            xTerminalConsole.CONSOLE_OUT.print(prompt);
            xTerminalConsole.CONSOLE_OUT.flush();
        }

        try {
            if (suppressEcho) {
                return String.of(ctx,  xTerminalConsole.CONSOLE_IN.readLine());
            } else {
                char[] achLine = xTerminalConsole.CONSOLE.readPassword();
                return String.of(ctx, new java.lang.String(achLine));
            }
        } catch (java.io.IOException e) {
            throw new IOException(ctx).$init(ctx, e.getMessage(), e);
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
