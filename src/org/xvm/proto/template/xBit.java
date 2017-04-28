package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.Constants;
import org.xvm.asm.constants.IntConstant;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.JavaLong;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;


/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xBit
        extends TypeCompositionTemplate
    {
    public xBit(TypeSet types)
        {
        super(types, "x:Bit", "x:Object", Shape.Const);
        }

    @Override
    public void initDeclared()
        {
        //    construct Bit(IntLiteral literal)     // TODO
        //
        //    private IntLiteral literal;
        //    static Bit defaultValue = 0;        // TODO
        //    IntLiteral to<IntLiteral>()
        //    Boolean to<Boolean>()
        //    @auto Byte to<Byte>()
        //    @auto Int to<Int>()
        //    @auto UInt to<UInt>()
        //    @op Bit and(Bit that)
        //    @op Bit or(Bit that)
        //    @op Bit xor(Bit that)
        //    @op Bit not()

        PropertyTemplate ptLiteral = ensurePropertyTemplate("literal", "x:IntLiteral");
        ptLiteral.setGetAccess(Constants.Access.PRIVATE);
        ptLiteral.setSetAccess(Constants.Access.PRIVATE);

        ensureMethodTemplate("to", new String[]{"x:IntLiteral"}, new String[]{"x:IntLiteral"});
        ensureMethodTemplate("to", BOOLEAN, BOOLEAN);
        ensureMethodTemplate("to", new String[]{"x:Byte"}, new String[]{"x:Byte"});
        ensureMethodTemplate("to", INT, INT);
        // addMethodTemplate("to", new String[] {"x:UInt64"}, new String[] {"x:UInt64"});

        ensureMethodTemplate("and", THIS, THIS);
        ensureMethodTemplate("or", THIS, THIS);
        ensureMethodTemplate("xor", THIS, THIS);
        ensureMethodTemplate("not", VOID, THIS);
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant)
        {
        return constant instanceof IntConstant ? new JavaLong(f_clazzCanonical,
                ((IntConstant) constant).getValue().getLong()) : null;
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new JavaLong(clazz);
        }
    }
