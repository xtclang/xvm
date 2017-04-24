package org.xvm.proto;

import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;
import org.xvm.proto.TypeCompositionTemplate.InvocationTemplate;

/**
 * Various helpers.
 *
 * @author gg 2017.03.10
 */
public abstract class Utils
    {
    public final static int[] ARGS_NONE = new int[0];
    public final static Type[] TYPE_NONE = new Type[0];
    public final static TypeName[] TYPE_NAME_NONE = new TypeName[0];
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

    public static String formatIterator(Iterator iter, String sOpen, String sClose, String sDelim)
        {
        if (!iter.hasNext())
            {
            return "";
            }

        StringBuilder sb = new StringBuilder();
        sb.append(sOpen);

        boolean fFirst = true;
        while (iter.hasNext())
            {
            if (fFirst)
                {
                fFirst = false;
                }
            else
                {
                sb.append(sDelim);
                }
            sb.append(iter.next());
            }
        sb.append(sClose);
        return sb.toString();
        }

    public static <K, V> String formatEntries(Map<K, V> map, String sDelim, BiFunction<K, V, String> formatEntry)
        {
        if (map == null || map.isEmpty())
            {
            return "";
            }

        StringBuilder sb = new StringBuilder();
        boolean fFirst = true;
        for (Map.Entry<K, V> entry : map.entrySet())
            {
            if (fFirst)
                {
                fFirst = false;
                }
            else
                {
                sb.append(sDelim);
                }
            sb.append(formatEntry.apply(entry.getKey(), entry.getValue()));
            }
        return sb.toString();
        }

    public static ObjectHandle resolveConst(Frame frame, int nConstValueId)
        {
        assert nConstValueId < 0;

        return nConstValueId < -Op.MAX_CONST_ID ? frame.getPredefinedArgument(nConstValueId) :
            frame.f_context.f_heapGlobal.ensureConstHandle(-nConstValueId);
        }

    public static void log(String sMsg)
        {
        System.out.println(new Date().toString() + " " + ServiceContext.getCurrentContext() + ": " + sMsg);
        }
    }
