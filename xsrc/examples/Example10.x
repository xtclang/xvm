
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