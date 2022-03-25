/**
 * A provider of a function, typically a handler for an HTTP request.
 * TODO remove or refactor
 */
interface ExecutableFunction
        extends Const
    {
    function void () createFunction();

    @RO Boolean conditionalResult;
    }

