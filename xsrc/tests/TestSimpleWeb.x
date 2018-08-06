module TestSimpleWeb.xqiz.it
        incorporates WebApp
    {
    @Path("/")
    service PageCounter
            incorporates WebHandler
        {
        Int c = 0;

        @GET
        String genPage()
            {
            return "Hello World for the " + (++c) + "th time.";
            }
        }
    }

// somewhere else


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
