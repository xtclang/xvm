class ClientDBCounter
        extends ClientDBObject
        implements oodb.DBCounter
    {
    construct((ServerDBObject + oodb.DBCounter) dbCounter,
              function Boolean() isAutoCommit)
        {
        construct ClientDBObject(dbCounter, isAutoCommit);
        }

    protected oodb.DBCounter serverDBCounter.get()
        {
        return dbObject_.as(oodb.DBCounter);
        }

    protected ClientChange? change_;

    protected ClientChange ensureChange()
        {
        ClientChange? change = change_;
        if (change == Null)
            {
            change  = new ClientChange();
            change_ = change;
            }
        return change;
        }


    // ----- DBCounter API -------------------------------------------------------------------------

    @Override
    Int get()
        {
        if (transactional)
            {
            ClientChange? change = change_;
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
        if (transactional && !isAutoCommit_())
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
            implements oodb.DBCounter.TxChange
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
