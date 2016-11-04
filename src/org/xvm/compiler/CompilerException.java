package org.xvm.compiler;


/**
 * A non-fatal exception that can be emitted during any stage of the compilation
 * process to indicate that forward progress is not possible due to a flaw in
 * source material being compiled.
 *
 * @author cp 2016.11.04
 */
public class CompilerException
        extends RuntimeException
    {
    public CompilerException(String message)
        {
        super(message);
        }

    public CompilerException(String message, Throwable cause)
        {
        super(message, cause);
        }

    public CompilerException(Throwable cause)
        {
        super(cause);
        }
    }


