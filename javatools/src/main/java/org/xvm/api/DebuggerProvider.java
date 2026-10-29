package org.xvm.api;

import java.io.IOException;

import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.runtime.Debugger;

/**
 * A debugger for a program that the launchers run with {@code --debug}, such as an IDE's, which
 * {@link org.xvm.tool.Runner} and {@link org.xvm.tool.TestRunner} find with
 * {@link java.util.ServiceLoader}: on their class path, or in the jar that the option's
 * {@code agent=<jar>} names. javatools has no provider of its own, so without one, {@code --debug}
 * is refused, and the console debugger stays the debugger of {@code assert:debug}.
 */
public interface DebuggerProvider {
    /**
     * Start debugging a program: make the debugger that the launcher installs in the program's
     * runtime before its module starts. With the option {@code suspend}, return once the debugger
     * outside the program has sent what the program starts with, such as its breakpoints.
     *
     * @param options     what {@code --debug} gave, without {@code agent=<jar>}, such as
     *                    {@code listen:127.0.0.1:5005,suspend}
     * @param repository  the repository of the program's modules
     * @param module      the program's module
     *
     * @return the debugging of this run, which the launcher ends when the program does
     *
     * @throws IllegalArgumentException  if the options cannot be understood
     * @throws IOException               if the debugger outside cannot be reached or waited for
     */
    Session start(String options, ModuleRepository repository, ModuleStructure module)
            throws IOException;

    /**
     * The debugging of one run.
     */
    interface Session extends AutoCloseable {
        /**
         * @return the debugger to install in the program's runtime
         */
        Debugger debugger();

        /**
         * The program ended with the specified exit code; the debugger outside is told.
         *
         * @param code  the exit code
         */
        void ended(int code);

        /**
         * End the debugging, also if the program did not end normally. Once this returns, the
         * provider's own threads have ended: the launcher then closes the jar that {@code
         * agent=<jar>} named, so no class of it can be loaded any more.
         */
        @Override
        void close();
    }
}
