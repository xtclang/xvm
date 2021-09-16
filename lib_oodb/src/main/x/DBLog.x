/**
 * The database interface for a "log" of information. A log is an opaque, append-only entity.
 *
 * A `DBLog` may be extra-transactional, which means that elements appended to a `DBLog` may persist
 * even if the transaction that logged those elements does not commit.
 */
interface DBLog<Element extends immutable Const>
        extends Appender<Element>
        extends DBObject
    {
    // ----- DBObject methods ----------------------------------------------------------------------

    @Override
    @RO DBCategory dbCategory.get()
        {
        return DBLog;
        }


    // ----- transactional information -------------------------------------------------------------

    /**
     * Represents specific database changes that occurred to a transactional database log.
     *
     * This interface represents the change without the context of the `DBLog`, thus it is `static`,
     * and cannot provide a before and after view on its own; when combined with the `TxChange`
     * interface, it can provide both the change information, and a before/after view of the data.
     */
    static interface DBChange<Element>
        {
        /**
         * The elements appended to the `Log`.
         *
         * The returned `List` does not allow mutation, but if the transaction is still processing,
         * any items subsequently logged within the transaction _may_ appear in the list.
         */
        List<Element> added;
        }

    /**
     * Represents a transactional change to a database log.
     *
     * This interface provides both the discrete change information, as well as the contextual
     * before-and-after view of the transaction. Obtaining a 'before' or 'after' transactional view
     * of the log should be assumed to be a relatively expensive operation, particularly for an
     * historical `TxChange` (one pulled from some previous point in the a commit history).
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

    @Override static interface Validator<TxChange extends DBLog.TxChange>
            extends DBObject.Validator<TxChange> {}
    @Override static interface Rectifier<TxChange extends DBLog.TxChange>
            extends DBObject.Rectifier<TxChange> {}
    @Override static interface Distributor<TxChange extends DBLog.TxChange>
            extends DBObject.Distributor<TxChange> {}
    @Override static interface AsyncTrigger<TxChange extends DBLog.TxChange>
            extends DBObject.AsyncTrigger<TxChange> {}
    }
