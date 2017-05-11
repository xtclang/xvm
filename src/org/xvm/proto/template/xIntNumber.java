package org.xvm.proto.template;

import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;


/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xIntNumber
        extends TypeCompositionTemplate
    {
    public xIntNumber(TypeSet types)
        {
        super(types, "x:IntNumber", "x:Object", Shape.Interface);

        addImplement("x:Number");
        addImplement("x:Sequential");
        }

    @Override
    public void initDeclared()
        {
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

        ensureMethodTemplate("nextValue", VOID, THIS);
        ensureMethodTemplate("prevValue", VOID, THIS);
        ensureMethodTemplate("next", VOID, CONDITIONAL_THIS);
        ensureMethodTemplate("prev", VOID, CONDITIONAL_THIS);
        ensureMethodTemplate("and", THIS, THIS);
        ensureMethodTemplate("or", THIS, THIS);
        ensureMethodTemplate("xor", THIS, THIS);
        ensureMethodTemplate("not", VOID, THIS);
        ensureMethodTemplate("shiftLeft", THIS, THIS);
        ensureMethodTemplate("shiftRight", THIS, THIS);
        ensureMethodTemplate("shiftAllRight", THIS, THIS);
        ensureMethodTemplate("rotateLeft", THIS, THIS);
        ensureMethodTemplate("rotateRight", THIS, THIS);
        ensureMethodTemplate("truncate", THIS, THIS);
        ensurePropertyTemplate("leftmostBit", "this.Type").makeReadOnly();
        ensurePropertyTemplate("rightmostBit", "this.Type").makeReadOnly();
        ensurePropertyTemplate("leadingZeroCount", "this.Type").makeReadOnly();
        ensurePropertyTemplate("trailingZeroCount", "this.Type").makeReadOnly();
        ensurePropertyTemplate("bitCount", "this.Type").makeReadOnly();
        ensureMethodTemplate("reverseBits", VOID, THIS);
        ensureMethodTemplate("reverseBytes", VOID, THIS);
        ensureMethodTemplate("to", THIS, new String[]{"x:Range<x:Int>"});
        ensureMethodTemplate("to", VOID, new String[]{"x:collections.Array<x:Boolean>"});
        }
    }
