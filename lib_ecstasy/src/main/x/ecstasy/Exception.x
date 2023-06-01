/**
 * The Exception class represents a class of objects that can be _thrown_ by the `throw`
 * keyword, and _caught_ by the `catch` keyword.
 *
 * An exception is injected with stack trace information by the runtime when it is instantiated.
 * The stack trace information adapts to the service context within which it is examined, always
 * hiding portions of the stack trace that represent information from outside of the current
 * container.
 */
const Exception {
    construct(String? text = Null, Exception? cause = Null) {
        this.text  = text;
        this.cause = cause;
    }

    String? text;
    Exception!? cause;
    Iterable<StackFrame> stackTrace;

    @RO String message.get() {
        return text ?: "";
    }

    @Override
    String toString() {
        String name       = &this.actualClass.name;
        String stackTrace = formatStackTrace();
        return formatExceptionString(name, stackTrace);
    }

    String formatExceptionString(String exceptionName, String stackTrace) {
        StringBuffer buf = new StringBuffer();

        buf.append(exceptionName)
           .append(": ")
           .append(message)
           .append(stackTrace);

        if (cause != Null) {
            buf.append("\nCaused by: ")
               .append(cause.toString());
        }

        return buf.toString();
    }

    String formatStackTrace() {
        TODO
    }

    static const StackFrame {
        /**
         * The module containing the code corresponding to the execution frame.
         *
         * The module may not be available in an opaque frame.
         */
        Module? containingModule;

        /**
         * The service instance that is the service context for the execution frame.
         *
         * The service instance may not be available in an opaque frame.
         */
        Service? containingService;

        /**
         * The method or function whose source code corresponds to the executing frame. Note that
         * it is possible that the executing frame actually corresponds to a different method or
         * function, as will occur when a lambda function is defined in the body of another method
         * or function.
         *
         * The method or function may not be available in an opaque frame.
         */
        (Method | Function)? containingCode;

        /**
         * The line number in the source code of the method or function body, if it is available.
         *
         * Line number information may not be available if debugging information has been stripped
         * from a module, or in opaque frames.
         */
        Int? lineNumber;

        /**
         * True iff this frame represents a portion (one or more frames) of the actual call stack
         * that is purposefully hidden from the caller by the runtime.
         */
        Boolean opaque;
    }
}