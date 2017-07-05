package org.xvm.proto;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.IdentityConstant;

import org.xvm.proto.ObjectHandle.GenericHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;

import org.xvm.proto.template.xArray;
import org.xvm.proto.template.xException;
import org.xvm.proto.template.xFunction.FullyBoundHandle;
import org.xvm.proto.template.xObject;
import org.xvm.proto.template.xRef.RefHandle;
import org.xvm.proto.template.xType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        int cParams = structClass.getTypeParamsAsList().size();
        f_clazzCanonical = new TypeComposition(this, xObject.getTypeArray(cParams));

        f_structSuper = f_types.f_adapter.getSuper(structClass);
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
            structSuper = f_types.f_adapter.getSuper(structSuper);
            }

        m_mapRelations.put(structThat, Relation.INCOMPATIBLE);
        return false;
        }

    public ClassTemplate getSuper()
        {
        return f_structSuper == null ? null :
                f_types.getTemplate((IdentityConstant) f_structSuper.getIdentityConstant());
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

            // TODO: pick the correct one based on the type
            if (!list.isEmpty())
                {
                return (MethodStructure) list.get(0);
                }
            }
        return null;
        }

    // produce a TypeComposition for this template by resolving the generic type compositions
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

    @Override
    public String toString()
        {
        return f_struct.toString();
        }

    // ---- OpCode support: construction and initialization -----

    // create a RefHandle for the specified class
    public RefHandle createRefHandle(TypeComposition clazz)
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
        ahVar[0] = createStruct(frame, clazz); // this:struct

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
            ObjectHandle hNew = ahVar[0];
            frame.assignValue(iReturn,
                    hNew.f_clazz.ensureAccess(hNew, Constants.Access.PUBLIC));
            return null;
            };

        Frame frameRC = frame.f_context.createFrame1(frame, constructor, null, ahVar, Frame.RET_UNUSED);

        Frame frameDC = clazz.callDefaultConstructors(frame, ahVar, () -> frameRC);

        // we need a non-null anchor (see Frame#chainFinalizer)
        FullyBoundHandle hF1 = f_types.f_adapter.makeFinalizer(constructor, ahVar);
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

    // ----- OpCode support ------

    // invokeNative with exactly one argument and zero or one return value
    // place the result into the specified frame register
    // return one of the Op.R_ values
    public int invokeNative(Frame frame, ObjectHandle hTarget,
                            MethodStructure method, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Unknown method: (" + f_sName + ")." + method);
        }

    // invokeNative with zero or more than one arguments and zero or one return values
    // return one of the Op.R_ values
    public int invokeNative(Frame frame, ObjectHandle hTarget,
                            MethodStructure method, ObjectHandle[] ahArg, int iReturn)
        {
        // many classes don't have native methods
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
                    "Un-initialized property " : "Invalid property ") + property);
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

        if (Adapter.isRef(property))
            {
            return Adapter.getRefTemplate(f_types, property).invokePreInc(frame, hProp, null, iReturn);
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

        if (Adapter.isRef(property))
            {
            return Adapter.getRefTemplate(f_types, property).invokePostInc(frame, hProp, null, iReturn);
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
            return invokeNative(frame, hTarget, method, Utils.OBJECTS_NONE, iReturn);
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

        if (Adapter.isGenericType(property))
            {
            Type type = hThis.f_clazz.resolveFormalType(sName);

            return frame.assignValue(iReturn, xType.makeHandle(type));
            }

        ObjectHandle hValue = hThis.m_mapFields.get(sName);

        if (hValue == null)
            {
            String sErr;
            if (Adapter.isInjectable(property))
                {
                hValue = frame.f_context.f_container.getInjectable(hThis.f_clazz, property);
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
                        "Un-initialized property " : "Invalid property ";
                }

            frame.m_hException = xException.makeHandle(sErr + property.getName());
            return Op.R_EXCEPTION;
            }

        if (Adapter.isRef(property))
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
        else if (Adapter.isReadOnly(property))
            {
            hException = xException.makeHandle("Read-only property: " + property.getName());
            }

        if (hException == null)
            {
            MethodStructure method = hTarget.isStruct() ? null : Adapter.getGetter(property);

            if (method == null)
                {
                hException = setFieldValue(hTarget, property, hValue);
                }
            else
                {
                if (f_types.f_adapter.isNative(method))
                    {
                    return invokeNative(frame, hTarget, method, hValue, Frame.RET_UNUSED);
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

        if (Adapter.isRef(property))
            {
            return ((RefHandle) hThis.m_mapFields.get(property.getName())).set(hValue);
            }

        hThis.m_mapFields.put(property.getName(), hValue);
        return null;
        }

    // compare two object handles for equality
    public boolean callEquals(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        if (f_struct.getFormat() == Component.Format.ENUMVALUE)
            {
            return hValue1 == hValue2;
            }

        if (hValue1.f_clazz != hValue2.f_clazz)
            {
            return false;
            }

        Map<String, ObjectHandle> map1 = ((GenericHandle) hValue1).m_mapFields;
        Map<String, ObjectHandle> map2 = ((GenericHandle) hValue2).m_mapFields;

        for (String sField : map1.keySet())
            {
            ObjectHandle h1 = map1.get(sField);
            ObjectHandle h2 = map2.get(sField);

            PropertyStructure property = getProperty(sField);
            IdentityConstant constClass = (IdentityConstant) property.getParent().getIdentityConstant();
            ClassTemplate template = f_types.getTemplate(constClass);

            if (!template.callEquals(h1, h2))
                {
                return false;
                }
            }
        return true;
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

    // =========== TEMPORARY ========

    public void markNativeMethod(String sName, String[] asParamType)
        {
        markNativeMethod(sName, asParamType, VOID);
        }

    public void markNativeMethod(String sName, String[] asParamType, String[] asRetType)
        {
        getMethodTemplate(sName, asParamType, asRetType).m_fNative = true;
        }

    public MethodTemplate getMethodTemplate(String sName, String[] asParam)
        {
        return getMethodTemplate(sName, asParam, VOID);
        }

    public MethodTemplate getMethodTemplate(IdentityConstant constMethod)
        {
        return m_mapMethods.get(constMethod);
        }

    public MethodTemplate getMethodTemplate(String sName, String[] asParam, String[] asRetType)
        {
        MethodStructure method = Adapter.getMethod(f_struct, sName, asParam, asRetType);

        return m_mapMethods.computeIfAbsent(method.getIdentityConstant(), (id) -> new MethodTemplate(method));
        }

    public void markNativeGetter(String sPropName)
        {
        getGetter(sPropName).m_fNative = true;
        }

    public void markNativeSetter(String sPropName)
        {
        getSetter(sPropName).m_fNative = true;
        }

    public MethodTemplate getGetter(String sPropName)
        {
        PropertyStructure prop = getProperty(sPropName);
        MethodStructure getter = Adapter.getGetter(prop);
        IdentityConstant constId = getter == null ? prop.getIdentityConstant() : getter.getIdentityConstant();
        return m_mapMethods.computeIfAbsent(constId, (id) -> new MethodTemplate(getter));
        }

    public MethodTemplate getSetter(String sPropName)
        {
        PropertyStructure prop = getProperty(sPropName);
        MethodStructure setter = Adapter.getSetter(prop);
        IdentityConstant constId = setter == null ? prop.getIdentityConstant() : setter.getIdentityConstant();

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

    private Map<IdentityConstant, MethodTemplate> m_mapMethods = new HashMap<>();

    public static String[] VOID = new String[0];
    public static String[] INT = new String[]{"Int64"};
    public static String[] STRING = new String[]{"String"};
    }
