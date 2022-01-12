module TestSimple.test.org
    {
    @Inject Console console;

    import ecstasy.collections.*;

    void run()
        {
        Map<Object, Object> map1  = new HasherMap(new CaseInsensitiveHasher());
        Map<Int, String> map2  = new ListMap([1], "");
        }
}
