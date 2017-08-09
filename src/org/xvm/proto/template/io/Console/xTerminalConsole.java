package org.xvm.proto.template.io.Console;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.Op;
import org.xvm.proto.TypeSet;

import org.xvm.proto.template.xString;
import org.xvm.proto.template.xString.StringHandle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xTerminalConsole
        extends ClassTemplate
    {
    public static xTerminalConsole INSTANCE;
    public static BufferedReader CONSOLE_IN = new BufferedReader(new InputStreamReader(System.in));
    public static PrintWriter CONSOLE_OUT = new PrintWriter(System.out, true);

    public xTerminalConsole(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
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
                int iResult = hArg.f_clazz.f_template.buildStringValue(frame, hArg, Frame.RET_LOCAL);
                switch (iResult)
                    {
                    case Op.R_NEXT:
                        CONSOLE_OUT.print(((StringHandle) frame.getFrameLocal()).getValue());
                        return Op.R_NEXT;

                    case Op.R_CALL:
                        frame.m_frameNext.setContinuation(() ->
                            {
                            CONSOLE_OUT.print(((StringHandle) frame.getFrameLocal()).getValue());
                            return null;
                            });

                        // fall through
                    case Op.R_EXCEPTION:
                        return iResult;
                    }
                }

            case "println": // Object o
                {
                int iResult = hArg.f_clazz.f_template.buildStringValue(frame, hArg, Frame.RET_LOCAL);
                switch (iResult)
                    {
                    case Op.R_NEXT:
                        CONSOLE_OUT.println(((StringHandle) frame.getFrameLocal()).getValue());
                        return Op.R_NEXT;

                    case Op.R_CALL:
                        frame.m_frameNext.setContinuation(() ->
                            {
                            CONSOLE_OUT.println(((StringHandle) frame.getFrameLocal()).getValue());
                            return null;
                            });

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
    }
