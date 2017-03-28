package org.xvm.proto;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * TypeCompositionTemplate represents a design unit (e.g. Class, Interface, Mixin, etc) that
 *  - has a well-known name in the type system
 *  - may have a number of formal type parameters
 *
 * @author gg 2017.02.23
 */
public abstract class TypeCompositionTemplate
    {
    protected final TypeSet f_types;

    protected final String f_sName; // globally known type composition name (e.g. x:Boolean or x:annotation.AtomicRef)
    protected final String[] f_asFormalType;
    protected final String f_sSuper; // super composition name; always x:Object for interfaces
    protected final Shape f_shape;

    public final TypeComposition f_clazzCanonical; // public non-parameterized

    protected boolean m_fResolved;

    protected List<TypeName> m_listImplement = new LinkedList<>(); // used as "extends " for interfaces
    protected List<String> m_listIncorporate = new LinkedList<>();

    protected Map<String, PropertyTemplate> m_mapProperties = new TreeMap<>();
    protected Map<String, MultiMethodTemplate> m_mapMultiMethods = new TreeMap<>();

    protected Map<String, MultiFunctionTemplate> m_mapMultiFunctions = new TreeMap<>(); // class level child functions

    // ----- caches ------

    // cache of TypeCompositions
    protected Map<List<Type>, TypeComposition> m_mapCompositions = new HashMap<>();

    // cache of relationships
    protected enum Relation {EXTENDS, IMPLEMENTS, INCOMPATIBLE};
    protected Map<TypeCompositionTemplate, Relation> m_mapRelations = new HashMap<>();

    // construct the template
    public TypeCompositionTemplate(TypeSet types, String sName, String sSuper, Shape shape)
        {
        f_types = types;

        int ofBracket = sName.indexOf('<');
        if (ofBracket < 0)
            {
            f_sName = sName;
            f_asFormalType = TypeName.NON_GENERIC;
            f_clazzCanonical = new TypeComposition(this, Utils.TYPE_NONE);
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
            f_clazzCanonical = null;
            }

        f_sSuper = sSuper;
        f_shape = shape;
        }

    // add an "implement"
    public void ensureImplement(String sInterface)
        {
        TypeName tnInterface = TypeName.parseName(sInterface);
        if (m_listImplement.add(tnInterface))
            {
            f_types.ensureTemplate(tnInterface.getSimpleName());
            }
        }

    // add an "incorporate"
    public void ensureIncorporate(String sInterface)
        {
        m_listIncorporate.add(sInterface);
        }

    // add a property
    public PropertyTemplate addPropertyTemplate(String sPropertyName, String sTypeName)
        {
        return m_mapProperties.computeIfAbsent(sPropertyName, sName ->
            {
            PropertyTemplate templateP = new PropertyTemplate(sName, sTypeName);
            templateP.f_typeName.resolve(this);
            return templateP;
            });
        }

    public PropertyTemplate ensurePropertyTemplate(String sPropertyName, TypeName typeName)
        {
        return m_mapProperties.computeIfAbsent(sPropertyName,
                sName -> new PropertyTemplate(sName, typeName));
        }

    public PropertyTemplate getPropertyTemplate(String sPropertyName)
        {
        return m_mapProperties.get(sPropertyName);
        }

    public void forEachProperty(Consumer<PropertyTemplate> consumer)
        {
        m_mapProperties.values().forEach(consumer::accept);
        }

    // add a method
    public MethodTemplate addMethodTemplate(String sMethodName, String[] asArgTypes, String[] asRetTypes)
        {
        MethodTemplate templateM = m_mapMultiMethods.computeIfAbsent(sMethodName, s -> new MultiMethodTemplate()).
                add(new MethodTemplate(sMethodName, asArgTypes, asRetTypes));
        templateM.resolveTypes(this);

        return templateM;
        }

    public MethodTemplate ensureMethodTemplate(String sMethodName, TypeName[] atArg, TypeName[] atRet)
        {
        return m_mapMultiMethods.computeIfAbsent(sMethodName, s -> new MultiMethodTemplate()).
                add(new MethodTemplate(sMethodName, atArg, atRet));
        }

    public MethodTemplate addMethodTemplate(MethodTemplate templateM)
        {
        m_mapMultiMethods.computeIfAbsent(templateM.f_sName, s -> new MultiMethodTemplate()).
                add(templateM);
        return templateM;
        }

    public MethodTemplate getMethodTemplate(String sMethodName, String[] asArgTypes, String[] asRetTypes)
        {
        return getMethodTemplate(sMethodName, null);
        }

    public MethodTemplate getMethodTemplate(String sMethodName, String sSig)
        {
        MultiMethodTemplate mmt = m_mapMultiMethods.get(sMethodName);

        // TODO: signature support
        return mmt.m_setInvoke.iterator().next();
        }

    public void forEachMethod(Consumer<MethodTemplate> consumer)
        {
        for (MultiMethodTemplate mmt : m_mapMultiMethods.values())
            {
            mmt.m_setInvoke.forEach(consumer::accept);
            }
        }

    // add a function
    public FunctionTemplate addFunctionTemplate(String sFunctionName, String[] asArgTypes, String[] asRetTypes)
        {
        FunctionTemplate templateF = new FunctionTemplate(sFunctionName, asArgTypes, asRetTypes);

        m_mapMultiFunctions.computeIfAbsent(sFunctionName, s -> new MultiFunctionTemplate()).
                add(templateF);

        templateF.resolveTypes(this);

        return templateF;
        }

    public FunctionTemplate getFunctionTemplate(String sFunctionName, String[] asArgTypes, String[] asRetTypes)
        {
        return getFunctionTemplate(sFunctionName, null);
        }
    public FunctionTemplate getFunctionTemplate(String sFunctionName, String sSig)
        {
        MultiFunctionTemplate mft = m_mapMultiFunctions.get(sFunctionName);

        // TODO: signature support
        return mft.m_setInvoke.iterator().next();
        }

    public void forEachFunction(Consumer<FunctionTemplate> consumer)
        {
        for (MultiFunctionTemplate mft : m_mapMultiFunctions.values())
            {
            mft.m_setInvoke.forEach(consumer::accept);
            }
        }

    /**
     * Initialize properties, methods and functions declared at the "top" layer.
     */
    public void initDeclared()
        {
        }

    /**
     * Resolve the "implements", "extends", "incorporates", "delegates"
     */
    public void resolveDependencies()
        {
        if (!m_fResolved)
            {
            m_fResolved = true;

            if (f_sSuper != null)
                {
                f_types.ensureTemplate(f_sSuper).resolveDependencies();
                }

            resolveImplements();

            resolveExtends();

            resolveDeclared();
            }
        }

    protected void resolveImplements()
        {
        for (TypeName tnIface : m_listImplement)
            {
            TypeCompositionTemplate templateIface = f_types.getTemplate(tnIface.getSimpleName());

            templateIface.forEachProperty(propIface ->
                    ensurePropertyTemplate(propIface.f_sName, propIface.f_typeName).
                            resolveFrom(propIface));

            templateIface.forEachMethod(methodIface ->
                {
                if (methodIface.m_access == Access.Public)
                    {
                    addMethodTemplate(methodIface);
                    }
                });
            }
        }

    protected void resolveExtends()
        {
        if (f_sSuper != null)
            {
            TypeCompositionTemplate templateSuper = f_types.ensureTemplate(f_sSuper);

            templateSuper.forEachProperty(propSuper ->
                    ensurePropertyTemplate(propSuper.f_sName, propSuper.f_typeName).
                            resolveFrom(propSuper));

            templateSuper.forEachMethod(methodSuper ->
            {
            if (methodSuper.m_access != Access.Private)
                {
                addMethodTemplate(methodSuper);
                }
            });
            }
        }

    // declared at this level
    protected void resolveDeclared()
        {
        forEachProperty(prop ->
                f_types.f_constantPool.registerProperty(this, prop));

        forEachMethod(method ->
                f_types.f_constantPool.registerInvocable(this, method));

        forEachFunction(function ->
                f_types.f_constantPool.registerInvocable(this, function));
        }

    public void initCode()
        {
        }


    // produce a TypeComposition for this template by resolving the generic types
    public TypeComposition resolve(Type[] atGenericActual)
        {
        if (atGenericActual.length == 0)
            {
            return f_clazzCanonical;
            }

        List<Type> key = Arrays.asList(atGenericActual);
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
        // TODO create the specified type
        return type;
        }

    // ---- OpCode support -----

    // create an un-initialized handle (Int i;)
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        throw new UnsupportedOperationException();
        }

    // assign (Int i = 5;)
    // @return null if this type doesn't take that constant
    public ObjectHandle createConstHandle(Constant constant)
        {
        return null;
        }

    // invokeNative with 0 arguments and 0 return values
    // @return - an exception handle
    public ObjectHandle invokeNative00(Frame frame, ObjectHandle hTarget, MethodTemplate method)
        {
        // many classes don't have native methods
        throw new IllegalStateException();
        }

    // invokeNative with 0 arguments and 1 return value
    // @return - an exception handle
    public ObjectHandle invokeNative01(Frame frame, ObjectHandle hTarget,
                                  MethodTemplate method, ObjectHandle[] ahReturn)
        {
        throw new IllegalStateException();
        }

    // invokeNative with 1 argument and 1 return value
    // @return - an exception handle
    public ObjectHandle invokeNative11(Frame frame, ObjectHandle hTarget,
                                       MethodTemplate method, ObjectHandle hArg, ObjectHandle[] ahReturn)
        {
        throw new IllegalStateException();
        }

    // Add operation
    // @return - an exception handle
    public ObjectHandle invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, ObjectHandle[] ahReturn)
        {
        throw new IllegalStateException();
        }

    // get a property value
    public ObjectHandle getProperty(ObjectHandle hTarget, String sName)
        {
        throw new IllegalStateException();
        }

    // set a property value
    public void setProperty(ObjectHandle hTarget, String sName, ObjectHandle hValue)
        {
        throw new IllegalStateException();
        }

    // return a handle with this:struct access
    public ObjectHandle createStruct(Frame frame)
        {
        throw new IllegalStateException();
        }

    // does this template extend that?
    public boolean extends_(TypeCompositionTemplate that)
        {
        assert that.f_shape != Shape.Interface;

        if (this == that)
            {
            return true;
            }

        Relation relation = m_mapRelations.get(that);
        if (relation != null)
            {
            return relation == Relation.EXTENDS;
            }

        TypeCompositionTemplate templateSuper = f_types.getTemplate(f_sSuper);
        while (templateSuper != null)
            {
            m_mapRelations.put(that, Relation.EXTENDS);

            // there is just one template instance per name
            if (templateSuper == that)
                {
                return true;
                }
            templateSuper = f_types.getTemplate(templateSuper.f_sSuper);
            }

        m_mapRelations.put(that, Relation.INCOMPATIBLE);
        return false;
        }

    public ObjectHandle changeType(ObjectHandle handle, Access access)
        {
        handle = handle.cloneHandle();
        handle.m_type = handle.f_clazz.ensurePublicType();
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
                        .append(template.f_typeName)
                        .append(' ').append(template.f_sName));

        sb.append("\nMethods:");
        m_mapMultiMethods.values().forEach(
                mmt ->
                {
                mmt.m_setInvoke.forEach(mt ->
                        sb.append("\n  ")
                                .append(TypeName.format(mt.m_retTypeName))
                                .append(' ').append(mt.f_sName)
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
                                .append(' ').append(ft.f_sName)
                                .append('(')
                                .append(TypeName.format(ft.m_argTypeName))
                                .append(')'));
                });

        return sb.toString();
        }

    // -----


    public abstract class FunctionContainer
        {
        public TypeCompositionTemplate getClazzTemplate()
            {
            return TypeCompositionTemplate.this;
            }
        }

    public abstract class MethodContainer
            extends FunctionContainer
        {
        }

    public class PropertyTemplate
            extends MethodContainer
        {
        public final String f_sName;
        public final TypeName f_typeName;

        private boolean m_fReadOnly = false;
        private boolean m_fAtomic = false;
        public Access m_accessGet = Access.Public;
        public Access m_accessSet = Access.Public;

        // the following fields don't impact the type (and neither do the super fields)
        // (e.g. a presence of a "get" implementation doesn't change the type)
        public MethodTemplate m_templateGet; // can be null
        public MethodTemplate m_templateSet; // can be null

        // construct a property template
        public PropertyTemplate(String sName, String sType)
            {
            this(sName, TypeName.parseName(sType));
            }

        public PropertyTemplate(String sName, TypeName typeName)
            {
            f_sName = sName;
            f_typeName = typeName;
            }

        public void makeReadOnly()
            {
            m_fReadOnly = true;
            m_accessSet = null;
            }

        public boolean isReadOnly()
            {
            return m_fReadOnly;
            }

        public void makeAtomic()
            {
            m_fAtomic = true;
            m_accessSet = null;
            }

        public boolean isAtomic()
            {
            return m_fAtomic;
            }

        public void setGetAccess(Access access)
            {
            m_accessGet = access;
            }

        public void setSetAccess(Access access)
            {
            m_accessSet = access;
            }

        public MethodTemplate addGet()
            {
            MethodTemplate templateThis = addMethod("get", VOID, new String[]{f_typeName.toString()});
            templateThis.setSuper(m_templateGet);
            return m_templateGet = templateThis;
            }

        public MethodTemplate addSet()
            {
            MethodTemplate templateThis = addMethod("set", new String[]{f_typeName.toString()}, VOID);
            templateThis.setSuper(m_templateSet);
            return m_templateSet = templateThis;
            }

        public MethodTemplate addMethod(String sMethodName, String[] asArgTypes, String[] asRetTypes)
            {
            return addMethodTemplate(f_sName + '$' + sMethodName, asArgTypes, asRetTypes);
            }

        public void resolveFrom(PropertyTemplate that)
            {
            if (that.m_accessGet != Access.Private)
                {
                this.m_accessGet = that.m_accessGet;

                // check for the "super" implementations
                if (that.m_templateGet != null)
                    {
                    this.m_templateGet = addMethodTemplate(that.m_templateGet);
                    }
                }

            if (that.m_accessSet != Access.Private)
                {
                this.m_accessSet = that.m_accessSet;

                // check for the "super" implementations
                if (that.m_templateSet != null)
                    {
                    this.m_templateSet = addMethodTemplate(that.m_templateSet);
                    }
                }

            }
        }

    public abstract class InvocationTemplate
            extends FunctionContainer
        {
        public final String f_sName;

        public TypeName[] m_argTypeName; // length = 0 for zero args
        public TypeName[] m_retTypeName; // length = 0 for Void return type

        Access m_access;
        boolean m_fNative;
        Set<FunctionTemplate> m_setFunctions; // method/function level function templates (lambdas)

        // TODO: pointer to what XVM Structure?
        public int m_cArgs; // number of args
        public int m_cReturns; // number of return values
        public int m_cVars; // max number of local vars (including "this")
        public int m_cScopes = 1; // max number of scopes
        public Op[] m_aop;

        protected InvocationTemplate(String sName, String[] asArgType, String[] asRetType)
            {
            this(sName, TypeName.parseNames(asArgType), TypeName.parseNames(asRetType));
            }

        protected InvocationTemplate(String sName, TypeName[] atArg, TypeName[] atRet)
            {
            f_sName = sName;

            m_argTypeName = atArg;
            m_cArgs = atArg.length;

            m_retTypeName = atRet;
            m_cReturns = atRet.length;
            }

        protected void resolveTypes(TypeCompositionTemplate template)
            {
            for (TypeName t : m_argTypeName)
                {
                t.resolve(template);
                }

            for (TypeName t : m_retTypeName)
                {
                t.resolve(template);
                }
            }

        public void setAccess(Access access)
            {
            m_access = access;
            }

        public boolean isNative()
            {
            return m_fNative;
            }
        public void markNative()
            {
            m_fNative = true;
            }

        @Override
        public String toString()
            {
            return getClass().getSimpleName() + " " +
                    f_sName + " " + Utils.formatArray(m_argTypeName, "<", ">", ", ");
            }
        }

    public abstract class MultiInvocationTemplate<T>
        {
        Set<T> m_setInvoke = new HashSet<>();

        public T add(T template)
            {
            m_setInvoke.add(template);
            return template;
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
        MethodTemplate m_methodSuper;

        MethodTemplate(String sName, String[] asArgType, String[] asRetType)
            {
            super(sName, asArgType, asRetType);
            }

        MethodTemplate(String sName, TypeName[] atArg, TypeName[] atRet)
            {
            super(sName, atArg, atRet);
            }

        public void setSuper(MethodTemplate methodSuper)
            {
            m_methodSuper = methodSuper;
            }
        }

    public class FunctionTemplate
            extends InvocationTemplate
        {
        FunctionTemplate(String sName, String[] asArgType, String[] asRetType)
            {
            super(sName, asArgType, asRetType);
            }
        }

    public static enum Shape {Class, Interface, Trait, Mixin, Const, Service, Enum}
    public static enum Access {Public, Protected, Private, Struct}

    public static String[] VOID = new String[0];
    public static String[] BOOLEAN = new String[]{"x:Boolean"};
    public static String[] INT = new String[]{"x:Int"};
    public static String[] STRING = new String[]{"x:String"};
    public static String[] THIS = new String[]{"this.Type"};
    public static String[] CONDITIONAL_THIS = new String[]{"x:ConditionalTuple<this.Type>"};
    }
