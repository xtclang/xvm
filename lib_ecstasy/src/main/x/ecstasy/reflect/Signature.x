/**
 * The signature of a method or function.
 */
interface Signature<ParamTypes extends Tuple<ParamTypes>, ReturnTypes extends Tuple<ReturnTypes>>
        extends immutable Const
    {
    /**
     * The function's name.
     */
    @RO String name;

    /**
     * The parameters, by ordinal.
     *
     * If this function represents a partially bound function, then the already-bound parameters
     * will not be present in this value.
     */
    @RO Parameter[] params;

    /**
     * Find a parameter by the provided name.
     *
     * @param name  the name of the parameter to find
     *
     * @return True iff a parameter with the specified name was found
     * @return (conditional) the parameter
     */
    conditional Parameter findParam(String name)
        {
        return params.iterator().untilAny(p ->
            {
            if (String s := p.hasName())
                {
                return s == name;
                }
            return False;
            });
        }

    /**
     * The return values, by ordinal.
     */
    @RO Return[] returns;

    /**
     * Find a return value by the provided name.
     *
     * @param name  the name of the return value to find
     *
     * @return True iff a return value with the specified name was found
     * @return (conditional) the return value
     */
    conditional Return findReturn(String name)
        {
        return returns.iterator().untilAny(r ->
            {
            if (String s := r.hasName())
                {
                return s == name;
                }
            return False;
            });
        }

    /**
     * Determine if the function return value is a _conditional return_. A conditional return is a
     * Tuple of at least two elements, whose first element is a Boolean; when the Boolean value is
     * False, the remainder of the expected return values are absent.
     */
    @RO Boolean conditionalResult;

    /**
     * Determine if the function represents a service invocation. Service invocations have the
     * _potential_ for asynchronous execution.
     */
    @RO Boolean futureResult;

    /**
     * Obtain the template that defines the function, if it is available.
     *
     * @return True iff the function can provide a method template for itself
     * @return (conditional) the method template
     */
    conditional MethodTemplate hasTemplate();


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
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

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        Return[] returns = this.returns;
        Int      count   = returns.size;
        if (count == 0)
            {
            "void".appendTo(buf);
            }
        else
            {
            Int     first  = 0;
            Boolean parens = False;
            if (count > 1)
                {
                if (conditionalResult)
                    {
                    "conditional ".appendTo(buf);
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
                buf.add('(');
                }

            EachReturn: for (Int i : [first..count))
                {
                if (!EachReturn.first)
                    {
                    ", ".appendTo(buf);
                    }
                returns[i].appendTo(buf);
                }

            if (parens)
                {
                buf.add(')');
                }
            }

        buf.add(' ')
           .addAll(name)
           .add('(');

        EachParam: for (Parameter param : params)
            {
            if (!EachParam.first)
                {
                ", ".appendTo(buf);
                }
            param.appendTo(buf);
            }

        return buf.add(')');
        }
    }
