package org.xvm.proto;


import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.proto.template.xConst;
import org.xvm.proto.template.xObject;
import org.xvm.proto.template.xString;

import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;


/**
 * Various helpers.
 *
 * @author gg 2017.03.10
 */
public abstract class Utils
    {
    public final static int[] ARGS_NONE = new int[0];
    public final static ObjectHandle[] OBJECTS_NONE = new ObjectHandle[0];

    public static String formatArray(Object[] ao, String sOpen, String sClose, String sDelim)
        {
        if (ao == null || ao.length == 0)
            {
            return "";
            }

        StringBuilder sb = new StringBuilder();
        sb.append(sOpen);

        boolean fFirst = true;
        for (Object o : ao)
            {
            if (fFirst)
                {
                fFirst = false;
                }
            else
                {
                sb.append(sDelim);
                }
            sb.append(o);
            }
        sb.append(sClose);
        return sb.toString();
        }

    public static void log(String sMsg)
        {
        if (sMsg.charAt(0) == '\n')
            {
            System.out.println();
            sMsg = sMsg.substring(1);
            }
        System.out.println(new Timestamp(System.currentTimeMillis())
                + " " + ServiceContext.getCurrentContext() + ": " + sMsg);
        }

    // ----- hash.get() support -----

    // call "hash.get" method for the given const value, placing the result into the frame local
    // return R_EXCEPTION, R_NEXT or R_CALL
    public static int callHash(Frame frame, ObjectHandle hConst)
        {
        TypeComposition clzConst = hConst.f_clazz;
        PropertyStructure property = clzConst.getProperty("hash");
        MethodStructure method = Adapter.getGetter(property);

        if (frame.f_adapter.isNative(method))
            {
            xConst template = (xConst) clzConst.f_template; // should we get it from method?
            return template.buildHashCode(frame, hConst, Frame.RET_LOCAL);
            }

        ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(method)];
        return frame.call1(method, hConst, ahVar, Frame.RET_LOCAL);
        }

    // ----- to<String> support -----

    // call "to<String>" method for the given value, placing the result into the frame local
    // return R_EXCEPTION, R_NEXT or R_CALL
    public static int callToString(Frame frame, ObjectHandle hValue)
        {
        TypeComposition clzValue = hValue.f_clazz;
        List<MethodStructure> callChain = clzValue.getMethodCallChain(xObject.TO_STRING);
        MethodStructure method = callChain.isEmpty() ? null : callChain.get(0);

        if (method == null || frame.f_adapter.isNative(method))
            {
            return clzValue.f_template.buildStringValue(frame, hValue, Frame.RET_LOCAL);
            }

        ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(method)];
        return frame.call1(method, hValue, ahVar, Frame.RET_LOCAL);
        }

    // there may be a way to use it in xConst and xTuple...
    public static class StringBuilderContinuation
            implements Supplier<Frame>
        {
        final private Frame frame;
        final private Iterator<ObjectHandle> iter;
        final private String sDelim;
        final private String sTerm;
        final private StringBuilder sb;
        final private int iReturn;

        public StringBuilderContinuation(Frame frame, Iterator<ObjectHandle> iter, String sDelim,
                                         String sTerm, StringBuilder sb, int iReturn)
            {
            this.frame = frame;
            this.iter = iter;
            this.sDelim = sDelim;
            this.sTerm = sTerm;
            this.sb = sb;
            this.iReturn = iReturn;
            }

        public Frame get()
            {
            sb.append(((xString.StringHandle) frame.getFrameLocal()).getValue())
              .append(sDelim);

            while (iter.hasNext())
                {
                switch (callToString(frame, iter.next()))
                    {
                    case Op.R_NEXT:
                        sb.append(((xString.StringHandle) frame.getFrameLocal()).getValue())
                          .append(sDelim);
                        continue;

                    case Op.R_CALL:
                        Frame frameNext = frame.m_frameNext;
                        frameNext.setContinuation(this);
                        return frameNext;

                    case Op.R_EXCEPTION:
                        return null;

                    default:
                        throw new IllegalStateException();
                    }
                }

            sb.setLength(sb.length() - sDelim.length()); // remove the trailing delimiter
            sb.append(sTerm);

            frame.assignValue(iReturn, xString.makeHandle(sb.toString()));
            return null;
            }
        }
    }
