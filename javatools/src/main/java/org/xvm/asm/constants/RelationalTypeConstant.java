package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.function.Consumer;
import java.util.function.Function;

import org.xvm.asm.Annotation;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.Register;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.util.Hash;
import org.xvm.util.ListMap;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A common base for relational types.
 */
public abstract class RelationalTypeConstant
        extends TypeConstant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a relational constant based on two specified types.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constType1  the first TypeConstant
     * @param constType2  the second TypeConstant
     */
    protected RelationalTypeConstant(ConstantPool pool, TypeConstant constType1, TypeConstant constType2) {
        super(pool);

        if (constType1 == null || constType2 == null) {
            throw new IllegalArgumentException("types required");
        }

        m_constType1 = constType1;
        m_constType2 = constType2;
    }

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    protected RelationalTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException {
        super(pool);

        m_iType1 = readMagnitude(in);
        m_iType2 = readMagnitude(in);
    }

    @Override
    protected void resolveConstants() {
        ConstantPool pool = getConstantPool();

        m_constType1 = (TypeConstant) pool.getConstant(m_iType1);
        m_constType2 = (TypeConstant) pool.getConstant(m_iType2);
    }

    // ----- type specific methods -----------------------------------------------------------------

    /**
     * @return clone this relational type based on the underlying types
     */
    protected abstract TypeConstant cloneRelational(ConstantPool pool,
                                                    TypeConstant type1, TypeConstant type2);

    /**
     * Simplify this relational type based on the specified underlying types.
     *
     * @return the resulting type, which may or may not be relational or this type if this type
     *         cannot be simplified
     */
    public TypeConstant simplify(ConstantPool pool) {
        TypeConstant typeS = simplifyInternal(m_constType1, m_constType2);

        return typeS == null ? this : typeS;
    }

    /**
     * Simplify or clone this relational type based on the specified underlying types.
     *
     * @return the resulting type, which may or may not be relational
     */
    protected TypeConstant simplifyOrClone(ConstantPool pool,
                                           TypeConstant type1, TypeConstant type2) {
        TypeConstant typeS = simplifyInternal(type1, type2);

        return typeS == null ? cloneRelational(pool, type1, type2) : typeS;
    }

    /**
     * Simplify this relational type based on the specified underlying types.
     *
     * @return the resulting type, which may or may not be relational or null if the type cannot be
     *         simplified
     */
    protected abstract TypeConstant simplifyInternal(TypeConstant type1, TypeConstant type2);


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isRelationalType() {
        return true;
    }

    @Override
    public TypeConstant getUnderlyingType() {
        return m_constType1;
    }

    @Override
    public TypeConstant getUnderlyingType2() {
        return m_constType2;
    }

    @Override
    public boolean isShared(ConstantPool poolOther) {
        return m_constType1.isShared(poolOther) && m_constType2.isShared(poolOther);
    }

    @Override
    public boolean isComposedOfAny(Set<IdentityConstant> setIds) {
        return m_constType1.isComposedOfAny(setIds) || m_constType2.isComposedOfAny(setIds);
    }

    @Override
    public TypeConstant removeImmutable() {
        return simplifyOrClone(getConstantPool(),
                m_constType1.removeImmutable(), m_constType2.removeImmutable());
    }

    @Override
    public boolean isAccessSpecified() {
        // relational types are never access-modified
        return false;
    }

    @Override
    public Access getAccess() {
        return Access.PUBLIC;
    }

    @Override
    public boolean isAccessModifiable() {
        return false;
    }

    @Override
    public TypeConstant adjustAccess(IdentityConstant idClass) {
        return this;
    }

    @Override
    public int getTypeDepth() {
        return m_constType1.getTypeDepth() + m_constType2.getTypeDepth();
    }

    @Override
    public boolean extendsClass(IdentityConstant constClass) {
        return m_constType1.extendsClass(constClass)
            || m_constType2.extendsClass(constClass);
    }

    @Override
    public Constant getDefiningConstant() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOnlyNullable() {
        return m_constType1.isOnlyNullable()
            && m_constType2.isOnlyNullable();
    }

    @Override
    public TypeConstant freeze() {
        // both Intersection and Union have the same algorithm
        TypeConstant typeOriginal1  = m_constType1;
        TypeConstant typeOriginal2  = m_constType2;
        TypeConstant typeImmutable1 = typeOriginal1.freeze();
        TypeConstant typeImmutable2 = typeOriginal2.freeze();

        return typeOriginal1 == typeImmutable1 && typeOriginal2 == typeImmutable2
                ? this
                : cloneRelational(getConstantPool(), typeImmutable1, typeImmutable2);
    }

    @Override
    public TypeConstant resolveTypedefs() {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.resolveTypedefs();
        TypeConstant constResolved2 = constOriginal2.resolveTypedefs();

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(getConstantPool(), constResolved1, constResolved2);
    }

    @Override
    public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver) {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.resolveGenerics(pool, resolver);
        TypeConstant constResolved2 = constOriginal2.resolveGenerics(pool, resolver);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : simplifyOrClone(pool, constResolved1, constResolved2);
    }

    @Override
    public TypeConstant resolveConstraints(boolean fPendingOnly) {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.resolveConstraints(fPendingOnly);
        TypeConstant constResolved2 = constOriginal2.resolveConstraints(fPendingOnly);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : simplifyOrClone(getConstantPool(), constResolved1, constResolved2);
    }

    @Override
    public TypeConstant resolveDynamicConstraints(Register register) {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.resolveDynamicConstraints(register);
        TypeConstant constResolved2 = constOriginal2.resolveDynamicConstraints(register);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : simplifyOrClone(getConstantPool(), constResolved1, constResolved2);
    }

    @Override
    public boolean containsFormalType(boolean fAllowParams) {
        return m_constType1.containsFormalType(fAllowParams)
            || m_constType2.containsFormalType(fAllowParams);
    }

    @Override
    public void collectFormalTypes(boolean fAllowParams, Set<TypeConstant> setFormal) {
        m_constType1.collectFormalTypes(fAllowParams, setFormal);
        m_constType2.collectFormalTypes(fAllowParams, setFormal);
    }

    @Override
    public boolean containsDynamicType(Register register) {
        return m_constType1.containsDynamicType(register)
            || m_constType2.containsDynamicType(register);
    }

    @Override
    public boolean containsGenericType(boolean fAllowParams) {
        return m_constType1.containsGenericType(fAllowParams)
            || m_constType2.containsGenericType(fAllowParams);
    }

    @Override
    public boolean containsTypeParameter(boolean fAllowParams) {
        return m_constType1.containsTypeParameter(fAllowParams)
            || m_constType2.containsTypeParameter(fAllowParams);
    }

    @Override
    public boolean containsRecursiveType() {
        return m_constType1.containsRecursiveType()
            || m_constType2.containsRecursiveType();
    }

    @Override
    public boolean containsFunctionType() {
        // for now, don't allow coalescing functions for either Union or Intersection types
        return m_constType1.containsFunctionType()
            || m_constType2.containsFunctionType();
    }

    @Override
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams) {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.adoptParameters(pool, atypeParams);
        TypeConstant constResolved2 = constOriginal2.adoptParameters(pool, atypeParams);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : simplifyOrClone(pool, constResolved1, constResolved2);
    }

    @Override
    public TypeConstant[] collectGenericParameters() {
        TypeConstant[] atype1 = m_constType1.collectGenericParameters();
        TypeConstant[] atype2 = m_constType2.collectGenericParameters();

        if (atype1 == null || atype2 == null) {
            return null;
        }

        int c1 = atype1.length;
        int c2 = atype2.length;

        if (c1 == c2) {
            for (int i = 0; i < c1; i++) {
                TypeConstant type1 = atype1[i];
                TypeConstant type2 = atype2[i];

                assert type1.isGenericType() && type2.isGenericType();

                PropertyConstant id1 = (PropertyConstant) type1.getDefiningConstant();
                PropertyConstant id2 = (PropertyConstant) type2.getDefiningConstant();

                if (!id1.getName().equals(id2.getName()) ||
                    !id1.getConstraintType().equals(id2.getConstraintType())) {
                    return null;
                }
            }
            return atype1;
        }
        return null;
    }

    @Override
    public boolean containsAutoNarrowing(boolean fAllowVirtChild) {
        return m_constType1.containsAutoNarrowing(fAllowVirtChild)
            || m_constType2.containsAutoNarrowing(fAllowVirtChild);
    }

    @Override
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams,
                                             TypeConstant typeTarget, IdentityConstant idCtx) {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.resolveAutoNarrowing(pool, fRetainParams, typeTarget, idCtx);
        TypeConstant constResolved2 = constOriginal2.resolveAutoNarrowing(pool, fRetainParams, typeTarget, idCtx);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(pool, constResolved1, constResolved2);
    }

    @Override
    public TypeConstant replaceUnderlying(ConstantPool pool, Function<TypeConstant, TypeConstant> transformer) {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = transformer.apply(constOriginal1);
        TypeConstant constResolved2 = transformer.apply(constOriginal2);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : simplifyOrClone(pool, constResolved1, constResolved2);
    }

    @Override
    public TypeConstant resolveTypeParameter(TypeConstant typeActual, String sFormalName) {
        typeActual = typeActual.resolveTypedefs();

        if (getFormat() != typeActual.getFormat()) {
            return null;
        }

        RelationalTypeConstant that = (RelationalTypeConstant) typeActual;

        TypeConstant constThis1 = this.m_constType1;
        TypeConstant constThis2 = this.m_constType2;
        TypeConstant constThat1 = that.m_constType1;
        TypeConstant constThat2 = that.m_constType2;

        // check all four possibilities
        TypeConstant typeResult;
        typeResult = constThis1.resolveTypeParameter(constThat1, sFormalName);
        if (typeResult != null) {
            return typeResult;
        }
        typeResult = constThis2.resolveTypeParameter(constThat2, sFormalName);
        if (typeResult != null) {
            return typeResult;
        }
        typeResult = constThis1.resolveTypeParameter(constThat2, sFormalName);
        if (typeResult != null) {
            return typeResult;
        }
        return constThis2.resolveTypeParameter(constThat1, sFormalName);
    }

    @Override
    public boolean isIntoPropertyType() {
        return false;
    }

    @Override
    public TypeConstant getIntoPropertyType() {
        return null;
    }

    @Override
    public boolean isIntoVariableType() {
        return false;
    }

    @Override
    public TypeConstant getIntoVariableType() {
        return null;
    }

    @Override
    public boolean isIntoMetaData(TypeConstant typeTarget, boolean fStrict) {
        return false;
    }

    @Override
    public boolean isTypeOfType() {
        return false;
    }

    @Override
    public TypeConstant widenEnumValueTypes() {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.widenEnumValueTypes();
        TypeConstant constResolved2 = constOriginal2.widenEnumValueTypes();

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : simplifyOrClone(getConstantPool(), constResolved1, constResolved2);
    }


    // ----- TypeInfo support ----------------------------------------------------------------------

    @Override
    public TypeInfo ensureTypeInfo(IdentityConstant idClass, ErrorListener errs) {
        int      cInvals = getConstantPool().getInvalidationCount();
        TypeInfo info1   = m_constType1.ensureTypeInfo(idClass, errs);
        TypeInfo info2   = m_constType2.ensureTypeInfo(idClass, errs);

        return mergeTypeInfo(info1, info2, cInvals, errs);
    }

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs) {
        // most of the time, we come here from {@link TypeConstant#ensureTypeInfo}, where we always
        // resolve auto-narrowing and normalize parameters; however there are some scenarios (e.g.:
        // relational type contributions) that bypass that normalization
        int      cInvals = getConstantPool().getInvalidationCount();
        TypeInfo info1   = m_constType1.removeAutoNarrowing().normalizeParameters().ensureTypeInfoInternal(errs);
        TypeInfo info2   = m_constType2.removeAutoNarrowing().normalizeParameters().ensureTypeInfoInternal(errs);

        if (info1 == null && info2 == null) {
            return null;
        }

        return mergeTypeInfo(info1, info2, cInvals, errs);
    }

    /**
     * Merge the specified TypeInfos.
     *
     * Note, that either of the two TypeInfos can be null, in which case we need to return an
     * incomplete TypeInfo.
     *
     * @return merged TypeInfo
     */
    protected TypeInfo mergeTypeInfo(TypeInfo info1, TypeInfo info2, int cInvals, ErrorListener errs) {
        return new TypeInfo(this,
                            cInvals,
                            null,                   // struct
                            0,                      // depth
                            false,                  // synthetic
                            mergeTypeParams(info1, info2, errs),
                            Annotation.NO_ANNOTATIONS,
                            mergeAnnotations(info1, info2, errs),
                            null,                   // typeExtends
                            null,                   // typeRebase
                            null,                   // typeInto
                            Collections.emptyList(), // listProcess,
                            ListMap.EMPTY,          // listmapClassChain
                            ListMap.EMPTY,          // listmapDefaultChain
                            mergeProperties(info1, info2, errs),
                            mergeMethods(info1, info2, errs),
                            Collections.emptyMap(),  // mapVirtProps
                            Collections.emptyMap(),  // mapVirtMethods
                            mergeChildren(info1, info2, errs),
                            mergeDepends(info1, info2),
                            info1 == null || info2 == null
                                    ? TypeInfo.Progress.Incomplete
                                    : info1.getProgress().worstOf(info2.getProgress())
                            );
    }


    /**
     * Produce a map of TypeParams for a merge of the specified TypeInfos.
     *
     * Note, that either of the two TypeInfos can be null.
     *
     * @return a merged map
     */
    protected abstract Map<Object, ParamInfo> mergeTypeParams(TypeInfo info1, TypeInfo info2, ErrorListener errs);

    /**
     * Produce an array of Annotations for a merge of the specified TypeInfos.
     *
     * Note, that either of the two TypeInfos can be null.
     *
     * @return a merged array
     */
    protected abstract Annotation[] mergeAnnotations(TypeInfo info1, TypeInfo info2, ErrorListener errs);

    /**
     * Produce a map of properties for a merge of the specified TypeInfos.
     *
     * Note, that either of the two TypeInfos can be null.
     *
     * @return a merged map
     */
    protected abstract Map<PropertyConstant, PropertyInfo> mergeProperties(TypeInfo info1, TypeInfo info2, ErrorListener errs);

    /**
     * Produce a map of methods for a merge of the specified TypeInfos.
     *
     * Note, that either of the two TypeInfos can be null.
     *
     * @return a merged map
     */
    protected abstract Map<MethodConstant, MethodInfo> mergeMethods(TypeInfo info1, TypeInfo info2, ErrorListener errs);

    /**
     * Produce a ListMap of children for a merge of the specified TypeInfos.
     *
     * Note, that either of the two TypeInfos can be null.
     *
     * @return a merged ListMap
     */
    protected abstract ListMap<String, ChildInfo> mergeChildren(TypeInfo info1, TypeInfo info2, ErrorListener errs);

    /**
     * Produce a set of the types the [incomplete] TypeInfo for this type depends on.
     *
     * Note, that either of the two TypeInfos can be null.
     *
     * @return a merged set of types
     */
    protected Set<TypeConstant> mergeDepends(TypeInfo info1, TypeInfo info2) {
        Set<TypeConstant> setDepends = null;
        if (info1 != null) {
            setDepends = info1.collectDependTypes(setDepends);
        }
        if (info2 != null) {
            setDepends = info2.collectDependTypes(setDepends);
        }

        return setDepends;
    }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected boolean isDuckTypeAbleFrom(TypeConstant typeRight) {
        return getUnderlyingType() .isDuckTypeAbleFrom(typeRight) &&
               getUnderlyingType2().isDuckTypeAbleFrom(typeRight);
    }

    @Override
    protected Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams) {
        assert listParams.isEmpty();
        return Usage.valueOf(m_constType1.producesFormalType(sTypeName, access)
                          || m_constType2.producesFormalType(sTypeName, access));
    }

    @Override
    protected Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams) {
        assert listParams.isEmpty();
        return Usage.valueOf(m_constType1.consumesFormalType(sTypeName, access)
                          || m_constType2.consumesFormalType(sTypeName, access));
    }


    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public ClassTemplate getTemplate(Container container) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int callEquals(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int callCompare(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int callHashCode(Frame frame, ObjectHandle hValue, int iReturn) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MethodInfo findFunctionInfo(SignatureConstant sig) {
        throw new UnsupportedOperationException();
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public boolean containsUnresolved() {
        return !isHashCached() && (m_constType1.containsUnresolved() || m_constType2.containsUnresolved());
    }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor) {
        visitor.accept(m_constType1);
        visitor.accept(m_constType2);
    }

    @Override
    protected int compareDetails(Constant obj) {
        if (obj == null || this.getClass() != obj.getClass()) {
            return -1;
        }

        RelationalTypeConstant that = (RelationalTypeConstant) obj;

        int n = this.m_constType1.compareTo(that.m_constType1);
        if (n == 0) {
            n = this.m_constType2.compareTo(that.m_constType2);
        }
        return n;
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool) {
        m_constType1 = (TypeConstant) pool.register(m_constType1);
        m_constType2 = (TypeConstant) pool.register(m_constType2);
    }

    @Override
    protected void assemble(DataOutput out)
            throws IOException {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, indexOf(m_constType1));
        writePackedLong(out, indexOf(m_constType2));
    }

    @Override
    public boolean validate(ErrorListener errs) {
        return !isValidated()
            && m_constType1.validate(errs) && m_constType2.validate(errs)
            && super.validate(errs);
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode() {
        return Hash.of(m_constType1,
               Hash.of(m_constType2));
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the first type constant.
     */
    private int m_iType1;

    /**
     * During disassembly, this holds the index of the second type constant.
     */
    private int m_iType2;

    /**
     * The first type referred to.
     */
    protected TypeConstant m_constType1;

    /**
     * The second type referred to.
     */
    protected TypeConstant m_constType2;
}