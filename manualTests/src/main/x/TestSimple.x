module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        DBMap<Int, String> mapImpl = new DBMapImpl();

        mapImpl.defer(1);
        }

    interface DBObject
        {
        void defer(function Boolean(DBObject) adjust);
        }

    interface DBMap<Key extends immutable Const, Value extends immutable Const>
            extends DBObject
            extends Map<Key, Value>
        {
        void defer(Key key)
            {
            defer(lambda); // <-- this call used to throw at run-time
            }

        Boolean lambda(DBMap map)
            {
            return True;
            }
        }

    class DBObjectImpl
            implements DBObject
        {
        @Override
        void defer(function Boolean(DBObjectImpl) adjust)
            {
            assert adjust(this);
            }
        }

    import ecstasy.collections.maps.KeySetBasedMap;

    class DBMapImpl<Key extends immutable Const, Value extends immutable Const>
            extends DBObjectImpl
            implements DBMap<Key, Value>
            incorporates KeySetBasedMap<Key, Value>
        {
        @Override
        conditional Value get(Key key)
            {
            TODO
            }

        @Override
        @Lazy public/private Set<Key> keys.calc()
            {
            TODO
            }
        }
    }