package org.xvm.runtime.template._native.io;


import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import java.nio.file.Path;

import java.time.Instant;

import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.util.ConsoleLog;


/**
 * The injectable "Console" that prints to the screen / terminal.
 */
public class xTerminalConsole
        extends xService {
    public static xTerminalConsole INSTANCE;

    public xTerminalConsole(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public void initNative() {
        markNativeMethod("print"   , null, VOID  );
        markNativeMethod("readLine", null, STRING);

        invalidateTypeInfo();
    }

    @Override
    public TypeConstant getCanonicalType() {
        return pool().ensureEcstasyTypeConstant("io.Console");
    }

    /**
     * Injection support.
     */
    public ObjectHandle ensureConsole(Frame frame, ObjectHandle hOpts) {
        ObjectHandle hConsole = m_hConsole;
        if (hConsole == null) {
            hConsole = m_hConsole = createServiceHandle(f_container.createServiceContext("Console"),
                    getCanonicalClass(), getCanonicalType());

            ensureLineReader(frame);
        }
        return hConsole;
    }

    @SuppressWarnings("fallthrough")
    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        switch (method.getName()) {
        case "print": { // Object o = "", Boolean suppressNewline = False
            boolean fNewline = ahArg[1] != xBoolean.TRUE;

            ObjectHandle hVal = ahArg[0];
            if (hVal == ObjectHandle.DEFAULT) {
                if (fNewline) {
                    CONSOLE_OUT.println();
                }
                return Op.R_NEXT;
            }

            int iResult = Utils.callToString(frame, hVal);
            switch (iResult) {
            case Op.R_NEXT:
                return fNewline
                        ? PRINTLN.proceed(frame)
                        : PRINT.proceed(frame);

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(fNewline ? PRINTLN : PRINT);
                // fall through
            case Op.R_EXCEPTION:
                return iResult;
            }
        }

        case "readLine": // String prompt = "", Boolean suppressEcho = False
            return invokeReadLine(frame, ahArg, iReturn);
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    /**
     * Native implementation of "String readLine(String prompt = "", Boolean suppressEcho = False)"
     */
    protected int invokeReadLine(Frame frame, ObjectHandle[] ahArg, int iReturn) {
        String  sPrompt = ahArg[0] instanceof StringHandle hString ? hString.getStringValue() : "";
        boolean fEcho   = ahArg[1] != xBoolean.TRUE;

        StringHandle hLine;
        if (READER == null) {
            try {
                if (!sPrompt.isEmpty()) {
                    CONSOLE_OUT.print(sPrompt);
                    CONSOLE_OUT.flush();
                }

                if (fEcho || CONSOLE == null) {
                    String sLine = CONSOLE_IN.readLine();
                    hLine = sLine == null
                        ? xString.EMPTY_STRING
                        : xString.makeHandle(sLine);
                } else {
                    char[] achLine = CONSOLE.readPassword();
                    hLine = achLine == null
                        ? xString.EMPTY_STRING
                        : xString.makeHandle(achLine);
                }
            } catch (IOException e) {
                return frame.raiseException(xException.obscureIoException(frame, e.getMessage()));
            }
        } else {
            try {
                hLine = xString.makeHandle(READER.readLine(sPrompt, fEcho ? null : '\0'));
            } catch (UserInterruptException e) {
                System.exit(0);
                return 0; // not reachable
            }
        }
        return frame.assignValue(iReturn, hLine);
    }

    /**
     * Ensure JLine reader.
     *
     * @param frame  the current frame; if not null, create a persistent history file based on the
     *               frame context name
     */
    public static LineReader ensureLineReader(Frame frame) {
        if (READER == null) {
            try {
                if (CONSOLE != null) {
                    Terminal          terminal = TerminalBuilder.builder().build();
                    LineReaderBuilder builder  = LineReaderBuilder.builder();

                    builder.terminal(terminal)
                           .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                           .option(LineReader.Option.HISTORY_TIMESTAMPED, false);

                    if (frame != null) {
                        String   sTmpDir  = System.getProperty("java.io.tmpdir");
                        String   sAppName = frame.f_context.f_container.getModule().getName();
                        Path     pathHist = Path.of(sTmpDir,  sAppName + ".history");
                        History  history  = new LimitedHistory(pathHist, 100);

                        builder = builder.variable(LineReader.HISTORY_FILE, pathHist)
                                         .history(history);
                    }
                    READER   = builder.build();
                    TERMINAL = terminal;
                }
            } catch (IOException ignore) {}
        }
        return READER;
    }

    /**
     * Trivial DefaultHistory extension that limits the history file size.
     *
     * Note: {@link LineReader#HISTORY_SIZE} controls the history size in memory, not on disk
     */
    static public class LimitedHistory
            extends DefaultHistory {
        public LimitedHistory(Path path, int cMaxEntries) {
            f_path        = path;
            f_cMaxEntries = cMaxEntries;
        }

        @Override
        public void add(Instant time, String line) {
            if (size() >= f_cMaxEntries) {
                // purge a quarter of the history
                try {
                    trimHistory(f_path, f_cMaxEntries - (f_cMaxEntries >> 2));
                } catch (IOException ignore) {}
            }
            super.add(time, line);
        }

        private final Path f_path;
        private final int  f_cMaxEntries;
    }


    // ---- constants and data fields --------------------------------------------------------------

    public static final Console        CONSOLE     = System.console();
    public static final BufferedReader CONSOLE_IN;
    public static final PrintWriter    CONSOLE_OUT;
    public static final ConsoleLog     CONSOLE_LOG = new ConsoleLog();
    public static       LineReader     READER;
    public static       Terminal       TERMINAL;
    static {
        CONSOLE_IN  = CONSOLE == null || CONSOLE.reader() == null
                ? new BufferedReader(new InputStreamReader(System.in))
                : new BufferedReader(CONSOLE.reader());
        CONSOLE_OUT = CONSOLE == null || CONSOLE.writer() == null
                ? new PrintWriter(System.out, true)
                : CONSOLE.writer();
    }

    private static final Frame.Continuation PRINT = frameCaller -> {
        char[] ach = ((StringHandle) frameCaller.popStack()).getValue();
        CONSOLE_LOG.log(ach, false);
        CONSOLE_OUT.print(ach);
        CONSOLE_OUT.flush();
        return Op.R_NEXT;
    };

    private static final Frame.Continuation PRINTLN = frameCaller -> {
        char[] ach = ((StringHandle) frameCaller.popStack()).getValue();
        CONSOLE_LOG.log(ach, true);
        CONSOLE_OUT.println(ach);
        CONSOLE_OUT.flush();
        return Op.R_NEXT;
    };

    /**
     * Cached Console handle.
     */
    private ObjectHandle m_hConsole;
}