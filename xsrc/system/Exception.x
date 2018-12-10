/**
 * The Exception class represents a class of objects that can be _thrown_ by the {@code throw}
 * keyword, and _caught_ by the {@code catch} keyword.
 *
 * An exception is injected with stack trace information by the runtime when it is instantiated.
 * The stack trace information adapts to the service context within which it is examined, always
 * hiding portions of the stack trace that represent information from outside of the current
 * container.
 */
const Exception
    {
    construct(String? text = null, Exception? cause = null)
        {
        @Inject Iterable<StackFrame> stack;

        this.text = text;
        this.cause = cause;
        // this.stackTrace = stack;
        }

    String? text;
    Exception!? cause;
    Iterable<StackFrame> stackTrace;

    @Override
    String to<String>()
        {
        return formatExceptionString(null);
        }

    // TODO pass in output stream of text instead of building and returning String
    String formatExceptionString(StackFrame? lastFrame)
        {
        // exception name / text
        String strbuf = "";

        (String s, StackFrame firstFrame) = formatStackTrace(stackTrace, lastFrame);
        strbuf += s;

        // caused by ...
        if (cause != null)
            {
            TODO; // + cause.formatExceptionString(firstFrame);
            }

        return strbuf;
        }

    // TODO pass in output stream of text instead of building and returning String
    (String, StackFrame /* firstFrame */) formatStackTrace(Iterable<StackFrame> frames, StackFrame? lastFrame)
        {
        // stack trace
        StackFrame? firstFrame = null;

        Iterator<StackFrame> frames = stackTrace.iterator();
        for (StackFrame frame : frames)
            {
            if (firstFrame == null)
                {
                firstFrame = frame;
                }

            // TODO "  at "

            if (frame.opaque || frame.containingCode == null)
                {
                TODO("(unknown)");
                // continue;
                }

            // TODO path-to-code

            Int? lineNumber = frame.lineNumber;
            if (lineNumber != null)
                {
                TODO("[" + lineNumber + "]");
                }

            if (frame == lastFrame)
                {
                break;
                }
            }

        assert firstFrame != null;
        return TODO, firstFrame;
        }

    static const StackFrame
        {
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