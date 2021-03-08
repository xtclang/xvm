module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        BaseParent.Child0 c = new DerivedParent().new Child1(2);
        }

    class BaseParent
        {
        class Child0(Int value)
            {
            construct(Int value)
                {
                console.println("construct BP.C0");
                this.value = value;
                }
            }

        class Child1(Int value)
                extends Child0(value)
            {
            construct(Int value)
                {
                console.println("construct BP.C1");
                construct Child0(value);  // could be "super(value)"?
                }
            }
        }

    class DerivedParent
            extends BaseParent
        {
        @Override
        class Child0(Int value)
            {
            construct(Int value)
                {
                console.println("construct DP.C0");
                construct BaseParent.Child0(value); // could be "super(value)"
                }

            }

        @Override
        class Child1(Int value)
            {
//            construct(Int value)
//                {
//                console.println("construct DP.C1");
//                construct BaseParent.Child1(value);
//                }
            }
        }
    }