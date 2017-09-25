
// part of Ecstasy runtime
interface RestClient
    {
    RestResult query(String request);
    }
interface ConsoleApp
    {
    Void onCommand(String command);
    @ro Console console;
    }


// client application
module MyApp
        implements ConsoleApp
    {
    Void onCommand(String command)
        {
        @Inject(domain="https://jsonplaceholder.typicode.com") RestClient client;
        @Future result = client.query(command);
        &result.passTo(onCompletion);
        &result.handle(onException);
        }

    Void onCompletion(RestResult result)
        {
        @Inject Console console;
        console.print("Query completed with result:");
        console.print(result);
        }

    Void onException(Exception e)
        {
        @Inject Console console;
        console.print("Query did not complete successfully:");
        console.print(e);
        console.result = 1;
        }
    }


// object as function

// some method foo
Void foo(function Object() provide)
    {
    if (...)
        {
        Object o = provide();
        }
    }

// calling foo with a long running process
foo(() -> for (Server server : Amazon.farm() {server.formatAllDisks();});

// calling foo with "hello"
foo("hello");

// async example

service VideoRenderer
    {
    enum Status {Initial, Running, Finishing, Completed, Failed}

    @Atomic Status status;
    }

class Cache
    {
    @Soft @Lazy @Future Video video.calc()
        {
        return new VideoRender.render(...);
        }
    }

// worst thing for a parser

x = 1;      // expression? or statement? (YES)

// C / C++ / Java / shit
int x = y = z = 0;      // Seriously .. WTF?!?!?
x = 1, 2;               // 1, 2 is an expression that evaluates to ... um ... 2
x = (1, 2);             // it's just precedence .. same as above

// x
x = (1, 2);             // x is a tuple of (Int, Int) containing (1, 2)
(Int, Int) foo() { return x; }

conditional Int size()  { if (err) {return false;} else {return true, count;}

if (Int c : o.size()}
    {
    // ...
    }
else
    {
    // error
    }
