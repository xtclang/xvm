package org.xvm.javajit.intrinsic.io;

import java.io.IOException;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.intrinsic.xException;
import org.xvm.javajit.intrinsic.xObj;
import org.xvm.javajit.intrinsic.xService;
import org.xvm.javajit.intrinsic.xStr;
import org.xvm.javajit.intrinsic.xType;

import org.xvm.runtime.template._native.xTerminalConsole;

/**
 * Native implementation for _native.io.TerminalConsole.
 */
public class TerminalConsole
        extends xService
        implements jit.org.xtclang.ecstasy.io.Console {

    public TerminalConsole() {
        super(-1);
    }

    @Override
    public xType $type() {
        return $xvm().ecstasyPool.ensureEcstasyTypeConstant("io.Console").ensureXType($owner());
    }

    @Override
    public void print(Ctx $ctx, xObj object, boolean suppressNewline) {
        xTerminalConsole.CONSOLE_OUT.print(object.toString());
        if (!suppressNewline) {
            xTerminalConsole.CONSOLE_OUT.println();
        }
        xTerminalConsole.CONSOLE_OUT.flush();
    }

    @Override
    public xStr readLine(Ctx $ctx, xStr prompt, boolean suppressEcho) {
        if (prompt.size() != 0) {
            xTerminalConsole.CONSOLE_OUT.print(prompt);
            xTerminalConsole.CONSOLE_OUT.flush();
        }

        try {
            if (suppressEcho) {
                return new xStr($ctx.container.id,  xTerminalConsole.CONSOLE_IN.readLine());
            } else {
                char[] achLine = xTerminalConsole.CONSOLE.readPassword();
                return new xStr($ctx.container.id, new String(achLine));
            }
        } catch (IOException e) {
            throw new xException(e); // TODO: IOException
        }
    }
}
