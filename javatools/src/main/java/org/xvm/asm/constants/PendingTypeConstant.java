package org.xvm.asm.constants;

import java.io.DataOutput;

import java.util.List;
import java.util.Set;

import org.xvm.asm.ComponentResolver.ResolutionCollector;
import org.xvm.asm.ComponentResolver.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.Register;

import org.xvm.util.Hash;

/**
 * Represents a formal type parameter constant that has not been determined yet.
 */
public class PendingTypeConstant
        extends TypeConstant {
    /**
     * Construct the type constant.
     */
    public PendingTypeConstant(ConstantPool pool, TypeConstant typeConstraint) {
        super(pool);

        f_typeConstraint = typeConstraint == null ? pool.typeObject() : typeConstraint;
    }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isShared(ConstantPool poolOther) {
        return f_typeConstraint.isShared(poolOther);
    }

    @Override
    public TypeConstant resolveTypedefs() {
        return this;
    }

    @Override
    public ResolutionResult resolveContributedName(
            String sName, Access access, MethodConstant idMethod, ResolutionCollector collector) {
        return ResolutionResult.UNKNOWN;
    }

    @Override
    public boolean containsAutoNarrowing(boolean fAllowVirtChild) {
        return false;
    }

    @Override
    public boolean isOnlyNullable() {
        return false;
    }

    @Override
    public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver) {
        return this;
    }

    @Override
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams) {
        return this;
    }

    @Override
    public boolean isImmutabilitySpecified() {
        return false;
    }

    @Override
    public boolean isImmutable() {
        return false;
    }

    @Override
    public boolean isAccessSpecified() {
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
    public boolean isSingleUnderlyingClass(boolean fAllowInterface) {
        return false;
    }

    @Override
    public boolean isTypeOfType() {
        return false;
    }

    @Override
    public Category getCategory() {
        // Note: while we know this type is "formal", it's neither "generic type" nor "type parameter"
        return Category.FORMAL;
    }

    @Override
    public boolean isGenericType() {
        return false;
    }

    @Override
    public boolean isTypeParameter() {
        return false;
    }

    @Override
    public boolean containsTypeParameter(boolean fAllowParams) {
        return false;
    }

    @Override
    public boolean containsFormalType(boolean fAllowParams) {
        return true;
    }

    @Override
    public void collectFormalTypes(boolean fAllowParams, Set<TypeConstant> setFormal) {
    }

    @Override
    public boolean containsGenericType(boolean fAllowParams) {
        return false;
    }

    @Override
    public boolean containsGenericParam(String sName) {
        return false;
    }

    @Override
    public TypeConstant resolveConstraints(boolean fPendingOnly) {
        return f_typeConstraint;
    }

    @Override
    public TypeConstant resolvePending(ConstantPool pool, TypeConstant typeActual) {
        return typeActual;
    }

    @Override
    public boolean containsDynamicType(Register register) {
        return false;
    }

    @Override
    public TypeInfo ensureTypeInfo(ErrorListener errs) {
        return f_typeConstraint.ensureTypeInfo(errs);
    }

    @Override
    public Relation calculateRelation(TypeConstant typeLeft) {
        return Relation.IS_A;
    }

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft) {
        return Relation.IS_A;
    }

    @Override
    protected Relation calculateRelationToRight(TypeConstant typeRight) {
        return typeRight.calculateRelation(f_typeConstraint);
    }

    @Override
    public boolean consumesFormalType(String sTypeName, Access access) {
        return false;
    }

    @Override
    protected Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams) {
        return Usage.NO;
    }

    @Override
    public boolean producesFormalType(String sTypeName, Access access) {
        return false;
    }

    @Override
    protected Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams) {
        return Usage.NO;
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public boolean containsUnresolved() {
        // this will prevent this constant from being stored to the pool
        return true;
    }

    @Override
    public Format getFormat() {
        return Format.UnresolvedType;
    }

    @Override
    protected int compareDetails(Constant that) {
        return this.equals(that) ? 0 : -1;
    }

    @Override
    protected void registerConstants(ConstantPool pool) {
        throw new IllegalStateException();
    }

    @Override
    protected void assemble(DataOutput out) {
        throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PendingTypeConstant that &&
                this.f_typeConstraint.equals(that.f_typeConstraint);
    }

    @Override
    public int computeHashCode() {
        return Hash.of(f_typeConstraint);
    }

    @Override
    public String getValueString() {
        return "PendingTypeParameter";
    }


    // ----- data fields ---------------------------------------------------------------------------

    private final TypeConstant f_typeConstraint;
}