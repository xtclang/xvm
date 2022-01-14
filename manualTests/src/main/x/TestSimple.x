module TestSimple.test.org
    {
    @Inject Console console;

    import ecstasy.collections.*;

    void run()
        {
        Bucket[] buckets = new Bucket[3](i -> new Bucket(i));
        console.println(buckets);
        buckets.makeImmutable();

        buckets[0].key = 17; // this used to not throw!!

        console.println(buckets);
        }

    class Bucket(Int key)
        {
        @Override
        String toString()
            {
            return "b="+key;
            }
        }
    }
