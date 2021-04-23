module TestSimple.test.org
    {
    @Inject Console console;

    void run( )
        {
        Int x = 1;
        Int y = 2;

        @Lazy(() -> x ^ y)
        Int hash;

        console.println(hash);

        Point1 p1 = new Point1(x, y);
        console.println(p1.hash);

        Point2 p2 = new Point2(x, y);
        console.println(p2.hash);

        console.println(&hash.get());
        console.println(p1.&hash.get());
        console.println(p2.&hash.get());

        try
            {
            p1.&hash.set(7);
            assert;
            }
        catch (Exception e) {}

        try
            {
            p2.&hash.set(7);
            assert;
            }
        catch (Exception e) {}
        }

      const Point1(Int x, Int y)
          {
          @Lazy(() -> Int:42)
          Int hash;
          }

      const Point2(Int x, Int y)
          {
          @Lazy
          Int hash.calc()
              {
              return x ^ y + 2;
              }
          }
    }