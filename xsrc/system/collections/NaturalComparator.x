/**
 * A NaturalComparator uses the object's own relational operators for determining equality and
 * ordering.
 *
 * @param DataType  the _Resolved Declarative Type_ to compare
 */
const NaturalComparator<DataType extends Orderable>
    {
    /**
     * Obtain the function that can compare two instances of the _Resolved Declarative Type_ for
     * the purpose of determining equality.
     *
     * @throws UnsupportedOperationException if the Comparator does not support comparison of the
     *         {@code DataType} for the purpose of determining equality
     */
    @RO function Boolean (DataType, DataType) compareForEquality.get()
        {
        return (v1, v2) -> v1 == v2;
        }

    /**
     * Obtain the function that can compare two instances of the _Resolved Declarative Type_ for
     * the purpose of determining order.
     *
     * @throws UnsupportedOperationException if the Comparator does not support comparison of the
     *         {@code DataType} for the purpose of determining order
     */
    @RO function Ordered (DataType, DataType) compareForOrder.get()
        {
        return (v1, v2) -> v1 <=> v2;
        }
    }
