package org.xvm.runtime.template.io.Console;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xString;
import org.xvm.runtime.template.xString.StringHandle;


/**
 * TODO:
 */
public class xTerminalConsole
        extends ClassTemplate
    {
    public static BufferedReader CONSOLE_IN = new BufferedReader(new InputStreamReader(System.in));
    public static PrintWriter CONSOLE_OUT = new PrintWriter(System.out, true);

    public xTerminalConsole(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);
        }

    @Override
    public void initDeclared()
        {
        markNativeMethod("print", OBJECT, VOID);
        markNativeMethod("println", OBJECT, VOID);
        markNativeMethod("readLine", VOID, STRING);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "print": // Object o
                {
                int iResult = hArg.getTemplate().buildStringValue(frame, hArg, Frame.RET_LOCAL);
                switch (iResult)
                    {
                    case Op.R_NEXT:
                        return PRINT.proceed(frame);

                    case Op.R_CALL:
                        frame.m_frameNext.setContinuation(PRINT);
                        // fall through
                    case Op.R_EXCEPTION:
                        return iResult;
                    }
                }

            case "println": // Object o
                {
                int iResult = hArg.getTemplate().buildStringValue(frame, hArg, Frame.RET_LOCAL);
                switch (iResult)
                    {
                    case Op.R_NEXT:
                        return PRINTLN.proceed(frame);

                    case Op.R_CALL:
                        frame.m_frameNext.setContinuation(PRINTLN);
                        // fall through
                    case Op.R_EXCEPTION:
                        return iResult;
                    }
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
            case "readLine": // String format, Sequence<Object> args
                {
                try
                    {
                    String sLine = CONSOLE_IN.readLine();

                    return frame.assignValue(iReturn, xString.makeHandle(sLine));
                    }
                catch (IOException e) {}
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    private static Frame.Continuation PRINT = frameCaller ->
        {
        CONSOLE_OUT.println(((StringHandle) frameCaller.getFrameLocal()).getValue());
        return Op.R_NEXT;
        };

    private static Frame.Continuation PRINTLN = frameCaller ->
        {
        CONSOLE_OUT.println(((StringHandle) frameCaller.getFrameLocal()).getValue());
        return Op.R_NEXT;
        };
    }
