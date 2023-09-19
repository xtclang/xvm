module TestSimple {

    @Inject Console console;

    void run( ) {
        Map<Type, Mapping> mappings = Map:[Int=new Mapping<Int>("I"), String=new Mapping<String>("S")];

        Type<Tuple> type = Tuple<Int, String>;
        assert Type[] types := type.DataType.parameterized();

        // this lambda used to assert the compiler
        Mapping[] array = new Mapping[types.size] (i ->
            {
            Type valueType = types[i];
            return new Mapping<valueType.DataType>("");
            });
        console.print(array);
    }

    const Mapping<T>(String name);
}