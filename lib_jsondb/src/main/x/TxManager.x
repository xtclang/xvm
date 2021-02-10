import storage.ObjectStore;

/**
 *
 */
service TxManager(ObjectStore[] stores)
    {
    /**
     * Exposes the last completed transaction ID so that connections can
     */
    protected ObjectStore[] stores;

    /**
     * Exposes the last completed transaction ID so that connections can
     */
    @Atomic Int lastCompletedTx;
    // TODO @Atomic public/protected Int lastCompletedTx;

    /**
     * A fake transaction ID that is used to indicate that the [lastCompletedTx] should be used.
     */
    static Int USE_LAST_TX = -1;

    /**
     * Attempt to commit a transaction.
     */
    Boolean commit(Int clientId, Int txBase) // TODO changesByDBOid)
        {
        // validate the transaction
        TODO

        // issue updates to the various ObjectStore instances
        return TODO
        }

    // ----- TODO ----------------------------------------------------------------------------
    }
