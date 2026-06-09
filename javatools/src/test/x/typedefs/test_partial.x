module test_partial {
    typedef <K, V> Map<K, V> as Dictionary<K, V>;

    // Partial application - provide only K, V remains open
    typealias StringMap<V> = Dictionary<String, V>;

    void run() {
        StringMap<Int> map = StringMap<Int>();
    }
}