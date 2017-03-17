package org.xvm.proto.template;

import org.xvm.proto.TypeSet;


/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xIntNumber
        extends xObject
    {
    public xIntNumber(TypeSet types)
        {
        super(types, "x:IntNumber", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        addImplement("x:Number");
        addImplement("x:Sequential");

        //    @op IntNumber increment()
        //    @op IntNumber decrement()
        //    @op IntNumber and(IntNumber that);
        //    @op IntNumber or(IntNumber that);
        //    @op IntNumber xor(IntNumber that);
        //    @op IntNumber not();
        //    @op IntNumber shiftLeft(Int count);
        //    @op IntNumber shiftRight(Int count);
        //    @op IntNumber shiftAllRight(Int count);
        //    IntNumber rotateLeft(Int count);
        //    IntNumber rotateRight(Int count);
        //    IntNumber truncate(Int count);
        //    @ro IntNumber leftmostBit;
        //    @ro IntNumber rightmostBit;
        //    @ro IntNumber leadingZeroCount;
        //    @ro IntNumber trailingZeroCount;
        //    @ro IntNumber bitCount;
        //    IntNumber reverseBits();
        //    IntNumber reverseBytes();
        //    Range<IntNumber> to(IntNumber that);
        //    Boolean[] to<Boolean[]>()

        addMethodTemplate("nextValue", VOID, THIS);
        addMethodTemplate("prevValue", VOID, THIS);
        addMethodTemplate("next", VOID, CONDITIONAL_THIS);
        addMethodTemplate("prev", VOID, CONDITIONAL_THIS);
        addMethodTemplate("and", THIS, THIS);
        addMethodTemplate("or", THIS, THIS);
        addMethodTemplate("xor", THIS, THIS);
        addMethodTemplate("not", VOID, THIS);
        addMethodTemplate("shiftLeft", THIS, THIS);
        addMethodTemplate("shiftRight", THIS, THIS);
        addMethodTemplate("shiftAllRight", THIS, THIS);
        addMethodTemplate("rotateLeft", THIS, THIS);
        addMethodTemplate("rotateRight", THIS, THIS);
        addMethodTemplate("truncate", THIS, THIS);
        addPropertyTemplate("leftmostBit", "this.Type").makeReadOnly();
        addPropertyTemplate("rightmostBit", "this.Type").makeReadOnly();
        addPropertyTemplate("leadingZeroCount", "this.Type").makeReadOnly();
        addPropertyTemplate("trailingZeroCount", "this.Type").makeReadOnly();
        addPropertyTemplate("bitCount", "this.Type").makeReadOnly();
        addMethodTemplate("reverseBits", VOID, THIS);
        addMethodTemplate("reverseBytes", VOID, THIS);
        addMethodTemplate("to", THIS, new String[] {"x:Range<x:Int>"});
        addMethodTemplate("to", new String[]{"x:collections.Array<x:Boolean>"}, new String[]{"x:collections.Array<x:Boolean>"});
        }
    }
