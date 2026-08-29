package org.xvm.runtime.template;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant.NestedIdentity;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.RangeConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.ByteConstant;
import org.xvm.asm.constants.UnionTypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassComposition.FieldInfo;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xEnum.EnumHandle;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;

import org.xvm.util.Lazy;


/**
 * While this template represents a native interface, it never serves as an inception type
 * by itself.
 */
public class xConst
        extends ClassTemplate {
    public xConst(Container container, ClassStructure structure) {
        super(container, structure, PROP_HASH);

        // NativeContainer still supports the legacy reflective constructor shape
        // (Container, ClassStructure, boolean). Converted templates ignore the flag; canonical
        // template ownership and lookup now live in NativeTemplates, not in constructor-published
        // static fields.
    }

    @Override
    public void initNative() {
        if (NativeTemplates.get(this).isConst(this)) {
            // equals and Comparable support
            getStructure().findMethod("equals",   3).markNative();
            getStructure().findMethod("compare",  3).markNative();
            getStructure().findMethod("hashCode", 2).markNative();

            invalidateTypeInfo();

            // Preserve the old eager bootstrap work, but cache the results in this owner instead
            // of publishing them through process-global static fields.
            info();
        }
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof RangeConstant constRange) {
            ObjectHandle  h1 = frame.getConstHandle(constRange.getFirst());
            ObjectHandle  h2 = frame.getConstHandle(constRange.getLast());
            BooleanHandle f1 = xBoolean.makeHandle(frame, constRange.isFirstExcluded());
            BooleanHandle f2 = xBoolean.makeHandle(frame, constRange.isLastExcluded());
            ConstInfo     info = info();

            TypeConstant    typeRange   = constRange.getType();
            TypeComposition clzRange    = typeRange.ensureClass(frame);
            MethodStructure constructor = info.rangeConstruct();

            ObjectHandle[] ahArg = new ObjectHandle[constructor.getMaxVars()];
            ahArg[0] = h1;
            ahArg[1] = h2;
            ahArg[2] = f1;
            ahArg[3] = f2;

            if (Op.anyDeferred(ahArg)) {
                Frame.Continuation stepNext = frameCaller ->
                    clzRange.getTemplate().construct(
                        frameCaller, constructor, clzRange, null, ahArg, Op.A_STACK);
                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }
            return clzRange.getTemplate().construct(
                frame, constructor, clzRange, null, ahArg, Op.A_STACK);
        }

        Literal:
        if (constant instanceof LiteralConstant constLiteral) {
            ConstantPool    pool      = frame.poolContext();
            Container       container = f_container;
            ConstInfo       info      = info();
            TypeComposition clz;
            MethodStructure constructor;
            switch (constant.getFormat()) {
            case Time:
                clz         = ensureClass(container, pool.typeTime());
                constructor = info.timeConstruct();
                break;

            case Date:
                clz         = ensureClass(container, pool.typeDate());
                constructor = info.dateConstruct();
                break;

            case TimeOfDay:
                clz         = ensureClass(container, pool.typeTimeOfDay());
                constructor = info.timeOfDayConstruct();
                break;

            case TimeZone:
                clz         = ensureClass(container, pool.typeTimeZone());
                constructor = info.timeZoneConstruct();
                break;

            case Duration:
                clz         = ensureClass(container, pool.typeDuration());
                constructor = info.durationConstruct();
                break;

            case Version:
                clz         = ensureClass(container, pool.typeVersion());
                constructor = info.versionConstruct();
                break;

            case Path:
                clz         = ensureClass(container, pool.typePath());
                constructor = info.pathConstruct();
                break;

            default:
                break Literal;
            }

            ObjectHandle[] ahArg = new ObjectHandle[constructor.getMaxVars()];
            ahArg[0] = xString.makeHandle(frame, constLiteral.getValue());

            return construct(frame, constructor, clz, null, ahArg, Op.A_STACK);
        }

        if (constant.getFormat() == Format.Nibble) {
            byte[] abValue = new byte[] {(byte) (((ByteConstant) constant).getValue().byteValue() << 4)};
            MethodStructure constructor = info().nibbleConstruct();

            ObjectHandle[] ahArg = new ObjectHandle[constructor.getMaxVars()];
            ahArg[0] = xArray.makeBitArrayHandle(frame.container(), abValue, 4, Mutability.Constant);

            return construct(frame, constructor,
                    ensureClass(frame.f_context.f_container, constant.getType()),
                    null, ahArg, Op.A_STACK);
        }

        return super.createConstHandle(frame, constant);
    }

    @Override
    protected int postValidate(Frame frame, ObjectHandle hStruct) {
        if (hStruct.isMutable()) {
            GenericHandle hConst = (GenericHandle) hStruct;
            if (hConst.containsMutableFields()) {
                TypeComposition clz = hStruct.getComposition();

                // remove all immutable and proxied (services and services' children);
                // collect all freezable into a "freezable" list along with their names and types
                List<ObjectHandle> listFreezable = null;
                List<FieldInfo>    listInfo      = null;
                List<TypeConstant> listTypes     = null;

                for (FieldInfo field : clz.getFieldLayout().values()) {
                    if (field.isTransient() || field.isSynthetic() || field.isLazy()) {
                        continue;
                    }

                    ObjectHandle hField = hConst.getField(field.getIndex());
                    if (hField == null || hField.isPassThrough()) {
                        // we already checked that it's allowed to be unassigned in
                        // GenericHandle.validateFields()
                        continue;
                    }

                    String sName = field.getName();
                    if (hField.getType().isA(frame.poolContext().typeFreezable())) {
                        if (listFreezable == null) {
                            listFreezable = new ArrayList<>();
                            listInfo      = new ArrayList<>();
                            listTypes     = new ArrayList<>();
                        }
                        listFreezable.add(hField);
                        listInfo.add(field);
                        listTypes.add(hField.getType());
                    } else {
                        return frame.raiseException(xException.notFreezableProperty(frame,
                                sName, hConst.getType()));
                    }
                }

                if (listFreezable != null) {
                    ObjectHandle[] ahFreezable = listFreezable.toArray(Utils.OBJECTS_NONE);
                    FieldInfo[]    aFieldInfo  = listInfo.toArray(NO_FIELDS);
                    ArrayHandle    haValues    =
                        xArray.makeObjectArrayHandle(frame.container(), ahFreezable, Mutability.Fixed);

                    MethodStructure fnFreeze = info().freeze();
                    ObjectHandle[] ahVars = new ObjectHandle[fnFreeze.getMaxVars()];
                    ahVars[0] = haValues;

                    Frame frameFreeze = frame.createFrame1(fnFreeze, null, ahVars, Op.A_IGNORE);
                    frameFreeze.addContinuation(frameCaller -> {
                        ObjectHandle[] ahValueNew;
                        try {
                            ahValueNew = haValues.getTemplate().toArray(frameCaller, haValues);
                        } catch (ExceptionHandle.WrapperException e) {
                            return frameCaller.raiseException(e);
                        }

                        for (int i = 0, c = aFieldInfo.length; i < c; i++) {
                            // verify that "freeze" didn't widen the type
                            FieldInfo    field   = aFieldInfo[i];
                            ObjectHandle hNew    = ahValueNew[i];
                            TypeConstant typeOld = field.getType();
                            TypeConstant typeNew = hNew.getType();
                            if (typeNew.isA(typeOld)) {
                                hConst.setField(field.getIndex(), hNew);
                            } else {
                                return frameCaller.raiseException(
                                    "The freeze() result type for the \"" + field.getName() +
                                    "\" field was illegally changed; \"" +
                                    typeOld.freeze().getValueString() + "\" expected, \"" +
                                    typeNew.getValueString() + "\" returned");
                            }
                        }

                        hConst.makeImmutable();
                        return Op.R_NEXT;
                    });

                    return frame.callInitialized(frameFreeze);
                }
            }
            hConst.makeImmutable();
        }
        return Op.R_NEXT;
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        if (method.getName().equals("appendTo")) {
            return callAppendTo(frame, hTarget, hArg, iReturn);
        }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        switch (method.getName()) {
        case "compare": {
            Container     container = frame.f_context.f_container;
            TypeHandle    hType     = (TypeHandle) ahArg[0];
            ClassTemplate template  = this;

            // allow for narrower native implementations
            if (container.getTemplate(hType.getDataType()) instanceof xConst templateConst) {
                template = templateConst;
            }

            return template.callCompare(frame,
                    getCanonicalClass(container), ahArg[1], ahArg[2], iReturn);
        }

        case "estimateStringLength":
            return callEstimateLength(frame, hTarget, iReturn);

        case "equals": {
            Container     container = frame.f_context.f_container;
            TypeHandle    hType     = (TypeHandle) ahArg[0];
            ClassTemplate template  = this;

            // allow for narrower native implementations
            if (container.getTemplate(hType.getDataType()) instanceof xConst templateConst) {
                template = templateConst;
            }

            return template.callEquals(frame,
                    getCanonicalClass(container), ahArg[1], ahArg[2], iReturn);
        }

        case "hashCode": {
            TypeHandle hType = (TypeHandle) ahArg[0];
            return callHashCode(frame, hType.getDataType(), ahArg[1], iReturn);
        }
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    @Override
    protected int callEqualsImpl(Frame frame, TypeComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        // Note: the actual types could be subclasses of the specified class
        return NativeTemplates.get(this).isConst(this)
                ? frame.raiseException(xException.abstractMethod(frame, "Const.compare()"))
                : new Equals((GenericHandle) hValue1, (GenericHandle) hValue2,
                    (ClassComposition) clazz, iReturn).doNext(frame);
    }

    @Override
    protected int callCompareImpl(Frame frame, TypeComposition clazz,
                                  ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        // Note: the actual types could be subclasses of the specified class
        return NativeTemplates.get(this).isConst(this)
                ? frame.raiseException(xException.abstractMethod(frame, "Const.compare()"))
                : new Compare((GenericHandle) hValue1, (GenericHandle) hValue2,
                        (ClassComposition) clazz, iReturn).doNext(frame);
    }

    /**
     * Compute the hash code of the specified object handle that belongs to the specified type.
     *
     * @param frame    the current frame
     * @param type     the type to use for the hash computation
     * @param hValue   the value
     * @param iReturn  the register id to place an Int64 result into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int callHashCode(Frame frame, TypeConstant type, ObjectHandle hValue, int iReturn) {
        return NativeTemplates.get(this).isConst(this)
                ? frame.raiseException(xException.abstractMethod(frame, "Const.hashCode()"))
                : buildHashCode(frame, getCanonicalClass(frame.f_context.f_container), hValue, iReturn);
    }

    /**
     * Build the hash value for the specified const handle and assign it to the specified register.
     *
     * @return R_NEXT, R_CALL or R_EXCEPTION
     */
    protected int buildHashCode(Frame frame, TypeComposition clazz, ObjectHandle hTarget, int iReturn) {
        GenericHandle hConst = (GenericHandle) hTarget;

        // allow caching the hash only if the targeting class is the actual object's class
        boolean fCache = hConst.getComposition().equals(clazz);
        if (fCache) {
            JavaLong hHash = (JavaLong) hConst.getField(frame, PROP_HASH);
            if (hHash != null) {
                return frame.assignValue(iReturn, hHash);
            }
        }

        return new HashCode(hConst, (ClassComposition) clazz, fCache, info().hashSig(), iReturn).
                doNext(frame);
    }

    /**
     * Native implementation of the "estimateStringLength" method.
     *
     * @param frame    the frame
     * @param hTarget  the target Const value
     * @param iReturn  the register id to place the result of the call into
     *
     * @return one of R_NEXT, R_CALL or R_EXCEPTION
     */
    protected int callEstimateLength(Frame frame, ObjectHandle hTarget, int iReturn) {
        GenericHandle   hConst = (GenericHandle) hTarget;
        TypeComposition clz    = hConst.getComposition();

        StringHandle[] ahNames  = clz.getFieldNameArray();
        ObjectHandle[] ahFields = clz.getFieldValueArray(frame, hConst);
        if (ahNames.length > 0) {
            ObjectHandle hNames  = xArray.makeStringArrayHandle(frame.container(), ahNames);
            ObjectHandle hValues = xArray.makeObjectArrayHandle(frame.container(), ahFields, Mutability.Constant);
            MethodStructure fnEstimateLength = info().estimateLength();

            // estimateStringLength(String[] names, Object[] fields)
            ObjectHandle[] ahVars = new ObjectHandle[fnEstimateLength.getMaxVars()];
            ahVars[0] = hNames;
            ahVars[1] = hValues;

            return frame.call1(fnEstimateLength, null, ahVars, iReturn);
        } else {
            return frame.assignValue(iReturn, xInt64.makeHandle(frame, 2));
        }
    }

    /**
     * Native implementation of the "appendTo" method.
     *
     * @param frame      the frame
     * @param hTarget    the target Const value
     * @param hAppender  the appender
     * @param iReturn    the register id to place the result of the call into
     *
     * @return one of R_NEXT, R_CALL or R_EXCEPTION
     */
    protected int callAppendTo(Frame frame, ObjectHandle hTarget, ObjectHandle hAppender, int iReturn) {
        GenericHandle   hConst = (GenericHandle) hTarget;
        TypeComposition clz    = hConst.getComposition();

        StringHandle[] ahNames  = clz.getFieldNameArray();
        ObjectHandle[] ahFields = clz.getFieldValueArray(frame, hConst);

        ObjectHandle hNames  = xArray.makeStringArrayHandle(frame.container(), ahNames);
        ObjectHandle hValues = xArray.makeObjectArrayHandle(frame.container(), ahFields, Mutability.Constant);
        MethodStructure fnAppendTo = info().appendTo();

        // appendTo(Appender<Char> appender, String[] names, Object[] fields)
        ObjectHandle[] ahVars = new ObjectHandle[fnAppendTo.getMaxVars()];
        ahVars[0] = hAppender; // appender
        ahVars[1] = hNames;
        ahVars[2] = hValues;

        return frame.call1(fnAppendTo, null, ahVars, iReturn);
    }

    /**
     * @return immutable owner-scoped metadata for the canonical Const template
     */
    private ConstInfo info() {
        return f_info.get(this);
    }

    private ConstInfo createInfo() {
        // Stringable support
        ClassStructure constHelper = Utils.constHelper(f_container);
        MethodStructure estimateLength = constHelper.findMethod("estimateStringLength", 2);
        MethodStructure appendTo       = constHelper.findMethod("appendTo", 3);
        MethodStructure freeze         = constHelper.findMethod("freeze", 1);

        // Range support
        MethodStructure rangeConstruct =
                f_container.getClassStructure("Range").findMethod("construct", 4);

        // Nibble support
        ConstantPool pool         = pool();
        TypeConstant typeBitArray = pool.ensureArrayType(pool.typeBit());
        MethodStructure nibbleConstruct = f_container.getClassStructure("numbers.Nibble").
                findMethod("construct", 1, typeBitArray);

        // Time support
        MethodStructure timeConstruct = f_container.getClassStructure("temporal.Time").
                findMethod("construct", 1, pool.typeString());
        MethodStructure dateConstruct = f_container.getClassStructure("temporal.Date").
                findMethod("construct", 1, pool.typeString());
        MethodStructure timeOfDayConstruct = f_container.getClassStructure("temporal.TimeOfDay").
                findMethod("construct", 1, pool.typeString());
        MethodStructure timeZoneConstruct = f_container.getClassStructure("temporal.TimeZone").
                findMethod("construct", 1, pool.typeString());
        MethodStructure durationConstruct = f_container.getClassStructure("temporal.Duration").
                findMethod("construct", 1, pool.typeString());
        MethodStructure versionConstruct = f_container.getClassStructure("reflect.Version").
                findMethod("construct", 1, pool.typeString());
        MethodStructure pathConstruct = f_container.getClassStructure("fs.Path").
                findMethod("construct", 1, pool.typeString());

        SignatureConstant hashSig = f_container.getClassStructure("collections.Hashable").
                findMethod("hashCode", 2).getIdentityConstant().getSignature();

        return new ConstInfo(estimateLength, appendTo, freeze, rangeConstruct, nibbleConstruct,
                timeConstruct, dateConstruct, timeOfDayConstruct, timeZoneConstruct,
                durationConstruct, versionConstruct, pathConstruct, hashSig);
    }


    // ----- helper classes ------------------------------------------------------------------------

    /**
     * Helper class for equals() implementation.
     */
    protected static class Equals
            implements Frame.Continuation {
        private final GenericHandle    hValue1;
        private final GenericHandle    hValue2;
        private final ClassComposition clzBase;
        private final int              iReturn;
        private final Iterator<Map.Entry<Object, FieldInfo>> iterFields;

        public Equals(GenericHandle hValue1, GenericHandle hValue2,
                      ClassComposition clzBase, int iReturn) {
            this.hValue1 = hValue1;
            this.hValue2 = hValue2;
            this.clzBase = clzBase;
            this.iReturn = iReturn;

            iterFields = clzBase.getFieldLayout().entrySet().iterator();
        }

        @Override
        public int proceed(Frame frameCaller) {
            ObjectHandle hResult = frameCaller.popStack();
            if (xBoolean.isFalse(hResult)) {
                return frameCaller.assignValue(iReturn, hResult);
            }
            return doNext(frameCaller);
        }

        public int doNext(Frame frameCaller) {
            ConstantPool    pool = frameCaller.poolContext();
            TypeComposition clz1 = hValue1.getComposition();
            TypeComposition clz2 = hValue2.getComposition();

            while (iterFields.hasNext()) {
                Map.Entry<Object, FieldInfo> entry = iterFields.next();

                Object    enid  = entry.getKey();
                FieldInfo field = entry.getValue();

                if (enid instanceof NestedIdentity || !field.isRegular()) {
                    continue;
                }

                ObjectHandle h1 = clz1 == clzBase
                        ? hValue1.getField(field.getIndex())
                        : enid instanceof PropertyConstant idProp
                            ? hValue1.getField(frameCaller, idProp)
                            : hValue1.getField(frameCaller, enid.toString());
                ObjectHandle h2 = clz2 == clzBase
                        ? hValue2.getField(field.getIndex())
                        : enid instanceof PropertyConstant idProp
                            ? hValue2.getField(frameCaller, idProp)
                            : hValue2.getField(frameCaller, enid.toString());

                if (h1 == null || h2 == null) {
                    return frameCaller.raiseException("Unassigned property \"" + field.getName() +'"');
                }

                TypeConstant typeProp = pool.register(clzBase.getFieldType(enid));

                typeProp = typeProp.resolveGenerics(pool,
                            frameCaller.getGenericsResolver(typeProp.containsDynamicType()));

                switch (typeProp.callEquals(frameCaller, h1, h2, Op.A_STACK)) {
                case Op.R_NEXT:
                    ObjectHandle hResult = frameCaller.popStack();
                    if (xBoolean.isFalse(hResult)) {
                        return frameCaller.assignValue(iReturn, hResult);
                    }
                    break;

                case Op.R_CALL:
                    frameCaller.m_frameNext.addContinuation(this);
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
            return frameCaller.assignValue(iReturn, xBoolean.trueHandle(frameCaller));
        }
    }

    /**
     * Helper class for compare() implementation.
     */
    protected static class Compare
            implements Frame.Continuation {
        private final GenericHandle    hValue1;
        private final GenericHandle    hValue2;
        private final ClassComposition clzBase;
        private final int              iReturn;
        private final Iterator<Map.Entry<Object, FieldInfo>> iterFields;

        public Compare(GenericHandle hValue1, GenericHandle hValue2,
                       ClassComposition clzBase, int iReturn) {
            this.hValue1 = hValue1;
            this.hValue2 = hValue2;
            this.clzBase = clzBase;
            this.iReturn = iReturn;

            iterFields = clzBase.getFieldLayout().entrySet().iterator();
        }

        @Override
        public int proceed(Frame frameCaller) {
            EnumHandle hResult = (EnumHandle) frameCaller.popStack();
            if (!xOrdered.isEqual(hResult)) {
                return frameCaller.assignValue(iReturn, hResult);
            }
            return doNext(frameCaller);
        }

        public int doNext(Frame frameCaller) {
            ConstantPool    pool = frameCaller.poolContext();
            TypeComposition clz1 = hValue1.getComposition();
            TypeComposition clz2 = hValue2.getComposition();

            while (iterFields.hasNext()) {
                Map.Entry<Object, FieldInfo> entry = iterFields.next();

                Object    enid  = entry.getKey();
                FieldInfo field = entry.getValue();

                if (enid instanceof NestedIdentity || !field.isRegular()) {
                    continue;
                }

                ObjectHandle h1 = clz1 == clzBase
                        ? hValue1.getField(field.getIndex())
                        : enid instanceof PropertyConstant idProp
                            ? hValue1.getField(frameCaller, idProp)
                            : hValue1.getField(frameCaller, enid.toString());
                ObjectHandle h2 = clz2 == clzBase
                        ? hValue2.getField(field.getIndex())
                        : enid instanceof PropertyConstant idProp
                            ? hValue2.getField(frameCaller, idProp)
                            : hValue2.getField(frameCaller, enid.toString());

                if (h1 == null || h2 == null) {
                    return frameCaller.raiseException("Unassigned property \"" + field.getName() +'"');
                }

                TypeConstant typeProp = pool.register(clzBase.getFieldType(enid));

                int iResult;
                if (typeProp instanceof UnionTypeConstant && typeProp.isA(pool.typeOrderable())) {
                    iResult = typeProp.callCompare(frameCaller, h1, h2, Op.A_STACK);
                } else {
                    // this check is only to provide a better exception description
                    if (typeProp.findCallable(pool.sigCompare()) == null) {
                        return frameCaller.raiseException("Property \"" + field.getName() +
                                "\" is not Orderable");
                    }

                    iResult = typeProp.callCompare(frameCaller, h1, h2, Op.A_STACK);
                }

                switch (iResult) {
                case Op.R_NEXT:
                    EnumHandle hResult = (EnumHandle) frameCaller.popStack();
                    if (!xOrdered.isEqual(hResult)) {
                        return frameCaller.assignValue(iReturn, hResult);
                    }
                    break;

                case Op.R_CALL:
                    frameCaller.m_frameNext.addContinuation(this);
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
            return frameCaller.assignValue(iReturn, xOrdered.equalHandle(frameCaller));
        }
    }

    /**
     * Helper class for buildHashCode() implementation.
     */
    protected static class HashCode
            implements Frame.Continuation {
        private final GenericHandle    hConst;
        private final ClassComposition clzBase;
        private final boolean          fCache;
        private final SignatureConstant f_sigHash;
        private final int              iReturn;
        private       long             lResult;
        private final Iterator<Map.Entry<Object, FieldInfo>> iterFields;

        public HashCode(GenericHandle hConst, ClassComposition clzBase, boolean fCache,
                        SignatureConstant sigHash, int iReturn) {
            this.hConst  = hConst;
            this.clzBase = clzBase;
            this.fCache  = fCache;
            f_sigHash    = sigHash;
            this.iReturn = iReturn;

            iterFields = clzBase.getFieldLayout().entrySet().iterator();
        }

        @Override
        public int proceed(Frame frameCaller) {
            updateResult(frameCaller);

            return doNext(frameCaller);
        }

        protected void updateResult(Frame frameCaller) {
            lResult = 37 * lResult + ((JavaLong) frameCaller.popStack()).getValue();
        }

        protected int doNext(Frame frameCaller) {
            Container       container = frameCaller.f_context.f_container;
            ConstantPool    pool      = frameCaller.poolContext();
            TypeComposition clz       = hConst.getComposition();

            while (iterFields.hasNext()) {
                Map.Entry<Object, FieldInfo> entry = iterFields.next();

                Object    enid  = entry.getKey();
                FieldInfo field = entry.getValue();

                if (enid instanceof NestedIdentity || !field.isRegular()) {
                    continue;
                }

                ObjectHandle hProp = clz == clzBase
                        ? hConst.getField(field.getIndex())
                        : enid instanceof PropertyConstant idProp
                            ? hConst.getField(frameCaller, idProp)
                            : hConst.getField(frameCaller, enid.toString());

                if (hProp == null) {
                    return frameCaller.raiseException("Unassigned property: \"" + field.getName() + '"');
                }

                TypeConstant typeProp = pool.register(clzBase.getFieldType(enid));

                typeProp = typeProp.resolveGenerics(pool,
                            frameCaller.getGenericsResolver(typeProp.containsDynamicType()));

                int iResult;
                if (typeProp instanceof UnionTypeConstant && typeProp.isA(pool.typeHashable())) {
                    iResult = typeProp.callHashCode(frameCaller, hProp, Op.A_STACK);
                } else {
                    MethodStructure methodHash = typeProp.findCallable(f_sigHash);
                    if (methodHash == null) {
                        // ignore this field
                        continue;
                    }

                    if (methodHash.isNative()) {
                        iResult = hProp.getTemplate().invokeNativeN(frameCaller, methodHash, null,
                            new ObjectHandle[] {typeProp.ensureTypeHandle(container), hProp}, Op.A_STACK);
                    } else {
                        ObjectHandle[] ahVar = new ObjectHandle[methodHash.getMaxVars()];
                        ahVar[0] = typeProp.ensureTypeHandle(container);
                        ahVar[1] = hProp;
                        iResult = frameCaller.call1(methodHash, null, ahVar, Op.A_STACK);
                    }
                }

                switch (iResult) {
                case Op.R_NEXT:
                    updateResult(frameCaller);
                    continue;

                case Op.R_CALL:
                    frameCaller.m_frameNext.addContinuation(this);
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }

            if (lResult == 0) {
                // use a stable non-zero value
                lResult = clzBase.hashCode();
            }

            JavaLong hHash = xInt64.makeHandle(frameCaller, lResult);
            if (fCache) {
                hConst.setField(frameCaller, PROP_HASH, hHash);
            }

            return frameCaller.assignValue(iReturn, hHash);
        }
    }

    // ----- constants -----------------------------------------------------------------------------

    // name of the synthetic property for cached hash value
    private static final String      PROP_HASH = "@hash";
    private static final FieldInfo[] NO_FIELDS = new FieldInfo[0];

    private record ConstInfo(MethodStructure estimateLength,
                             MethodStructure appendTo,
                             MethodStructure freeze,
                             MethodStructure rangeConstruct,
                             MethodStructure nibbleConstruct,
                             MethodStructure timeConstruct,
                             MethodStructure dateConstruct,
                             MethodStructure timeOfDayConstruct,
                             MethodStructure timeZoneConstruct,
                             MethodStructure durationConstruct,
                             MethodStructure versionConstruct,
                             MethodStructure pathConstruct,
                             SignatureConstant hashSig) {}


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Owner-scoped equivalent of the old static Const metadata caches. The structures and
     * signatures are derived from this template's container and constant pool, so they cannot be
     * shared safely between containers.
     */
    private final Lazy.Bound<xConst, ConstInfo> f_info = Lazy.ofBound(xConst::createInfo);
}
