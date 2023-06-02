/**
 * The database interface for a queue of database keys or values of a specific type, based on the
 * standard `Queue` interface.
 *
 * A DBQueue allows a database _application_ to manage the processing of the contents of the queue.
 * For automated queue processing, managed by the database, use a [DBProcessor] instead.
 *
 * A `DBQueue` is always transactional.
 */
interface DBQueue<Element extends immutable Const>
        extends Queue<Element>
        extends DBObject {
    /**
     * Obtain all of the contents of the `Queue` as a `List`. This method is potentially an
     * incredibly expensive operation, so it should only be used if the size of the `Queue` is
     * known to be small and there are no other processes concurrently operating on the Queue.
     *
     * This is roughly the equivalent of:
     *
     *     List<Element> list = new Element[];
     *     while (Element e := next())
     *         {
     *         list.add(e);
     *         }
     *     return list;
     *
     * @return a `List` with all of the elements in the `Queue`
     */
    List<Element> takeAll();


    // ----- DBObject methods ----------------------------------------------------------------------

    @Override
    @RO DBCategory dbCategory.get() {
        return DBQueue;
    }


    // ----- transactional information -------------------------------------------------------------

    /**
     * Represents specific database changes that occurred to a transactional database queue.
     *
     * This interface represents the change without the context of the `DBQueue`, thus it is
     * `static`, and cannot provide a before and after view on its own; when combined with the
     * `TxChange` interface, it can provide both the change information, and a before/after view of
     * the data.
     */
    static interface DBChange<Element> {
        /**
         * The elements appended to the `Queue`.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently added within the transaction _may_ appear in the list.
         */
        List<Element> added;

        /**
         * The elements taken from the `Queue`.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently taken within the transaction _may_ appear in the list.
         */
        List<Element> removed;
    }

    /**
     * Represents a transactional change to a database queue.
     *
     * This interface provides both the discrete change information, as well as the contextual
     * before-and-after view of the transaction. Obtaining a 'before' or 'after' transactional
     * view of the queue should be assumed to be a relatively expensive operation, particularly for
     * an historical `TxChange` (one pulled from some previous point in the a commit history).
     */
    @Override
    interface TxChange
            extends DBChange<Element> {}


    // ----- transaction trigger API ---------------------------------------------------------------

    // these interfaces can be used in lieu of the more generic interfaces of the same names found
    // on [DBObject], but these exists only as a convenience, in that they can save the application
    // database developer a few type-casts that might otherwise be necessary.

    @Override static interface Validator<TxChange extends DBObject.TxChange>
            extends DBObject.Validator<TxChange> {}
    @Override static interface Rectifier<TxChange extends DBObject.TxChange>
            extends DBObject.Rectifier<TxChange> {}
    @Override static interface Distributor<TxChange extends DBObject.TxChange>
            extends DBObject.Distributor<TxChange> {}
}
