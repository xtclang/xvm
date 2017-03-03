package org.xvm.proto;

import java.util.Map;

/**
 * The actual x.Object representation.
 *
 * @author gg 2017.02.24
 */
public class Struct
    {
    TypeComposition typeComposition;

    // anything that fits long
    public static class IntrinsicLong extends Struct
        {
        long m_lValue;
        }

    // x.String
    public static class IntrinsicString extends Struct
        {
        String m_sValue;
        }

    // generic Java delegation
    public static class JavaNative extends Struct
        {
        Object m_delegate;
        }

    public static class Natural extends Struct
        {
        // keyed by the property name
        Map<String, Struct> m_mapValues;
        }

    }
