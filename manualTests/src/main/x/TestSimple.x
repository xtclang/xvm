module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        String? s = "hello";
        if (String s2 := s.is(String))
            {
            console.println($"s={s}, s2={s2}");
            }
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
