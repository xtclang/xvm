import ecstasy.reflect.MethodTemplate;
import ecstasy.reflect.Parameter;
import ecstasy.reflect.Return;
import ecstasy.reflect.Signature;

/**
 * The native Signature implementation.
 */
const RTSignature<ParamTypes extends Tuple<ParamTypes>, ReturnTypes extends Tuple<ReturnTypes>>
        implements Signature<ParamTypes, ReturnTypes>
    {
    @Override @RO String      name                         .get() { TODO("native"); }
    @Override @RO Parameter[] params                       .get() { TODO("native"); }
    @Override @RO Return[]    returns                      .get() { TODO("native"); }
    @Override @RO Boolean     conditionalResult            .get() { TODO("native"); }
    @Override @RO Boolean     futureResult                 .get() { TODO("native"); }

    @Override conditional MethodTemplate hasTemplate()            { TODO("native"); }

    // these methods are currently implemented as natural code:
    //   conditional Parameter findParam(String name)
    //   conditional Return findReturn(String name)

    @Override // note: master copy of this code found on Signature interface
    Int estimateStringLength()
        {
        Int      total   = 0;

        Return[] returns = this.returns;
        Int      count   = returns.size;
        if (count == 0)
            {
            total += 4; // void
            }
        else
            {
            Int first  = 0;
            if (count > 1)
                {
                if (conditionalResult)
                    {
                    total += 12; // conditional
                    first  = 1;
                    }
                }

            if (count - first > 1)
                {
                total += 2 + (count - first - 1) * 2; // parens + comma and space delimiters
                }

            for (Int i : [first..count))
                {
                total += returns[i].estimateStringLength();
                }
            }

        total += 1 + name.size + 2; // space before name + name + parens

        if (!params.empty)
            {
            total += (params.size - 1) * 2; // comma and space delimiter between params
            for (Parameter param : params)
                {
                total += param.estimateStringLength();
                }
            }

        return total;
        }

    @Override // note: master copy of this code found on Signature interface
    void appendTo(Appender<Char> appender)
        {
        Return[] returns = this.returns;
        Int      count   = returns.size;
        if (count == 0)
            {
            appender.add("void");
            }
        else
            {
            Int     first  = 0;
            Boolean parens = False;
            if (count > 1)
                {
                if (conditionalResult)
                    {
                    appender.add("conditional ");
                    first  = 1;
                    parens = count > 2;
                    }
                else
                    {
                    parens = True;
                    }
                }

            if (parens)
                {
                appender.add('(');
                }

            EachReturn: for (Int i : [first..count))
                {
                if (!EachReturn.first)
                    {
                    appender.add(", ");
                    }
                returns[i].appendTo(appender);
                }

            if (parens)
                {
                appender.add(')');
                }
            }

        appender.add(' ')
                .add(name)
                .add('(');

        EachParam: for (Parameter param : params)
            {
            if (!EachParam.first)
                {
                appender.add(", ");
                }
            param.appendTo(appender);
            }

        appender.add(')');
        }
    }
