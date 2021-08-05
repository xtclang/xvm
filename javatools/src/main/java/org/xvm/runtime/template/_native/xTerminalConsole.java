package org.xvm.runtime.template._native;


import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.util.ConsoleLog;


/**
 * The injectable "Console" that prints to the screen / terminal.
 */
public class xTerminalConsole
        extends xService
    {
    public static final Console        CONSOLE     = System.console();
    public static final BufferedReader CONSOLE_IN;
    public static final PrintWriter    CONSOLE_OUT;
    public static final ConsoleLog     CONSOLE_LOG = new ConsoleLog();
    static
        {
        CONSOLE_IN  = CONSOLE == null || CONSOLE.reader() == null
                ? new BufferedReader(new InputStreamReader(System.in))
                : new BufferedReader(CONSOLE.reader());
        CONSOLE_OUT = CONSOLE == null || CONSOLE.writer() == null
                ? new PrintWriter(System.out, true)
                : CONSOLE.writer();
        }

    public xTerminalConsole(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        markNativeMethod("print"   , OBJECT , VOID   );
        markNativeMethod("println" , OBJECT , VOID   );
        markNativeMethod("readLine", VOID   , STRING );
        markNativeMethod("echo"    , BOOLEAN, BOOLEAN);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "print": // Object o
                {
                if (hArg == ObjectHandle.DEFAULT)
                    {
                    CONSOLE_OUT.flush();
                    return Op.R_NEXT;
                    }

                int iResult = Utils.callToString(frame, hArg);
                switch (iResult)
                    {
                    case Op.R_NEXT:
                        return PRINT.proceed(frame);

                    case Op.R_CALL:
                        frame.m_frameNext.addContinuation(PRINT);
                        // fall through
                    case Op.R_EXCEPTION:
                        return iResult;
                    }
                }

            case "println": // Object o
                {
                if (hArg == ObjectHandle.DEFAULT)
                    {
                    CONSOLE_OUT.println();
                    return Op.R_NEXT;
                    }

                int iResult = Utils.callToString(frame, hArg);
                switch (iResult)
                    {
                    case Op.R_NEXT:
                        return PRINTLN.proceed(frame);

                    case Op.R_CALL:
                        frame.m_frameNext.addContinuation(PRINTLN);
                        // fall through
                    case Op.R_EXCEPTION:
                        return iResult;
                    }
                }

            case "echo":
                {
                boolean fOld = m_fEcho;
                m_fEcho = ((BooleanHandle) hArg).get();
                return frame.assignValue(iReturn, xBoolean.makeHandle(fOld));
                }
            }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "readLine": // String format, List<Object> args
                {
                StringHandle hLine;
                try
                    {
                    hLine = m_fEcho || CONSOLE == null
                            ? xString.makeHandle(CONSOLE_IN.readLine())
                            : xString.makeHandle(CONSOLE.readPassword());
                    }
                catch (IOException e)
                    {
                    hLine = xString.makeHandle(e.getMessage());
                    }

                return frame.assignValue(iReturn, hLine);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    private static Frame.Continuation PRINT = frameCaller ->
        {
        char[] ach = ((StringHandle) frameCaller.popStack()).getValue();
        CONSOLE_LOG.log(ach, false);
        CONSOLE_OUT.print(ach);
        CONSOLE_OUT.flush();
        return Op.R_NEXT;
        };

    private static Frame.Continuation PRINTLN = frameCaller ->
        {
        char[] ach = ((StringHandle) frameCaller.popStack()).getValue();
        CONSOLE_LOG.log(ach, true);
        CONSOLE_OUT.println(ach);
        return Op.R_NEXT;
        };

    private boolean m_fEcho = true;
    }
