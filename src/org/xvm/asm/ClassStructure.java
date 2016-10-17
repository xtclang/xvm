package org.xvm.asm;


import org.xvm.asm.ConstantPool.ClassConstant;
import org.xvm.asm.StructureContainer.MethodContainer;




/**
 * An XVM Structure that represents an entire Class.
 *
 * @author cp 2016.04.14
 */
public class ClassStructure
        extends MethodContainer
    {
    /**
     * Construct a ClassStructure with the specified identity.
     *
     * @param structParent  the XvmStructure (probably a FileStructure, a
     *                      ModuleStructure, a PackageStructure, a
     *                      ClassStructure, or a MethodStructure) that
     *                      contains this ClassStructure
     * @param constclass    the constant that specifies the identity of the
     *                      Class
     */
    ClassStructure(XvmStructure structParent, ConstantPool.ClassConstant constclass)
        {
        super(structParent, constclass);
        }


    // ----- accessors ---------------------------------------------------------

    /**
     * Obtain the ClassConstant that holds the identity of this Class.
     *
     * @return the ClassConstant representing the identity of this
     *         ClassStructure
     */
    public ClassConstant getClassConstant()
        {
        return (ClassConstant) getIdentityConstant();
        }
    }
