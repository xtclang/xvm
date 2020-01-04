import collections.ListMap;

/**
 * A "printer" for a JSON object that supports the transformation of Ecstasy data structures into
 * a JSON `Doc`, which can then be converted into character strings that can be used to transmit,
 * store, or view ("pretty print") the JSON-formatted data.
 */
class BufferedPrinter
        extends Printer
//        implements ElementOutput<Nullable>
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

//    @Override
//    BufferedPrinter add(String name, Doc value)
//        {
//        Map<String, Doc> cur;
//        if (doc == Null)
//            {
//            cur = new ListMap();
//            doc = cur;
//            stack.add(doc);
//            }
//        else
//            {
//            val top = stack[stack.size-1];
//            if (top.is(Map<String, Doc>))
//                {
//                cur = top;
//                }
//            else
//                {
//                throw new IllegalState("invalid context from which to add");
//                }
//            }
//
//        cur.put(name, value);
//        return this;
//        }
//
//    @Override
//    BufferedPrinter openObject(String name)
//        {
//        ListMap<String, Doc> value = new ListMap();
//        add(name, value);
//        stack.add(value);
//        return this;
//        }
//
//    @Override
//    BufferedPrinter close()
//        {
//        Int index = stack.size-1;
//        if (index > 0 && !stack[index].is(Array))
//            {
//            stack.delete(index);
//            }
//        return this;
//        }
    }