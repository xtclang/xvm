package org.xvm.asm;


import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.StructureContainer.ClassContainer;


/**
 * An XVM Structure that represents a method.
 *
 * @author cp 2016.04.25
 */
public class MethodStructure
        extends ClassContainer
    {
    /**
     * Construct a MethodStructure with the specified identity.
     *
     * @param structParent  the XvmStructure (probably a FileStructure, a
     *                      ModuleStructure, a PackageStructure, a
     *                      ClassStructure, a PropertyStructure, or a
     *                      MethodStructure) that contains this MethodStructure
     * @param constmethod   the constant that specifies the identity of the
     *                      method
     */
    MethodStructure(XvmStructure structParent, MethodConstant constmethod)
        {
        super(structParent, constmethod);
        }


    // ----- accessors ---------------------------------------------------------

    /**
     * Obtain the MethodConstant that holds the identity of this Method.
     *
     * @return the MethodConstant representing the identity of this
     *         MethodStructure
     */
    public MethodConstant getMethodConstant()
        {
        return (MethodConstant) getIdentityConstant();
        }


    // ----- data members ------------------------------------------------------

    }
