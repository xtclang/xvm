package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants;
import org.xvm.asm.constants.IntConstant;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.ObjectHeap;
import org.xvm.proto.ClassTemplate;
import org.xvm.proto.TypeSet;


/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xBit
        extends ClassTemplate
    {
    public xBit(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);
        }

    @Override
    public void initDeclared()
        {
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
        {
        return constant instanceof IntConstant ? new JavaLong(f_clazzCanonical,
                ((IntConstant) constant).getValue().getLong()) : null;
        }

    @Override
    public boolean callEquals(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return h1.getValue() == h2.getValue();
        }
    }
