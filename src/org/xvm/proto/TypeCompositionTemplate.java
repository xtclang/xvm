package org.xvm.proto;

import org.xvm.asm.ConstantPool;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * TypeCompositionTemplate represents a design unit (e.g. Class, Interface, Mixin, etc) that
 *  * has a well-known name in the type system
 *  * may have a number of formal type parameters
 *
 * @author gg 2017.02.23
 */
public abstract class TypeCompositionTemplate
    {
    final protected TypeSet f_types;

    final protected String f_sName; // globally known type composition name (e.g. x:Boolean or x:annotation.AtomicRef)
    final protected String[] f_asFormalType;
    final protected String f_sSuper; // super composition name; always x:Object for interfaces
    final protected Shape f_shape;
    protected TypeCompositionTemplate m_parent;

    List<TypeName> m_listImplement = new LinkedList<>(); // used as "extends " for interfaces
    List<String> m_listIncorporate = new LinkedList<>();

    Map<String, PropertyTemplate> m_mapProperties = new TreeMap<>();
    Map<String, MultiMethodTemplate> m_mapMultiMethods = new TreeMap<>();

    Map<String, MultiFunctionTemplate> m_mapMultiFunctions = new TreeMap<>(); // class level child functions

    // cache of TypeCompositions
    Map<List<Type>, TypeComposition> m_mapCompositions = new HashMap<>();

    // construct a simple (non-generic) type template
    public TypeCompositionTemplate(TypeSet types, String sName, String sSuper, Shape shape)
        {
        f_types = types;

        int ofBracket = sName.indexOf('<');
        if (ofBracket < 0)
            {
            f_sName = sName;
            f_asFormalType = TypeName.NON_GENERIC;
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
            f_sName = sName.substring(0, ofBracket);
            f_asFormalType = asFormalType;
            }

        f_sSuper = sSuper;
        f_shape = shape;
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

        f_types.ensureTemplate(tnInterface.getSimpleName());
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

        f_types.f_constantPool.registerMethod(templateM);

        return templateM;
        }

    public MethodTemplate getMethodTemplate(String sMethodName, String sSig)
        {
        MultiMethodTemplate mmt = m_mapMultiMethods.get(sMethodName);

        // TODO: signature support
        return mmt.m_setInvoke.iterator().next();
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

    public FunctionTemplate getFunctionTemplate(String sFunctionName, String sSig)
        {
        MultiFunctionTemplate mft = m_mapMultiFunctions.get(sFunctionName);

        // TODO: signature support
        return mft.m_setInvoke.iterator().next();
        }

    // produce a TypeComposition for this template by resolving the generic types
    public TypeComposition resolve(Type[] atGenericActual)
        {
        List<Type> key = atGenericActual.length == 0 ?
                Collections.emptyList() : Arrays.asList(atGenericActual);

        return m_mapCompositions.computeIfAbsent(key,
                (x) -> new TypeComposition(this, atGenericActual));
        }

    public TypeComposition resolve(ConstantPool.ClassConstant classConstant)
        {
        // TODO: awaiting support from ClassConstant
        return resolve(Utils.TYPE_NONE);
        }

    public Type createType(Type[] atGenericActual, Access access)
        {
        Type type = new Type(f_sName);
        return null;
        }

    // ---- OpCode support -----

    // create an un-initialized handle (Int i;)
    abstract public ObjectHandle createHandle(TypeComposition clazz);

    // assign (Int i = 5;)
    abstract public void assignConstValue(ObjectHandle handle, Object oValue);

    // construct (Point p = new Point(0, 0);)
    abstract public void initializeHandle(ObjectHandle handle, ObjectHandle[] ahArg);

    public ObjectHandle newInstance(Type[] aType, ObjectHandle[] ahArg)
        {
        switch (f_shape)
            {
            case Interface: case Trait: case Mixin: case Enum:
                throw new IllegalStateException();
            }

        TypeComposition clazz = resolve(aType);
        ObjectHandle handle = createHandle(clazz);
        initializeHandle(handle, ahArg);
        return handle;
        }

    @Override
    public String toString()
        {
        return f_shape + " " + f_sName + Utils.formatArray(f_asFormalType, "<", ">", ", ");
        }

    // ----- debugging support -----

    public String getDescription()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(f_shape).append(' ').append(f_sName)
          .append(Utils.formatArray(f_asFormalType, "<", ">", ", "));

        switch (f_shape)
            {
            case Class:
                if (f_sSuper != null)
                    {
                    sb.append("\n  extends ").append(f_sSuper);
                    }
                if (!m_listImplement.isEmpty())
                    {
                    sb.append("\n  implements ")
                      .append(Utils.formatIterator(m_listImplement.iterator(), "", "", ", "));
                    }
                if (!m_listIncorporate.isEmpty())
                    {
                    sb.append("\n  incorporates ")
                      .append(Utils.formatIterator(m_listIncorporate.iterator(), "", "", ", "));
                    }
                break;

            case Interface:
                if (!m_listImplement.isEmpty())
                    {
                    sb.append("\n  extends ")
                      .append(Utils.formatIterator(m_listImplement.iterator(), "", "", ", "));
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

        public TypeCompositionTemplate getClazzTemplate()
            {
            return TypeCompositionTemplate.this;
            }
        }

    public abstract class InvocationTemplate
            extends FunctionContainer
        {
        TypeName[] m_argTypeName; // length = 0 for zero args
        TypeName[] m_retTypeName; // length = 0 for Void return type

        Access m_access;
        Set<FunctionTemplate> m_setFunctions; // method/function level function templates (lambdas)

        // TODO: pointer to what XVM Structure?
        public int m_cArgs; // number of args
        public int m_cVars; // number of local vars
        public int[] m_anRetTypeId; // could be zeros for formal types
        public Op[] m_aop;

        protected InvocationTemplate(String[] asArgType, String[] asRetType)
            {
            TypeName[] aTypes;
            aTypes = new TypeName[asArgType.length];
            for (int i = 0; i < aTypes.length; i++)
                {
                aTypes[i] = TypeName.parseName(asArgType[i]);
                }
            m_argTypeName = aTypes;
            m_cArgs = aTypes.length;

            aTypes = new TypeName[asRetType.length];
            for (int i = 0; i < aTypes.length; i++)
                {
                aTypes[i] = TypeName.parseName(asRetType[i]);
                }
            m_retTypeName = aTypes;
            }

        protected void ensureDependents(TypeCompositionTemplate template)
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

        protected void setAccess(Access access)
            {
            m_access = access;
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
        protected String m_sMethodName;

        MethodTemplate(String sName, String[] asArgType, String[] asRetType)
            {
            super(asArgType, asRetType);

            m_sMethodName = sName;
            }
        }

    public class FunctionTemplate
            extends InvocationTemplate
        {
        protected String m_sFunctionName;

        FunctionTemplate(String sName, String[] asArgType, String[] asRetType)
            {
            super(asArgType, asRetType);

            m_sFunctionName = sName;
            }
        }

    public static enum Shape {Class, Interface, Trait, Mixin, Const, Service, Enum}
    public static enum Access {Public, Protected, Private}
    }
