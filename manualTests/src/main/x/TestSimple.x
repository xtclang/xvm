module TestSimple
    {
    @Inject Console console;

    void run()
        {
//        console.print($"d1:  {new Derived1()}");
//        console.print($"d2:  {new Derived2()}");
//        console.print($"d2a: {new Derived2a(3)}");
        }

    const Base
            extends Base2 // used to cause stack overflow
        {
        }

    const Base2(Int bi)
            extends Base
        {
        }

//    mixin M(Int mi)
//            into Base
//        {
//        }

//    const Derived1
//            extends Base
//            incorporates M(10) // should be an error  because of the explicit constructor
//        {
//        }


//    const Derived2
//            incorporates M(10)
//            extends Base2(5)
//        {
//        }

//    const Derived2a(Int ai)
//            incorporates M(ai)
//            extends Base2(ai*2)
//        {
//        }
    }