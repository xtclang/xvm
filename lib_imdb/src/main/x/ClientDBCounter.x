class ClientDBCounter
        extends ClientDBObject
        implements db.DBCounter
    {
    construct((ServerDBObject + db.DBCounter) dbCounter,
              function Boolean() isAutoCommit)
        {
        construct ClientDBObject(dbCounter, isAutoCommit);
        }

    protected db.DBCounter serverDBCounter.get()
        {
        return dbObject.as(db.DBCounter);
        }

    protected ClientChange? change;

    protected ClientChange ensureChange()
        {
        ClientChange? change = this.change;
        if (change == Null)
            {
            change      = new ClientChange();
            this.change = change;
            }
        return change;
        }


    // ----- DBCounter API -------------------------------------------------------------------------

    @Override
    Int get()
        {
        if (transactional)
            {
            ClientChange? change = this.change;
            if (change != Null)
                {
                return change.get();
                }
            }
        return serverDBCounter.get();
        }

    @Override
    void set(Int value)
        {
        if (transactional && !isAutoCommit())
            {
            ensureChange().set(value);
            }
        else
            {
            serverDBCounter.set(value);
            }
        }

    @Override
    void adjustBy(Int value)
        {
        if (transactional)
            {
            ensureChange().adjustBy(value);
            }
        else
            {
            serverDBCounter.adjustBy(value);
            }
        }

    class ClientChange
            implements db.DBCounter.TxChange
        {
        @Override
        Boolean relativeOnly = True;

        /**
         * If the `relativeValue` is `True`, the `oldValue` has no meaning.
         */
        @Override
        Int oldValue = 0;

        /**
         * If the `relativeValue` is `True`, the `newValue` represents an adjustment.
         */
        @Override
        Int newValue = 0;

        @Override
        ClientDBCounter pre.get()
            {
            TODO("read-only new ClientDBCounter(...)");
            }

        @Override
        ClientDBCounter post.get()
            {
            TODO("read-only this.ClientDBCounter");
            }

        Int get()
            {
            if (relativeOnly)
                {
                oldValue     = serverDBCounter.get();
                newValue    += oldValue;
                relativeOnly = False;
                }
            return newValue;
            }

        void set(Int value)
            {
            if (relativeOnly)
                {
                oldValue     = serverDBCounter.get();
                relativeOnly = False;
                }
            newValue = value;
            }

        void adjustBy(Int value)
            {
            newValue += value;
            }
        }
    }
