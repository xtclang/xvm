module TestSimple
    {
    @Inject Console console;

    void run(String[] args=[])
        {
        (String name, Int value) = test(new C1());
        console.println($"name={name}, value={value}");

        (name, value) = test(new C2());
        console.println($"name={name}, value={value}");

        console.println(TestSimple.classes);
        }

    class C1
        {
        Int value()
            {
            return 1;
            }
        String name="C1";
        }

    class C2
        {
        Int value()
            {
            return 2;
            }
        String name="C2";
        }

    (String, Int) test(C1|C2 c)
        {
        return (c.name, c.value()); // this used to fail to compile
        }
    }