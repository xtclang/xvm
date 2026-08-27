package org.xvm.runtime.template.collections;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.reflect.xRef;
import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.collections.arrays.xRTBitDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTBooleanDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTUInt8Delegate;
import org.xvm.runtime.template._native.collections.arrays.xRTCharDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.DelegateHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTViewToBit;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;

import org.xvm.util.Handy;
import org.xvm.util.Lazy;


/**
 * Native generic Array implementation.
 */
public class xArray
        extends ClassTemplate
        implements IndexSupport {

    public static xArray getInstance(Frame frame) {
        return NativeTemplates.get(frame).array();
    }

    public static xArray getInstance(Container container) {
        return NativeTemplates.get(container).array();
    }

    public xArray(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void registerNativeTemplates() {
        if (NativeTemplates.get(this).isArray(this)) {
            registerNativeTemplate(new xBitArray   (f_container, f_struct));
            registerNativeTemplate(new xByteArray  (f_container, f_struct));
            registerNativeTemplate(new xNibbleArray(f_container, f_struct));
        }
    }

    @Override
    public void initNative() {
        ClassStructure clzHashable = f_container.getTemplate("collections.Array.HashableArray").getStructure();
        ((PropertyStructure) clzHashable.getChild("cachedHash")).markNative();

        // mark native properties and methods
        markNativeProperty("delegate");
        markNativeProperty("mutability");

        markNativeMethod("clear",      VOID, THIS);
        markNativeMethod("getElement", INT,  ELEMENT_TYPE);
        markNativeMethod("setElement", null, VOID);
        markNativeMethod("elementAt",  INT,  null);
        markNativeMethod("slice",      null, THIS);
        markNativeMethod("deleteAll",  null, THIS);
        markNativeMethod("indexOf",    new String[]{"collections.List", "numbers.Int64"},
                                       new String[]{"Boolean", "numbers.Int64"});

        ClassTemplate mixinNumber = f_container.getTemplate("collections.arrays.NumberArray");
        mixinNumber.markNativeMethod("asBitArray" , VOID, null);

        invalidateTypeInfo();
    }

    /**
     * @return the Array.Mutability enum template
     */
    public xEnum getMutabilityTemplate() {
        return f_templateMutability.get(this);
    }

    @Override
    public boolean isGenericHandle() {
        return false;
    }

    @Override
    public TypeComposition ensureParameterizedClass(Container container, TypeConstant... atypeParams) {
        assert atypeParams.length == 1;

        xArray template = arrayTemplates().get(atypeParams[0]);

        return template == null
                ? super.ensureParameterizedClass(container, atypeParams)
                : template.getCanonicalClass();
    }

    @Override
    public ClassTemplate getTemplate(TypeConstant type) {
        xArray template = arrayTemplates().get(type.getParamType(0));

        return template == null ? this : template;
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        ArrayConstant constArray = (ArrayConstant) constant;

        boolean fSet = switch (constArray.getFormat()) {
            case Array -> false;
            case Set   -> true;
            default    -> throw new IllegalStateException();
        };

        TypeConstant typeArray = constArray.getType();
        Constant[]   aconst    = constArray.getValue();
        int          cSize     = aconst.length;

        ObjectHandle[] ahValue   = new ObjectHandle[cSize];
        boolean        fDeferred = false;
        for (int i = 0; i < cSize; i++) {
            ObjectHandle hValue = frame.getConstHandle(aconst[i]);

            if (Op.isDeferred(hValue)) {
                fDeferred = true;
            }
            ahValue[i] = hValue;
        }

        if (typeArray.containsFormalType(true)) {
            typeArray = typeArray.resolveGenerics(frame.poolContext(),
                            frame.getGenericsResolver(typeArray.containsDynamicType()));
        }

        if (fSet) {
            TypeConstant typeEl = typeArray.getParamType(0);

            if (fDeferred) {
                Frame.Continuation stepNext = frameCaller ->
                    createListSet(frameCaller, typeEl, ahValue);
                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
            }

            return createListSet(frame, typeEl, ahValue);
        } else {
            TypeComposition clzArray = frame.container().resolveClass(typeArray);
            if (fDeferred) {
                Frame.Continuation stepNext = frameCaller ->
                        frameCaller.pushStack(createImmutableArray(clzArray, ahValue));
                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
            }

            return frame.pushStack(createImmutableArray(clzArray, ahValue));
        }
    }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clzArray,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn) {
        IdentityConstant idConstruct = constructor.getIdentityConstant();
        MethodConstant[] aConstruct  = info().constructors;
        int              nScenario;
        for (nScenario = 0; nScenario < 4; nScenario++) {
            if (aConstruct[nScenario].equals(idConstruct)) {
                break;
            }
        }

        return switch (nScenario) {
            case 0  -> construct0(frame, clzArray, ahVar, iReturn);
            case 1  -> construct1(frame, clzArray, ahVar, iReturn);
            case 2  -> construct2(frame, clzArray, ahVar, iReturn);
            case 3  -> construct3(frame, clzArray, ahVar, iReturn);
            case 4  -> construct4(frame, clzArray, ahVar, iReturn);
            default -> frame.raiseException("Unknown constructor: " + idConstruct.getValueString());
        };
    }

    /**
     * Native implementation of "construct(Int capacity = 0)".
     */
    private int construct0(Frame frame, TypeComposition clzArray, ObjectHandle[] ahVar, int iReturn) {
        ObjectHandle hCapacity = ahVar[0];
        long         cCapacity = hCapacity == ObjectHandle.DEFAULT ?
                                    0 : ((JavaLong) hCapacity).getValue();

        if (cCapacity < 0 || cCapacity > Integer.MAX_VALUE) {
            return frame.raiseException(
                xException.illegalArgument(frame, "Invalid array size: " + cCapacity));
        }

        ObjectHandle hArray = createEmptyArray(clzArray, (int) cCapacity, Mutability.Mutable);
        return frame.assignValue(iReturn, hArray);
    }

    /**
     * Native implementation of "construct(Int size, Element | function Element (Int) supply)".
     */
    private int construct1(Frame frame, TypeComposition clzArray, ObjectHandle[] ahVar, int iReturn) {
        JavaLong hCapacity = (JavaLong) ahVar[0];
        long     cCapacity = hCapacity.getValue();

        if (cCapacity < 0 || cCapacity > Integer.MAX_VALUE) {
            return frame.raiseException(
                xException.illegalArgument(frame, "Invalid array size: " + cCapacity));
        }

        ArrayHandle hArray = createEmptyArray(clzArray, (int) cCapacity, Mutability.Fixed);
        int cSize = (int) cCapacity;
        if (cSize > 0) {
            hArray.getDelegate().m_cSize = cSize;

            ObjectHandle hValue = ahVar[1];
            // we could get here either naturally (e.g. new Array<String>(7, "");)
            // or via the ArrayExpression (e.g. new Int[7])
            TypeConstant typeEl = clzArray.getType().getParamType(0);
            if (hValue == ObjectHandle.DEFAULT) {
                hValue = frame.getConstHandle(typeEl.getDefaultValue());
                if (Op.isDeferred(hValue)) {
                    return hValue.proceed(frame, frameCaller ->
                        fill(frameCaller, hArray, cSize, frameCaller.popStack(), iReturn));
                }
            } else {
                ConstantPool pool      = frame.poolContext();
                TypeConstant typeValue = hValue.getType();

                IsFunction:
                if (typeValue.isFunction()) {
                    TypeConstant[] atypeParam = pool.extractFunctionParams(typeValue);
                    if (atypeParam.length != 1 || !atypeParam[0].equals(pool.typeInt64())) {
                        break IsFunction;
                    }
                    TypeConstant[] atypeRet = pool.extractFunctionReturns(typeValue);
                    if (atypeRet.length != 1 || !atypeRet[0].isA(typeEl)) {
                        break IsFunction;
                    }

                    FunctionHandle      hfnSupplier = (FunctionHandle) hValue;
                    int                 cArgs       = hfnSupplier.getVarCount();
                    ObjectHandle[]      ahArg       = new ObjectHandle[cArgs];
                    Utils.ValueSupplier supplier    = (frameCaller, index) -> {
                        ahArg[0] = xInt64.makeHandle(frame, index);
                        return hfnSupplier.call1(frameCaller, null, ahArg, Op.A_STACK);
                    };
                    return new Utils.FillArray(hArray, cSize, supplier, iReturn).doNext(frame);
                }
            }
            return fill(frame, hArray, cSize, hValue, iReturn);
        }
        return frame.assignValue(iReturn, hArray);
    }

    /**
     * Native implementation of "construct(Mutability mutability, Iterable<Element> elements)".
     */
    private int construct2(Frame frame, TypeComposition clzArray, ObjectHandle[] ahVar, int iReturn) {
        // call RTDelegate.fillFromIterable() helper naturally
        ObjectHandle hMutability = ahVar[0];
        ObjectHandle hIterable   = ahVar[1];

        int cCapacity = hIterable instanceof ArrayHandle hA
                ? (int) hA.getDelegate().m_cSize
                : 0;

        ArrayHandle    hArray = createEmptyArray(clzArray, cCapacity, Mutability.Mutable);
        MethodStructure fillFromIterable = info().fillFromIterable;
        ObjectHandle[]  ahArg            = new ObjectHandle[fillFromIterable.getMaxVars()];
        ahArg[0] = clzArray.getType().getParamType(0).ensureTypeHandle(frame.container());
        ahArg[1] = hArray;
        ahArg[2] = hIterable;
        ahArg[3] = hMutability;

        return frame.call1(fillFromIterable, null, ahArg, iReturn);
    }

    /**
     * Native implementation of "construct(Array that)".
     */
    private int construct3(Frame frame, TypeComposition clzArray, ObjectHandle[] ahVar, int iReturn) {
        // see the natural implementation for the comments
        ArrayHandle hThat      = (ArrayHandle) ahVar[0];
        Mutability  mutability = hThat.getMutability();

        if (hThat.isMutable() && (mutability == Mutability.Mutable || mutability == Mutability.Fixed)) {
            ObjectHandle[] ahArg = new ObjectHandle[] {
                getMutabilityTemplate().ensureEnumByOrdinal(frame, mutability.ordinal()),
                hThat
            };
            Frame.Continuation stepNext = frameCaller ->
                    construct2(frameCaller, clzArray, ahArg, iReturn);
            return Op.anyDeferred(ahArg)
                    ? new Utils.GetArguments(ahArg, stepNext).doNext(frame)
                    : construct2(frame, clzArray, ahArg, iReturn);
        } else {
            ObjectHandle[] ahArg = new ObjectHandle[] {
                hThat.getDelegate(),
                getMutabilityTemplate().ensureEnumByOrdinal(frame, mutability.ordinal())
            };
            Frame.Continuation stepNext = frameCaller ->
                    construct4(frameCaller, clzArray, ahArg, iReturn);
            return Op.anyDeferred(ahArg)
                    ? new Utils.GetArguments(ahArg, stepNext).doNext(frame)
                    : construct4(frame, clzArray, ahArg, iReturn);
        }
    }

    /**
     * Native implementation of "protected construct(ArrayDelegate<Element> delegate, Mutability mutability)".
     */
    private int construct4(Frame frame, TypeComposition clzArray, ObjectHandle[] ahVar, int iReturn) {
        ObjectHandle hTarget = ahVar[0];
        if (!(hTarget instanceof DelegateHandle hDelegate)) {
            return frame.raiseException(xException.unsupported(frame));
        }

        ObjectHandle hMutability = ahVar[1];
        ArrayHandle  hArray      = new ArrayHandle(
            clzArray, hDelegate, Mutability.values()[((EnumHandle) hMutability).getOrdinal()]);

        if (hArray.getMutability() == Mutability.Constant) {
            hArray.makeImmutable();
        }
        return frame.assignValue(iReturn, hArray);
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        ArrayHandle hArray = (ArrayHandle) hTarget;

        switch (sPropName) {
        case "cachedHash":
            return calculateHash(frame, hArray, iReturn);

        case "delegate":
            return frame.assignValue(iReturn, hArray.getDelegate());

        case "mutability":
            return frame.assignDeferredValue(iReturn,
                    getMutabilityTemplate().ensureEnumByOrdinal(frame, hArray.getMutability().ordinal()));
        }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    @Override
    public int invokeNativeSet(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hValue) {
        ArrayHandle hArray = (ArrayHandle) hTarget;

        switch (sPropName) {
        case "mutability": {
            Mutability mutability = Mutability.values()[((EnumHandle) hValue).getOrdinal()];
            if (mutability.compareTo(hArray.getMutability()) > 0) {
                return frame.raiseException(
                    xException.illegalState(frame, hArray.getMutability().toString()));
            }
            hArray.setMutability(mutability);
            return Op.R_NEXT;
        }
        }
        return super.invokeNativeSet(frame, hTarget, sPropName, hValue);
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        switch (method.getName()) {
        case "elementAt":
            return makeRef(frame, hTarget, ((JavaLong) hArg).getValue(), false, iReturn);

        case "getElement":
            return extractArrayValue(frame, hTarget, ((JavaLong) hArg).getValue(), iReturn);

        case "slice": {
            GenericHandle hInterval = (GenericHandle) hArg;

            long    ixFrom   = ((JavaLong) hInterval.getField(frame, "lowerBound")).getValue();
            long    ixTo     = ((JavaLong) hInterval.getField(frame, "upperBound")).getValue();
            boolean fExLower = ((BooleanHandle) hInterval.getField(frame, "lowerExclusive")).get();
            boolean fExUpper = ((BooleanHandle) hInterval.getField(frame, "upperExclusive")).get();
            boolean fReverse = ((BooleanHandle) hInterval.getField(frame, "descending")).get();

            return invokeSlice(frame, hTarget, ixFrom, fExLower, ixTo, fExUpper, fReverse, iReturn);
        }

        case "deleteAll": {
            GenericHandle hInterval = (GenericHandle) hArg;

            long    ixFrom   = ((JavaLong) hInterval.getField(frame, "lowerBound")).getValue();
            long    ixTo     = ((JavaLong) hInterval.getField(frame, "upperBound")).getValue();
            boolean fExLower = ((BooleanHandle) hInterval.getField(frame, "lowerExclusive")).get();
            boolean fExUpper = ((BooleanHandle) hInterval.getField(frame, "upperExclusive")).get();

            return invokeDeleteAll(frame, hTarget, ixFrom, fExLower, ixTo, fExUpper, iReturn);
        }
        }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        switch (method.getName()) {
        case "asBitArray": {
            ArrayHandle hArray = (ArrayHandle) hTarget;

            // a view cannot naturally grow or shrink
            Mutability mutability = hArray.getMutability() == Mutability.Constant ||
                                    hArray.getMutability() == Mutability.Persistent
                    ? Mutability.Constant
                    : Mutability.Fixed;

            DelegateHandle hView  =
                    xRTViewToBit.getInstance(frame).createBitViewDelegate(hArray.getDelegate(), mutability);

            return frame.assignValue(iReturn,
                    new ArrayHandle(xBitArray.getInstance(frame.container()).getCanonicalClass(),
                            hView, mutability));
        }

        case "clear": {
            ArrayHandle hArray     = (ArrayHandle) hTarget;
            Mutability  mutability = hArray.getMutability();

            if (hArray.getDelegate().m_cSize > 0) {
                switch (mutability) {
                case Mutable:
                    hArray.setDelegate(makeDelegate(hArray.getComposition(), 0,
                        Utils.OBJECTS_NONE, mutability));
                    break;

                case Fixed:
                    return frame.raiseException(xException.readOnly(frame, mutability));

                case Constant:
                case Persistent:
                    hArray = createEmptyArray(hArray.getComposition(), 0, mutability);
                    break;
                }
            }
            return frame.assignValue(iReturn, hArray);
        }

        case "setElement":
            return assignArrayValue(frame, hTarget, ((JavaLong) ahArg[0]).getValue(), ahArg[1]);
        }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        switch (ahArg.length) {
        case 2:
            switch (method.getName()) {
            case "indexOf": {
                ArrayHandle hThis = (ArrayHandle) hTarget;

                if (ahArg[0] instanceof ArrayHandle hThat) {
                    DelegateHandle hDelegateThis = hThis.getDelegate();
                    DelegateHandle hDelegateThat = hThat.getDelegate();
                    if (hDelegateThis.getTemplate() instanceof xRTDelegate templateThis &&
                        hDelegateThat.getTemplate() instanceof xRTDelegate templateThat &&
                            templateThis == templateThat) {
                        ObjectHandle hStart = ahArg[1];
                        int ofStart = hStart == ObjectHandle.DEFAULT
                                ? 0
                                : (int) ((JavaLong) hStart).getValue();
                        int iResult = templateThis.invokeIndexOf(
                                frame, hDelegateThis, hDelegateThat, ofStart, aiReturn);
                        if (iResult < 0) {
                            return iResult;
                        }
                    }
                }
                MethodStructure listIndexOf = info().listIndexOf;
                return frame.callN(listIndexOf, hTarget,
                    Utils.ensureSize(ahArg, listIndexOf.getMaxVars()), aiReturn);
            }}
        }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }

    @Override
    public int createPropertyRef(Frame frame, ObjectHandle hTarget,
                                 PropertyConstant idProp, boolean fRO, int iReturn) {
        if ("delegate".equals(idProp.getName())) {
            ArrayHandle    hArray    = (ArrayHandle) hTarget;
            DelegateHandle hDelegate = hArray.getDelegate();

            ConstantPool   pool      = frame.poolContext();
            TypeConstant   typeRef   = pool.ensureParameterizedTypeConstant(
                                           pool.typeVar(), hDelegate.getType());

            ClassComposition clzRef  = frame.container().ensureClassComposition(
                    typeRef, xRef.getInstance(frame));
            RefHandle        hRef    = RefHandle.createReferentRef(clzRef, "delegate", hDelegate);

            return frame.assignValue(iReturn, hRef);
        }

        return super.createPropertyRef(frame, hTarget, idProp, fRO, iReturn);
    }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2) {
        if (super.compareIdentity(hValue1, hValue2)) {
            return true;
        }

        ArrayHandle hArray1 = (ArrayHandle) hValue1;
        ArrayHandle hArray2 = (ArrayHandle) hValue2;

        return !hArray1.isMutable() && !hArray2.isMutable() &&
            hArray1.getDelegate().getTemplate().
                compareIdentity(hArray1.getDelegate(), hArray2.getDelegate());
    }

    /**
     * Native "cachedHash.get" implementation.
     */
    private int calculateHash(Frame frame, ArrayHandle hTarget, int iReturn) {
        JavaLong hHash = hTarget.m_hHash;
        if (hHash == null) {
            MethodStructure calculateHash = info().calculateHash;
            frame.call1(calculateHash, hTarget,
                new ObjectHandle[calculateHash.getMaxVars()], Op.A_STACK);
            frame.m_frameNext.addContinuation(frameCaller -> {
                JavaLong hValue = (JavaLong) frameCaller.popStack();
                frameCaller.assignValue(iReturn, hValue);
                hTarget.m_hHash = hValue;
                return Op.R_NEXT;
            });
            return Op.R_CALL;
        }
        return frame.assignValue(iReturn, hHash);
    }


    // ----- IndexSupport methods ------------------------------------------------------------------

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn) {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.getDelegate();

        return ((xRTDelegate) hDelegate.getTemplate()).
                extractArrayValue(frame, hDelegate, lIndex, iReturn);
    }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue) {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.getDelegate();

        return ((xRTDelegate) hDelegate.getTemplate()).
                assignArrayValue(frame, hDelegate, lIndex, hValue);
    }

    @Override
    public TypeConstant getElementType(Frame frame, ObjectHandle hTarget, long lIndex) {
        return hTarget.getType().resolveGenericType("Element");
    }

    @Override
    public long size(ObjectHandle hTarget) {
        ArrayHandle hArray = (ArrayHandle) hTarget;

        return hArray.getDelegate().m_cSize;
    }

    @Override
    public int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn) {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.getDelegate();

        return ((xRTDelegate) hDelegate.getTemplate()).
                invokePreInc(frame, hDelegate, lIndex, iReturn);
    }

    @Override
    public int invokePostInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn) {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.getDelegate();

        return ((xRTDelegate) hDelegate.getTemplate()).
                invokePostInc(frame, hDelegate, lIndex, iReturn);
    }

    @Override
    public int invokePreDec(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn) {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.getDelegate();

        return ((xRTDelegate) hDelegate.getTemplate()).
                invokePreDec(frame, hDelegate, lIndex, iReturn);
    }

    @Override
    public int invokePostDec(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn) {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.getDelegate();

        return ((xRTDelegate) hDelegate.getTemplate()).
                invokePostDec(frame, hDelegate, lIndex, iReturn);
    }

    @Override
    public ObjectHandle[] toArray(Frame frame, ObjectHandle hTarget)
            throws ExceptionHandle.WrapperException {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.getDelegate();

        return ((xRTDelegate) hDelegate.getTemplate()).toArray(frame, hDelegate);
    }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Construct an immutable ListSet handle based on the specified array of handles and put on the
     * frame's stack.
     *
     * @param frame    the current frame
     * @param typeEl   the array element type
     * @param ahValue  the array handles
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    private int createListSet(Frame frame, TypeConstant typeEl, ObjectHandle[] ahValue) {
        TypeConstant    typeArray = frame.poolContext().ensureArrayType(typeEl);
        TypeComposition clzArray  = frame.container().resolveClass(typeArray);

        return createListSet(frame, createImmutableArray(clzArray, ahValue), Op.A_STACK);
    }

    /**
     * Construct an immutable ListSet handle based on the specified array.
     *
     * @param frame    the current frame
     * @param hArray   the array handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public static int createListSet(Frame frame, ArrayHandle hArray, int iResult) {
        MethodStructure createListSet = info(frame.container()).createListSet;
        ObjectHandle[]  ahVar         = new ObjectHandle[createListSet.getMaxVars()];
        ahVar[0] = hArray.getType().getParamType(0).ensureTypeHandle(frame.container());
        ahVar[1] = hArray;

        return frame.call1(createListSet, null, ahVar, iResult);
    }

    /**
     * slice(Interval<Int>) implementation.
     */
    protected int invokeSlice(Frame frame, ObjectHandle hTarget, long ixLower, boolean fExLower,
                              long ixUpper, boolean fExUpper, boolean fReverse, int iReturn) {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.getDelegate();
        xRTDelegate    template  = (xRTDelegate) hDelegate.getTemplate();

        if (fExLower) {
            // exclusive lower
            ++ixLower;
        }

        if (ixLower < 0) {
            return frame.raiseException(xException.outOfBounds(frame, ixLower, 0));
        }

        if (fExUpper) {
            // exclusive upper
            --ixUpper;
        }

        int cSize = (int) hDelegate.m_cSize;
        if (ixUpper >= cSize) {
            return frame.raiseException(xException.outOfBounds(frame, ixUpper, cSize));
        }

        DelegateHandle hSlice = template.slice(hDelegate, ixLower, ixUpper - ixLower + 1, fReverse);
        if (hSlice != hDelegate) {
            Mutability mutability = hArray.getMutability();
            if (mutability == Mutability.Mutable) {
                mutability = Mutability.Fixed;
            }
            hArray = new ArrayHandle(hArray.getComposition(), hSlice, mutability);
        }
        return frame.assignValue(iReturn, hArray);
    }

    /**
     * deleteAll(Interval<Int>) implementation.
     */
    protected int invokeDeleteAll(Frame frame, ObjectHandle hTarget, long ixLower, boolean fExLower,
                                 long ixUpper, boolean fExUpper, int iReturn) {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.getDelegate();
        xRTDelegate    template  = (xRTDelegate) hDelegate.getTemplate();

        if (fExLower) {
            // exclusive lower
            ++ixLower;
        }

        if (ixLower < 0) {
            return frame.raiseException(xException.outOfBounds(frame, ixLower, 0));
        }

        if (fExUpper) {
            // exclusive upper
            --ixUpper;
        }

        int cSize = (int) hDelegate.m_cSize;
        if (ixUpper < 0 || ixUpper >= cSize) {
            return frame.raiseException(xException.outOfBounds(frame, ixUpper, cSize));
        }

        Mutability mutability = hArray.getMutability();
        if (mutability == Mutability.Fixed) {
            return frame.raiseException(xException.sizeLimited(frame, "Fixed size array"));
        }

        DelegateHandle hDelegateNew = template.deleteRange(hDelegate, ixLower, ixUpper - ixLower + 1);
        if (hDelegateNew != hDelegate) {
            if (hDelegateNew == null) {
                return frame.raiseException(xException.readOnly(frame, mutability));
            }
            hArray = new ArrayHandle(hArray.getComposition(), hDelegateNew, mutability);
        }
        return frame.assignValue(iReturn, hArray);
    }

    /**
     * Fill the array content with the specified value.
     *
     * @param hTarget  the array
     * @param cSize    the number of elements to fill
     * @param hValue   the value
     */
    protected int fill(Frame frame, ObjectHandle hTarget, int cSize, ObjectHandle hValue, int iReturn) {
        ArrayHandle    hArray    = (ArrayHandle) hTarget;
        DelegateHandle hDelegate = hArray.getDelegate();
        xRTDelegate    template  = (xRTDelegate) hDelegate.getTemplate();

        if (!hDelegate.checkAssign(hValue)) {
            TypeConstant typeEl = hDelegate.getElementType();
            return frame.raiseException(xException.typeMismatch(frame, hValue.getType(), typeEl));
        }

        DelegateHandle hDelegateNew = template.fill(hDelegate, cSize, hValue);
        if (hDelegateNew != hDelegate) {
            if (hDelegateNew == null) {
                return frame.raiseException(xException.readOnly(frame, hArray.getMutability()));
            }
            hArray = new ArrayHandle(hArray.getComposition(), hDelegateNew, hArray.getMutability());
        }
        return frame.assignValue(iReturn, hArray);
    }

    /**
     * Create an immutable one dimensional array for a specified type and size filled based the
     * specified supplier.
     */
    public static int createAndFill(Frame frame, TypeComposition clzArray, int cSize,
                                    Utils.ValueSupplier supplier, int iReturn) {
        // make it "Mutable" first; freeze after filling up
        ArrayHandle hArray = createEmptyArray(clzArray, cSize, Mutability.Mutable);

        switch (new Utils.FillArray(hArray, cSize, supplier, iReturn).doNext(frame)) {
        case Op.R_NEXT:
            hArray.setMutability(Mutability.Constant);
            return Op.R_NEXT;

        case Op.R_CALL:
            frame.m_frameNext.addContinuation(frameCaller -> {
                hArray.setMutability(Mutability.Constant);
                return Op.R_NEXT;
            });
            return Op.R_CALL;

        case Op.R_EXCEPTION:
            return Op.R_EXCEPTION;

        default:
            throw new IllegalStateException();
        }
    }


    // ----- TypeComposition helpers ---------------------------------------------------------------

    /**
     * @return the TypeComposition for Array<Boolean>.
     */
    public static TypeComposition getBooleanArrayComposition(Container container) {
        return info(container).booleanArrayClz;
    }


    // ----- ObjectHandle helpers ------------------------------------------------------------------

    /**
     * Create a one dimensional immutable array for a specified class and content.
     *
     * @param clzArray  the class of the array
     * @param ahArg     the array elements
     *
     * @return the array handle
     */
    public static ArrayHandle createImmutableArray(TypeComposition clzArray, ObjectHandle[] ahArg) {
        return makeArrayHandle(clzArray, ahArg.length, ahArg, Mutability.Constant);
    }

    /**
     * Create an empty one dimensional array for a specified type and arity.
     *
     * @param clzArray    the class of the array
     * @param cCapacity   the array capacity
     * @param mutability  the mutability constraint
     *
     * @return the array handle
     */
    public static ArrayHandle createEmptyArray(TypeComposition clzArray, int cCapacity,
                                               Mutability mutability) {
        return makeArrayHandle(clzArray, cCapacity, Utils.OBJECTS_NONE, mutability);
    }

    /**
     * @return an immutable String array handle
     */
    public static ArrayHandle makeStringArrayHandle(Container container, StringHandle[] ahValue) {
        return makeArrayHandle(info(container).stringArrayClz, ahValue.length, ahValue, Mutability.Constant);
    }

    /**
     * @return a Bit array handle
     */
    public static ArrayHandle makeBitArrayHandle(
            Container container, byte[] abValue, int cBits, Mutability mutability) {
        ArrayInfo      info      = info(container);
        DelegateHandle hDelegate = info.bitDelegate.makeHandle(abValue, cBits, mutability);
        return new ArrayHandle(info.bitArrayClz, hDelegate, mutability);
    }

    /**
     * @return a Boolean array handle
     */
    public static ArrayHandle makeBooleanArrayHandle(
            Container container, byte[] abValue, int cBits, Mutability mutability) {
        ArrayInfo      info      = info(container);
        DelegateHandle hDelegate = info.booleanDelegate.makeHandle(abValue, cBits, mutability);
        return new ArrayHandle(info.booleanArrayClz, hDelegate, mutability);
    }

    /**
     * @return a Byte array handle
     */
    public static ArrayHandle makeByteArrayHandle(
            Container container, byte[] abValue, Mutability mutability) {
        return makeByteArrayHandle(container, abValue, abValue.length, mutability);
    }

    /**
     * @return a Byte array handle
     */
    public static ArrayHandle makeByteArrayHandle(
            Container container, byte[] abValue, int cBytes, Mutability mutability) {
        if (abValue.length == 0 && mutability == Mutability.Constant) {
            return ensureEmptyByteArray(container);
        }
        ArrayInfo      info      = info(container);
        DelegateHandle hDelegate = info.byteDelegate.makeHandle(abValue, cBytes, mutability);
        return new ArrayHandle(info.byteArrayClz, hDelegate, mutability);
    }

    /**
     * @return the handle for an empty immutable array of Bytes.
     */
    public static ArrayHandle ensureEmptyByteArray(Container container) {
        return info(container).emptyByteArray.get();
    }

    /**
     * @return a Char array handle
     */
    public static ArrayHandle makeCharArrayHandle(
            Container container, char[] achValue, Mutability mutability) {
        ArrayInfo      info      = info(container);
        DelegateHandle hDelegate = info.charDelegate.makeHandle(achValue, mutability);
        return new ArrayHandle(info.charArrayClz, hDelegate, mutability);
    }

    /**
     * @return an Object array handle with the specified mutability
     */
    public static ArrayHandle makeObjectArrayHandle(
            Container container, ObjectHandle[] ahValue, Mutability mutability) {
        return makeArrayHandle(info(container).objectArrayClz, ahValue.length, ahValue, mutability);
    }

    /**
     * Create an ArrayHandle for the specified TypeComposition and fill it with objects from the
     * specified array.
     */
    public static ArrayHandle makeArrayHandle(TypeComposition clzArray, int cCapacity,
                                              ObjectHandle[] ahValue, Mutability mutability) {
        DelegateHandle hDelegate = makeDelegate(clzArray, cCapacity, ahValue, mutability);
        return new ArrayHandle(clzArray, hDelegate, mutability);
    }

    /**
     * Create a DelegateHandle for the specified TypeComposition and fill it with objects from the
     * specified array.
     */
    protected static DelegateHandle makeDelegate(TypeComposition clzArray, int cCapacity,
                                                 ObjectHandle[] ahValue, Mutability mutability) {
        TypeConstant typeElement      = clzArray.getType().getParamType(0);
        xRTDelegate  templateDelegate = xRTDelegate.getArrayTemplate(
                clzArray.getContainer(), typeElement);

        return templateDelegate.createDelegate(
                clzArray.getContainer(), typeElement, cCapacity, ahValue, mutability);
    }

    public static class ArrayHandle
            extends ObjectHandle {
        /**
         * The lifecycle state shared by every access view of this array. MOV_THIS_A and
         * {@link org.xvm.runtime.ClassComposition#ensureAccess} create this:public/private/
         * protected views of one live array through a shallow {@link #cloneAs}, and the two
         * pieces of state that can move after construction - the delegate pointer, which
         * clear() swaps wholesale for Mutable arrays, and the mutability enum, which freeze
         * moves toward Constant - must be visible through every view. Both therefore live in
         * this one cell, which the shallow clone shares by reference; per-view copies of
         * either field were exactly the mechanism-4 desync (a cleared view forking the
         * storage pointer, a frozen view leaving a sibling still willing to write).
         */
        private final ArrayState f_state;

        public JavaLong m_hHash;

        protected ArrayHandle(TypeComposition clzArray, DelegateHandle hDelegate,
                              Mutability mutability) {
            super(clzArray);

            m_fMutable = mutability != Mutability.Constant;
            f_state    = new ArrayState(hDelegate, mutability);
        }

        @Override
        protected boolean supportsMutableViews() {
            // all live lifecycle state is in the shared f_state cell, so a mutable view
            // clone cannot split it; this is the same opt-in GenericHandle earned with its
            // freeze/init cells
            return true;
        }

        @Override
        protected void prepareMutableViewShare() {
            // deliberately no base freeze cell: the delegate pointer AND the mutability enum
            // already live in the constructor-final shared f_state cell, and isMutable()
            // derives from it
        }

        public DelegateHandle getDelegate() {
            return f_state.m_hDelegate;
        }

        /**
         * Replace the storage delegate for every view of this array at once; the only caller
         * is clear() on a Mutable array, the one wholesale delegate replacement that exists.
         */
        public void setDelegate(DelegateHandle hDelegate) {
            f_state.m_hDelegate = hDelegate;
        }

        public Mutability getMutability() {
            return f_state.m_mutability;
        }

        public void setMutability(Mutability mutability) {
            assert mutability.compareTo(f_state.m_mutability) <= 0;
            f_state.m_mutability = mutability;
            f_state.m_hDelegate.setMutability(mutability);
        }

        @Override
        public boolean isMutable() {
            // derived from the shared cell, never from the per-view flag, so a freeze through
            // any view is immediately authoritative for all of them
            return f_state.m_mutability != Mutability.Constant;
        }

        @Override
        public xArray getTemplate() {
            return super.getTemplate(xArray.class);
        }

        @Override
        public boolean makeImmutable() {
            setMutability(Mutability.Constant);
            super.makeImmutable();

            return f_state.m_hDelegate.makeImmutable();
        }

        @Override
        public boolean isShared(Container container, Map<ObjectHandle, Boolean> mapVisited) {
            return f_state.m_hDelegate.isShared(container, mapVisited);
        }

        @Override
        public String toString() {
            return super.toString() + f_state.m_mutability + " " + f_state.m_hDelegate;
        }

        private static class ArrayState {
            DelegateHandle m_hDelegate;
            Mutability     m_mutability;

            ArrayState(DelegateHandle hDelegate, Mutability mutability) {
                m_hDelegate  = hDelegate;
                m_mutability = mutability;
            }
        }
    }

    public enum Mutability {Constant, Persistent, Fixed, Mutable}

    protected static final String[] ELEMENT_TYPE = new String[] {"Element"};

    /**
     * Lazily resolved Array.Mutability enum template.
     */
    private final Lazy.Bound<xArray, xEnum> f_templateMutability =
            Lazy.ofBound(owner -> owner.container().getEnumTemplate("collections.Array.Mutability"));

    // This dispatch map is used while Container.getTemplate(TypeConstant) promotes Array<T> to a
    // specialized template, so it must stay separate from ArrayInfo, which resolves Array classes.
    private final Lazy.Bound<xArray, Map<TypeConstant, xArray>> f_arrayTemplates =
            Lazy.ofBound(xArray::createArrayTemplates);

    private final Lazy.Bound<xArray, ArrayInfo> f_info = Lazy.ofBound(xArray::createArrayInfo);

    private Map<TypeConstant, xArray> arrayTemplates() {
        return f_arrayTemplates.get(this);
    }

    private ArrayInfo info() {
        return f_info.get(this);
    }

    private static ArrayInfo info(Container container) {
        return NativeTemplates.get(container).array().info();
    }

    private ArrayInfo createArrayInfo() {
        ConstantPool pool = pool();

        MethodConstant[] constructors = new MethodConstant[5];
        for (MethodStructure method :
                ((MultiMethodStructure) getStructure().getChild("construct")).methods()) {
            if (method.getAccess() == Constants.Access.PUBLIC) {
                TypeConstant typeParam0 = method.getParam(0).getType();

                if (method.getParamCount() == 1) {
                    if (typeParam0.equals(pool.typeInt64())) {
                        // 0) construct(Int capacity = 0)
                        constructors[0] = method.getIdentityConstant();
                    } else {
                        // 3) construct(Array that)
                        constructors[3] = method.getIdentityConstant();
                    }
                } else {
                    // 1) construct(Int size, Element | function Element (Int) supply)
                    // 2) construct(Mutability mutability, Element... elements)
                    if (typeParam0.equals(pool.typeInt64())) {
                        constructors[1] = method.getIdentityConstant();
                    } else {
                        constructors[2] = method.getIdentityConstant();
                    }
                }
            } else {
                // protected construct(ArrayDelegate<Element> delegate, Mutability mutability)
                constructors[4] = method.getIdentityConstant();
            }
        }

        ClassStructure clzList = f_container.getTemplate("collections.List").getStructure();
        ClassStructure clzHash = f_container.getTemplate("collections.Array.HashableArray").getStructure();

        TypeComposition clzByteArray = f_container.resolveClass(pool.typeByteArray());
        xRTUInt8Delegate byteDelegate =
                (xRTUInt8Delegate) xRTDelegate.getArrayTemplate(f_container, pool.typeByte());

        Lazy<ArrayHandle> emptyByteArray = Lazy.of(() -> new ArrayHandle(
                clzByteArray,
                byteDelegate.makeHandle(Handy.EMPTY_BYTE_ARRAY, 0, Mutability.Constant),
                Mutability.Constant));

        return new ArrayInfo(
                constructors,
                f_container.getClassStructure("_native.ConstHelper").findMethod("createListSet", 2),
                xRTDelegate.getInstance(f_container).getStructure().findMethod("fillFromIterable", 4),
                clzList.findMethod("indexOf", m ->
                        m.getParamCount() == 2 && m.getParam(0).getType().isA(pool.typeList())),
                clzHash.findMethod("calculateHash", 0),
                f_container.resolveClass(pool.ensureArrayType(pool.typeObject())),
                f_container.resolveClass(pool.ensureArrayType(pool.typeString())),
                f_container.resolveClass(pool.typeBitArray()),
                f_container.resolveClass(pool.ensureArrayType(pool.typeBoolean())),
                clzByteArray,
                f_container.resolveClass(pool.ensureArrayType(pool.typeChar())),
                (xRTBitDelegate) xRTDelegate.getArrayTemplate(f_container, pool.typeBit()),
                (xRTBooleanDelegate) xRTDelegate.getArrayTemplate(f_container, pool.typeBoolean()),
                byteDelegate,
                (xRTCharDelegate) xRTDelegate.getArrayTemplate(f_container, pool.typeChar()),
                emptyByteArray);
    }

    private Map<TypeConstant, xArray> createArrayTemplates() {
        ConstantPool              pool         = pool();
        Map<TypeConstant, xArray> mapTemplates = new HashMap<>();

        mapTemplates.put(pool.typeBit(),  f_container.getTemplate(pool.typeBitArray(), xArray.class));
        mapTemplates.put(pool.typeByte(), f_container.getTemplate(pool.typeByteArray(), xArray.class));

        return Map.copyOf(mapTemplates);
    }

    private record ArrayInfo(
            MethodConstant[] constructors,
            MethodStructure createListSet,
            MethodStructure fillFromIterable,
            MethodStructure listIndexOf,
            MethodStructure calculateHash,
            TypeComposition objectArrayClz,
            TypeComposition stringArrayClz,
            TypeComposition bitArrayClz,
            TypeComposition booleanArrayClz,
            TypeComposition byteArrayClz,
            TypeComposition charArrayClz,
            xRTBitDelegate bitDelegate,
            xRTBooleanDelegate booleanDelegate,
            xRTUInt8Delegate byteDelegate,
            xRTCharDelegate charDelegate,
            Lazy<ArrayHandle> emptyByteArray) {}
}
