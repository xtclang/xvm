import collections.ListMap;

/**
 * A "printer" for a JSON object that supports the transformation of Ecstasy data structures into
 * a JSON `Doc`, which can then be converted into character strings that can be used to transmit,
 * store, or view ("pretty print") the JSON-formatted data.
 */
class BufferedPrinter
        extends Printer
        implements Builder
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an empty JSON printer.
     */
    construct()
        {
        construct Printer(null);
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The "path" from the outermost JSON doc "down to" the JSON doc that is currently being added
     * to.
     */
    private Doc[] stack = new Doc[];


    // ----- builder -------------------------------------------------------------------------------

    @Override
    BufferedPrinter add(String name, Doc value)
        {
        Map<String, Doc> cur;
        if (doc == Null)
            {
            cur = new ListMap();
            doc = cur;
            stack.add(doc);
            }
        else
            {
            val top = stack[stack.size-1];
            if (top.is(Map<String, Doc>))
                {
                cur = top;
                }
            else
                {
                cur = new ListMap(); // TODO GG remove
                throw new IllegalState("invalid context from which to add");
                }
            }

        cur.put(name, value);
        return this;
        }

    @Override
    BufferedPrinter addArray(String name, Int size, function void (Int, BufferedPrinter) build)
        {
        Doc[] array = new Array<Doc>(size);
        add(name, array);

        // use the array as a temporary "floor" in the stack to disable unbalanced calls to exit()
        // from within the lambda
        stack.add(array);
        Int popTo = stack.size;

        for (Int i = 0; i < size; ++i)
            {
            ListMap<String, Doc> value = new ListMap();
            array.add(value);
            if (stack.size > popTo)
                {
                stack.delete(stack.size-1 .. popTo);
                }
            stack.add(value);
            build(i, this);
            }

        if (stack.size >= popTo)
            {
            stack.delete(stack.size-1 .. popTo-1);
            }

        return this;
        }

    @Override
    BufferedPrinter enter(String name)
        {
        ListMap<String, Doc> value = new ListMap();
        add(name, value);
        stack.add(value);
        return this;
        }

    @Override
    BufferedPrinter exit()
        {
        Int index = stack.size-1;
        if (index > 0 && !stack[index].is(Array))
            {
            stack.delete(index);
            }
        return this;
        }
    }