package org.xvm.api;

import java.io.File;
import java.io.PrintWriter;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.stream.Collectors;

import org.xvm.asm.ModuleRepository;

/**
 * One application execution. The host retains ownership of its repository, console and directory.
 *
 * @param repository     application modules and dependencies
 * @param moduleName     the module to execute
 * @param method         the entry method name
 * @param arguments      strings passed to an entry method accepting a String array
 * @param console        optional application console
 * @param directory      isolated filesystem root, or working directory in host filesystem mode;
 *                       null selects an owned temporary root in isolated mode
 * @param hostFileSystem use host storage, root, home and temporary directories, with the explicit
 *                       working directory; does not change the Java process directory
 * @param injections     request-local String and String[] injections
 * @param backend        execution backend; JIT requests are reserved for the separate JIT branch
 */
public record RunRequest(ModuleRepository repository, String moduleName, String method,
                         List<String> arguments, PrintWriter console, File directory, boolean hostFileSystem,
                         Map<String, List<String>> injections, Backend backend) {
    /**
     * Execution backend. Selecting JIT currently throws {@link UnsupportedOperationException}
     * when the request is submitted; its implementation is maintained on the JIT branch.
     */
    public enum Backend { INTERPRETER, JIT }

    public RunRequest {
        Objects.requireNonNull(moduleName);
        Objects.requireNonNull(method);
        Objects.requireNonNull(backend);
        arguments = List.copyOf(arguments);
        injections = injections.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        if (hostFileSystem) {
            Objects.requireNonNull(directory, "Host filesystem mode requires a working directory");
        }
    }

    public RunRequest(ModuleRepository repository, String moduleName, String method,
                      List<String> arguments, PrintWriter console, File directory, boolean hostFileSystem,
                      Map<String, List<String>> injections) {
        this(repository, moduleName, method, arguments, console, directory, hostFileSystem,
                injections, Backend.INTERPRETER);
    }

    public RunRequest(ModuleRepository repository, String moduleName, String method,
                      List<String> arguments, PrintWriter console, File directory, boolean hostFileSystem) {
        this(repository, moduleName, method, arguments, console, directory, hostFileSystem, Map.of());
    }
}
