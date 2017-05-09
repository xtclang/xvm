package org.xvm.proto;

import org.xvm.asm.Constant;
import org.xvm.asm.Constants;
import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.proto.ObjectHandle.ArrayHandle;
import org.xvm.proto.ObjectHandle.GenericHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

import org.xvm.proto.template.xArray;
import org.xvm.proto.template.xException;
import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xObject;
import org.xvm.proto.template.xRef.RefHandle;

import org.xvm.util.ListMap;

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
    public final Shape f_shape;

    public final TypeComposition f_clazzCanonical; // public non-parameterized

    public TypeCompositionTemplate m_templateSuper;
    protected boolean m_fResolved;

    protected List<TypeName> m_listImplement = new LinkedList<>(); // used as "extends " for interfaces
    protected List<String> m_listIncorporate = new LinkedList<>();

    // manual ordering of properties is kind of useful in the output
    protected Map<String, PropertyTemplate> m_mapProperties = new ListMap<>();
    protected Map<String, MultiMethodTemplate> m_mapMultiMethods = new TreeMap<>();

    protected Map<String, MultiFunctionTemplate> m_mapMultiFunctions = new TreeMap<>(); // class level child functions

    // ----- caches ------

    // cache of TypeCompositions
    protected Map<List<Type>, TypeComposition> m_mapCompositions = new HashMap<>();

    // cache of relationships
    protected enum Relation {EXTENDS, IMPLEMENTS, INCOMPATIBLE}
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
            int cTypes = asFormalType.length;
            for (int i = 0; i < cTypes; i++)
                {
                String sFormalType = asFormalType[i];
                assert (!types.existsTemplate(sFormalType)); // must not be know
                }
            f_sName = sName.substring(0, ofBracket);
            f_asFormalType = asFormalType;
            f_clazzCanonical = new TypeComposition(this, xObject.getTypeArray(cTypes));
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
        propThis.f_typeName.resolveDependencies(this);

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
            f_types.f_adapter.registerProperty(this, propThis);
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

        return mmt == null ? null : mmt.m_mapMethods.get(sSig);
        }

    public MethodTemplate getMethodTemplate(MethodConstant constMethod)
        {
        MultiMethodTemplate mmt = m_mapMultiMethods.get(constMethod.getName());

        // TODO: when MethodConstant is done
        return mmt == null ? null : mmt.m_mapMethods.values().iterator().next();
        }

    public void forEachMethod(Consumer<MethodTemplate> consumer)
        {
        for (MultiMethodTemplate mmt : m_mapMultiMethods.values())
            {
            mmt.m_mapMethods.values().forEach(consumer::accept);
            }
        }

    // add a default constructor
    public FunctionTemplate ensureDefaultConstructTemplate()
        {
        FunctionTemplate templateD = new FunctionTemplate("default", THIS, VOID);

        m_mapMultiFunctions.computeIfAbsent(templateD.f_sName, s -> new MultiFunctionTemplate()).
                add(templateD);

        templateD.resolveTypes(this);

        return templateD;
        }

    // add a constructor
    public ConstructTemplate ensureConstructTemplate(String[] asArgTypes)
        {
        ConstructTemplate templateC = new ConstructTemplate(asArgTypes);

        m_mapMultiFunctions.computeIfAbsent(templateC.f_sName, s -> new MultiFunctionTemplate()).
                add(templateC);

        templateC.resolveTypes(this);

        return templateC;
        }

    // add a function
    public FunctionTemplate ensureFunctionTemplate(String sFunctionName, String[] asArgTypes, String[] asRetTypes)
        {
        FunctionTemplate templateF = new FunctionTemplate(sFunctionName, asArgTypes, asRetTypes);

        m_mapMultiFunctions.computeIfAbsent(sFunctionName, s -> new MultiFunctionTemplate()).
                add(templateF);

        templateF.resolveTypes(this);

        return templateF;
        }

    public FunctionTemplate ensureFunctionTemplate(FunctionTemplate templateF)
        {
        m_mapMultiFunctions.computeIfAbsent(templateF.f_sName, s -> new MultiFunctionTemplate()).
                add(templateF);
        return templateF;
        }

    public FunctionTemplate getDefaultConstructTemplate()
        {
        return getFunctionTemplate("default", THIS, VOID);
        }

    public FunctionTemplate getFunctionTemplate(String sFunctionName, String[] asArgTypes, String[] asRetTypes)
        {
        return getFunctionTemplate(sFunctionName,
                TypeName.getFunctionSignature(sFunctionName, asArgTypes, asRetTypes));
        }

    protected FunctionTemplate getFunctionTemplate(String sFunctionName, String sSig)
        {
        MultiFunctionTemplate mft = m_mapMultiFunctions.get(sFunctionName);

        return mft == null ? null : mft.m_mapFunctions.get(sSig);
        }

    public FunctionTemplate getFunctionTemplate(MethodConstant constMethod)
        {
        MultiFunctionTemplate mft = m_mapMultiFunctions.get(constMethod.getName());

        // TODO: when MethodConstant is done
        return mft == null ? null : mft.m_mapFunctions.values().iterator().next();
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
            if (propSuper.m_accessGet != Constants.Access.PRIVATE || propSuper.m_accessSet != Constants.Access.PRIVATE)
                {
                derivePropertyTemplateFrom(propSuper);
                }
            });

            m_templateSuper.forEachMethod(methodSuper ->
            {
            if (methodSuper.m_access != Constants.Access.PRIVATE)
                {
                deriveMethodTemplateFrom(methodSuper);
                }
            });
            }
        }


    // produce a TypeComposition based on the specified ClassTypeConstant
    public TypeComposition resolve(ClassTypeConstant constClass)
        {
        List<TypeConstant> listParams = constClass.getTypeConstants();
        int cParams = listParams.size();
        if (cParams == 0)
            {
            return resolve(Utils.TYPE_NONE);
            }

        TypeComposition[] aClz = new TypeComposition[cParams];
        int iParam = 0;
        for (TypeConstant constParamType : listParams)
            {
            if (constParamType instanceof ClassTypeConstant)
                {
                ClassTypeConstant constParamClass = (ClassTypeConstant) constParamType;

                String sSimpleName = ConstantPoolAdapter.getClassName(constParamClass);
                TypeCompositionTemplate templateParam = f_types.ensureTemplate(sSimpleName);
                aClz[iParam] = templateParam.resolve(constParamClass);
                }
            else
                {
                throw new IllegalArgumentException("Invalid param type constant: " + constParamType);
                }
            }
        return resolve(aClz);
        }

    // produce a TypeComposition for this template by resolving the generic types
    public TypeComposition resolve(TypeComposition[] aclzGenericActual)
        {
        int    c = aclzGenericActual.length;
        Type[] aType = new Type[c];
        for (int i = 0; i < c; i++)
            {
            aType[i] = aclzGenericActual[i].ensurePublicType();
            }
        return resolve(aType);
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

    public Type createType(Type[] atGenericActual, Constant.Access access)
        {
        Type type = new Type(f_sName);
        // TODO create the specified type

        f_types.addType(type);
        return type;
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

    public boolean isService()
        {
        return f_shape == Shape.Service;
        }

    public boolean isSingleton()
        {
        // TODO: add static classes
        return f_shape == Shape.Enum;
        }

    @Override
    public String toString()
        {
        return f_shape + " " + f_sName + Utils.formatArray(f_asFormalType, "<", ">", ", ");
        }

    // ---- OpCode support: construction and initialization -----

    // create an un-initialized handle (Int i;)
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new ObjectHandle(clazz);
        }

    // assign (Int i = 5;)
    // @return null if this type doesn't take that constant
    public ObjectHandle createConstHandle(Constant constant)
        {
        return null;
        }

    // return a handle with this:struct access
    protected ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        assert f_asFormalType.length == 0;
        assert f_shape == Shape.Class || f_shape == Shape.Const;

        return new GenericHandle(clazz, clazz.ensureStructType());
        }

    // call the default constructors, then the specified constructor,
    // then finalizers; change this:struct handle to this:public
    public ExceptionHandle construct(Frame frame, ConstructTemplate constructor,
                                     TypeComposition clazz, ObjectHandle[] ahVar, int iReturn)
        {
        ahVar[0] = createStruct(frame, clazz); // this:struct

        ExceptionHandle hException = clazz.callDefaultConstructors(frame, ahVar);

        if (hException == null)
            {
            hException = frame.call1(constructor, null, ahVar, Frame.R_UNUSED);

            if (hException == null)
                {
                xFunction.FullyBoundHandle fnFinalize = xFunction.FullyBoundHandle.resolveFinalizer(
                        frame.m_hfnFinally, constructor.makeFinalizer(ahVar));
                if (fnFinalize != null)
                    {
                    // this:struct -> this:private
                    hException = fnFinalize.callChain(frame, Constants.Access.PRIVATE);
                    }

                if (hException == null)
                    {
                    ObjectHandle hNew = ahVar[0];
                    hException = frame.assignValue(iReturn,
                            hNew.f_clazz.ensureAccess(hNew, Constants.Access.PUBLIC));
                    }
                }
            }

        return hException;
        }

    // ----- OpCode support: register operations ------

    // invokeNative with one argument and zero or one return value
    // place the result into the specified frame register
    public ExceptionHandle invokeNative(Frame frame, ObjectHandle hTarget,
                                        MethodTemplate method, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Unknown method: " + f_sName + "." + method);
        }

    // invokeNative with N arguments and zero or one return values
    public ExceptionHandle invokeNative(Frame frame, ObjectHandle hTarget,
                                        MethodTemplate method, ObjectHandle[] ahArg, int iReturn)
        {
        // many classes don't have native methods
        throw new IllegalStateException("Unknown method: " + f_sName + "." + method);
        }

    // Add operation; place the result into the specified frame register
    public ExceptionHandle invokeAdd(Frame frame, ObjectHandle hTarget,
                                     ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);

        }

    // Neg operation
    public ExceptionHandle invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // ---- OpCode support: register or property operations -----

    // helper method
    private ObjectHandle extractPropertyValue(GenericHandle hTarget, PropertyTemplate property)
        {
        if (property == null)
            {
            throw new IllegalStateException("Invalid op for " + f_sName);
            }

        ObjectHandle hProp = hTarget.m_mapFields.get(property.f_sName);

        if (hProp == null)
            {
            throw new IllegalStateException((hTarget.m_mapFields.containsKey(property.f_sName) ?
                    "Un-initialized property " : "Invalid property ") + property);
            }
        return hProp;
        }

    // Increment and place the result into the specified frame register
    public ExceptionHandle invokePreInc(Frame frame, ObjectHandle hTarget,
                                        PropertyTemplate property, int iReturn)
        {
        GenericHandle hThis = (GenericHandle) hTarget;

        ObjectHandle hProp = extractPropertyValue(hThis, property);

        if (property.isRef())
            {
            return property.getRefTemplate().invokePreInc(frame, hProp, null, iReturn);
            }

        ExceptionHandle hException = hProp.f_clazz.f_template.invokePreInc(frame, hProp, null, Frame.R_FRAME);
        if (hException != null)
            {
            return hException;
            }

        ObjectHandle hPropNew = frame.getFrameLocal();
        hThis.m_mapFields.put(property.f_sName, hPropNew);
        return frame.assignValue(iReturn, hPropNew);
        }

    // PostIncrement operation; place the result into the specified frame register
    public ExceptionHandle invokePostInc(Frame frame, ObjectHandle hTarget,
                                         PropertyTemplate property, int iReturn)
        {
        GenericHandle hThis = (GenericHandle) hTarget;

        ObjectHandle hProp = extractPropertyValue(hThis, property);

        if (property.isRef())
            {
            return property.getRefTemplate().invokePostInc(frame, hProp, null, iReturn);
            }

        ExceptionHandle hException = hProp.f_clazz.f_template.invokePostInc(frame, hProp, null, Frame.R_FRAME);
        if (hException != null)
            {
            return hException;
            }

        ObjectHandle hPropNew = frame.getFrameLocal();
        hThis.m_mapFields.put(property.f_sName, hPropNew);
        return frame.assignValue(iReturn, hProp);
        }

    // ----- OpCode support: property operations -----

    // get a property value into the specified place in the array
    public ExceptionHandle getPropertyValue(Frame frame, ObjectHandle hTarget,
                                            PropertyTemplate property, int iReturn)
        {
        if (property == null)
            {
            throw new IllegalStateException(f_sName);
            }

        MethodTemplate method = hTarget.isStruct() ? null : property.m_templateGet;

        if (method == null)
            {
            return getFieldValue(frame, hTarget, property, iReturn);
            }

        if (method.isNative())
            {
            return invokeNative(frame, hTarget, method, Utils.OBJECTS_NONE, iReturn);
            }

        ObjectHandle[] ahVar = new ObjectHandle[method.m_cVars];

        return frame.call1(method, hTarget, ahVar, iReturn);
        }

    public ExceptionHandle getFieldValue(Frame frame, ObjectHandle hTarget,
                                         PropertyTemplate property, int iReturn)
        {
        if (property == null)
            {
            throw new IllegalStateException(f_sName);
            }

        GenericHandle hThis = (GenericHandle) hTarget;

        ObjectHandle hProp = hThis.m_mapFields.get(property.f_sName);

        if (hProp == null)
            {
            throw new IllegalStateException((hThis.m_mapFields.containsKey(property.f_sName) ?
                    "Un-initialized property " : "Invalid property ") + property);
            }

        if (property.isRef())
            {
            try
                {
                hProp = ((RefHandle) hProp).get();
                }
            catch (ExceptionHandle.WrapperException e)
                {
                return e.getExceptionHandle();
                }
            }

        return frame.assignValue(iReturn, hProp);
        }

    // set a property value
    public ExceptionHandle setPropertyValue(Frame frame, ObjectHandle hTarget,
                                            PropertyTemplate property, ObjectHandle hValue)
        {
        if (property == null)
            {
            throw new IllegalStateException(f_sName);
            }

        // TODO: check the access rights

        MethodTemplate method = hTarget.isStruct() ? null : property.m_templateSet;

        if (method == null)
            {
            return setFieldValue(hTarget, property, hValue);
            }

        if (method.isNative())
            {
            return invokeNative(frame, hTarget, method, hValue, Frame.R_UNUSED);
            }

        ObjectHandle[] ahVar = new ObjectHandle[method.m_cVars];
        ahVar[1] = hValue;

        return frame.call1(method, hTarget, ahVar, Frame.R_UNUSED);
        }

    public ExceptionHandle setFieldValue(ObjectHandle hTarget,
                                         PropertyTemplate property, ObjectHandle hValue)
        {
        if (property == null)
            {
            throw new IllegalStateException(f_sName);
            }

        GenericHandle hThis = (GenericHandle) hTarget;

        assert hThis.m_mapFields.containsKey(property.f_sName);

        if (property.isRef())
            {
            return ((RefHandle) hThis.m_mapFields.get(property.f_sName)).set(hValue);
            }

        hThis.m_mapFields.put(property.f_sName, hValue);
        return null;
        }

    // ----- Op-code support: array operations -----

    // get a handle to an array for the specified class
    public ExceptionHandle createArrayHandle(Frame frame,
                TypeComposition clazz, long cCapacity, boolean fFixed, int iReturn)
        {
        if (cCapacity < 0 || cCapacity > Integer.MAX_VALUE)
            {
            return xException.makeHandle("Invalid array size: " + cCapacity);
            }

        return frame.assignValue(iReturn, xArray.makeInstance(cCapacity, fFixed));
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

    // ----- inner classes

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

        // indicate that the property is represented by a RefHandle
        private TypeCompositionTemplate m_templateRef;

        // indicates that the property is represented by an AtomicRef
        private boolean m_fAtomic = false;

        public Constant.Access m_accessGet = Constants.Access.PUBLIC;
        public Constant.Access m_accessSet = Constants.Access.PUBLIC;

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
            m_accessSet = null;
            }

        public boolean isReadOnly()
            {
            return m_accessSet == null;
            }

        public void makeAtomic()
            {
            m_fAtomic = true;
            m_templateRef = f_typeName.getSimpleName().equals("x:Int64")
                ? f_types.ensureTemplate("x:AtomicIntNumber")
                : f_types.ensureTemplate("x:AtomicRef");
            }

        public boolean isAtomic()
            {
            return m_fAtomic;
            }

        public void makeRef(String sRefClassName)
            {
            m_templateRef = f_types.ensureTemplate(sRefClassName);
            }

        public boolean isRef()
            {
            return m_templateRef != null;
            }

        public TypeCompositionTemplate getRefTemplate()
            {
            return m_templateRef;
            }

        public RefHandle createRefHandle(Type typeReferent)
            {
            TypeComposition clzReferent = typeReferent == null ?
                m_templateRef.resolve(new Type[] {typeReferent}) :
                m_templateRef.f_clazzCanonical;
            return (RefHandle) m_templateRef.createHandle(clzReferent);
            }

        public void setGetAccess(Constant.Access access)
            {
            m_accessGet = access;
            }

        public void setSetAccess(Constant.Access access)
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
            MethodTemplate method = ensureMethodTemplate(f_sName + '$' + sMethodName, asArgTypes, asRetTypes);
            method.m_property = this;
            return method;
            }

        public void deriveFrom(PropertyTemplate that)
            {
            if (that.m_accessGet != Constants.Access.PRIVATE)
                {
                this.m_accessGet = that.m_accessGet;

                // check for the "super" implementations
                if (that.m_templateGet != null)
                    {
                    this.m_templateGet = deriveMethodTemplateFrom(that.m_templateGet);
                    }
                }

            if (that.m_accessSet != Constants.Access.PRIVATE)
                {
                this.m_accessSet = that.m_accessSet;

                // check for the "super" implementations
                if (that.m_templateSet != null)
                    {
                    this.m_templateSet = deriveMethodTemplateFrom(that.m_templateSet);
                    }
                }
            }

        // get the property type in the context of the specified parent class
        public Type getType(TypeComposition clzParent)
            {
            return f_typeName.resolveFormalTypes(clzParent);
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();
            if (isRef())
                {
                sb.append('@').append(m_templateRef.f_sName).append(' ');
                }
            if (isReadOnly())
                {
                sb.append("@ro ").append(m_accessGet.name().toLowerCase());
                }
            else if (m_accessGet == m_accessSet)
                {
                sb.append(m_accessGet.name().toLowerCase());
                }
            else
                {
                sb.append(m_accessGet.name().toLowerCase()).append('/')
                  .append(m_accessSet.name().toLowerCase());
                }
            sb.append(' ').append(f_typeName).append(' ').append(f_sName);
            return sb.toString();
            }
        }

    public abstract class InvocationTemplate
            extends FunctionContainer
        {
        public final String f_sName;

        public TypeName[] m_argTypeName; // length = 0 for zero args
        public TypeName[] m_retTypeName; // length = 0 for Void return type

        // TODO: pointer to what XVM Structure?
        Constant.Access m_access = Constants.Access.PUBLIC;
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
            m_cVars = m_cArgs = atArg.length;

            m_retTypeName = atRet;
            m_cReturns = atRet.length;
            }

        protected void resolveTypes(TypeCompositionTemplate template)
            {
            for (TypeName t : m_argTypeName)
                {
                t.resolveDependencies(template);
                }

            for (TypeName t : m_retTypeName)
                {
                t.resolveDependencies(template);
                }
            }

        // get the type of the returned value in the context of the specified parent class
        public Type getReturnType(int iRet, TypeComposition clzParent)
            {
            return m_retTypeName[iRet].resolveFormalTypes(clzParent);
            }

        public void setAccess(Constant.Access access)
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

        public String getSignature()
            {
            return TypeName.getFunctionSignature(f_sName, m_argTypeName, m_retTypeName);
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();
            if (isNative())
                {
                sb.append("native ");
                }
            sb.append(m_access.name().toLowerCase()).append(' ').append(getSignature());
            return sb.toString();
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
                f_types.f_adapter.registerInvocable(TypeCompositionTemplate.this, method);
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
                f_types.f_adapter.registerInvocable(TypeCompositionTemplate.this, function);
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
        protected PropertyTemplate m_property; // indicates that this method is property accessor

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
            if (m_methodSuper == null)
                {
                if (m_property == null)
                    {
                    throw new IllegalStateException(
                            TypeCompositionTemplate.this + " - no super for method: \"" + getSignature());
                    }
                else
                    {
                    return new PropertyAccessTemplate(m_property, true);
                    }
                }

            return m_methodSuper;
            }
        }

    public class PropertyAccessTemplate
            extends MethodTemplate
        {
        public final PropertyTemplate f_property;
        protected PropertyAccessTemplate(PropertyTemplate property, boolean fGetter)
            {
            super(property.f_sName,
                    fGetter ? Utils.TYPE_NAME_NONE : new TypeName[] {property.f_typeName},
                    fGetter ? new TypeName[] {property.f_typeName} : Utils.TYPE_NAME_NONE
                    );
            f_property = property;
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

    // constructor function specialization
    public class ConstructTemplate
            extends FunctionTemplate
        {
        private FunctionTemplate m_ftFinally;

        protected ConstructTemplate(String[] asArgType)
            {
            super("construct", asArgType, VOID);
            }

        public void setFinally(FunctionTemplate ftFinally)
            {
            m_ftFinally = ftFinally;
            }

        public FunctionTemplate getFinally()
            {
            return m_ftFinally;
            }

        public xFunction.FullyBoundHandle makeFinalizer(ObjectHandle[] ahArg)
            {
            return m_ftFinally == null ? null : xFunction.makeHandle(m_ftFinally).bindAll(ahArg);
            }

        public int getVarCount()
            {
            return m_ftFinally == null ? m_cVars : Math.max(m_cVars, m_ftFinally.m_cVars);
            }
        }

    public enum Shape {Class, Interface, Trait, Mixin, Const, Service, Enum}

    public static String[] VOID = new String[0];
    public static String[] BOOLEAN = new String[]{"x:Boolean"};
    public static String[] INT = new String[]{"x:Int64"};
    public static String[] STRING = new String[]{"x:String"};
    public static String[] THIS = new String[]{"this.Type"};
    public static String[] CONDITIONAL_THIS = new String[]{"x:ConditionalTuple<this.Type>"};
    }
