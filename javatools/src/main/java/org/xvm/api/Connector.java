package org.xvm.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.MainContainer;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Runtime;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.text.xString;


/**
 * The API between Java host environment and an XVM runtime.
 * <p>
 * For a given Connector there is one and only one Runtime and one and only one top level
 * Container. All underlying Containers will use the same Runtime.
 * <p>
 * Normally, the usage of the Connector follows these steps:
 * <ul>
 *   <li> instantiate a Connector
 *   <li> add necessary module repositories
 *   <li> load a [main] module getting the Runtime back
 *   <li> configure the Runtime and the Container
 *   <li> start the Runtime and the Container for the main module
 *   <li> wait for the natural termination or explicitly shutdown the container at some point
 * </ul>
 */
public class Connector {
    /**
     * Construct the Connector based on the specified ModuleRepository.
     */
    public Connector(final ModuleRepository repository) {
        f_repository      = repository;
        f_runtime         = new Runtime();
        f_containerNative = new NativeContainer(f_runtime, repository);
    }

    /**
     * Create the main container for the specified module.
     */
    public void loadModule(final String sAppName) {
        if (m_containerMain != null) {
            throw new IllegalStateException("Connector is already activated");
        }

        ModuleStructure moduleApp = f_repository.loadModule(sAppName);
        if (moduleApp == null) {
            throw new IllegalStateException("Unable to load module \"" + sAppName + "\"");
        }

        FileStructure  structApp = f_containerNative.createFileStructure(moduleApp);
        ModuleConstant idMissing = structApp.linkModules(f_repository, true);
        if (idMissing != null) {
            throw new IllegalStateException("Unable to load module \"" + idMissing.getName() + "\"");
        }

        m_containerMain = new MainContainer(f_runtime, f_containerNative, structApp.getModuleId());
    }

    /**
     * Obtain the ConstantPool for the container associated with this Connector.
     */
    public ConstantPool getConstantPool() {
        return m_containerMain.getConstantPool();
    }

    /**
     * Obtain the container associated with this Connector.
     */
    public MainContainer getContainer() {
        return m_containerMain;
    }

    /**
     * Start the Runtime and the main Container.
     */
    public void start(final Map<String, String> mapInjections) {
        if (!m_fStarted) {
            f_runtime.start();
            m_fStarted = true;
        }

        m_containerMain.start(mapInjections);
    }

    /**
     * Find any possible entry points for a given name in the main module.
     */
    public Set<MethodStructure> findMethods(final String sMethodName) {
        return findMethods(m_containerMain.getModule(), sMethodName);
    }

    /**
     * Find an entry points for a given name in the specified module.
     */
    protected Set<MethodStructure> findMethods(final ModuleConstant idModule, final String sMethodName) {
        final var typeInfo = idModule.getType().ensureTypeInfo();
        return typeInfo.findMethods(sMethodName, -1, TypeInfo.MethodKind.Any).stream()
                .map(typeInfo::getMethodById)
                .map(info -> info.getHead().getMethodStructure())
                .collect(Collectors.toSet());
    }

    /**
     * Invoke an XTC method with a void return and specified arguments.
     *
     * @param method  the method structure
     * @param args    arguments (varargs for compatibility)
     */
    public void invoke0(final MethodStructure method, final String... args) {
        invoke0(method, List.of(args));
    }

    /**
     * Invoke an XTC method with a void return and specified arguments.
     * <p>
     * Supports methods with signatures like:
     * <ul>
     *   <li>{@code void run()} - no params, args ignored</li>
     *   <li>{@code void run(String[] args = [])} - optional param, args passed if present</li>
     *   <li>{@code void run(String[] args)} - required param, args always passed</li>
     * </ul>
     *
     * @param method  the method structure
     * @param args    arguments as a list (never null)
     */
    public void invoke0(final MethodStructure method, final List<String> args) {
        if (!m_fStarted) {
            throw new IllegalStateException("The container has not been started");
        }

        final int requiredParams = method.getRequiredParamCount();
        final int totalParams    = method.getParamCount();

        // Determine if we should pass args to the method
        final boolean shouldPassArgs = requiredParams > 0 || (!args.isEmpty() && totalParams > 0);

        ObjectHandle[] ahArg = Utils.OBJECTS_NONE;
        if (shouldPassArgs && totalParams > 0) {
            ConstantPool pool        = ConstantPool.getCurrentPool();
            TypeConstant typeStrings = pool.ensureArrayType(pool.typeString());
            TypeConstant typeArg     = method.getParam(0).getType();
            assert typeStrings.isA(typeArg) : "First parameter must be String[]";
            ahArg = new ObjectHandle[]{xString.makeArrayHandle(args.toArray(String[]::new))};
        }

        m_containerMain.invoke0(method.getName(), ahArg);
    }

    /**
     * Wait for the container termination.
     *
     * @return zero if the main method was void or the return type not an int-convertible; otherwise
     *              the return value
     */
    public int join() throws InterruptedException {
        // extremely naive; replace
        do  {
            Thread.sleep(500);
        } while (!f_runtime.isIdle() || !m_containerMain.isIdle());

        int nResult = m_containerMain.getResult();
        m_containerMain = null;
        return nResult;
    }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The module repository.
     */
    protected final ModuleRepository f_repository;

    /**
     * The runtime associated with this Connector.
     */
    private final Runtime f_runtime;

    /**
     * The native container associated with this Connector.
     */
    private final NativeContainer f_containerNative;

    /**
     * The main container currently associated with this Connector.
     */
    private MainContainer m_containerMain;

    /**
     * Status indicator.
     */
    private boolean m_fStarted;
}
