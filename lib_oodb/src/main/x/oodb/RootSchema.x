/**
 * A `DBSchema` is a `DBObject` that is used to hierarchically organize database contents.
 *
 * Every Database automatically contains a `DBSchema` named "sys"; it is the database system's own
 * schema. (The top-level schema "sys" is a reserved name; it is an error to attempt to override,
 * replace, or augment the database system's schema.) A database implementation may expose as much
 * information as it desires via its schema, but there will always exist the following contents,
 * regardless of the database implementation:
 */
interface RootSchema
        extends DBSchema {

    @RO SystemSchema sys;

    @Override
    @RO Connection<RootSchema> connection;

    @Override
    @RO Transaction<RootSchema>? transaction.get() = super();

    /**
     * Create a new [Transaction] if one does not exist, or if a `Transaction` already exists, then
     * this creates and returns a _nested_ `Transaction`. With the `using` statement, it becomes
     * straightforward to manage transactions, without having to check if a `Transaction` already
     * exists:
     *
     *     using (schema.createTransaction()) {
     *         // within this block, all db operations are performed within a transaction
     *         // ...
     *     }
     *
     * For each call to `createTransaction()`, the [Transaction.close] method must be called on the
     * returned result; this is performed automatically by the `using` statement, as in the above
     * example.
     *
     * For a nested `Transaction`:
     *
     * * A call to [Transaction.commit] has no effect if the `Transaction` has **not** been
     *   marked as [Transaction.rollbackOnly], and returns a failure code otherwise;
     * * A call to [Transaction.rollback] marks the enclosing `Transaction` as
     *   [Transaction.rollbackOnly];
     * * A call to [Transaction.close] will close the nested `Transaction`, and if that
     *   `Transaction` has not already been committed or rolled back, then an attempt to do so is
     *   performed automatically, committing in the absence of an exception and if
     *   [Transaction.rollbackOnly] is not indicated.
     *
     * @return a new [Transaction] object
     */
    Transaction<RootSchema> createTransaction() = connection.createTransaction();
}