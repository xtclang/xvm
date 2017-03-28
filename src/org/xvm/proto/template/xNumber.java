package org.xvm.proto.template;

import org.xvm.proto.TypeSet;


/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xNumber
        extends xObject
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

        addPropertyTemplate("bitLength", "x:Int").makeReadOnly();
        addPropertyTemplate("byteLength", "x:Int").makeReadOnly(); // TODO: get

        addMethodTemplate("add", THIS, THIS);
        addMethodTemplate("sub", THIS, THIS);
        addMethodTemplate("mul", THIS, THIS);
        addMethodTemplate("div", THIS, THIS);
        addMethodTemplate("mod", THIS, THIS);
        addMethodTemplate("mod", THIS, THIS);
        addMethodTemplate("divmod", new String[]{"this.Type", "this.Type"}, new String[]{"this.Type", "this.Type"});
        addMethodTemplate("remainder", THIS, THIS);
        addMethodTemplate("abs", VOID, THIS);
        addMethodTemplate("neg", VOID, THIS);
        addMethodTemplate("power", THIS, THIS);
        addMethodTemplate("atMost", THIS, THIS);
        addMethodTemplate("atLeast", THIS, THIS);

        // TODO conversions
        }
    }
