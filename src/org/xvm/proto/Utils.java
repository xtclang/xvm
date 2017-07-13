package org.xvm.proto;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.MethodStructure;

import java.sql.Timestamp;


/**
 * Various helpers.
 *
 * @author gg 2017.03.10
 */
public abstract class Utils
    {
    public final static int[] ARGS_NONE = new int[0];
    public final static Type[] TYPE_NONE = new Type[0];
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
    }
