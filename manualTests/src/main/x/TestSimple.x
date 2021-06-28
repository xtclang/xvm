module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        }

    interface Iter<Element extends Const>
        {
        typedef Element.Comparer Comparer;  // used to fail the compilation

        Boolean test(Comparer compare, Element e1, Element e2)
            {
            return compare(e1, e2);
            }
        }

    @Abstract
    class IntSet
            implements Collection<Int>
        {
        void f(Orderer cmp)
            {
            Orderer order = (i1, i2) -> Int.compare(i1, i2); // used to fail the compilation
            }
        }
    }
