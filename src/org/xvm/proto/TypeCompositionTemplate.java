package org.xvm.proto;

import java.util.*;

/**
 * TypeCompositionTemplate represents a design unit (e.g. Class, Interface, Mixin, etc) that
 *  * has a well-known name in the type system
 *  * may have a number of formal type parameters
 *
 * @author gg 2017.02.23
 */
public class TypeCompositionTemplate
    {
    protected TypeSet m_types;

    String m_sName; // globally known type composition name (e.g. x:Boolean or x:annotation.AtomicRef)
    String[] m_asFormalType;
    String m_sSuper; // super composition name; always x:Object for interfaces
    Shape  m_shape;
    TypeCompositionTemplate m_parent;

    List<TypeName> m_listImplement = new LinkedList<>(); // used as "extends " for interfaces
    List<String> m_listIncorporate = new LinkedList<>();

    Map<String, PropertyTemplate> m_mapProperties = new TreeMap<>();
    Map<String, MultiMethodTemplate> m_mapMultiMethods = new TreeMap<>();

    Map<String, MultiFunctionTemplate> m_mapMultiFunctions = new TreeMap<>(); // class level child functions

    // construct a simple (non-generic) type template
    public TypeCompositionTemplate(TypeSet types, String sName, String sSuper, Shape shape)
        {
        m_types = types;

        int ofBracket = sName.indexOf('<');
        if (ofBracket < 0)
            {
            m_sName = sName;
            m_asFormalType = TypeName.NON_GENERIC;
            }
        else
            {
            int ofLast = sName.length() - 1;
            assert(sName.charAt(ofLast) == '>');

            String[] asFormalType = sName.substring(ofBracket + 1, ofLast).split(",");
            for (int i = 0; i < asFormalType.length; i++)
                {
                String sFormalType = asFormalType[i];
                assert (!types.existsTemplate(sFormalType)); // must not be know
                }
            m_sName = sName.substring(0, ofBracket);
            m_asFormalType = asFormalType;
            }

        m_sSuper = sSuper;
        m_shape = shape;
        }

    /**
     * Initialize properties, methods and functions declared at the "top" layer.
     */
    public void initDeclared()
        {
        }

    // set the "parent"
    public void setParent(TypeCompositionTemplate parent)
        {
        m_parent = parent;
        }

    // add an "implement"
    public void addImplement(String sInterface)
        {
        TypeName tnInterface = TypeName.parseName(sInterface);
        m_listImplement.add(tnInterface);

        m_types.ensureTemplate(tnInterface.getSimpleName());
        }

    // add an "incorporate"
    public void addIncorporate(String sInterface)
        {
        m_listIncorporate.add(sInterface);
        }

    // add a property
    public PropertyTemplate addPropertyTemplate(String sPropertyName, String sTypeName)
        {
        PropertyTemplate templateP = new PropertyTemplate(sPropertyName, sTypeName);

        m_mapProperties.put(sPropertyName, templateP);

        templateP.m_propertyTypeName.ensureDependents(this);

        return templateP;
        }

    // add a method
    public MethodTemplate addMethodTemplate(String sMethodName, String[] asArgTypes, String[] asRetTypes)
        {
        MethodTemplate templateM = new MethodTemplate(sMethodName, asArgTypes, asRetTypes);

        m_mapMultiMethods.computeIfAbsent(sMethodName, s -> new MultiMethodTemplate()).
                add(templateM);

        templateM.ensureDependents(this);

        return templateM;
        }

    // add a function
    public FunctionTemplate addFunctionTemplate(String sFunctionName, String[] asArgTypes, String[] asRetTypes)
        {
        FunctionTemplate templateF = new FunctionTemplate(sFunctionName, asArgTypes, asRetTypes);

        m_mapMultiFunctions.computeIfAbsent(sFunctionName, s -> new MultiFunctionTemplate()).
                add(templateF);

        templateF.ensureDependents(this);

        return templateF;
        }

    // produce a TypeComposition for this template by resolving the generic types
    public TypeComposition resolve(TypeName[] atnGenericActual)
        {
        return new TypeComposition(this, atnGenericActual);
        }

    // ---- OpCode support -----

    public ObjectHandle createHandle()
        {
        throw new UnsupportedOperationException();
        }

    public ObjectHandle createHandle(Object oValue)
        {
        ObjectHandle handle = createHandle();
        assignHandle(handle, oValue);
        return handle;
        }

    public void assignHandle(ObjectHandle handle, Object oValue)
        {
        throw new UnsupportedOperationException();
        }

    // ----- debugging support -----

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(m_shape).append(' ').append(m_sName)
          .append(Formatting.formatArray(m_asFormalType, "<", ">", ", "));

        switch (m_shape)
            {
            case Class:
                if (m_sSuper != null)
                    {
                    sb.append("\n  extends ").append(m_sSuper);
                    }
                if (!m_listImplement.isEmpty())
                    {
                    sb.append("\n  implements ")
                      .append(Formatting.formatIterator(m_listImplement.iterator(), "", "", ", "));
                    }
                if (!m_listIncorporate.isEmpty())
                    {
                    sb.append("\n  incorporates ")
                      .append(Formatting.formatIterator(m_listIncorporate.iterator(), "", "", ", "));
                    }
                break;

            case Interface:
                if (!m_listImplement.isEmpty())
                    {
                    sb.append("\n  extends ")
                      .append(Formatting.formatIterator(m_listImplement.iterator(), "", "", ", "));
                    }
                break;
            }

        sb.append("\nProperties:");
        m_mapProperties.values().forEach(
                template -> sb.append("\n  ")
                        .append(template.m_propertyTypeName)
                        .append(' ').append(template.m_sPropertyName));

        sb.append("\nMethods:");
        m_mapMultiMethods.values().forEach(
                mmt ->
                {
                mmt.m_setInvoke.forEach(mt ->
                        sb.append("\n  ")
                                .append(TypeName.format(mt.m_retTypeName))
                                .append(' ').append(mt.m_sMethodName)
                                .append('(')
                                .append(TypeName.format(mt.m_argTypeName))
                                .append(')'));
                });

        sb.append("\nFunctions:");
        m_mapMultiFunctions.values().forEach(
                mft ->
                {
                mft.m_setInvoke.forEach(ft ->
                        sb.append("\n  ")
                                .append(TypeName.format(ft.m_retTypeName))
                                .append(' ').append(ft.m_sFunctionName)
                                .append('(')
                                .append(TypeName.format(ft.m_argTypeName))
                                .append(')'));
                });

        return sb.toString();
        }

    // -----

    public abstract class MethodContainer
            extends FunctionContainer
        {
        Map<String, MultiMethodTemplate> m_mapMultiMethods;
        }

    public class PropertyTemplate
            extends MethodContainer
        {
        String m_sPropertyName;
        TypeName m_propertyTypeName;
        boolean m_fReadOnly = false;
        Access m_accessGet = Access.Public;
        Access m_accessSet = Access.Public;

        // the following fields don't impact the type (and neither do the super fields)
        // (e.g. a presence of a "get" implementation doesn't change the type)
        MethodTemplate m_templateGet; // can be null
        MethodTemplate m_templateSet; // can be null

        // construct a property template
        public PropertyTemplate(String sName, String sType)
            {
            m_sPropertyName = sName;
            m_propertyTypeName = TypeName.parseName(sType);
            }

        public void makeReadOnly()
            {
            m_fReadOnly = true;
            m_accessSet = null;
            }
        public void setGetAccess(Access access)
            {
            m_accessGet = access;
            }
        public void setSetAccess(Access access)
            {
            m_accessSet = access;
            }
        }

    public abstract class FunctionContainer
        {
        Map<String, MultiFunctionTemplate> m_mapMultiFunctions; // names could be synthetic for anonymous functions
        }

    public abstract class InvocationTemplate
            extends FunctionContainer
        {
        TypeName[] m_argTypeName; // length = 0 for zero args
        TypeName[] m_retTypeName; // length = 0 for Void return type

        Access m_access;
        Set<FunctionTemplate> m_setFunctions; // method/function level function templates (lambdas)

        // TODO: pointer to what XVM Structure?
        int m_cVars; // number of local vars
        Op[] m_aop;

        InvocationTemplate(String[] asArgType, String[] asRetType)
            {
            TypeName[] aTypes;
            aTypes = new TypeName[asArgType.length];
            for (int i = 0; i < aTypes.length; i++)
                {
                aTypes[i] = TypeName.parseName(asArgType[i]);
                }
            m_argTypeName = aTypes;

            aTypes = new TypeName[asRetType.length];
            for (int i = 0; i < aTypes.length; i++)
                {
                aTypes[i] = TypeName.parseName(asRetType[i]);
                }
            m_retTypeName = aTypes;
            }

        void ensureDependents(TypeCompositionTemplate template)
            {
            for (TypeName t : m_argTypeName)
                {
                t.ensureDependents(template);
                }

            for (TypeName t : m_retTypeName)
                {
                t.ensureDependents(template);
                }
            }

        void setAccess(Access access)
            {
            m_access = access;
            }

        Frame createFrame(ServiceContext context, ObjectHandle[] ahArgs)
            {
            assert ahArgs.length == m_argTypeName.length;

            return new Frame(context, this, ahArgs, m_cVars, m_retTypeName.length);
            }
        }

    public abstract class MultiInvocationTemplate<T>
        {
        Set<T> m_setInvoke = new HashSet<>();

        public void add(T template)
            {
            m_setInvoke.add(template);
            }

        }
    public class MultiMethodTemplate  extends MultiInvocationTemplate<MethodTemplate>
        {
        }

    public class MultiFunctionTemplate extends MultiInvocationTemplate<FunctionTemplate>
        {
        }

    public class MethodTemplate
            extends InvocationTemplate
        {
        String m_sMethodName;

        MethodTemplate(String sName, String[] asArgType, String[] asRetType)
            {
            super(asArgType, asRetType);

            m_sMethodName = sName;
            }
        }

    public class FunctionTemplate
            extends InvocationTemplate
        {
        String m_sFunctionName;

        FunctionTemplate(String sName, String[] asArgType, String[] asRetType)
            {
            super(asArgType, asRetType);

            m_sFunctionName = sName;
            }
        }

    public static enum Shape {Class, Interface, Trait, Mixin, Const, Service, Enum}
    public static enum Access {Public, Protected, Private}
    }
