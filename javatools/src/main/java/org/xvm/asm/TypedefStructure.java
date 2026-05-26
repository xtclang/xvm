package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.xvm.asm.Constants.Access;

import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypedefConstant;

import org.xvm.util.ListMap;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writeMagnitude;


/**
 * An XVM Structure that represents a "typedef" statement, which acts as a way to name an arbitrary
 * type, by associating a named structure (this) with a type constant.
 */
public class TypedefStructure
        extends Component {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a TypeDefStructure with the specified identity.
     *
     * @param xsParent   the XvmStructure that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Typedef
     * @param condition  the optional condition for this TypeDefStructure
     */
    protected TypedefStructure(XvmStructure xsParent, int nFlags, TypedefConstant constId,
            ConditionalConstant condition) {
        super(xsParent, nFlags, constId, condition);
    }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public TypedefConstant getIdentityConstant() {
        return (TypedefConstant) super.getIdentityConstant();
    }

    /**
     * @return the TypeConstant representing the data type of the typedef
     */
    public TypeConstant getType() {
        return m_type;
    }

    @Override
    public boolean isClassContainer() {
        return true;
    }

    /**
     * Configure the typedef's type.
     *
     * @param type  the type constant that indicates the typedef's type
     */
    public void setType(TypeConstant type) {
        assert type != null;
        m_type = type;
    }

    /**
     * Add a generic type parameter owned by this typedef.
     *
     * @param sName           the parameter name
     * @param typeConstraint  the parameter constraint
     *
     * @return the synthetic property that backs the formal type
     */
    public PropertyStructure addTypeParam(String sName, TypeConstant typeConstraint) {
        ConstantPool pool = getConstantPool();

        if (typeConstraint.getParamsCount() >= 1 &&
                typeConstraint.isTuple() &&
                typeConstraint.getParamType(0).getValueString().equals(sName)) {
            typeConstraint = pool.ensureTypeSequenceTypeConstant();
        }

        TypeConstant typeConstraintType =
                pool.ensureClassTypeConstant(pool.clzType(), null, typeConstraint);

        PropertyStructure prop = createProperty(false, Access.PUBLIC, Access.PUBLIC,
                typeConstraintType, sName);
        prop.markAsGenericTypeParameter();
        markModified();
        return prop;
    }

    /**
     * @return the number of type parameters owned by this typedef
     */
    public int getTypeParamCount() {
        return getTypeParamProperties().size();
    }

    /**
     * @return true iff this typedef declares generic type parameters
     */
    public boolean isParameterized() {
        return getTypeParamCount() > 0;
    }

    /**
     * Obtain the type parameters for this typedef in declaration order.
     *
     * @return the ordered list of type parameter names and constraint types
     */
    public List<Map.Entry<String, TypeConstant>> getTypeParamsAsList() {
        List<PropertyStructure> listProps = getTypeParamProperties();
        List<Map.Entry<String, TypeConstant>> list = new ArrayList<>(listProps.size());
        for (PropertyStructure prop : listProps) {
            list.add(Map.entry(prop.getName(), extractConstraint(prop)));
        }
        return list;
    }

    /**
     * Update (narrow) the constraint type for the specified generic type.
     *
     * @param sName           the generic type name
     * @param typeConstraint  the new constraint type
     */
    public void updateConstraint(String sName, TypeConstant typeConstraint) {
        PropertyStructure prop = (PropertyStructure) getChild(sName);
        if (prop == null) {
            throw new IllegalArgumentException("unknown typedef type parameter: " + sName);
        }

        ConstantPool pool = getConstantPool();
        TypeConstant typeConstraintType =
                pool.ensureClassTypeConstant(pool.clzType(), null, typeConstraint);

        prop.setType(typeConstraintType);
        markModified();
    }

    /**
     * Resolve the typedef's formal type parameters against the specified actual types.
     *
     * @param typeAlias    the current typedef type template
     * @param atypeParams  the actual type parameters
     *
     * @return the resulting resolved type
     */
    public TypeConstant resolveTypeParameters(TypeConstant typeAlias, TypeConstant[] atypeParams) {
        List<PropertyStructure> listParams = getTypeParamProperties();
        if (listParams.size() != atypeParams.length) {
            return typeAlias;
        }

        Map<FormalConstant, TypeConstant> mapResolve = new ListMap<>();
        for (int i = 0, c = atypeParams.length; i < c; ++i) {
            PropertyConstant idFormal = listParams.get(i).getIdentityConstant();
            mapResolve.put(idFormal, atypeParams[i]);
        }

        return typeAlias.resolveGenerics(getConstantPool(), GenericTypeResolver.of(mapResolve));
    }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
    throws IOException {
        super.disassemble(in);

        m_type = getConstantPool().getConstant(readIndex(in), TypeConstant.class);
    }

    @Override
    protected void registerConstants(ConstantPool pool) {
        super.registerConstants(pool);

        m_type = pool.register(m_type);
    }

    @Override
    protected void assemble(DataOutput out)
    throws IOException {
        super.assemble(out);

        writeMagnitude(out, m_type.getPosition());
    }

    @Override
    public String getDescription() {
        return "type=" + m_type + ", " + super.getDescription();
    }

    private List<PropertyStructure> getTypeParamProperties() {
        List<PropertyStructure> list = new ArrayList<>();
        for (Component child : getChildByNameMap().values()) {
            if (child instanceof PropertyStructure prop && prop.isGenericTypeParameter()) {
                list.add(prop);
            }
        }
        return list;
    }

    private TypeConstant extractConstraint(PropertyStructure prop) {
        return prop.getType().getParamType(0);
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The actual type that the typedef represents.
     */
    private TypeConstant m_type;
}
