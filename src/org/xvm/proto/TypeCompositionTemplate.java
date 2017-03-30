package org.xvm.proto;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool.ClassConstant;
import org.xvm.asm.ConstantPool.MethodConstant;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

    public TypeCompositionTemplate m_templateSuper;
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
    public void addImplement(String sInterface)
        {
        m_listImplement.add(TypeName.parseName(sInterface));
        }

    // add an "incorporate"
    public void addIncorporate(String sInterface)
        {
        m_listIncorporate.add(sInterface);
        }

    // add a property
    public PropertyTemplate ensurePropertyTemplate(String sPropertyName, String sTypeName)
        {
        PropertyTemplate propThis = new PropertyTemplate(sPropertyName, sTypeName);
        propThis.f_typeName.resolve(this);

        return addPropertyTemplate(sPropertyName, propThis);
        }

    public PropertyTemplate derivePropertyTemplateFrom(PropertyTemplate propThat)
        {
        PropertyTemplate propThis = new PropertyTemplate(propThat.f_sName, propThat.f_typeName);
        propThis.deriveFrom(propThat);

        return addPropertyTemplate(propThat.f_sName, propThis);
        }

    protected PropertyTemplate addPropertyTemplate(String sPropertyName, PropertyTemplate propThis)
        {
        PropertyTemplate propPrev = m_mapProperties.put(sPropertyName, propThis);

        if (propPrev == null)
            {
            f_types.f_constantPool.registerProperty(this, propThis);
            }
        else
            {
            propThis.deriveFrom(propPrev);
            }
        return propThis;
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
    public MethodTemplate ensureMethodTemplate(String sMethodName, String[] asArgTypes, String[] asRetTypes)
        {
        MethodTemplate templateM = m_mapMultiMethods.computeIfAbsent(sMethodName, s -> new MultiMethodTemplate()).
                add(new MethodTemplate(sMethodName, asArgTypes, asRetTypes));
        templateM.resolveTypes(this);

        return templateM;
        }

    public MethodTemplate deriveMethodTemplateFrom(MethodTemplate templateM)
        {
        m_mapMultiMethods.computeIfAbsent(templateM.f_sName, s -> new MultiMethodTemplate()).
                add(templateM);
        return templateM;
        }

    public MethodTemplate getMethodTemplate(String sMethodName, String[] asArgTypes, String[] asRetTypes)
        {
        return getMethodTemplate(sMethodName,
                TypeName.getFunctionSignature(sMethodName, asArgTypes, asRetTypes));
        }

    public MethodTemplate getMethodTemplate(String sMethodName, String sSig)
        {
        MultiMethodTemplate mmt = m_mapMultiMethods.get(sMethodName);

        return mmt.m_mapMethods.get(sSig);
        }

    public MethodTemplate getMethodTemplate(MethodConstant constMethod)
        {
        MultiMethodTemplate mft = m_mapMultiMethods.get(constMethod.getName());

        // TODO: when MethodConstant is done
        return mft.m_mapMethods.values().iterator().next();
        }

    public void forEachMethod(Consumer<MethodTemplate> consumer)
        {
        for (MultiMethodTemplate mmt : m_mapMultiMethods.values())
            {
            mmt.m_mapMethods.values().forEach(consumer::accept);
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

    public FunctionTemplate addFunctionTemplate(FunctionTemplate templateF)
        {
        m_mapMultiFunctions.computeIfAbsent(templateF.f_sName, s -> new MultiFunctionTemplate()).
                add(templateF);
        return templateF;
        }

    public FunctionTemplate getFunctionTemplate(String sFunctionName, String[] asArgTypes, String[] asRetTypes)
        {
        return getFunctionTemplate(sFunctionName,
                TypeName.getFunctionSignature(sFunctionName, asArgTypes, asRetTypes));
        }

    public FunctionTemplate getFunctionTemplate(String sFunctionName, String sSig)
        {
        MultiFunctionTemplate mft = m_mapMultiFunctions.get(sFunctionName);

        return mft.m_mapFunctions.get(sSig);
        }

    public FunctionTemplate getFunctionTemplate(MethodConstant constMethod)
        {
        MultiFunctionTemplate mft = m_mapMultiFunctions.get(constMethod.getName());

        // TODO: when MethodConstant is done
        return mft.m_mapFunctions.values().iterator().next();
        }

    public void forEachFunction(Consumer<FunctionTemplate> consumer)
        {
        for (MultiFunctionTemplate mft : m_mapMultiFunctions.values())
            {
            mft.m_mapFunctions.values().forEach(consumer::accept);
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
                // this will recursively resolveDependencies on the super
                m_templateSuper = f_types.ensureTemplate(f_sSuper);
                }

            resolveImplements();

            resolveExtends();
            }
        }

    protected void resolveImplements()
        {
        for (TypeName tnIface : m_listImplement)
            {
            TypeCompositionTemplate templateIface = f_types.ensureTemplate(tnIface.getSimpleName());

            templateIface.forEachProperty(this::derivePropertyTemplateFrom);

            templateIface.forEachMethod(this::deriveMethodTemplateFrom);
            }
        }

    protected void resolveExtends()
        {
        if (m_templateSuper != null)
            {
            m_templateSuper.forEachProperty(propSuper ->
                {
                if (propSuper.m_accessGet != Access.Private || propSuper.m_accessSet != Access.Private)
                    {
                    derivePropertyTemplateFrom(propSuper);
                    }
                });

            m_templateSuper.forEachMethod(methodSuper ->
                {
                if (methodSuper.m_access != Access.Private)
                    {
                    deriveMethodTemplateFrom(methodSuper);
                    }
                });
            }
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

    public TypeComposition resolve(ClassConstant classConstant)
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

    // @return - an exception handle
    public ObjectHandle invokeNeg(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahReturn)
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

        TypeCompositionTemplate templateSuper = m_templateSuper;
        while (templateSuper != null)
            {
            m_mapRelations.put(that, Relation.EXTENDS);

            // there is just one template instance per name
            if (templateSuper == that)
                {
                return true;
                }
            templateSuper = templateSuper.m_templateSuper;
            }

        m_mapRelations.put(that, Relation.INCOMPATIBLE);
        return false;
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
                template -> sb.append("\n  ").append(template));

        sb.append("\nMethods:");
        m_mapMultiMethods.values().forEach(
            mmt ->
                {
                mmt.m_mapMethods.values().forEach(mt -> sb.append("\n  ").append(mt));
                });

        sb.append("\nFunctions:");
        m_mapMultiFunctions.values().forEach(
            mft ->
                {
                mft.m_mapFunctions.values().forEach(ft -> sb.append("\n  ").append(ft));
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
            return ensureMethodTemplate(f_sName + '$' + sMethodName, asArgTypes, asRetTypes);
            }

        public void deriveFrom(PropertyTemplate that)
            {
            if (that.m_accessGet != Access.Private)
                {
                this.m_accessGet = that.m_accessGet;

                // check for the "super" implementations
                if (that.m_templateGet != null)
                    {
                    this.m_templateGet = deriveMethodTemplateFrom(that.m_templateGet);
                    }
                }

            if (that.m_accessSet != Access.Private)
                {
                this.m_accessSet = that.m_accessSet;

                // check for the "super" implementations
                if (that.m_templateSet != null)
                    {
                    this.m_templateSet = deriveMethodTemplateFrom(that.m_templateSet);
                    }
                }
            }

        @Override
        public String toString()
            {
            return (m_accessGet == m_accessSet ? m_accessGet.name() :
                    (m_accessGet.name() + "/" + m_accessSet.name())).toLowerCase()
                + " " + f_typeName + " " + f_sName;
            }
        }

    public abstract class InvocationTemplate
            extends FunctionContainer
        {
        public final String f_sName;

        public TypeName[] m_argTypeName; // length = 0 for zero args
        public TypeName[] m_retTypeName; // length = 0 for Void return type

        // TODO: pointer to what XVM Structure?
        Access m_access = Access.Public;
        boolean m_fNative;
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

        protected void copyCodeAttributes(InvocationTemplate that)
            {
            m_access = that.m_access;
            m_fNative = that.m_fNative;
            m_cArgs = that.m_cArgs;
            m_cReturns = that.m_cReturns;
            m_cVars = that.m_cVars;
            m_cScopes = that.m_cScopes;
            m_aop = that.m_aop;
            }

        // create a new function template for a bound method
        public FunctionTemplate bind(int iArg)
            {
//            int cArgs = m_cArgs;
//            TypeName[] atArg = new TypeName[cArgs + 1];
//            System.arraycopy(m_argTypeName, 0, atArg, 1, cArgs);
//            atArg[0] = new TypeName.SimpleTypeName(TypeCompositionTemplate.this.f_sName);
//
//            FunctionTemplate function = new FunctionTemplate(f_sName, atArg, m_retTypeName);
//            function.copyCodeAttributes(this);
//            return function;
            throw new UnsupportedOperationException("TODO");
            }

        public String getSignature()
            {
            return TypeName.getFunctionSignature(f_sName, m_argTypeName, m_retTypeName);
            }

        @Override
        public String toString()
            {
            return m_access.name().toLowerCase() + ' ' + getSignature();
            }
        }

    public class MultiMethodTemplate
        {
        Map<String, MethodTemplate> m_mapMethods = new HashMap<>();

        public MethodTemplate add(MethodTemplate method)
            {
            MethodTemplate methodSuper = m_mapMethods.put(method.getSignature(), method);
            if (methodSuper == null)
                {
                f_types.f_constantPool.registerInvocable(TypeCompositionTemplate.this, method);
                }
            else
                {
                method.setSuper(methodSuper);
                }
            return method;
            }
        }

    public class MultiFunctionTemplate
        {
        Map<String, FunctionTemplate> m_mapFunctions = new HashMap<>();

        public FunctionTemplate add(FunctionTemplate function)
            {
            if (m_mapFunctions.put(function.getSignature(), function) == null)
                {
                f_types.f_constantPool.registerInvocable(TypeCompositionTemplate.this, function);
                }
            else
                {
                throw new IllegalStateException("Function already exists: " + function);
                }
            return function;
            }
        }

    public class MethodTemplate
            extends InvocationTemplate
        {
        protected MethodTemplate m_methodSuper;

        protected MethodTemplate(String sName, String[] asArgType, String[] asRetType)
            {
            super(sName, asArgType, asRetType);
            }

        protected MethodTemplate(String sName, TypeName[] atArg, TypeName[] atRet)
            {
            super(sName, atArg, atRet);
            }

        public void setSuper(MethodTemplate methodSuper)
            {
            m_methodSuper = methodSuper;
            }

        public MethodTemplate getSuper()
            {
            if (m_methodSuper != null)
                {
                return m_methodSuper;
                }
            throw new IllegalStateException(TypeCompositionTemplate.this + " - no super for method: \"" + getSignature());
            }
        }

    public class FunctionTemplate
            extends InvocationTemplate
        {
        protected FunctionTemplate(String sName, String[] asArgType, String[] asRetType)
            {
            super(sName, asArgType, asRetType);
            }
        protected FunctionTemplate(String sName, TypeName[] atArg, TypeName[] atRet)
            {
            super(sName, atArg, atRet);
            }
        }

    public static enum Shape {Class, Interface, Trait, Mixin, Const, Service, Enum}
    public static enum Access {Public, Protected, Private, Struct}

    public static String[] VOID = new String[0];
    public static String[] BOOLEAN = new String[]{"x:Boolean"};
    public static String[] INT = new String[]{"x:Int64"};
    public static String[] STRING = new String[]{"x:String"};
    public static String[] THIS = new String[]{"this.Type"};
    public static String[] CONDITIONAL_THIS = new String[]{"x:ConditionalTuple<this.Type>"};
    }
