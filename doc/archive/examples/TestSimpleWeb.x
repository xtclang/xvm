module TestSimpleWeb
        incorporates WebApp
    {
    @Path("/")
    service PageCounter
            incorporates WebHandler
        {
        // Int c = 0;
        @Inject Database db;

        @GET
        String genPage()
            {
            return $"Hello World for the {db.counter.next()}th time.";
            }
        }

    @DBSchema("db")
    module EmbeddedDatabaseExample
        {
        class Counter
            {
            // no annotations are necessary, because (as a database object) it is by default persistent
            Int current = 0;

            Int next()
                {
                return ++current;
                }
            }

        @DBService
        service Database
            {
            @DBObject Counter counter;
            }
        }
    }


// =================================================================================================
// somewhere else
// =================================================================================================

/**
 * Whatever methods and information that the xPlatform needs will be contained here.
 */
mixin WebApp
        into Module
    {


    }

mixin WebHandler
        into Service
    {
    void init()
        {
        // TODO find all of the methods on "this" that have GET/PUT/POST/DELETE
        }

    }

mixin Path(String path)
        into WebHandler
    {
    }

mixin GET    into Method {}
mixin PUT    into Method {}
mixin POST   into Method {}
mixin DELETE into Method {}


// =================================================================================================
// somewhere else
// =================================================================================================

module AutoGenFor___EmbeddedDatabaseExample
    {
    class AutoGenFor___Counter
            extends EmbeddedDatabaseExample.Counter
        {
        construct()
            {
            current = 0;
            }

        construct(Int fromDisk)
            {
            current = fromDisk;
            }

        @Override
        Int current
            {
            @Override
            void set(Int newValue)
                {
                @Inject Database db;
                super(newValue);
                db.counter = this;
                }
            }
        }

    @DBService
    service AutoGenFor___Database
            extends EmbeddedDatabaseExample.Database
        {
        @Override
        Counter counter
            {
            @Override
            Counter get()
                {
                // first check if the counter exists on disk (or wherever)
                if (AutoGenFor___Counter counter := diskStorageServiceHandlingPersistence.get("counter"))
                    {
                    return counter;
                    }
                else
                    {
                    counter = new AutoGenFor___Counter();
                    set(counter);
                    return counter;
                    }
                }

            @Override
            void set(Counter newCounter)
                {
                // update the database
                // TODO if in a tx, just add it to the "enlisted" set, otherwise write it directly
                }
            }
        }
    }
