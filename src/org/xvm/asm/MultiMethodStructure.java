package org.xvm.asm;


import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.TypeConstant;


/**
 * An XVM Structure that represents a multi-method, which is a group of methods that share a name.
 * The multi-method does not have a corresponding development-time analogy; a developer does NOT
 * declare or define a multi-method. Instead, it is a compile-time construction, used to collect
 * together methods that share a name into a group, within which they are identified by a more
 * exacting set of attributes, namely their accessibility and their parameter/return types.
 *
 * @author cp 2016.04.26
 */
public class MultiMethodStructure
        extends Component
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a MultiMethodStructure with the specified identity.
     *
     * @param xsParent   the XvmStructure (probably a FileStructure) that contains this structure
     * @param nFlags     the Component bit flags
     * @param constId    the constant that specifies the identity of the Module
     * @param condition  the optional condition for this ModuleStructure
     */
    protected MultiMethodStructure(XvmStructure xsParent, int nFlags, MultiMethodConstant constId, ConditionalConstant condition)
        {
        super(xsParent, nFlags, constId, condition);
        assert Format.fromFlags(nFlags) == Format.MULTIMETHOD;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * Create the method with the specified attributes.
     *
     * @param fFunction    true if the structure being created is a function; false means a method
     * @param access       an access specifier
     * @param returnTypes  the types of the return values (zero or more)
     * @param paramTypes   the types of the parameters (zero or more)
     *
     * @return a method structure
     */
    public MethodStructure createMethod(boolean fFunction, Access access, TypeConstant[] returnTypes, TypeConstant[] paramTypes)
        {
        int nFlags = Format.METHOD.ordinal() | access.FLAGS | (fFunction ? Component.STATIC_BIT : 0);
        MethodConstant constId = getConstantPool().ensureMethodConstant(
                getIdentityConstant(), getName(), access, returnTypes, paramTypes);
        MethodStructure struct = new MethodStructure(this, nFlags, constId, null);
        addChild(struct);
        return struct;
        }
    }
