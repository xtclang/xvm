package org.xvm.proto;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.CharStringConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.asm.constants.IntersectionTypeConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ParameterTypeConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnionTypeConstant;

import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.ObjectHandle.GenericHandle;

import org.xvm.proto.template.xArray;
import org.xvm.proto.template.xBoolean;
import org.xvm.proto.template.xEnum.EnumHandle;
import org.xvm.proto.template.xException;
import org.xvm.proto.template.xFunction;
import org.xvm.proto.template.xFunction.FullyBoundHandle;
import org.xvm.proto.template.xObject;
import org.xvm.proto.template.xOrdered;
import org.xvm.proto.template.xRef.RefHandle;
import org.xvm.proto.template.xService;
import org.xvm.proto.template.xString;
import org.xvm.proto.template.xType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * ClassTemplate represents a run-time class.
 *
 * @author gg 2017.02.23
 */
public abstract class ClassTemplate
    {
    public final TypeSet f_types;
    public final ClassStructure f_struct;

    public final String f_sName; // globally known ClassTemplate name (e.g. x:Boolean or x:annotation.AtomicRef)

    public final TypeComposition f_clazzCanonical; // public non-parameterized class

    public final ClassStructure f_structSuper;

    // ----- caches ------

    // cache of TypeCompositions
    protected Map<List<Type>, TypeComposition> m_mapCompositions = new HashMap<>();

    // cache of relationships
    protected enum Relation {EXTENDS, IMPLEMENTS, INCOMPATIBLE}
    protected Map<ClassStructure, Relation> m_mapRelations = new HashMap<>();

    // construct the template
    public ClassTemplate(TypeSet types, ClassStructure structClass)
        {
        f_types = types;
        f_struct = structClass;
        f_sName = structClass.getName();

        f_clazzCanonical = createCanonicalClass();
        f_structSuper = getSuperStructure();
        }

    protected TypeComposition createCanonicalClass()
        {
        Map<CharStringConstant, TypeConstant> mapParams = f_struct.getTypeParams();
        Map<String, Type> mapParamsActual;

        if (mapParams.isEmpty())
            {
            mapParamsActual = Collections.EMPTY_MAP;
            }
        else
            {
            mapParamsActual = new HashMap<>(mapParams.size());
            Type typeObject = xObject.INSTANCE.f_clazzCanonical.ensurePublicType();
            for (CharStringConstant constName : mapParams.keySet())
                {
                mapParamsActual.put(constName.getValue(), typeObject);
                }
            }

        return new TypeComposition(this, mapParamsActual);
        }

    // find a super structure for this template
    protected ClassStructure getSuperStructure()
        {
        ClassStructure structure = f_struct;

        Optional<ClassStructure.Contribution> opt = structure.getContributionsAsList().stream().
                filter((c) -> c.getComposition().equals(ClassStructure.Composition.Extends)).findFirst();
        if (opt.isPresent())
            {
            ClassConstant constClass = opt.get().getClassConstant().getClassConstant();
            try
                {
                return (ClassStructure) constClass.getComponent();
                }
            catch (RuntimeException e)
                {
                // TODO: remove when getComponent() is fixed
                String sName = constClass.getName();
                ClassTemplate templateSuper = f_types.getTemplate(sName);
                return templateSuper.f_struct;
                }
            }
        else
            {
            switch (structure.getFormat())
                {
                case ENUMVALUE:
                    return (ClassStructure) structure.getParent();

                case SERVICE:
                    return xService.INSTANCE.f_struct;

                case CLASS:
                    if (structure.getName().equals("Object"))
                        {
                        return null;
                        }
                    // break through
                case ENUM:
                case INTERFACE:
                case CONST:
                    return xObject.INSTANCE.f_struct;
                }
            }
        return null;
        }

    public boolean isRootObject()
        {
        return f_structSuper == null;
        }

    /**
     * Initialize properties, methods and functions declared at the "top" layer.
     *
     * TODO: remove
     */
    public void initDeclared()
        {
        markNativeMethod("to", VOID, STRING);
        }

    // does this template extend that?
    public boolean extends_(ClassTemplate that)
        {
        if (this == that)
            {
            return true;
            }

        ClassStructure structThat = that.f_struct;

        Relation relation = m_mapRelations.get(structThat);
        if (relation != null)
            {
            return relation == Relation.EXTENDS;
            }

        ClassStructure structSuper = f_structSuper;
        while (structSuper != null)
            {
            m_mapRelations.put(structSuper, Relation.EXTENDS);

            // there is just one template instance per name
            if (structSuper == structThat)
                {
                return true;
                }
            structSuper = getSuper().f_struct;
            }

        m_mapRelations.put(structThat, Relation.INCOMPATIBLE);
        return false;
        }

    public ClassTemplate getSuper()
        {
        return f_structSuper == null ? null :
                f_types.getTemplate(f_structSuper.getIdentityConstant());
        }

    public boolean isService()
        {
        return f_struct.getFormat() == Component.Format.SERVICE;
        }

    public boolean isSingleton()
        {
        return f_struct.isStatic();
        }

    public PropertyStructure getProperty(String sName)
        {
        return (PropertyStructure) f_struct.getChild(sName);
        }

    public MethodStructure getMethod(String sName, String[] asArgType, String[] asRetType)
        {
        MultiMethodStructure mms = (MultiMethodStructure) f_struct.getChild(sName);
        if (mms != null)
            {
            List<Component> list = mms.children();

            // TODO: pick the correct one based on the signature
            if (!list.isEmpty())
                {
                return (MethodStructure) list.get(0);
                }
            }
        return null;
        }

    // produce a TypeComposition for this template by resolving the generic types
    public TypeComposition resolve(ClassTypeConstant constClassType, Map<String, Type> mapActual)
        {
        assert constClassType.getClassConstant().getName().equals(f_sName);

        List<TypeConstant> listParams = constClassType.getTypeConstants();

        int cParams = listParams.size();
        if (cParams == 0)
            {
            return f_clazzCanonical;
            }

        List<Map.Entry<CharStringConstant, TypeConstant>> listFormalTypes =
                f_struct.getTypeParamsAsList();

        assert listFormalTypes.size() == listParams.size();

        Map<String, Type> mapParams = new HashMap<>();
        for (int i = 0, c = listParams.size(); i < c; i++)
            {
            Map.Entry<CharStringConstant, TypeConstant> entryFormal = listFormalTypes.get(i);

            mapParams.put(entryFormal.getKey().getValue(),
                    resolveParameterType(listParams.get(i), mapActual));
            }
        return ensureClass(mapParams);
        }

    // resolve a parameter type
    protected Type resolveParameterType(TypeConstant constParamType, Map<String, Type> mapActual)
        {
        if (constParamType instanceof ClassTypeConstant)
            {
            ClassTypeConstant constClass = (ClassTypeConstant) constParamType;
            ClassTemplate template = f_types.getTemplate(constClass.getClassConstant());
            return template.resolve(constClass, mapActual).ensurePublicType();
            }

        if (constParamType instanceof ParameterTypeConstant)
            {
            ParameterTypeConstant constParam = (ParameterTypeConstant) constParamType;
            Type type = mapActual.get(constParam.getName());

            if (type == null)
                {
                throw new IllegalArgumentException("Unresolved type parameter: " + constParam);
                }
            return type;
            }

        if (constParamType instanceof IntersectionTypeConstant ||
                constParamType instanceof UnionTypeConstant)
            {
            throw new UnsupportedOperationException("TODO");
            }

        throw new IllegalArgumentException("Unresolved type constant: " + constParamType);
        }

    // produce a TypeComposition for this template using the actual types for formal parameters
    public TypeComposition ensureClass(Map<String, Type> mapParams)
        {
        // sort the parameters by name and use the list of sorted (by formal name) types as a key
        Map<String, Type> mapSorted = mapParams.size() > 1 ?
                new TreeMap<>(mapParams) : mapParams;
        List<Type> key = new ArrayList<>(mapSorted.values());

        return m_mapCompositions.computeIfAbsent(key,
                (x) -> new TypeComposition(this, mapParams));
        }

    @Override
    public String toString()
        {
        return f_struct.toString();
        }

    // ---- OpCode support: construction and initialization -----

    // create a RefHandle for the specified class
    // sName is an optional ref name
    // TODO: consider moving this method up to xRef
    public RefHandle createRefHandle(TypeComposition clazz, String sName)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // assign (Int i = 5;)
    // @return an immutable handle or null if this type doesn't take that constant
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        return null;
        }

    // return a handle with this:struct access
    protected ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        assert f_struct.getTypeParamsAsList().isEmpty();
        assert f_struct.getFormat() == Component.Format.CLASS ||
               f_struct.getFormat() == Component.Format.CONST;

        return new GenericHandle(clazz, clazz.ensureStructType());
        }

    // invoke the default constructors, then the specified constructor,
    // then finalizers; change this:struct handle to this:public
    // return one of the Op.R_ values
    public int construct(Frame frame, MethodStructure constructor,
                         TypeComposition clazz, ObjectHandle[] ahVar, int iReturn)
        {
        ObjectHandle hStruct = createStruct(frame, clazz); // this:struct

        // assume that we have C1 extending C0 with default constructors (DC),
        // regular constructors (RC), and finalizers (F);
        // the call sequence should be:
        //
        //  ("new" op-code) => DC0 -> DC1 -> C1 => C0 -> F0 -> F1 -> "assign" (continuation)
        //
        // -> indicates a call via continuation
        // => indicates a call via Construct op-cod
        //
        // we need to create the call chain in the revers order
        // (note that the C0 and F0 are added by the Construct op-code)
        //
        // the very last frame should also assign the resulting new object

        Supplier<Frame> contAssign = () ->
            {
            frame.assignValue(iReturn,
                    hStruct.f_clazz.ensureAccess(hStruct, Constants.Access.PUBLIC));
            return null;
            };

        Frame frameRC = frame.f_context.createFrame1(frame, constructor, hStruct, ahVar, Frame.RET_UNUSED);

        Frame frameDC = clazz.callDefaultConstructors(frame, hStruct, ahVar, () -> frameRC);

        // we need a non-null anchor (see Frame#chainFinalizer)
        FullyBoundHandle hF1 = makeFinalizer(constructor, hStruct, ahVar);
        frameRC.m_hfnFinally = hF1 == null ? FullyBoundHandle.NO_OP : hF1;

        frameRC.m_continuation = () ->
            {
            // this:struct -> this:private
            FullyBoundHandle hF = frameRC.m_hfnFinally;
            return hF == FullyBoundHandle.NO_OP ?
                    contAssign == null ? null : contAssign.get() :
                    hF.callChain(frame, Constants.Access.PRIVATE, contAssign);
            };

        frame.m_frameNext = frameDC == null ? frameRC : frameDC;
        return Op.R_CALL;
        }

    public FullyBoundHandle makeFinalizer(MethodStructure constructor,
                                          ObjectHandle hStruct, ObjectHandle[] ahArg)
        {
        MethodStructure methodFinally = f_types.f_adapter.getFinalizer(constructor);

        return methodFinally == null ? null :
                xFunction.makeHandle(methodFinally).bindAll(hStruct, ahArg);
        }

    // ----- OpCode support ------

    // invokeNative with exactly one argument and zero or one return value
    // place the result into the specified frame register
    // return one of the Op.R_ values
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Unknown method: (" + f_sName + ")." + method);
        }

    // invokeNative with zero or more than one arguments and zero or one return values
    // return one of the Op.R_ values
    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (ahArg.length)
            {
            case 0:
                if (method.getName().equals("to"))
                    {
                    // TODO: introduce a "callToString" method to be overridden when appropriate
                    // how to differentiate; check the method's return type?
                    return frame.assignValue(iReturn, xString.makeHandle(hTarget.toString()));
                    }
            }

        throw new IllegalStateException("Unknown method: (" + f_sName + ")." + method);
        }

    // invokeNative with zero or more arguments and more than one return values
    // return one of the Op.R_ values
    public int invokeNativeNN(Frame frame, MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        throw new IllegalStateException("Unknown method: (" + f_sName + ")." + method);
        }

    // Add operation; place the result into the specified frame register
    // return one of the Op.R_ values
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // Neg operation
    // return one of the Op.R_ values
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // ---- OpCode support: register or property operations -----

    // helper method
    private ObjectHandle extractPropertyValue(GenericHandle hTarget, PropertyStructure property)
        {
        if (property == null)
            {
            throw new IllegalStateException("Invalid op for " + f_sName);
            }

        ObjectHandle hProp = hTarget.m_mapFields.get(property.getName());

        if (hProp == null)
            {
            throw new IllegalStateException((hTarget.m_mapFields.containsKey(property.getName()) ?
                    "Un-initialized property \"" : "Invalid property \"") + property + '"');
            }
        return hProp;
        }

    // increment the property value and place the result into the specified frame register
    // return either R_NEXT or R_EXCEPTION
    public int invokePreInc(Frame frame, ObjectHandle hTarget,
                            PropertyStructure property, int iReturn)
        {
        GenericHandle hThis = (GenericHandle) hTarget;

        ObjectHandle hProp = extractPropertyValue(hThis, property);

        if (isRef(property))
            {
            return getRefTemplate(f_types, property).invokePreInc(frame, hProp, null, iReturn);
            }

        int nResult = hProp.f_clazz.f_template.invokePreInc(frame, hProp, null, Frame.RET_LOCAL);
        if (nResult == Op.R_EXCEPTION)
            {
            return nResult;
            }

        ObjectHandle hPropNew = frame.getFrameLocal();
        hThis.m_mapFields.put(property.getName(), hPropNew);

        return frame.assignValue(iReturn, hPropNew);
        }

    // place the property value into the specified frame register and increment it
    // return either R_NEXT or R_EXCEPTION
    public int invokePostInc(Frame frame, ObjectHandle hTarget,
                             PropertyStructure property, int iReturn)
        {
        GenericHandle hThis = (GenericHandle) hTarget;

        ObjectHandle hProp = extractPropertyValue(hThis, property);

        if (isRef(property))
            {
            return getRefTemplate(f_types, property).invokePostInc(frame, hProp, null, iReturn);
            }

        int nResult = hProp.f_clazz.f_template.invokePostInc(frame, hProp, null, Frame.RET_LOCAL);
        if (nResult == Op.R_EXCEPTION)
            {
            return nResult;
            }

        ObjectHandle hPropNew = frame.getFrameLocal();
        hThis.m_mapFields.put(property.getName(), hPropNew);

        return frame.assignValue(iReturn, hProp);
        }

    // ----- OpCode support: property operations -----

    // get a property value into the specified place in the array

    public int getPropertyValue(Frame frame, ObjectHandle hTarget,
                                PropertyStructure property, int iReturn)
        {
        if (property == null)
            {
            throw new IllegalStateException(f_sName);
            }

        MethodStructure method = hTarget.isStruct() ? null : Adapter.getGetter(property);

        if (method == null)
            {
            return getFieldValue(frame, hTarget, property, iReturn);
            }

        if (frame.f_adapter.isNative(method))
            {
            return invokeNativeN(frame, method, hTarget, Utils.OBJECTS_NONE, iReturn);
            }

        ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(method)];

        return frame.call1(method, hTarget, ahVar, iReturn);
        }

    public int getFieldValue(Frame frame, ObjectHandle hTarget,
                             PropertyStructure property, int iReturn)
        {
        if (property == null)
            {
            throw new IllegalStateException(f_sName);
            }

        GenericHandle hThis = (GenericHandle) hTarget;
        String sName = property.getName();

        if (isGenericType(property))
            {
            Type type = hThis.f_clazz.getActualType(sName);

            return frame.assignValue(iReturn, xType.makeHandle(type));
            }

        ObjectHandle hValue = hThis.m_mapFields.get(sName);

        if (hValue == null)
            {
            String sErr;
            if (isInjectable(property))
                {
                ClassTypeConstant constType = frame.f_adapter.resolveType(property);
                TypeComposition clz = f_types.resolve(constType);

                hValue = frame.f_context.f_container.getInjectable(sName, clz);
                if (hValue != null)
                    {
                    hThis.m_mapFields.put(sName, hValue);
                    return frame.assignValue(iReturn, hValue);
                    }
                sErr = "Unknown injectable property ";
                }
            else
                {
                sErr = hThis.m_mapFields.containsKey(sName) ?
                        "Un-initialized property \"" : "Invalid property \"";
                }

            frame.m_hException = xException.makeHandle(sErr + sName + '"');
            return Op.R_EXCEPTION;
            }

        if (isRef(property))
            {
            try
                {
                hValue = ((RefHandle) hValue).get();
                }
            catch (ExceptionHandle.WrapperException e)
                {
                frame.m_hException = e.getExceptionHandle();
                return Op.R_EXCEPTION;
                }
            }

        return frame.assignValue(iReturn, hValue);
        }

    // set a property value
    public int setPropertyValue(Frame frame, ObjectHandle hTarget,
                                PropertyStructure property, ObjectHandle hValue)
        {
        if (property == null)
            {
            throw new IllegalStateException(f_sName);
            }

        ExceptionHandle hException = null;
        if (!hTarget.isMutable())
            {
            hException = xException.makeHandle("Immutable object: " + hTarget);
            }
        else if (isReadOnly(property))
            {
            hException = xException.makeHandle("Read-only property: " + property.getName());
            }

        if (hException == null)
            {
            MethodStructure method = hTarget.isStruct() ? null : Adapter.getSetter(property);

            if (method == null)
                {
                hException = setFieldValue(hTarget, property, hValue);
                }
            else
                {
                if (f_types.f_adapter.isNative(method))
                    {
                    return invokeNative1(frame, method, hTarget, hValue, Frame.RET_UNUSED);
                    }

                ObjectHandle[] ahVar = new ObjectHandle[frame.f_adapter.getVarCount(method)];
                ahVar[1] = hValue;

                return frame.call1(method, hTarget, ahVar, Frame.RET_UNUSED);
                }
            }

        if (hException != null)
            {
            frame.m_hException = hException;
            return Op.R_EXCEPTION;
            }
        return Op.R_NEXT;
        }

    public ExceptionHandle setFieldValue(ObjectHandle hTarget,
                                         PropertyStructure property, ObjectHandle hValue)
        {
        if (property == null)
            {
            throw new IllegalStateException(f_sName);
            }

        GenericHandle hThis = (GenericHandle) hTarget;

        assert hThis.m_mapFields.containsKey(property.getName());

        if (isRef(property))
            {
            return ((RefHandle) hThis.m_mapFields.get(property.getName())).set(hValue);
            }

        hThis.m_mapFields.put(property.getName(), hValue);
        return null;
        }

    // ----- support for equality and comparison ------

    // compare for equality two object handles that both belong to the specified class
    // return R_NEXT or R_EXCEPTION
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        // if there is an "equals" function, we need to call it
        MethodStructure functionEquals = getMethod("equals", new String[] {f_sName, f_sName}, BOOLEAN);
        if (functionEquals != null && functionEquals.isStatic())
            {
            return frame.call1(functionEquals, null,
                    new ObjectHandle[]{hValue1, hValue2}, iReturn);
            }

        // only Const classes have an automatic implementation;
        // for everyone else it's either a native method or a ref equality
        return frame.assignValue(iReturn, xBoolean.makeHandle(hValue1 == hValue2));
        }

    // compare for order two object handles that both belong to the specified class
    // return R_NEXT or R_EXCEPTION
    public int callCompare(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        // if there is an "compare" function, we need to call it
        MethodStructure functionCompare = getMethod("compare",
                new String[] {f_sName, f_sName}, new String[] {"Ordered"});
        if (functionCompare != null && functionCompare.isStatic())
            {
            return frame.call1(functionCompare, null,
                    new ObjectHandle[]{hValue1, hValue2}, iReturn);
            }

        // only Const and Enum classes have automatic implementations
        switch (f_struct.getFormat())
            {
            case ENUMVALUE:
                EnumHandle hV1 = (EnumHandle) hValue1;
                EnumHandle hV2 = (EnumHandle) hValue2;

                return frame.assignValue(iReturn,
                        xOrdered.makeHandle(hV1.getValue() - hV2.getValue()));

            default:
                throw new IllegalStateException("No implementation for \"compare()\" function");
            }
        }

    // ----- Op-code support: array operations -----

    // get a handle to an array for the specified class
    // returns R_NEXT or R_EXCEPTION
    public int createArrayStruct(Frame frame, TypeComposition clzArray,
                                 long cCapacity, int iReturn)
        {
        if (cCapacity < 0 || cCapacity > Integer.MAX_VALUE)
            {
            frame.m_hException = xException.makeHandle("Invalid array size: " + cCapacity);
            return Op.R_EXCEPTION;
            }

        return frame.assignValue(iReturn, xArray.makeHandle(clzArray, cCapacity));
        }

    // ----- to be replaced when the structure support is added

    protected boolean isInjectable(PropertyStructure property)
        {
        PropertyTemplate template = m_mapProperties.get(property.getName());
        return template != null && template.m_fInjectable;
        }

    protected boolean isAtomic(PropertyStructure property)
        {
        PropertyTemplate template = m_mapProperties.get(property.getName());
        return template != null && template.m_fAtomic;
        }

    protected boolean isReadOnly(PropertyStructure property)
        {
        PropertyTemplate template = m_mapProperties.get(property.getName());
        return template != null && template.m_fReadOnly;
        }

    protected boolean isRef(PropertyStructure property)
        {
        PropertyTemplate template = m_mapProperties.get(property.getName());
        return template != null && template.m_templateRef != null;
        }

    protected ClassTemplate getRefTemplate(TypeSet types, PropertyStructure property)
        {
        PropertyTemplate template = m_mapProperties.get(property.getName());
        return template == null ? null : template.m_templateRef;
        }

    protected boolean isGenericType(PropertyStructure property)
        {
        // TODO:
        return false;
        }

    // =========== TEMPORARY ========

    public void markNativeMethod(String sName, String[] asParamType)
        {
        markNativeMethod(sName, asParamType, VOID);
        }

    public void markNativeMethod(String sName, String[] asParamType, String[] asRetType)
        {
        ensureMethodTemplate(sName, asParamType, asRetType).m_fNative = true;
        }

    public MethodTemplate ensureMethodTemplate(String sName, String[] asParam)
        {
        return ensureMethodTemplate(sName, asParam, VOID);
        }

    public MethodTemplate ensureMethodTemplate(String sName, String[] asParam, String[] asRetType)
        {
        MethodStructure method = getMethod(sName, asParam, asRetType);

        return m_mapMethods.computeIfAbsent(method.getIdentityConstant(), (id) -> new MethodTemplate(method));
        }

    public MethodTemplate getMethodTemplate(MethodConstant constMethod)
        {
        return m_mapMethods.get(constMethod);
        }

    public void markNativeGetter(String sPropName)
        {
        ensureGetter(sPropName).m_fNative = true;
        }

    public void markNativeSetter(String sPropName)
        {
        ensureSetter(sPropName).m_fNative = true;
        }

    public MethodTemplate ensureGetter(String sPropName)
        {
        PropertyStructure prop = getProperty(sPropName);
        MethodStructure getter = Adapter.getGetter(prop);
        MethodConstant constId = getter == null ?
                prop.getConstantPool().ensureMethodConstant(prop.getIdentityConstant(),
                        "get", Constants.Access.PUBLIC,
                        ConstantPool.NO_TYPES, ConstantPool.NO_TYPES) :
                getter.getIdentityConstant();

        return m_mapMethods.computeIfAbsent(constId, (id) -> new MethodTemplate(getter));
        }

    public MethodTemplate ensureSetter(String sPropName)
        {
        PropertyStructure prop = getProperty(sPropName);
        MethodStructure setter = Adapter.getSetter(prop);
        MethodConstant constId = setter == null ?
                prop.getConstantPool().ensureMethodConstant(prop.getIdentityConstant(),
                        "set", Constants.Access.PUBLIC,
                        ConstantPool.NO_TYPES, ConstantPool.NO_TYPES) :
                setter.getIdentityConstant();

        return m_mapMethods.computeIfAbsent(constId, (id) -> new MethodTemplate(setter));
        }

    public static class MethodTemplate
        {
        public final MethodStructure f_struct;
        public boolean m_fNative;
        public Op[] m_aop;
        public int m_cVars;
        public int m_cScopes = 1;
        public MethodTemplate m_mtFinally;

        public MethodTemplate(MethodStructure struct)
            {
            f_struct = struct;
            }
        }

    public PropertyTemplate ensurePropertyTemplate(String sPropName)
        {
        return m_mapProperties.computeIfAbsent(sPropName,
                (sName) -> new PropertyTemplate(this, getProperty(sName)));
        }

    public static class PropertyTemplate
        {
        public final ClassTemplate f_templateClass;
        public final PropertyStructure f_property;
        public boolean m_fAtomic;
        public boolean m_fInjectable;
        public boolean m_fReadOnly;
        public ClassTemplate m_templateRef;

        public PropertyTemplate(ClassTemplate template, PropertyStructure property)
            {
            f_templateClass = template;
            f_property = property;
            }

        public void markAsAtomicRef()
            {
            m_fAtomic = true;

            ClassTypeConstant constType = f_templateClass.f_types.f_adapter.resolveType(f_property);
            if (constType.getClassConstant().getName().equals("Int64"))
                {
                markAsRef("annotations.AtomicIntNumber");
                }
            else
                {
                markAsRef("annotations.AtomicRef");
                }
            }

        public void markAsRef(String sRefClassName)
            {
            m_templateRef = f_templateClass.f_types.getTemplate(sRefClassName);
            }
        }

    private Map<MethodConstant, MethodTemplate> m_mapMethods = new HashMap<>();
    private Map<String, PropertyTemplate> m_mapProperties = new HashMap<>();

    public static String[] VOID = new String[0];
    public static String[] OBJECT = new String[]{"Object"};
    public static String[] INT = new String[]{"Int64"};
    public static String[] BOOLEAN = new String[]{"Boolean"};
    public static String[] STRING = new String[]{"String"};
    }
