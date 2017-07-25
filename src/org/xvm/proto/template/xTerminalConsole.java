package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;
import org.xvm.proto.TypeSet;

import org.xvm.proto.template.xString.StringHandle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;

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
        markNativeMethod("format", new String[]{"String", "collections.Sequence"}, VOID);
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
                CONSOLE_OUT.print(hArg.toString());
                return Op.R_NEXT;
                }

            case "println": // Object o
                {
                CONSOLE_OUT.println(hArg.toString());
                return Op.R_NEXT;
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
            case "format": // String format, Sequence<Object> args
                {
                StringHandle hFormat = (StringHandle) ahArg[0];
                ObjectHandle hSequence = ahArg[1];
                IndexSupport support = (IndexSupport) hSequence.f_clazz.f_template;

                try
                    {
                    ObjectHandle[] ahValue = support.toArray(hSequence); // TODO: wrong
                    CONSOLE_OUT.format(hFormat.getValue(), ahValue);
                    return Op.R_NEXT;
                    }
                catch (ExceptionHandle.WrapperException e)
                    {
                    frame.m_hException = e.getExceptionHandle();
                    return Op.R_EXCEPTION;
                    }
                }

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
