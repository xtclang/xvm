module Example3
    {
    const Person(String name, Int age);

    interface AgeAware
        {
        Int age;
        }

    mixin AverageAgeCalculator
            into Array<AgeAware>
        {
        Int average()
            {
            Int sum = 0;
            for (AgeAware el : this)
                {
                sum += el.age;
                }
            return size == 0 ? 0 : sum/size;
            }
        }

    interface Extract<Element>
        {
        List insertAll(Int index, Iterable<Element> values);
        }

    void run()
        {
        @Inject Console console;

        // for exactly the same reason (Iterator is a producer now) the two below don't compile
        // Extract<AgeAware> array = new Array<Person>();
        // val calc = new @AverageAgeCalculator Array<Person>();

        val calc = new @AverageAgeCalculator Array<AgeAware>();

        calc += new Person("Bob", 31);
        calc += new Person("Sue", 25);

        console.println(calc.average());
        }
    }

