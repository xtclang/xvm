/**
 * The database interface for a list of database keys or values of a specific type, based on the
 * standard `List` interface.
 *
 * A `DBList` is always transactional.
 */
interface DBList<Element>
        extends List<Element>
        extends DBObject
    {
    @Override
    @RO Boolean transactional.get()
        {
        return True;
        }

    /**
     * Represents a change to a database list. Most list changes are extremely compact, representing
     * a limited set of additions and/or removals from the previous version of the list; however, a
     * list may be used to represent the order of a set of values, and if that order changes, the
     * change may be such that it is easiest to represent as the removal of all previous contents,
     * followed by the addition of those same contents in a different order.
     *
     * If the transaction is historical, the "pre" and "post" versions of the `List` may not be
     * available, and if they are available, they may be expensive to reconstruct.
     */
    @Override
    interface Change
        {
        /**
         * The elements added to the `List`. The key of this map is the index of the added element
         * in the [post] list, and the value of this map is the added element value.
         *
         * The returned `Map` does not allow mutation, but if the transaction is still processing,
         * any items subsequently added within the transaction _may_ appear in the list.
         *
         * To construct the [post] list from the [pre] list, make a copy of the [pre] list, then
         * remove the elements specified by [removed] from that list, then add the elements
         * specified by [added] into that list.
         */
        @RO Map<Int, Element> added;

        /**
         * The elements removed from the `List`. The key of this map is the index into the [pre]
         * list of each element to remove, and the value of this map is the element value at that
         * index.
         *
         * The returned `Map` does not allow mutation, but if the transaction is still processing,
         * any items subsequently removed within the transaction _may_ appear in the list.
         *
         * To construct the [post] list from the [pre] list, make a copy of the [pre] list, then
         * remove the elements specified by [removed] from that list, then add the elements
         * specified by [added] into that list.
         */
        @RO Map<Int, Element> removed;
        }
    }
