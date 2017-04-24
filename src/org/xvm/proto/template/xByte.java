package org.xvm.proto.template;

import org.xvm.asm.Constant;
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
public class xByte
        extends TypeCompositionTemplate
    {
    public xByte(TypeSet types)
        {
        super(types, "x:Byte", "x:Object", Shape.Const);
        }

    @Override
    public void initDeclared()
        {
        //    construct Bit(IntLiteral literal)     // TODO
        //
        //    private IntLiteral literal;
        //    static Byte defaultValue = 0;        // TODO
        //    IntLiteral to<IntLiteral>()
        //    Boolean to<Boolean>()
        //    @auto Bit to<Bit>()
        //    @auto Int to<Int>()
        //    @auto UInt to<UInt>()
        //    @op Byte and(Byte that)
        //    @op Byte or(Byte that)
        //    @op Byte xor(Byte that)
        //    @op Byte not()

        PropertyTemplate ptLiteral = ensurePropertyTemplate("literal", "x:IntLiteral");
        ptLiteral.setGetAccess(Access.Private);
        ptLiteral.setSetAccess(Access.Private);

        ensureMethodTemplate("to", new String[]{"x:IntLiteral"}, new String[]{"x:IntLiteral"});
        ensureMethodTemplate("to", BOOLEAN, BOOLEAN);
        ensureMethodTemplate("to", new String[]{"x:Bit"}, new String[]{"x:Bit"});
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
            (((IntConstant) constant).getValue().getLong() | 0xFF)) : null;
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new ObjectHandle.JavaLong(clazz);
        }
    }
