package org.xvm.asm;


import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MultiMethodConstant;
import org.xvm.asm.constants.TypeConstant;

import java.util.List;


/**
 * An XVM Structure that represents a multi-method, which is a group of methods that share a name.
 * The multi-method does not have a corresponding development-time analogy; a developer does NOT
 * declare or define a multi-method. Instead, it is a compile-time construction, used to collect
 * together methods that share a name into a group, within which they are identified by a more
 * exacting set of attributes, namely their accessibility and their parameter/return types.
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
     * @param annotations  the annotations
     * @param aReturns     the return values (zero or more)
     * @param aParams      the parameters (zero or more)
     * @param fUsesSuper   true indicates that the method is known to reference "super"
     *
     * @return a method structure
     */
    public MethodStructure createMethod(boolean fFunction, Access access, Annotation[] annotations,
            Parameter[] aReturns, Parameter[] aParams, boolean fUsesSuper)
        {
        int nFlags   = Format.METHOD.ordinal() | access.FLAGS | (fFunction ? Component.STATIC_BIT : 0);
        int cReturns = aReturns.length;
        int cParams  = aParams.length;

        if (annotations == null)
            {
            annotations = Annotation.NO_ANNOTATIONS;
            }

        TypeConstant[] aconstReturns = new TypeConstant[cReturns];
        TypeConstant[] aconstParams  = new TypeConstant[cParams ];

        for (int i = 0; i < cReturns; ++i)
            {
            Parameter param = aReturns[i];
            if (param.isConditionalReturn())
                {
                if (i > 0 || !param.getType().isEcstasy("Boolean"))
                    {
                    throw new IllegalArgumentException("only the first return value can be conditional, and it must be a boolean");
                    }
                }
            aconstReturns[i] = param.getType();
            }

        boolean fPastTypeParams = false;
        for (int i = 0; i < cParams; ++i)
            {
            Parameter param = aParams[i];
            if (param.isTypeParameter())
                {
                if (fPastTypeParams)
                    {
                    throw new IllegalArgumentException("type params must come first (" + i + ")");
                    }
                if (!param.getType().isEcstasy("Type"))
                    {
                    throw new IllegalArgumentException("type params must be of type \"Type\" (" + param.getType() + ")");
                    }
                }
            else
                {
                fPastTypeParams = true;
                }
            aconstParams[i] = param.getType();
            }

        MethodConstant constId = getConstantPool().ensureMethodConstant(
                getIdentityConstant(), getName(), aconstParams, aconstReturns);
        MethodStructure struct = new MethodStructure(this, nFlags, constId, null, annotations,
                aReturns, aParams, fUsesSuper);
        addChild(struct);
        return struct;
        }

    /**
     * Helper method to return a list of methods.
     */
    public List<MethodStructure> methods()
        {
        return (List<MethodStructure>) (List) super.children();
        }

    @Override
    public boolean isAutoNarrowingAllowed()
        {
        return getParent().isAutoNarrowingAllowed();
        }
    }
