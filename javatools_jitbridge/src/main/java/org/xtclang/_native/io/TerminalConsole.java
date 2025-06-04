package org.xtclang._native.io;

import java.io.IOException;

import org.xtclang.ecstasy.io.Console;

import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.xObj;
import org.xtclang.ecstasy.xService;
import org.xtclang.ecstasy.xType;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

import org.xvm.runtime.template._native.io.xTerminalConsole;

/**
 * Native implementation for _native.TerminalConsole.
 */
public class TerminalConsole
        extends xService
        implements Console {

    public TerminalConsole() {
        super(Ctx.get());
    }

    @Override
    public xType $type() {
        return (xType) $xvm().ecstasyPool.ensureEcstasyTypeConstant("io.Console").ensureXType($owner());
    }

    /**
     * {@code void print(Object object = "", Boolean suppressNewline = False)}
     */
    public void print$p(Ctx $ctx, xObj object, boolean suppressNewline, boolean dfltSuppressNewline) {
        if (object == null) {
            object = String.EmptyString;
        }
        if (dfltSuppressNewline) {
            suppressNewline = false;
        }
        xTerminalConsole.CONSOLE_OUT.print(object.toString()); // TODO: toString($ctx)
        if (!suppressNewline) {
            xTerminalConsole.CONSOLE_OUT.println();
        }
        xTerminalConsole.CONSOLE_OUT.flush();
    }

    /**
     * {@code String readLine(String prompt = "", Boolean suppressEcho = False)}
     */
    public String readLine$p(Ctx $ctx, String prompt, boolean suppressEcho, boolean dfltSuppressEcho) {
        if (prompt == null) {
            prompt = String.EmptyString;
        }
        if (dfltSuppressEcho) {
            suppressEcho = false;
        }
        if (prompt.size() != 0) {
            xTerminalConsole.CONSOLE_OUT.print(prompt);
            xTerminalConsole.CONSOLE_OUT.flush();
        }

        try {
            if (suppressEcho) {
                return new String($ctx,  xTerminalConsole.CONSOLE_IN.readLine());
            } else {
                char[] achLine = xTerminalConsole.CONSOLE.readPassword();
                return new String($ctx, new java.lang.String(achLine));
            }
        } catch (IOException e) {
            throw new Exception(e); // TODO: IOException
        }
    }

    // ------ injection support --------------------------------------------------------------------

    /**
     * Create a TerminalConsole.
     */
    public static Console $create(Object opts) {
        return new TerminalConsole();
    }
}
