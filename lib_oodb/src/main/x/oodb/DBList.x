/**
 * The database interface for a list of database keys or values of a specific type, based on the
 * standard `List` interface.
 *
 * A `DBList` is always transactional.
 */
interface DBList<Element extends immutable Const>
        extends List<Element>
        extends DBObject
    {
    // ----- DBObject methods ----------------------------------------------------------------------

    @Override
    @RO DBCategory dbCategory.get()
        {
        return DBList;
        }


    // ----- transactional information -------------------------------------------------------------

    /**
     * Represents a change to a database list. Most list changes are extremely compact, representing
     * a limited set of additions and/or removals from the previous version of the list; however, a
     * list may be used to represent the order of a set of values, and if that order changes, the
     * change may be such that it is easiest to represent as the removal of all previous contents,
     * followed by the addition of those same contents in a different order.
     *
     * This interface represents the change without the context of the `DBList`, thus it is `static`
     * and cannot provide a before and after view on its own; when combined with the `TxChange`
     * interface, it can provide both the change information, and a before/after view of the data.
     */
    static interface DBChange<Element>
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

    /**
     * Represents a change to a database list. Most list changes are extremely compact, representing
     * a limited set of additions and/or removals from the previous version of the list; however, a
     * list may be used to represent the order of a set of values, and if that order changes, the
     * change may be such that it is easiest to represent as the removal of all previous contents,
     * followed by the addition of those same contents in a different order.
     *
     * This interface provides both the discrete change information, as well as the contextual
     * before-and-after view of the transaction. Obtaining a 'before' or 'after' transactional
     * view of the list should be assumed to be a relatively expensive operation, particularly for
     * an historical `TxChange` (one pulled from some previous point in the a commit history).
     */
    @Override
    interface TxChange
            extends DBChange<Element>
        {
        }


    // ----- transaction trigger API ---------------------------------------------------------------

    // these interfaces can be used in lieu of the more generic interfaces of the same names found
    // on [DBObject], but these exists only as a convenience, in that they can save the application
    // database developer a few type-casts that might otherwise be necessary.

    @Override static interface Validator<TxChange extends DBList.TxChange>
            extends DBObject.Validator<TxChange> {}
    @Override static interface Rectifier<TxChange extends DBList.TxChange>
            extends DBObject.Rectifier<TxChange> {}
    @Override static interface Distributor<TxChange extends DBList.TxChange>
            extends DBObject.Distributor<TxChange> {}
    }
