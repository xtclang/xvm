/**
 * A Comparator is a const object that knows how to compare any two objects of a particular type for
 * ordering purposes.
 *
 * @param DataType  the _Resolved Declarative Type_ to compare
 */
const Comparator<DataType>
    {
    construct(function Ordered (DataType, DataType) compareForOrder)
        {
        this.compareForOrder = compareForOrder;
        }

    /**
     * Obtain the function that can compare two instances of the _Resolved Declarative Type_ for
     * the purpose of determining equality.
     *
     * @throws UnsupportedOperationException if the Comparator does not support comparison of the
     *         {@code DataType} for the purpose of determining equality
     */
    @RO function Boolean (DataType, DataType) compareForEquality.get()
        {
        return (v1, v2) -> compareForOrder(v1, v2) == Equal;
        }

    /**
     * Obtain the function that can compare two instances of the _Resolved Declarative Type_ for
     * the purpose of determining order.
     *
     * @throws UnsupportedOperationException if the Comparator does not support comparison of the
     *         {@code DataType} for the purpose of determining order
     */
    public/private function Ordered (DataType, DataType) compareForOrder;
    }
