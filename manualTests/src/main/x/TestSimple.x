module TestSimple {
    @Inject Console console;

    static void out(Object o = "") {
        console.print(o);
    }

    void run(String[] args = []) {
        DBMap map = new DBMap();
        Int[] nums = map.values.filter(n -> n >= 2).toArray();
        out(nums);
//        String[] strs = map.keys.filter(s -> s.indexOf('o')).toArray();
//        out(strs);
        val keys = map.keys;
        val filteredKeys = keys.filter(s -> s.indexOf('o'));
        val array = filteredKeys.toArray();
        out(array);
    }

    service DBMap
            implements Map<String, Int>
            incorporates ecstasy.maps.KeySetBasedMap<String, Int> {
        HashMap<String, Int> contents = new HashMap(["one"=1, "two"=2, "three"=3]);

        @Override @RO Set<Key> keys.get() = contents.keys;

        @Override @RO Int size.get() = contents.size;

        @Override @RO Boolean empty.get() = contents.empty;

        @Override Boolean contains(Key key) = contents.contains(key);

        @Override conditional Value get(Key key) = contents.get(key);

        @Override DBMap put(Key key, Value value) {
            contents.put(key, value);
            return this;
        }

        @Override DBMap remove(Key key) {
            contents.remove(key);
            return this;
        }
    }
}

