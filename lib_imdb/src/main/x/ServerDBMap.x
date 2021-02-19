class ServerDBMap<Key extends immutable Const, Value extends immutable Const>
        extends ServerDBObject
        implements oodb.DBMap<Key, Value>
        delegates Map<Key, Value>(contents_) // temporarily; need to override everything
    {
    construct(oodb.DBObject? parent, String name)
        {
        construct ServerDBObject(parent, DBMap, name);

        contents_ = new HashMap<Key, Value>();
        dates_    = new HashMap<Key, DateTime>();
        }

    @Inject("clock") Clock clock_;

    protected Map<Key, Value>    contents_;
    protected Map<Key, DateTime> dates_;

    class CursorEntry
            implements oodb.DBMap<Key, Value>.Entry
        {
        construct(Key key)
            {
            this.key = key;
            }

        @Override
        Key key;

        @Override
        Boolean exists.get()
            {
            return contents_.contains(key);
            }

        @Override
        Value value
            {
            @Override
            Value get()
                {
                if (Value value := contents_.get(key))
                    {
                    return value;
                    }
                throw new OutOfBounds("key=" + key);
                }

            @Override
            void set(Value value)
                {
                contents_.put(key, value);
                dates_.put(key, clock_.now);
                }
            }

        @Override
        void delete()
            {
            if (exists)
                {
                contents_.remove(key);
                }
            }

        @Override
        CursorEntry reify()
            {
            throw new UnsupportedOperation();
            }

        @Override
        DateTime modified.get()
            {
            if (DateTime date := dates_.get(key))
                {
                return date;
                }
            TODO;
            }

        @Override
        CursorEntry original.get()
            {
            return this;
            }

        @Override
        List<CursorEntry> changeLog.get()
            {
            return [this];
            }
        }
    }
