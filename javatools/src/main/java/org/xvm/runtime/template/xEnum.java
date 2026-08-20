package org.xvm.runtime.template;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Format;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;

import org.xvm.util.Lazy;


/**
 * A template for the base of all Enum classes
 */
public class xEnum
        extends xConst {

    public static xEnum getInstance(Frame frame) {
        return NativeTemplates.get(frame).enumTemplate();
    }

    public static xEnum getInstance(Container container) {
        return NativeTemplates.get(container).enumTemplate();
    }

    public xEnum(Container container, ClassStructure structure) {
        super(container, structure, false);
    }

    @Override
    public void initNative() {
        if (NativeTemplates.get(this).isEnum(this)) {
            // all the methods are marked as native due to a "rebase"
            f_templateRange.get();
            f_ctorRange.get();
        } else if (getStructure().getFormat() == Format.ENUM) {
            f_enumInfo.get();
        }
    }

    @Override
    public TypeComposition ensureClass(Container container, TypeConstant typeActual) {
        return ensureClass(container, typeActual, typeActual);
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof SingletonConstant constValue) {
            ObjectHandle hValue = constValue.getHandle();
            if (hValue != null) {
                // Note: this could be an InitializingHandle
                return frame.pushStack(hValue);
            }

            assert getStructure().getFormat() == Format.ENUMVALUE;

            xEnum templateEnum = (xEnum) getSuper();

            EnumHandle hEnum = templateEnum.getEnumByConstant(constValue.getClassConstant());
            // For natural enums, hEnum is the mutable struct until completeConstruction() returns.
            // Utils.initConstants() publishes the finalized singleton handle after construction.

            return hEnum.isStruct()
                    ? completeConstruction(frame, hEnum)
                    : frame.pushStack(hEnum);
        }

        return super.createConstHandle(frame, constant);
    }

    /**
     * Complete the construction of an Enum handle (struct) and place the result on the stack.
     *
     * @param frame    the current frame
     * @param hStruct  the enum struct handle
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION} values
     */
    public int completeConstruction(Frame frame, EnumHandle hStruct) {
        assert hStruct.isStruct();

        MethodStructure ctor  = hStruct.getStructure().findConstructor(TypeConstant.NO_TYPES);
        ObjectHandle[]  ahVar = Utils.ensureSize(Utils.OBJECTS_NONE, ctor.getMaxVars());

        switch (proceedConstruction(frame, ctor, true, hStruct, ahVar, Op.A_STACK)) {
        case Op.R_NEXT:
            hStruct.getTemplate().replaceHandle((EnumHandle) frame.peekStack());
            return Op.R_NEXT;

        case Op.R_CALL:

            Frame.Continuation contNext = frameCaller -> {
                EnumHandle hEnum = (EnumHandle) frameCaller.peekStack();
                hEnum.getTemplate().replaceHandle(hEnum);
                return Op.R_NEXT;
            };
            frame.m_frameNext.addContinuation(contNext);
            return Op.R_CALL;

        case Op.R_EXCEPTION:
            return Op.R_EXCEPTION;

        default:
            throw new IllegalStateException();
        }
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        EnumHandle hThis = (EnumHandle) hTarget;

        switch (sPropName) {
        case "enumeration": {
            IdentityConstant idValue        = (IdentityConstant) hTarget.getType().getDefiningConstant();
            ClassStructure   clzEnumeration = ((ClassStructure) idValue.getComponent()).getSuper();

            return frame.assignDeferredValue(iReturn,
                    frame.getConstHandle(clzEnumeration.getIdentityConstant()));
        }

        case "name":
            return frame.assignValue(iReturn,
                    xString.makeHandle(frame, enumInfo().names().get(hThis.getOrdinal())));

        case "ordinal":
            return frame.assignValue(iReturn, xInt64.makeHandle(frame, hThis.getOrdinal()));
        }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        EnumHandle hThis = (EnumHandle) hTarget;

        if (method.getName().equals("stepsTo")) {
            EnumHandle hThat = (EnumHandle) hTarget;
            return frame.assignValue(iReturn,
                    xInt64.makeHandle(frame, hThis.getOrdinal() - hThat.getOrdinal()));
        }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        return frame.assignValue(iReturn, xBoolean.makeHandle(frame, compareIdentity(hValue1, hValue2)));
    }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        EnumHandle hEnum1 = (EnumHandle) hValue1;
        EnumHandle hEnum2 = (EnumHandle) hValue2;
        return frame.assignValue(iReturn, xOrdered.makeHandle(frame, hEnum1.getOrdinal() - hEnum2.getOrdinal()));
    }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2) {
        return ((EnumHandle) hValue1).getOrdinal() == ((EnumHandle) hValue2).getOrdinal();
    }

    @Override
    public int buildHashCode(Frame frame, TypeComposition clazz, ObjectHandle hTarget, int iReturn) {
        EnumHandle hThis = (EnumHandle) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle(frame, hThis.getOrdinal()));
    }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn) {
        EnumHandle hThis = (EnumHandle) hTarget;

        return frame.assignValue(iReturn,
                xString.makeHandle(frame, enumInfo().names().get(hThis.getOrdinal())));
    }

    @Override
    public int invokeIRangeI(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        return createRange(frame, (EnumHandle) hTarget, (EnumHandle) hArg, false, false, iReturn);
    }

    @Override
    public int invokeERangeI(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        return createRange(frame, (EnumHandle) hTarget, (EnumHandle) hArg, true, false, iReturn);
    }

    @Override
    public int invokeIRangeE(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        return createRange(frame, (EnumHandle) hTarget, (EnumHandle) hArg, false, true, iReturn);
    }

    @Override
    public int invokeERangeE(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        return createRange(frame, (EnumHandle) hTarget, (EnumHandle) hArg, true, true, iReturn);
    }

    /**
     * Create a Range object for the specified elements and exclusion flags.
     *
     * Note: the reason we need this to be done differently from the generic logic at ClassTemplate
     *       is that the corresponding method signature (e.g. Range<Orderable> to(Orderable that))
     *       resolves the Orderable to the enum value type (e.g. Lesser) instead of the enum type
     *       (e.g. Ordered) resulting in a TypeMismatch exception; we want a {@code Range<Ordered>}
     *       and not a {@code Range<Lesser>}
     *
     * @return R_NEXT, R_CALL or R_EXCEPTION
     */
    private int createRange(Frame frame, EnumHandle hFirst, EnumHandle hLast,
                           boolean fFirstEx, boolean fLastEx, int iReturn) {
        ConstantPool pool      = frame.poolContext();
        TypeConstant typeRange = pool.ensureParameterizedTypeConstant(
                pool.typeRange(), getCanonicalType());

        ObjectHandle[] ahVar = new ObjectHandle[4];
        ahVar[0] = hFirst;
        ahVar[1] = hLast;
        ahVar[2] = xBoolean.makeHandle(frame, fFirstEx);
        ahVar[3] = xBoolean.makeHandle(frame, fLastEx);

        return f_templateRange.get().construct(frame, f_ctorRange.get(), typeRange.ensureClass(frame),
                null, ahVar, iReturn);
    }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Create an EnumHandle for the specified ordinal value.
     *
     * Note: this method is overridden by native enums.
     *
     * @param clz       the enum's class
     * @param iOrdinal  the ordinal value
     *
     * @return the corresponding EnumHandle
     */
    protected EnumHandle makeEnumHandle(TypeComposition clz, int iOrdinal) {
        // create an un-initialized struct, which will be properly initialized
        // by createConstHandle() below; overridden by native enums
        return new EnumHandle(clz.ensureAccess(Access.STRUCT), iOrdinal);
    }

    public EnumHandle getEnumByName(String sName) {
        EnumInfo info = enumInfo();
        int      ix   = info.names().indexOf(sName);
        return ix >= 0 ? info.handles().get(ix) : null;
    }

    /**
     * Obtain an initialized enum value handle by name.
     *
     * @return the initialized enum value handle, a deferred handle, or null
     */
    public ObjectHandle ensureEnumByName(Frame frame, String sName) {
        EnumHandle hEnum = getEnumByName(sName);
        return hEnum == null
                ? null
                : Utils.ensureInitializedEnum(frame, hEnum);
    }

    public EnumHandle getEnumByOrdinal(int ix) {
        return ix >= 0 ? enumInfo().handles().get(ix) : null;
    }

    /**
     * Obtain an initialized enum value handle by ordinal.
     *
     * @return the initialized enum value handle, a deferred handle, or null
     */
    public ObjectHandle ensureEnumByOrdinal(Frame frame, int ix) {
        EnumHandle hEnum = getEnumByOrdinal(ix);
        return hEnum == null
                ? null
                : Utils.ensureInitializedEnum(frame, hEnum);
    }

    /**
     * @return an EnumHandle for the specified id
     */
    public EnumHandle getEnumByConstant(IdentityConstant id) {
        ClassStructure clzThis = getStructure();

        assert clzThis.getFormat() == Format.ENUM;

        // need an ordinal value for the enum that this represents
        int i = 0;
        for (Component child : clzThis.children()) {
            if (child.getFormat() == Format.ENUMVALUE) {
                if (child.getIdentityConstant().equals(id)) {
                    return getEnumByOrdinal(i);
                }
                ++i;
            }
        }
        return null;
    }

    /**
     * @return an Enum value name for the specified ordinal
     */
    public String getNameByOrdinal(int ix) {
        return enumInfo().names().get(ix);
    }

    /**
     * @return a list of enum names
     */
    public List<String> getNames() {
        return enumInfo().names();
    }

    /**
     * @return a list of enum values
     */
    public List<EnumHandle> getValues() {
        return enumInfo().handles();
    }

    /**
     * Replace a struct handle with a public const one.
     */
    private void replaceHandle(EnumHandle hEnum) {
        assert !hEnum.isStruct();

        hEnum.makeImmutable();

        enumInfo().handles().set(hEnum.m_index, hEnum);
    }


    // ----- ObjectHandle --------------------------------------------------------------------------

    /**
     * The EnumHandle.
     *
     * Note: unlike other handles, EnumHandle's template is the Enumeration (not the EnumValue).
     */
    public static class EnumHandle
                extends GenericHandle {
        EnumHandle(TypeComposition clz, int index) {
            super(clz);

            m_index    = index;
            m_fMutable = clz.isStruct();
        }

        public int getOrdinal() {
            return m_index;
        }

        @Override
        public boolean isNativeEqual() {
            return true;
        }

        @Override
        public int compareTo(ObjectHandle that) {
            return getOrdinal() - ((EnumHandle) that).getOrdinal();
        }

        @Override
        public int hashCode() {
            return m_index;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof EnumHandle that &&
                    this.getTemplate() == that.getTemplate() &&
                    this.m_index == that.m_index;
        }

        /**
         * @return the ClassStructure for this Enum value
         */
        public ClassStructure getStructure() {
            xEnum  templateEnum = getTemplate();
            String sName        = templateEnum.getNameByOrdinal(m_index);
            return (ClassStructure) templateEnum.getStructure().getChild(sName);
        }

        /**
         * @return the name for this Enum value
         */
        public String getName() {
            return getTemplate().getNameByOrdinal(m_index);
        }

        @Override
        public xEnum getTemplate() {
            return super.getTemplate(xEnum.class);
        }

        @Override
        public String toString() {
            return getName();
        }

        protected int m_index;
    }


    // ----- constants and fields ------------------------------------------------------------------

    private final Lazy<ClassTemplate> f_templateRange = Lazy.of(() ->
            f_container.getTemplate("Range"));

    private final Lazy<MethodStructure> f_ctorRange = Lazy.of(() ->
            f_templateRange.get().getStructure().findMethod("construct", 4));

    private EnumInfo enumInfo() {
        return f_enumInfo.get();
    }

    private EnumInfo createEnumInfo() {
        Collection<? extends Component> listAll = getStructure().children();
        List<String>     listNames   = new ArrayList<>(listAll.size());
        List<EnumHandle> listHandles = new ArrayList<>(listAll.size());

        int iOrdinal = 0;
        for (Component child : listAll) {
            if (child.getFormat() == Format.ENUMVALUE) {
                TypeConstant type   = ((ClassStructure) child).getCanonicalType();
                EnumHandle   hValue = makeEnumHandle(ensureClass(f_container, type, type), iOrdinal++);

                listNames.add(child.getName());
                listHandles.add(hValue);

                // native enums don't require any initialization
                if (!hValue.isStruct()) {
                    pool().ensureSingletonConstConstant(child.getIdentityConstant()).setHandle(hValue);
                }
            }
        }

        return new EnumInfo(listNames, listHandles);
    }

    private final Lazy<EnumInfo> f_enumInfo = Lazy.of(this::createEnumInfo);

    /**
     * Template-local enum index. Natural enum values can start as construction structs; the
     * authoritative cross-fiber publication point for the final public value is the corresponding
     * SingletonConstant, so public callers must use ensureEnumByName/ensureEnumByOrdinal or
     * Utils.ensureInitializedEnum instead of treating this list as a publication boundary.
     */
    private record EnumInfo(List<String> names, List<EnumHandle> handles) {}
}
