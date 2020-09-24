class ClientDBMap<Key extends immutable Const, Value extends immutable Const>
        extends ClientDBObject
        implements db.DBMap<Key, Value>
        delegates Map<Key, Value>(serverDBMap) // temporary; should be all overridden
    {
    construct((ServerDBObject + db.DBMap) dbMap)
        {
        construct ClientDBObject(dbMap);
        }

    protected db.DBMap<Key, Value> serverDBMap.get()
        {
        return dbObject.as(db.DBMap<Key, Value>);
        }

    protected @Abstract @RO Boolean autoCommit;

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

    @Override
    ClientDBMap put(Key key, Value value)
        {
        if (autoCommit)
            {
            serverDBMap.put(key, value);
            }
        else
            {
            ensureChange().put(key, value);
            }
        return this;
        }

    @Override
    @RO Collection<Value> values.get()
        {
        TODO
        // return new Collection<Value>(Constant, serverDBMap.values);
        }

    void commit()
        {
        ClientChange? change = this.change;
        if (change == Null)
            {
            return;
            }

        Map<Key, Value> map = serverDBMap;
        for (Key key : change.removed.keys)
            {
            map.remove(key);
            }
        map.putAll(change.added);

        change = Null;
        }

    void rollback()
        {
        change = Null;
        }

    class CursorEntry
            implements db.DBMap<Key, Value>.Entry
//            implements Duplicable
        {
//        construct(Key key)
//            {
//            this.key    = key;
//            this.exists = False;
//            }
//        finally
//            {
//            this.original = this;
//            }
//
//        construct(Key key, Value value)
//            {
//            this.key    = key;
//            this.value  = value;
//            this.exists = True;
//            }
//        finally
//            {
//            this.original = this;
//            }
//
//        construct(CursorEntry entry)
//            {
//            this.key    = entry.key;
//            this.exists = entry;
//            }
//        finally
//            {
//            this.original = this;
//            }
//
//        Boolean changed;
//
//        @Override
//        public/private Key key;
//
//        @Override
//        public/private Boolean exists;
//
//        @Override
//        Value value
//            {
//            @Override
//            Value get()
//                {
//                if (exists)
//                    {
//                    return super();
//                    }
//
//                throw new OutOfBounds("key=" + key);
//                }
//
//            @Override
//            void set(Value value)
//                {
//                if (!changed)
//                    {
//                    original = this.new(this);
//                    }
//                modified = clock.now;
//                super(value);
//                }
//            }
//
//        @Override
//        void delete()
//            {
//            if (exists)
//                {
//                exists  = False;
//                changed = True;
//                }
//            }
//
//        @Override
//        CursorEntry reify()
//            {
//            TODO
//            }
//
//        @Override
//        DateTime modified.get()
//            {
//            return exists
//                    ? super()
//                    : TODO;
//            }
//
//        @Override
//        @Unassigned public/private original;
        }

    class ClientChange
            implements db.DBMap<Key, Value>.Change
        {
        construct()
            {
            added   = new HashMap();
            removed = new HashMap();
            }

        @Override
        ClientDBMap pre.get()
            {
            TODO("read-only new ClientDBMap(...)");
            }

        @Override
        ClientDBMap post.get()
            {
            TODO("read-only this.ClientDBMap");
            }

        @Override
        public/private Map<Key, Value> added;

        @Override
        public/private Map<Key, Value> removed;

        void put(Key key, Value value)
            {
            removed.remove(key);
            added.put(key, value);
            }
        }
    }
