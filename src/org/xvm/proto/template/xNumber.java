package org.xvm.proto.template;

import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;


/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xNumber
        extends TypeCompositionTemplate
    {
    public xNumber(TypeSet types)
        {
        super(types, "x:Number", "x:Object", Shape.Interface);
        }

    @Override
    public void initDeclared()
        {
        //  enum Signum(String prefix, Int factor, Ordered ordered)
        //        {
        //        Negative("-", -1, Lesser ),
        //        Zero    ("" ,  0, Equal  ),
        //        Positive("+", +1, Greater)
        //        }
        //    @ro Int bitLength;
        //    @ro Int byteLength.get()
        //    @ro Signum sign;               // TODO
        //    @op Number add(Number n);
        //    @op Number sub(Number n);
        //    @op Number mul(Number n);
        //    @op Number div(Number n);
        //    @op Number mod(Number n);
        //    @op (Number quotient, Number modulo) divmod(Number n)
        //    Number remainder(Number n)
        //    Number abs()
        //    @op Number neg();
        //    Number pow(Number n);
        //    Number atMost(Number n)
        //    Number atLeast(Number n)

        ensurePropertyTemplate("bitLength", "x:Int").makeReadOnly();
        ensurePropertyTemplate("byteLength", "x:Int").makeReadOnly(); // TODO: get

        ensureMethodTemplate("add", THIS, THIS);
        ensureMethodTemplate("sub", THIS, THIS);
        ensureMethodTemplate("mul", THIS, THIS);
        ensureMethodTemplate("div", THIS, THIS);
        ensureMethodTemplate("mod", THIS, THIS);
        ensureMethodTemplate("mod", THIS, THIS);
        ensureMethodTemplate("divmod", new String[]{"this.Type", "this.Type"}, new String[]{"this.Type", "this.Type"});
        ensureMethodTemplate("remainder", THIS, THIS);
        ensureMethodTemplate("abs", VOID, THIS);
        ensureMethodTemplate("neg", VOID, THIS);
        ensureMethodTemplate("power", THIS, THIS);
        ensureMethodTemplate("atMost", THIS, THIS);
        ensureMethodTemplate("atLeast", THIS, THIS);

        // TODO conversions
        }
    }
