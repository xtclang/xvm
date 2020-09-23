class ServerDBMap<Key extends immutable Const, Value extends immutable Const>
        extends ServerDBObject
        implements db.DBMap<Key, Value>
        delegates Map<Key, Value>(contents) // temporarily; need to override everything
    {
    construct(db.DBObject? parent, String name)
        {
        construct ServerDBObject(parent, name);

        contents = new HashMap<Key, Value>();
        dates    = new HashMap<Key, DateTime>();
        }

    @Inject Clock clock;

    class CursorEntry
            implements db.DBMap<Key, Value>.Entry
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
            return contents.contains(key);
            }

        @Override
        Value value
            {
            @Override
            Value get()
                {
                if (Value value := contents.get(key))
                    {
                    return value;
                    }
                throw new OutOfBounds("key=" + key);
                }

            @Override
            void set(Value value)
                {
                contents.put(key, value);
                dates.put(key, clock.now);
                }
            }

        @Override
        void delete()
            {
            if (exists)
                {
                contents.remove(key);
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
            if (DateTime date := dates.get(key))
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

    protected Map<Key, Value>    contents;
    protected Map<Key, DateTime> dates;
    }
