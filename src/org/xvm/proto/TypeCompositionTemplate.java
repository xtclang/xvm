package org.xvm.proto;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * TypeComposition (e.g. class)
 *
 * @author gg 2017.02.23
 */
public class TypeCompositionTemplate
    {
    CompositeTypeName m_typeName;

    Map<String, PropertyTemplate> m_mapProperties;
    Map<String, MultiMethodTemplate> m_mapMultiMethods;

    Set<FunctionTemplate> m_setFunctions; // class level child functions

    // -----

    public static abstract class TypeNameTemplate
        {
        }

    public static class FormalTypeName extends TypeNameTemplate
        {
        String m_sFormalName; // must be one of the names in m_asGenericTypeName
        }

    public static class CompositeTypeName extends TypeNameTemplate
        {
        String m_sActualName;         // globally known type composition name (e.g. x.Boolean or x.annotation.AtomicRef)
        String[] m_asGenericTypeName; // length 0 for non-generic types
        }

    public abstract static class InvocationTemplate
        {
        TypeNameTemplate[] m_argTypeName; // length = 0 for zero args
        TypeNameTemplate[] m_retTypeName; // length = 0 for Void return type

        Access m_access;
        Set<FunctionTemplate> m_setFunctions; // method/function level function templates (lambdas)

        // TODO: pointer to what XVM Structure?
        int m_cVars; // number of local vars
        byte[] m_opCode;
        }

    public static class PropertyTemplate
        {
        TypeNameTemplate m_propertyTypeName;
        MethodTemplate m_templateGet;
        MethodTemplate m_templateSet; // can be null
        }

    public static class MultiMethodTemplate
        {
        Set<MethodTemplate> m_setMethods; // TODO: Map<MethodConstant, MethodTemplate>?
        }

    public static class MethodTemplate extends InvocationTemplate
        {
        @Override
        public int hashCode()
            {
            return Arrays.hashCode(m_argTypeName) + Arrays.hashCode(m_retTypeName);
            }
        @Override
        public boolean equals(Object other)
            {
            MethodTemplate that = (MethodTemplate) other;
            return Arrays.equals(this.m_argTypeName, that.m_argTypeName)
                && Arrays.equals(this.m_retTypeName, that.m_retTypeName);
            }
        }

    public static class MultiFunctionTemplate
        {
        Set<FunctionTemplate> m_setFunctions; // TODO: Map<FunctionConstant, FunctionTemplate>?
        }
    public static class FunctionTemplate extends InvocationTemplate
        {
        String[] m_asCaptureName;
        TypeNameTemplate[] m_captureTypeName;
        }

    public static enum Access {Public, Protected, Private}
    }
