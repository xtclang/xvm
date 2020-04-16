module TestSimple
    {
    @Inject ecstasy.io.Console console;

    import ecstasy.collections.HashMap;

    class Wrapper<Key, Value>
        {
        Map<Key, Value> map = new HashMap();

        void put(Key key, Value value)
            {
            map.put(key, value);
            }

        conditional Value get(Key key)
            {
            return map.get(key);
            }
        }

    void run()
        {
        Wrapper<Int, String> wrapper = new Wrapper();
//        wrapper.put(1, "One");
        wrapper.get(1);
        }
    }