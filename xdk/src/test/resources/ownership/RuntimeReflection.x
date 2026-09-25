import ecstasy.reflect.Parameter;

module RuntimeReflection {
    void run() {
        Type<Box<Int>> type = Box<Int>;
        Box<Int> box = new Box<Int>(17);
        Int constructors = 0;
        for (val constructor : type.constructors) {
            if (constructor.params.size == 1) {
                val result = constructor.invoke((Int:23));
                assert result[0].content == 23;
                constructors++;
            }
        }
        assert constructors > 0;

        Boolean foundProperty = False;
        for (Property<Box<Int>> property : type.properties) {
            if (property.name == "content") {
                assert property.get(box).as(Int) == 17;
                property.set(box, Int:19);
                assert property.get(box).as(Int) == 19;
                property.set(box, Int:17);
                Ref reference = property.of(box);
                assert reference.get().as(Int) == 17;
                Type implementation = &reference.type;
                assert Property reflected := implementation.fromProperty();
                assert reflected.name == property.name;
                foundProperty = True;
            }
        }
        assert foundProperty;

        Boolean foundMethod = False;
        for (val method : type.methods) {
            if (method.name == "read") {
                val bound = method.bindTarget(box);
                assert bound.invoke(())[0].as(Int) == 17;
                foundMethod = True;
            }
        }
        assert foundMethod;

        Boolean foundFunction = False;
        for (Function fn : type.functions) {
            if (fn.name == "echo") {
                assert fn.invoke((Int:31))[0].as(Int) == 31;
                val bound = fn.bind(fn.params[0].as(Parameter<Int>), 37);
                assert bound.invoke(())[0].as(Int) == 37;
                assert (val template, val original, val bindings) := bound.isFunction();
                assert template.name == "echo";
                assert bindings.size == 1;
                assert original.invoke((Int:41))[0].as(Int) == 41;
                foundFunction = True;
            }
        }
        assert foundFunction;

        val child = box.child();
        Type<Box<Int>.Child> childType = &child.type;
        Boolean foundChildConstructor = False;
        for (val constructor : childType.constructors) {
            if (constructor.params.size == 2) {
                val result = constructor.invoke((box, Int:29));
                assert result[0].read() == 29;
                foundChildConstructor = True;
            }
        }
        assert foundChildConstructor;
    }

    class Box<Element>(Element content) {
        Element read() = content;
        Child child() = new Child(0);

        @Tag("echo")
        static Int echo(Int value) = value;

        class Child(Int extra) {
            Int read() = extra;
        }
    }

    annotation Tag(String label) into Method | Function {}
}
