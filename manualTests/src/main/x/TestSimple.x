module TestSimple
    {
    @Inject Console console;

    void run()
        {
        }

    interface Iface<Element>
        {
        typedef Element.Orderer as Orderer;

        void binarySearch(Element value, Orderer? compare=Null)
            {
            if (compare == Null)
                {
                assert Element.is(Type<Orderable>); // this line used to cause compilation failure
                assert compare := Element.ordered();
                }
            binarySearch(compare(value, _));
            }

        void binarySearch(function Ordered(Element) order)
            {
            TODO
            }
        }
    }