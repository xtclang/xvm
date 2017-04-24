package org.xvm.asm;


import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.StructureContainer.MethodContainer;


/**
 * An XVM Structure that represents a property.
 *
 * @author cp 2016.04.25
 */
public class PropertyStructure
        extends MethodContainer
    {
    /**
     * Construct a PropertyStructure with the specified identity.
     *
     * @param structParent    the XvmStructure (probably a FileStructure, a
     *                        ModuleStructure, a PackageStructure, a
     *                        ClassStructure, or a MethodStructure) that
     *                        contains this PropertyStructure
     * @param constproperty   the constant that specifies the identity of the
     *                        property
     */
    public PropertyStructure(XvmStructure structParent, PropertyConstant constproperty)
        {
        super(structParent, constproperty);
        }


    // ----- accessors ---------------------------------------------------------

    /**
     * Obtain the PropertyConstant that holds the identity of this Property.
     *
     * @return the PropertyConstant representing the identity of this
     *         PropertyStructure
     */
    public PropertyConstant getPropertyConstant()
        {
        return (PropertyConstant) getIdentityConstant();
        }


    // ----- data members ------------------------------------------------------

    }
