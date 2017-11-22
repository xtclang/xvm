package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.CompositeComponent;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.TypedefStructure;

import org.xvm.runtime.TypeSet;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents a type that is defined by some other structure within the module.
 * Specifically, the definition pointed to by this TypeConstant can be any one of:
 * <p/>
 * <ul>
 * <li>{@link ModuleConstant} for a module</li>
 * <li>{@link PackageConstant} for a package</li>
 * <li>{@link ClassConstant} for a class</li>
 * <li>{@link TypedefConstant} for a typedef</li>
 * <li>{@link PropertyConstant} for a class' type parameter</li>
 * <li>{@link RegisterConstant} for a method's type parameter</li>
 * <li>{@link ThisClassConstant} to indicate the auto-narrowing "this" class</li>
 * <li>{@link ParentClassConstant} for an auto-narrowing parent of an auto-narrowing class</li>
 * <li>{@link ChildClassConstant} for a named auto-narrowing child of an auto-narrowing class</li>
 * <li>{@link UnresolvedNameConstant} for a definition that has not been resolved at this point</li>
 * </ul>
 */
public class TerminalTypeConstant
        extends TypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool   the ConstantPool that will contain this Constant
     * @param format the format of the Constant in the stream
     * @param in     the DataInput stream to read the Constant value from
     *
     * @throws IOException if an issue occurs reading the Constant value
     */
    public TerminalTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iDef = readIndex(in);
        }

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param constId a ModuleConstant, PackageConstant, or ClassConstant
     */
    public TerminalTypeConstant(ConstantPool pool, Constant constId)
        {
        super(pool);

        assert !(constId instanceof TypeConstant);

        switch (constId.getFormat())
            {
            case Module:
            case Package:
            case Class:
            case Typedef:
            case Property:
            case Register:
            case ThisClass:
            case ParentClass:
            case ChildClass:
            case UnresolvedName:
                break;

            default:
                throw new IllegalArgumentException("constant " + constId.getFormat()
                        + " is not a Module, Package, Class, Typedef, or formal type parameter");
            }

        m_constId = constId;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isImmutabilitySpecified()
        {
        return false;
        }

    @Override
    public boolean isAccessSpecified()
        {
        return false;
        }

    @Override
    public Access getAccess()
        {
        return Access.PUBLIC;
        }

    @Override
    public boolean isParamsSpecified()
        {
        return false;
        }

    @Override
    public boolean isAnnotated()
        {
        return false;
        }

    @Override
    public boolean isSingleDefiningConstant()
        {
        return true;
        }

    @Override
    public Constant getDefiningConstant()
        {
        Constant constId = m_constId;
        if (constId instanceof ResolvableConstant)
            {
            Constant constResolved = ((ResolvableConstant) constId).getResolvedConstant();
            if (constResolved != null)
                {
                m_constId = constId = constResolved;
                }
            }
        return constId;
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return m_constId.isAutoNarrowing();
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        return (TypeConstant) simplify();
        }

    @Override
    public boolean isOnlyNullable()
        {
        TypeConstant typeResolved = resolveTypedefs();
        return this == typeResolved
                ? m_constId.equals(getConstantPool().clzNullable())
                : typeResolved.isOnlyNullable();
        }

    @Override
    protected TypeConstant unwrapForCongruence()
        {
        TypeConstant typeResolved = resolveTypedefs();
        return typeResolved == this
                ? this
                : typeResolved.unwrapForCongruence();
        }

    @Override
    public boolean extendsClass(IdentityConstant constClass)
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
            case Class:
                return ((ClassStructure) ((IdentityConstant) constant)
                        .getComponent()).extendsClass(constClass);

            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).extendsClass(constClass);

            case Property:
                return getPropertyTypeConstant((PropertyConstant) constant).extendsClass(constClass);

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constant).extendsClass(constClass);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((ClassStructure) ((PseudoConstant) constant).getDeclarationLevelClass()
                        .getComponent()).extendsClass(constClass);

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constant);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public boolean impersonatesClass(IdentityConstant constClass)
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
            case Class:
                return ((ClassStructure) ((IdentityConstant) constant)
                        .getComponent()).impersonatesClass(constClass);

            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).impersonatesClass(constClass);

            case Property:
                return getPropertyTypeConstant((PropertyConstant) constant).impersonatesClass(constClass);

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constant).impersonatesClass(constClass);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((ClassStructure) ((PseudoConstant) constant).getDeclarationLevelClass()
                        .getComponent()).impersonatesClass(constClass);

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constant);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public boolean extendsOrImpersonatesClass(IdentityConstant constClass)
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
            case Class:
                return ((ClassStructure) ((IdentityConstant) constant)
                        .getComponent()).extendsOrImpersonatesClass(constClass);

            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).extendsOrImpersonatesClass(
                    constClass);

            case Property:
                return getPropertyTypeConstant((PropertyConstant) constant).extendsOrImpersonatesClass(constClass);

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constant).extendsOrImpersonatesClass(constClass);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                return ((ClassStructure) ((PseudoConstant) constant).getDeclarationLevelClass()
                        .getComponent()).extendsOrImpersonatesClass(constClass);

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constant);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public boolean isClassType()
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
                // these are always class types (not interface types)
                return true;

            case Class:
                {
                // examine the structure to determine if it represents a class or interface
                ClassStructure clz = (ClassStructure) ((ClassConstant) constant).getComponent();
                return clz.getFormat() != Component.Format.INTERFACE;
                }

            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).isClassType();

            case Property:
                return getPropertyTypeConstant((PropertyConstant) constant).isClassType();

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constant).isClassType();

            case ThisClass:
            case ParentClass:
            case ChildClass:
                {
                ClassStructure clz = (ClassStructure) ((PseudoConstant) constant)
                        .getDeclarationLevelClass().getComponent();
                return clz.getFormat() != Component.Format.INTERFACE;
                }

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constant);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public boolean isSingleUnderlyingClass()
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).isSingleUnderlyingClass();

            case Property:
                return getPropertyTypeConstant((PropertyConstant) constant).isSingleUnderlyingClass();

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constant).isSingleUnderlyingClass();

            default:
                return isClassType();
            }
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass()
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Module:
            case Package:
                // these are always class types (not interface types)
                return (IdentityConstant) constant;

            case Class:
                // must not be an interface
                assert ((ClassStructure) ((ClassConstant) constant).getComponent()).getFormat() != Component.Format.INTERFACE;
                return (IdentityConstant) constant;

            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).getSingleUnderlyingClass();

            case Property:
                return getPropertyTypeConstant((PropertyConstant) constant).getSingleUnderlyingClass();

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constant).getSingleUnderlyingClass();

            case ParentClass:
            case ChildClass:
            case ThisClass:
                return ((PseudoConstant) constant).getDeclarationLevelClass();

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constant);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constant);
            }
        }

    @Override
    public Set<IdentityConstant> underlyingClasses()
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).underlyingClasses();

            case Property:
                return getPropertyTypeConstant((PropertyConstant) constant).underlyingClasses();

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constant).underlyingClasses();

            default:
                return isSingleUnderlyingClass()
                        ? Collections.singleton(getSingleUnderlyingClass())
                        : Collections.EMPTY_SET;
            }
        }

    @Override
    public boolean isConstant()
        {
        Constant constant = getDefiningConstant();
        switch (constant.getFormat())
            {
            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constant).isConstant();

            case Property:
            case Register:
                return false;

            default:
                return true;
            }
        }

    @Override
    public <T extends TypeConstant> T findFirst(Class<? extends TypeConstant> clz)
        {
        return clz == getClass() ? (T) this : null;
        }

    /**
     * Dereference a typedef constant to find the type to which it refers.
     *
     * @param constTypedef  a typedef constant
     *
     * @return the type that the typedef refers to
     */
    public static TypeConstant getTypedefTypeConstant(TypedefConstant constTypedef)
        {
        return ((TypedefStructure) constTypedef.getComponent()).getType();
        }

    /**
     * Dereference a property constant that is used for a type parameter, to obtain the constraint
     * type of that type parameter.
     *
     * @param constProp the property constant for the property that holds the type parameter type
     *
     * @return the constraint type of the type parameter
     */
    public static TypeConstant getPropertyTypeConstant(PropertyConstant constProp)
        {
        // the type points to a property, which means that the type is a parameterized type;
        // the type of the property will be "Type<X>", so return X
        TypeConstant typeProp = ((PropertyStructure) constProp.getComponent()).getType();
        assert typeProp.isEcstasy("Type") && typeProp.isParamsSpecified();
        return typeProp.getParamTypes().get(0);
        }

    /**
     * Dereference a register constant that is used for a type parameter, to obtain the constraint
     * type of that type parameter.
     *
     * @param constReg  the register constant for the register that holds the type parameter type
     *
     * @return the constraint type of the type parameter
     */
    public static TypeConstant getRegisterTypeConstant(RegisterConstant constReg)
        {
        // the type points to a register, which means that the type is a parameterized type;
        // the type of the register will be "Type<X>", so return X
        MethodConstant   constMethod = constReg.getMethod();
        int              nReg        = constReg.getRegister();
        TypeConstant[]   atypeParams = constMethod.getRawParams();
        assert atypeParams.length > nReg;
        TypeConstant     typeParam   = atypeParams[nReg];
        assert typeParam.isEcstasy("Type") && typeParam.isParamsSpecified();
        return typeParam.getParamTypes().get(0);
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Contribution checkAssignableTo(TypeConstant that)
        {
        Constant constIdThis = this.getDefiningConstant();
        Constant constIdThat = that.getDefiningConstant();

        if (constIdThis.equals(constIdThat))
            {
            return ((IdentityConstant) constIdThis).getComponent().
                new Contribution(Component.Composition.Equal, null);
            }

        switch (constIdThis.getFormat())
            {
            case Module:
            case Package:
                return null;

            case Class:
                switch (constIdThat.getFormat())
                    {
                    case Module:
                    case Package:
                        return null;

                    case Class:
                        // scenarios we can cover here are:
                        // 1. this extends that (recursively)
                        // 2. this or any of its contributions (recursively) impersonates that
                        // 3. this or any of its contributions (recursively) incorporates that
                        // 4. this or any of its contributions (recursively) delegates to that
                        // 5. this or any of its contributions (recursively) declares to implement that
                        {
                        ClassStructure clzThis = (ClassStructure) ((IdentityConstant) constIdThis).getComponent();
                        return clzThis.findContribution((IdentityConstant) constIdThat);
                        }

                    case Typedef:
                        return this.checkAssignableTo(getTypedefTypeConstant((TypedefConstant) constIdThat));

                    case Property:
                        PropertyConstant constProp = (PropertyConstant) constIdThat;
                        TypeConstant typeConstraint = constProp.getRefType();
                        return this.checkAssignableTo(typeConstraint);

                    case Register:
                        return this.checkAssignableTo(getRegisterTypeConstant((RegisterConstant) constIdThat));

                    case ThisClass:
                    case ParentClass:
                    case ChildClass:
                        ClassStructure clz = (ClassStructure)
                            ((PseudoConstant) constIdThis).getDeclarationLevelClass().getComponent();
                        return clz.getIdentityConstant().asTypeConstant().checkAssignableTo(that);

                    case UnresolvedName:
                        throw new IllegalStateException("unexpected unresolved-name constant: " + constIdThis);

                    default:
                        throw new IllegalStateException("unexpected defining constant: " + constIdThis);
                    }

            case Typedef:
                return getTypedefTypeConstant((TypedefConstant) constIdThis).checkAssignableTo(that);

            case Property:
                // scenario we can handle here is:
                // 1. this = T (formal parameter type), constrained by U (other formal type)
                //    that = U (formal parameter type)
                //
                // 2. this = T (formal parameter type), constrained by U (real type)
                //    that = V (real type), where U "is a" V

                PropertyConstant constProp = (PropertyConstant) constIdThis;
                TypeConstant typeConstraint = constProp.getRefType();

                return typeConstraint.checkAssignableTo(that);

            case Register:
                return getRegisterTypeConstant((RegisterConstant) constIdThis).checkAssignableTo(that);

            case ThisClass:
            case ParentClass:
            case ChildClass:
                ClassStructure clz = (ClassStructure)
                    ((PseudoConstant) constIdThis).getDeclarationLevelClass().getComponent();
                return clz.getIdentityConstant().asTypeConstant().checkAssignableTo(that);

            case UnresolvedName:
                throw new IllegalStateException("unexpected unresolved-name constant: " + constIdThis);

            default:
                throw new IllegalStateException("unexpected defining constant: " + constIdThis);
            }
        }

    @Override
    protected Contribution checkAssignableFrom(TypeConstant that)
        {
        return null;
        }

    @Override
    public boolean consumesFormalType(String sTypeName, TypeSet types, Access access)
        {
        return false;
        }

    @Override
    public boolean producesFormalType(String sTypeName, TypeSet types, Access access)
        {
        Constant constId = getDefiningConstant();

        return constId.getFormat() == Format.Property &&
            ((PropertyConstant) constId).getName().equals(sTypeName);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.TerminalType;
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_constId.containsUnresolved();
        }

    @Override
    public Constant simplify()
        {
        m_constId = m_constId.simplify();

        // compile down all of the types that refer to typedefs so that they refer to the underlying
        // types instead
        if (m_constId instanceof TypedefConstant)
            {
            Component    typedef   = ((TypedefConstant) m_constId).getComponent();
            TypeConstant constType;
            if (typedef instanceof CompositeComponent)
                {
                List<Component> typdefs = ((CompositeComponent) typedef).components();
                constType = (TypeConstant) ((TypedefStructure) typdefs.get(0)).getType().simplify();
                for (int i = 1, c = typdefs.size(); i < c; ++i)
                    {
                    TypeConstant constTypeN = (TypeConstant) ((TypedefStructure) typdefs.get(i)).getType().simplify();
                    if (!constType.equals(constTypeN))
                        {
                        // typedef points to more than one type, conditionally, so just leave the
                        // typedef in place
                        return this;
                        }
                    }
                }
            else
                {
                constType = (TypeConstant) ((TypedefStructure) typedef).getType().simplify();
                }
            assert constType != null;
            return constType;
            }

        return this;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constId);
        }

    @Override
    protected Object getLocator()
        {
        return m_constId.getFormat() == Format.UnresolvedName
                ? null
                : m_constId;
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        TerminalTypeConstant that = (TerminalTypeConstant) obj;
        Constant constThis = this.m_constId;
        if (constThis instanceof ResolvableConstant)
            {
            constThis = ((ResolvableConstant) constThis).unwrap();
            }
        Constant constThat = that.m_constId;
        if (constThat instanceof ResolvableConstant)
            {
            constThat = ((ResolvableConstant) constThat).unwrap();
            }
        return constThis.compareTo(constThat);
        }

    @Override
    public String getValueString()
        {
        return m_constId.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constId = getConstantPool().getConstant(m_iDef);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constId = pool.register(m_constId);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constId.getPosition());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constId.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that defines this type.
     */
    private transient int m_iDef;

    /**
     * The class referred to. May be an IdentityConstant (ModuleConstant, PackageConstant,
     * ClassConstant, TypedefConstant, PropertyConstant), or a PseudoConstant (ThisClassConstant,
     * ParentClassConstant, ChildClassConstant, RegisterConstant, or UnresolvedNameConstant).
     */
    private Constant m_constId;
    }
