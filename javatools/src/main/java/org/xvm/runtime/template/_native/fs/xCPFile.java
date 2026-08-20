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

import org.xvm.util.Lazy;


/**
 * Native CPFile implementation.
 */
public class xCPFile
        extends xConst {
    public xCPFile(Container container, ClassStructure structure) {
        super(container, structure, false);
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof FSNodeConstant constFile && constant.getFormat() == Format.FSFile) {
            TypeComposition clz    = ensureClass(frame.f_context.f_container,
                                        getCanonicalType(), frame.poolContext().typeFile());
            GenericHandle  hStruct = new GenericHandle(clz.ensureAccess(Access.STRUCT));
            MethodStructure constructor = f_constructor.get();
            ObjectHandle[]  ahVar       = Utils.ensureSize(Utils.OBJECTS_NONE,
                    constructor.getMaxVars());
            ahVar[0] = new ConstantHandle(frame.container(), constFile);

            return proceedConstruction(frame, constructor, true, hStruct, ahVar, Op.A_STACK);
        }

        return super.createConstHandle(frame, constant);
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Preserves the old one-time constructor lookup, but scopes the cached
     * MethodStructure to this template's owning ClassStructure instead of a
     * process-global static.
     */
    private final Lazy<MethodStructure> f_constructor = Lazy.of(() ->
            getStructure().findConstructor(f_container.getConstantPool().typeObject()));
}
