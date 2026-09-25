package org.xvm.runtime.template._native.fs;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.FSNodeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ConstantHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xConst;

/**
 * Native CPFile implementation.
 */
public class xCPFile
        extends xConst {
    public xCPFile(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof FSNodeConstant constFile && constant.getFormat() == Format.FSFile) {
            TypeComposition clz    = ensureClass(frame.f_context.f_container,
                                        getCanonicalType(), frame.poolContext().typeFile());
            GenericHandle  hStruct = new GenericHandle(clz.ensureAccess(Access.STRUCT));

            // The constructor must belong to the composition's prepared application declaration.
            ClassStructure structure = (ClassStructure) clz.getInceptionType()
                    .getSingleUnderlyingClass(true).getComponent();
            MethodStructure constructor = structure.findConstructor(frame.poolContext().typeObject());
            ObjectHandle[] ahVar = Utils.ensureSize(Utils.OBJECTS_NONE, frame.getMaxVars(constructor));
            ahVar[0] = new ConstantHandle(constFile);

            return proceedConstruction(frame, constructor, true, hStruct, ahVar, Op.A_STACK);
        }

        return super.createConstHandle(frame, constant);
    }
}
