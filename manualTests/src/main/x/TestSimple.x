module TestSimple {
    @Inject Console console;

    import ecstasy.reflect.*;

    void run() {
        Method method = test;

        Object[] values = new Object[];

        for (Parameter param : method.params) {
            if (param.ParamType defaultValue := param.defaultValue()) { // this used to blow up at runtime
                values += defaultValue;
            }
        }
        console.print(values[0]);

        assert MethodTemplate mt := method.hasTemplate();  // this used to blow up at runtime
        ParameterTemplate pt = mt.parameters[0];           // this used to blow up at runtime
        console.print(pt.defaultValue);
    }


    void test(Map<String, Int> map = ["a"=1]) {
    }
}

