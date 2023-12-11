module TestSimple {
    @Inject Console console;

    typedef function Bag(Int, Bag) as ParameterBinder;
    void run() {
        test(new Parameter<String>());
    }

    void test(Parameter param) {
        if (param.ParamType defaultValue := param.defaultValue()) {
            ParameterBinder bind = (i, values) -> values.add(defaultValue);

            bind(0, new Bag());
        }
    }

    class Bag {
        <Element> Bag! add(Element value) {
            console.print(Element);  // this used to assert the run-time resolution here
            TODO
        }
    }

    class Parameter<ParamType> {
        conditional ParamType defaultValue() {
            return True, "<none>".as(ParamType);
        }
    }
}
