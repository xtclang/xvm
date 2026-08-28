package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.IOException;

import java.util.Collections;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;

import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.PropertyExprAST;

import org.xvm.compiler.ast.Context;


/**
 * Represent a formal child of a generic property, type parameter or formal child constant.
 */
public final class FormalTypeChildConstant
        extends PropertyConstant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public FormalTypeChildConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException {
        super(pool, format, in);
    }

    /**
     * Construct a constant that represents the class of a non-static child whose identity is
     * auto-narrowing.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the parent constant
     * @param sName        the formal child name
     */
    public FormalTypeChildConstant(ConstantPool pool, FormalConstant constParent, String sName) {
        super(pool, constParent, sName, ParentFormat.FORMAL_CHILD);
    }

    /**
     * Adoption constructor: re-homes an already-validated formal child into a new pool, validating
     * the parent whenever its structure is resolvable and skipping the check only mid-merge, when
     * the module is not yet linked and formal-ness is unknowable (see {@link #copyForAdoption} and
     * {@code PropertyConstant.validateParentIfKnowable}).
     */
    private FormalTypeChildConstant(ConstantPool pool, FormalConstant constParent, String sName,
                                    boolean fStrict) {
        super(pool, constParent, sName, ParentFormat.FORMAL_CHILD, fStrict);
    }

    @Override
    protected void checkParent(IdentityConstant idParent) {
        validateParent(ParentFormat.FORMAL_CHILD, idParent);
    }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the top formal parent of this formal child
     */
    public IdentityConstant getTopParent() {
        IdentityConstant idParent = getParentConstant();
        while (idParent.getFormat() == Format.FormalTypeChild) {
            idParent = idParent.getParentConstant();
        }
        return idParent;
    }


    // ----- PropertyConstant methods --------------------------------------------------------------

    @Override
    public boolean isFormalType() {
        return true;
    }

    @Override
    public boolean isTypeSequenceTypeParameter() {
        return false;
    }


    // ----- FormalConstant methods ----------------------------------------------------------------

    @Override
    public TypeConstant getConstraintType() {
        TypeConstant typeConstraint = m_typeConstraint;
        if (typeConstraint != null) {
            return typeConstraint;
        }

        typeConstraint = getParentConstant().getConstraintType();

        // there is a possibility that this constant was constructed with some extra assumptions
        // during the compile time that are not "encoded" into the constant itself;
        // for example, the compiler may know that "CompileType" is an "Array", therefore
        // "CompileType.Element" is represented by a FormalTypeChildConstant, but that knowledge
        // is not encoded into the constant itself
        String sName = getName();
        if (typeConstraint.containsGenericParam(sName)) {
            if (typeConstraint.isTuple()) {
                return m_typeConstraint = getConstantPool().typeTuple();
            }

            TypeConstant type = typeConstraint.getSingleUnderlyingClass(true).getFormalType().
                                    getGenericParamType(sName, Collections.emptyList());
            assert type.isGenericType();

            PropertyConstant idProp = (PropertyConstant) type.getDefiningConstant();
            return m_typeConstraint = idProp.getConstraintType();
        }

        return m_typeConstraint = "OuterType".equals(sName) && typeConstraint.isVirtualChild()
                ? typeConstraint.getParentType()
                : getConstantPool().typeObject();
    }

    @Override
    public TypeConstant resolve(GenericTypeResolver resolver) {
        TypeConstant typeResolved = super.resolve(resolver);
        if (typeResolved != null) {
            return typeResolved;
        }

        TypeConstant typeParent = getParentConstant().resolve(resolver);
        return typeParent == null
                ? null
                : typeParent.resolveGenericType(getName());
    }

    @Override
    public ExprAST toExprAst(Context ctx) {
        return new PropertyExprAST(getParentConstant().toExprAst(ctx), getNameConstant());
    }


    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public IdentityConstant replaceParentConstant(IdentityConstant idParent) {
        return new FormalTypeChildConstant(getConstantPool(), (FormalConstant) idParent, getName());
    }

    @Override
    public FormalConstant getParentConstant() {
        return (FormalConstant) super.getParentConstant();
    }

    @Override
    public IdentityConstant getNamespace() {
        return getTopParent().getNamespace();
    }

    @Override
    public TypeConstant getType() {
        return getConstantPool().ensureTerminalTypeConstant(this);
    }

    @Override
    public IdentityConstant appendTrailingSegmentTo(IdentityConstant that) {
        return that.getConstantPool().ensureFormalTypeChildConstant((FormalConstant) that, getName());
    }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat() {
        return Format.FormalTypeChild;
    }

    @Override
    public boolean containsUnresolved() {
        return false;
    }

    @Override
    protected FormalTypeChildConstant copyForAdoption(AdoptionContext context) {
        // Preserve the formal-child format; inherited property caches are owner/type-system helper
        // state and are intentionally left empty on the adopted copy. Non-strict parent checking:
        // the source is already valid, and validateParent's isFormalType() resolves the parent
        // structure, which is unavailable mid-merge - so the check still runs whenever that structure
        // IS resolvable, and is skipped only when formal-ness is genuinely unknowable.
        return new FormalTypeChildConstant(context.pool(), getParentConstant(), getName(), false);
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    public String getDescription() {
        return "parent=" + getParentConstant() + ", child=" + getName();
    }
}
