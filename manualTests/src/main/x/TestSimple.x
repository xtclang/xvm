module TestSimple
    {
    @Inject Console console;

    @Inject(opts=Map:["shared"=True]) Random random;

    void run( )
        {
        console.println(random.int());

        @Inject(resourceName="random", opts=Map:["shared"=True]) Random random1;
        console.println(random1.int());

        @Inject(resourceName="random") Random random2;
        console.println(random2.int());
        }
    }
