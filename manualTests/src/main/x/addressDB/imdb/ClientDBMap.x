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
    conditional Value get(Key key)
        {
        ClientChange? change = this.change;
        if (change != Null)
            {
            if (Value value := change.internalAdded.get(key))
                {
                return True, value;
                }
            if (change.internalRemoved.contains(key))
                {
                return False;
                }
            }
        return serverDBMap.get(key);
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
        // TODO GG: this should return a proxy interface
        return new Array<Value>(Constant, serverDBMap.values);
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
            internalAdded   = new HashMap();
            internalRemoved = new HashMap();
            }

        protected HashMap<Key, Value> internalAdded;
        protected HashMap<Key, Value> internalRemoved;

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
        Map<Key, Value> added.get()
            {
            return internalAdded.freeze(False);
            }

        @Override
        Map<Key, Value> removed.get()
            {
            return internalRemoved.freeze(False);
            }

        void put(Key key, Value value)
            {
            internalRemoved.remove(key);
            internalAdded.put(key, value);
            }

        Boolean apply()
            {
            Map<Key, Value> map = serverDBMap;
            for (Key key : internalRemoved.keys)
                {
                map.remove(key);
                }
            map.putAll(internalAdded);

            change = Null;
            return True;
            }

        void discard()
            {
            change = Null;
            }
        }
    }
