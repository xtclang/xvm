package org.xvm.compiler;


import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Version;

import org.xvm.asm.constants.ModuleConstant;


/**
 * An in-memory {@link ModuleRepository} that several threads may read and write at once.
 *
 * <p>The same role as {@link BuildRepository} - hold the modules a build has produced - but usable
 * as the shared, accumulating output of concurrent compiles. {@code BuildRepository} is backed by a
 * plain {@link java.util.TreeMap}, so a thread calling {@code loadModule} while another calls
 * {@code storeModule} is a data race on the map itself, and a corrupted red-black tree can hang or
 * lose entries rather than fail loudly.</p>
 *
 * <p>This is a separate class rather than synchronization added to {@code BuildRepository} because
 * that type sits on the single-threaded compile path, where the guarding would be pure cost paid by
 * every existing caller to serve none of them.</p>
 *
 * <h2>What is thread-safe here, and what is NOT</h2>
 *
 * <p><b>Safe: the container.</b> Every mutation and every lookup goes through a
 * {@link ConcurrentSkipListMap}, so concurrent {@code storeModule} and {@code loadModule} cannot
 * corrupt the structure, lose an entry or see a partially built one. Skip-list rather than
 * {@link ConcurrentHashMap} because the keys are {@link ModuleConstant}s and the ordering
 * {@code BuildRepository} gets from {@code TreeMap} is preserved, so iteration order stays
 * deterministic and a build is reproducible.</p>
 *
 * <p>{@code loadModule} scans for a name match, which a concurrent map makes weakly consistent: the
 * scan may observe entries added while it runs. That is correct for this use - a module either was
 * or was not stored by the time the scan reached it, and both answers are ones the caller must
 * already handle, since a dependency it has not built yet is simply absent.</p>
 *
 * <p><b>NOT safe: the contents.</b> A {@link ModuleStructure} is mutable, and the compiler mutates
 * what it compiles against. This class hands out the instance it holds; it does not copy. Two
 * threads that both load the same module and then compile against it will corrupt each other, and
 * no amount of locking here would prevent that, because the damage happens outside this class.</p>
 *
 * <p><b>So the intended use is behind a read-through {@link org.xvm.asm.LinkedRepository} whose
 * FIRST repository is private to one compile</b>, with an instance of this class behind it:</p>
 *
 * <pre>new LinkedRepository(true, new BuildRepository(), sharedOutput, library)</pre>
 *
 * <p>{@code LinkedRepository} clones a module when it finds it in a repository after the first, and
 * caches the clone in the front one. With a per-compile front repository, every module a compile
 * touches is cloned for that compile, and this class is only ever read from - so the container
 * needs to be safe and the contents never become shared. Putting an instance of this class FIRST in
 * that chain would defeat exactly that: the first compile would cache its clone here, and every
 * later compile would find that same instance at position 0, uncloned, and mutate it.</p>
 *
 * <p>The one write the intended use makes is a build storing a finished module for later compiles
 * to depend on. That module is complete and its producer has released it, so the handoff is safe
 * for the same reason: no two compiles are mutating it.</p>
 */
public class ConcurrentBuildRepository
        implements ModuleRepository {
    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    public Set<String> getModuleNames() {
        return f_modulesById.keySet().stream()
                .map(ModuleConstant::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public ModuleStructure loadModule(String sModule) {
        for (Map.Entry<ModuleConstant, ModuleStructure> entry : f_modulesById.entrySet()) {
            if (entry.getKey().getName().equals(sModule)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public ModuleStructure loadModule(String sModule, Version version, boolean fExact) {
        if (version == null) {
            return loadModule(sModule);
        }

        for (Map.Entry<ModuleConstant, ModuleStructure> entry : f_modulesById.entrySet()) {
            if (entry.getKey().getName().equals(sModule)) {
                ModuleStructure module = entry.getValue();
                if (fExact
                        ? module.getVersion().equals(version)
                        : module.getVersion().isSubstitutableFor(version)) {
                    return module;
                }
            }
        }
        return null;
    }

    @Override
    public void storeModule(ModuleStructure module) {
        assert module != null;

        // Identical to BuildRepository's keying, deliberately: a module whose identity carries no
        // version is re-keyed under its declared one, so the two repositories are interchangeable
        // in a LinkedRepository chain and a module stored in either is found the same way.
        ModuleConstant idModule = module.getIdentityConstant();
        if (idModule.getVersion() == null) {
            Version version = module.getVersion();
            if (version != null) {
                idModule = idModule.getConstantPool()
                        .ensureModuleConstant(idModule.getName(), version);
            }
        }
        f_modulesById.put(idModule, module);
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return the number of modules held
     */
    public int size() {
        return f_modulesById.size();
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Concurrent and ordered: ordered so iteration matches {@link BuildRepository}'s and a build
     * stays reproducible, concurrent so a compile reading it while another stores into it is
     * defined behaviour rather than a corrupted tree.
     */
    private final Map<ModuleConstant, ModuleStructure> f_modulesById = new ConcurrentSkipListMap<>();
}
