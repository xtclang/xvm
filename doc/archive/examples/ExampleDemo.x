
Void foo(Beep beeper)
    {
    @Inject Console console;

    @Future Int result = beeper.beep();

    static @Soft @Lazy(() -> genChart()) Bitmap bmpChart;

    &result.whenDone(c -> console.println("count="+c));

    @Inject Panel panel;
    panel.draw(bmpChart);



    // calculate pi to a billion places ....

    console.println("beep #" + result + " (" + beeper.count + ")");
    }

service Beep
    {
    @Atomic Int count;

    Int beep()
        {
        @Inject Console console;
        console.println(0x7);
        return ++count;
        }
    }

--

class HashMap<K,V>
    {
    Void foo()
        {
        ... new Entry() ...
        }

    static class Entry<K,V>
        {
        K key;
        V val;
        }

    HashMap<K,V> format();
    }

// ... later
class SuperDuperHashMap<K,V>
        extends HashMap<K,V>
    {

    @Override
    static class Entry<K,V>
        {
        // ...
        }
    SuperDupeHashMap<K,V> format1();
    }

new HashMap().foo();
new SDHashMap().foo();

mapSD.format().format1();


// ----

interface DataStore<Key, Value>
    {
    }

service MyApp
    {
    Void foo()
        {
        @Inject DataStore<String, Player> players;

        Player player = players.load("Scott");
        // ...
        players.store(player);
        }
    }

// ----

service CorpApp
    {
    Void install()
        {
        @Inject Database hrdb;

        // "create tables"
        @Inject DataStore<Int, Employee> emps;
        @Inject DataStore<Int, Payment> checks;
        }

    Void start()
        {
        @Inject Database hrdb;
        hrdb.schema.locked = True;
        }

    Void foo()
        {
        @Inject Database hrdb;
        using (hrdb.createTransaction())
            {
            @Inject DataStore<Int, Employee> emps;
            @Inject DataStore<Int, Payment> checks;
            for (...)
                {
                Check paycheck = new Check();
                // ...
                checks.store(...);
                }
            }
        }
    }

- usage stats
- "gas pedal"?
-