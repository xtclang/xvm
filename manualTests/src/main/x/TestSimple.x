module TestSimple
    {
    @Inject Console console;
    void run()
        {
        console.println("Starting Test");

        Function<Tuple, Tuple> fn     = test;
        FunctionHolder         holder = new FunctionHolder(fn);
        Int                    value;
        Tuple                  result;        // call the test method directly with negative value, returns False - this works
        value = -1;
        result = fn.invoke(Tuple:(value));
        console.println($"Function invoke result={result}");        // call the test method directly with positive value, returns (True, value) - this works
        value  = 1;
        result = fn.invoke(Tuple:(value));
        console.println($"Function invoke result={result}");        // call the test method via the holder with negative value, returns False - this works
        value  = -1;
        result = holder.executeOne(value);
        console.println($"Holder executeOne result={result}");        // call the test method via the holder with positive value, returns (True, value) - this works
        value  = 1;
        result = holder.executeOne(value);
        console.println($"Holder executeOne result={result}");        // call the test method via the holder with negative value, returns False - this works
        value  = -1;
        result = holder.executeTwo(value);
        console.println($"Holder executeTwo result={result}");        // call the test method via the holder with positive value, returns (True, value) This blows up !!
        value  = 1;
        result = holder.executeTwo(value);
        console.println($"Holder executeTwo result={result}");
        }

    class FunctionHolder(Function<Tuple, Tuple> fn)
        {
        /**
         * Assign the return of the function call to a local variable before returning.
         * This always works.
         */
        Tuple executeOne(Int value)
            {
            Tuple t = fn.invoke(Tuple:(value));
            return t;
            }

        /**
         * Assign the return of the function call to a local variable before returning.
         * This works for negative values, when the function returns False but
         * blows up for value >= 0 when the function returns (True, value)
         */
        Tuple executeTwo(Int value)
            {
            return fn.invoke(Tuple:(value));
            }
        }

     /**
     * Simple method that returns True and the passed in value for
     * values >= 0 otherwise returns False
     */
    conditional Int test(Int value)
        {
        if (value >= 0)
            {
            return True, value;
            }
        return False;
        }
    }