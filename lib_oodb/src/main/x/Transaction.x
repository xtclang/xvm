/**
 * A database transaction, as viewed from outside of the database. In other words, this is the
 * client interface for managing a database transaction.
 *
 * A transaction represents the union of the contents of the database (the "root of the schema")
 * with the ability to commit or roll back the transaction.
 */
interface Transaction<Schema extends RootSchema>
        extends RootSchema
        extends Closeable
    {
    /**
     * The database connection. This property is only guaranteed to be available from within an
     * [pending] transaction.
     */
    @RO (Connection<Schema> + Schema) connection;

    /**
     * Represents the parameters used to create a Transaction.
     *
     * @param timeout     (optional) the requested time-out, which allows the database to roll back
     *                    and discard the transaction after that period of time has elapsed; `Null`
     *                    indicates that the database's default time-out should be used, and even if
     *                    a time-out is specified, the database may use a shorter or longer value
     * @param name        (optional) a descriptive name to associate with the transaction
     * @param id          (optional) an integer identifier to associate with the transaction
     * @param priority    (optional) the transactional priority
     * @param retryCount  (optional) the number of times that this same transaction has already been
     *                    attempted
     * @param readOnly    (optional) pass True to indicate that transaction is not going to modify
     *                    any data
     */
    static const TxInfo(Duration?              timeout     = Null,
                        String?                name        = Null,
                        UInt?                  id          = Null,
                        DBTransaction.Priority priority    = Normal,
                        Int                    retryCount  = 0,
                        Boolean                readOnly    = False)
        {
        /**
         * True iff the TxInfo doesn't specify any non-default information.
         */
        Boolean nondescript.get()
            {
            return timeout    == Null
                && name       == Null
                && id         == Null
                && priority   == Normal
                && retryCount == 0
                && readOnly   == False;
            }

        @Override
        String toString()
            {
            StringBuffer buf = new StringBuffer().append("TxInfo(");

            if (timeout != null)
                {
                buf.append("timeout=")
                   .append(timeout)
                   .append(", ");
                }

            if (name != null)
                {
                buf.append("name=")
                   .append(name)
                   .append(", ");
                }

            if (id != null)
                {
                buf.append("id=")
                   .append(id)
                   .append(", ");
                }

            buf.append("priority=")
               .append(priority);

            if (retryCount != 0)
                {
                buf.append(", retryCount=")
                   .append(retryCount);
                }

            if (readOnly)
                {
                buf.append(", readOnly");
                }

            return buf.append(')').toString();
            }
        }

    /**
     * The transaction parameters used to create this Transaction object.
     */
    @RO TxInfo txInfo;

    /**
     * True iff the transaction is active and can theoretically be committed or rolled back.
     */
    @RO Boolean pending;

    /**
     * Commit the transaction.
     *
     * @return `True` iff the commit succeeded
     *
     * @throws IllegalState  if the Transaction has already rolled back
     */
    Boolean commit();

    /**
     * Roll back the transaction. This allows the database to release any resources held by the
     * transaction.
     *
     * @throws IllegalState  if the Transaction has already committed
     */
    void rollback();

    @Override
    void close(Exception? e = Null)
        {
        if (pending)
            {
            if (e == Null)
                {
                commit();
                }
            else
                {
                rollback();
                }

            assert !pending;
            }
        }
    }
