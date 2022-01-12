module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Test t1 = new Test(1);
        Test t2 = new Test(2);

        Data d = new Data("test");
        d.svcId = 0;

        d.report("start");

        t1.send(d);
        d = t1.receive();

        t2.send(d);
        d = t2.receive();

        t1.send(d);
        d = t1.receive();

        t2.send(d);
        d = t2.receive();

        d.report("end");
        }


    const Data(String value)
        {
        @Transient Int? svcId;
        @Transient @Lazy Int square.calc()
            {
            return svcId? * svcId? : 0;
            }

        void report(String title)
            {
            console.println($"{title} {this}; svcId={svcId} square={square}");
            }
        }

    service Test(Int id)
        {
        Data? d;

        void send(Data d)
            {
            d.svcId = id;
            d.report($"Test {id}");
            this.d = d;
            }

        Data receive()
            {
            return d? : assert;
            }
        }
    }
