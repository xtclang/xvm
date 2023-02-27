module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.print($"d1:  {new Derived1()}");
        console.print($"d2:  {new Derived2()}");
        console.print($"d2a: {new Derived2a(3)}");
        console.print($"d3n: {new Derived3<Number>(Int:3)}");
        console.print($"d3i: {new Derived3<Int>(3)}");
        }

    const Base
        {
        }

    const Base2(Int bi)
            extends Base
        {
        }

    mixin M<Element>(Int mi)
            into Base
        {
        }

    const Derived1
            extends Base
            incorporates M(1)
        {
        }


    const Derived2
            incorporates M(20)
            extends Base2(20)
        {
        }

    const Derived2a(Int d2i)
            incorporates M(d2i+1)
            extends Base2(d2i*2)
        {
        }

    const Derived3<Value>(Value d3i)
            extends Base2(8)
            incorporates conditional M<Value extends Int>(d3i+1)
        {
        }
    }