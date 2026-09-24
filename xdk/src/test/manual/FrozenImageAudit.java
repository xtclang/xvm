import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Map;

import org.xvm.api.EmbeddingSupport;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;

import org.xvm.compiler.BuildRepository;

import org.xvm.runtime.MainContainer;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.Runtime;

/**
 * Run an assertion-based Ecstasy program with its prepared definition graph made read-only before
 * entry-method resolution. A forbidden image write fails the audit with its actual stack trace;
 * failure is not accepted as a passing test. This audit is separate from the normal integration
 * suite until the remaining interpreter descriptor/metadata paths have been migrated.
 *
 * <p>Build {@code :xdk:installDist}, then launch this source with the installed javatools.jar on the
 * classpath. Pass the installed XDK directory and an Ecstasy source file as the two arguments.
 * The existing {@code xdk/src/test/resources/ownership/*.x} programs are suitable workloads.
 * No JIT execution or new automatic CI task is involved.
 */
class FrozenImageAudit {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: FrozenImageAudit <installed-xdk> <program.x>");
        }
        var installed = Path.of(args[0]);
        var repository = new LinkedRepository(
                new DirRepository(installed.resolve("lib").toFile(), true),
                new DirRepository(installed.resolve("javatools").toFile(), true));
        var compiler = EmbeddingSupport.instance().configure(repository, null);
        var errors = new ErrorList(100);
        var module = compiler.compile(Files.readString(Path.of(args[1])), repository, errors);
        if (module == null || errors.hasSeriousErrors()) {
            throw new IllegalStateException("Compilation failed: " + errors);
        }

        // Reload the compiled artifact so runtime native preparation sees the same input as the
        // ordinary launcher, rather than the compiler's linked working structures.
        var binary = new ByteArrayOutputStream();
        module.getFileStructure().writeTo(binary);
        module = new FileStructure(new ByteArrayInputStream(binary.toByteArray())).getModule();
        var modules = new BuildRepository();
        modules.storeModule(module);
        var runtimeRepository = new LinkedRepository(modules, repository);
        var runtime = new Runtime();
        try {
            var root = new NativeContainer(runtime, runtimeRepository);
            var file = root.createFileStructure(module);
            var missing = file.linkModules(runtimeRepository, true);
            if (missing != null) {
                throw new IllegalStateException("Missing runtime module: " + missing);
            }
            var application = new MainContainer(runtime, root, file.getModuleId());
            application.getTypeContext().freezeDefinitions();
            application.start(Map.of());
            application.invokeAsync("run").join();
        } finally {
            runtime.shutdownXVM();
        }
    }
}
