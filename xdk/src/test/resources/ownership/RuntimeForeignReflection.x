import ecstasy.reflect.Annotation;
import ecstasy.reflect.InvalidType;

module RuntimeForeignReflection {
    void run() {}

    Type combine(Type type) = type.or(String);

    void inspect(Type type) {
        Boolean foundConstant = False;
        for (val prop : type.constants) {
            if (prop.name == "answer") {
                assert val value := prop.isConstant();
                assert value.as(Int) == 7;
                foundConstant = True;
            }
        }
        assert foundConstant;
        assert type.constructors.size > 0;
        Boolean foundProperty = False;
        for (val prop : type.properties) {
            if (prop.name == "content") {
                foundProperty = True;
            }
        }
        assert foundProperty;
        Boolean foundMethod = False;
        for (val method : type.methods) {
            if (method.name == "read") {
                foundMethod = True;
            }
        }
        assert foundMethod;
        Boolean foundFunction = False;
        for (val fn : type.functions) {
            if (fn.name == "echo") {
                assert fn.invoke((Int:31))[0].as(Int) == 31;
                foundFunction = True;
            }
        }
        assert foundFunction;
        Type sequence = Array;
        Type parameterized = sequence.parameterize([type]);
        assert val params := parameterized.parameterized();
        assert params[0] == type;
        Type union = type.or(String);
        assert union.form == Union;
        assert union.underlyingTypes.size == 2;

        Type local = Box;
        Boolean rejected = False;
        try {
            Type invalid = type.or(local);
        } catch (InvalidType e) {
            rejected = True;
        }
        assert rejected;

        Type flagType = Flag;
        assert Class flag := flagType.fromClass();
        rejected = False;
        try {
            Type invalid = type.annotate(new Annotation(flag));
        } catch (InvalidType e) {
            rejected = True;
        }
        assert rejected;
    }

    class Box(Int content) {
        static Int answer = 7;
        Int read() = content;
        static Int echo(Int value) = value;
    }

    annotation Flag into Object {}
}
