package org.xvm.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.xvm.api.Connector;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.tool.LauncherOptions.RunnerOptions;


/**
 * The "xtc test" command - runs tests in an Ecstasy module using the xunit engine.
 * <p>
 * Usage:
 * <pre>
 *   xtc test [-L repo(s)] module.xtc
 * </pre>
 * <p>
 * The TestRunner extends Runner but loads the xunit_engine module and injects the
 * test module information, allowing the xunit framework to discover and run tests.
 */
public class ProtoGenRunner extends Runner {

    /**
     * The Protocol Buffers module name.
     */
    public static final String PROTO_MODULE = "protobuf.xtclang.org";

    /**
     * The name of the injectable key used to specify the Ecstasy Protobuf module name.
     */
    public static final String PROTOBUF_MODULE_ARG = "xvm.protobuf.files";

    /**
     * Entry point from the OS.
     *
     * @param args command line arguments
     */
    static void main(final String[] args) {
        Launcher.main(insertCommand(CMD_TEST, args));
    }

    /**
     * ProtoGenRunner constructor for programmatic invocation.
     *
     * @param options     the runner options (RunnerOptions or ProtoGenOptions)
     * @param console     representation of the terminal within which this command is run
     * @param errListener optional error listener for programmatic error access
     */
    public ProtoGenRunner(final LauncherOptions.ProtoGenOptions options, final Console console, final ErrorListener errListener) {
        super(options, console, errListener);
    }

    @Override
    protected Connector createConnector(final ModuleRepository repo, final ModuleStructure module) {
        // Create connector with optional JIT
        final RunnerOptions options = options();
        final Connector connector = createBaseConnector(repo, options.isJit());

        // Load the protobuf engine module
        connector.loadModule(PROTO_MODULE);

        // Inject the test module information so xunit can discover and run tests
        final var injections    = new LinkedHashMap<>(options.getInjections());
        final var moduleVersion = module.getVersionString();
        final var moduleName    = module.getName();

        injections.putAll(Map.of(PROTOBUF_MODULE_ARG, List.of(moduleName)));

        connector.start(injections);
        return connector;
    }

    protected int invokeMethod(Connector connector, MethodStructure method, List<String> args)
            throws InterruptedException {
        // if args is empty, the .proto data will be coming from Std.in
        boolean useSdtIn = args.isEmpty();
        File    curDir   = new File(".");
        File    reqFile  = new File(curDir, "protoc_request.bin");
        File    resFile  = new File(curDir, "protoc_response.bin");
        if (useSdtIn) {
            try {
                byte[] bytes = System.in.readAllBytes();
                try (OutputStream out = new FileOutputStream(reqFile)) {
                    out.write(bytes);
                }
                args = List.of("protoc_request.bin", "protoc_response.bin");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        connector.invoke0(method, args);
        int exitCode = connector.join();
        if (useSdtIn) {
            try (InputStream in = new FileInputStream(resFile)) {
                byte[] bytes = in.readAllBytes();
                System.out.writeBytes(bytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            reqFile.delete();
            resFile.delete();
        }
        return exitCode;
    }

    @Override
    public String desc() {
        return """
            Ecstasy Protocol Buffers:

                Generates Ecstasy code from Protocol Buffer .proto files.
            """;
    }
}
