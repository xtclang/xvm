module TestSimple
    {
    @Inject Console console;

    package json import json.xtclang.org;

    import json.Schema;

    void run()
        {
        console.println(C1.value); // used to throw at run-time
        console.println(C1.range); // used to throw at run-time
        }

    class C1
        {
        static Int value = C2.value;
        static Range<Int> range = 1..C2.value;
        }

    class C2
        {
        static Int value = C3.value * 2;
        }

    class C3
        {
        static Int value = C1.value;
        }
    }