module TestSimple
    {
    @Inject Console console;
    void run()
        {
        function Int(Int, Int) divide = (x,y) -> x / y;

        val half = divide(_, 2);        // this used to fail to compile
        val partsOf120 = divide(120, _);

        console.println($|half of a dozen is {half(12)}
                         |half of 120 is {partsOf120(2)}
                         |a third is {partsOf120(3)}
                         |and a quarter is {partsOf120(4)}
                       );
        }
    }