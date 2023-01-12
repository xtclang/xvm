module TestSimple
    {
    @Inject Console console;

    import ecstasy.collections.ConstOrdinalList;

    void run(   )
        {
        test1();
        test2();
        }

    void test1()
        {
        B[] bs  = [new B(1), new B(2)];
        D[] ds  = [new D(1, 2), new D(2, 3)];
        D[] ds2 = [new D(1, 3), new D(2, 4)];

        // assert List<D>.equals(bs, ds);     // should not compile (it used to)

        assert !List<Object>.equals(bs, ds);  // definitely False
        assert !List<Object>.equals(ds, ds2); // definitely False

        assert List<B>.equals(bs, ds);        // should be True based on B equality
        assert List<B>.equals(ds, ds2);       // should be True based on B equality

        assert !List<D>.equals(ds, ds2);      // should be False based on D equality

        assert !List.equals(ds, ds2);         // should be False, because CompileType is computed as "List<D>"
        }

    const B(Int i);
    const D(Int i, Int j) extends B(i);

    void test2()
        {
        Int[]            l1 = [11, 2222];
        ConstOrdinalList l2 = new ConstOrdinalList([11, 2222], 0);

        assert List<Int>.equals(l1, l2);
        }
    }