package org.xvm.proto;

import org.xvm.proto.template.Const;
import org.xvm.proto.template.xObject;

import java.sql.Timestamp;


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
        CallChain chain = clzConst.getPropertyGetterChain("hash");

        if (chain.isNative())
            {
            Const template = (Const) clzConst.f_template; // should we get it from method?
            return template.buildHashCode(frame, hConst, Frame.RET_LOCAL);
            }

        ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(chain.getTop())];
        return clzConst.f_template.invoke1(frame, chain, hConst, ahVar, Frame.RET_LOCAL);
        }

    // ----- to<String> support -----

    // call "to<String>" method for the given value, placing the result into the frame local
    // return R_EXCEPTION, R_NEXT or R_CALL
    public static int callToString(Frame frame, ObjectHandle hValue)
        {
        TypeComposition clzValue = hValue.f_clazz;
        CallChain chain = clzValue.getMethodCallChain(xObject.TO_STRING);

        if (chain.isNative())
            {
            return clzValue.f_template.buildStringValue(frame, hValue, Frame.RET_LOCAL);
            }

        ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(chain.getTop())];
        return clzValue.f_template.invoke1(frame, chain, hValue, ahVar, Frame.RET_LOCAL);
        }
    }
