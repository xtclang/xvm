package org.xvm.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.MainContainer;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Runtime;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.text.xString;

import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

/**
 * The Connector implementation using the interpreter.
 */
public class InterpreterConnector
        extends Connector {
    /**
     * Construct the Connector based on the specified ModuleRepository, reporting runtime
     * diagnostics through {@link ErrorListener#RUNTIME} - which prints them.
     */
    public InterpreterConnector(ModuleRepository repository) {
        this(repository, ErrorListener.RUNTIME);
    }

    /**
     * Construct the Connector based on the specified ModuleRepository.
     *
     * <p>The listener is the native container's, and every container created under it inherits it,
     * so this is the one place an embedder has to name to hear everything the runtime reports. The
     * seam existed on {@link NativeContainer#create} already; this constructor is what makes it
     * reachable without building the runtime and the native container by hand.
     *
     * @param repository  the modules to run against
     * @param errs        the listener runtime diagnostics report through; required - pass
     *                    {@link ErrorListener#BLACKHOLE} to discard them
     */
    public InterpreterConnector(ModuleRepository repository, @NotNull ErrorListener errs) {
        super(repository);

        f_runtime         = new Runtime();
        f_containerNative = NativeContainer.create(f_runtime, repository,
                                requireNonNull(errs, "errs"));
    }

    @Override
    public void loadModule(String sAppName) {
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

        m_containerMain = MainContainer.create(f_runtime, f_containerNative, structApp.getModuleId());
    }

    @Override
    public ConstantPool getConstantPool() {
        return m_containerMain.getConstantPool();
    }

    @Override
    public void start(Map<String, List<String>> mapInjections) {
        if (!m_fStarted) {
            f_runtime.start();
            m_fStarted = true;
        }

        m_containerMain.start(mapInjections == null ? Map.of() : mapInjections);
    }

    @Override
    public Set<MethodStructure> findMethods(String sMethodName) {
        return findMethods(m_containerMain.getModule(), sMethodName);
    }

    @Override
    public void invoke0(MethodStructure method, String... asArg) {
        assert asArg != null;

        if (!m_fStarted) {
            throw new IllegalStateException("The container has not been started");
        }

        ConstantPool pool = m_containerMain.getConstantPool();
        TypeConstant   typeStrings = pool.ensureArrayType(pool.typeString());

        switch (method.getRequiredParamCount()) {
        case 0:
            if (asArg.length > 0) {
                assert method.getParamCount() > 0;
                TypeConstant typeArg = method.getParam(0).getType();

                assert typeStrings.isA(typeArg);
                m_containerMain.invoke0(method.getName(), xString.makeArrayHandle(m_containerMain, asArg));
                return;
            }
            break;

        case 1: {
            TypeConstant typeArg = method.getParam(0).getType();
            assert typeStrings.isA(typeArg);
            // the method requires an array that we can supply
            m_containerMain.invoke0(method.getName(), xString.makeArrayHandle(m_containerMain, asArg));
            return;
        }
        }

        m_containerMain.invoke0(method.getName(), Utils.OBJECTS_NONE);
    }

    /**
     * Wait for the container termination.
     *
     * @return zero if the main method was void or the return type not an int-convertible; otherwise
     *              the return value
     */
    @SuppressWarnings("BusyWait")
    public int join()
            throws InterruptedException {
        // extremely naive; replace
        do  {
            m_containerMain.throwIfRuntimeFailed();
            Thread.sleep(500);
        } while (!f_runtime.isIdle() || !m_containerMain.isIdle());

        m_containerMain.throwIfRuntimeFailed();
        int nResult = m_containerMain.getResult();
        m_containerLast = m_containerMain;
        m_containerMain = null;
        return nResult;
    }

    /**
     * Shut down the runtime this connector started.
     *
     * <p>Per connector, not global: {@code Runtime} holds no static state, and
     * {@code shutdownXVM} stops that instance's two executors, so closing one embedder cannot
     * affect another in the same JVM. Orderly rather than immediate - submitted work is allowed to
     * finish - and it does NOT wait for termination, so a caller that needs quiescence has to
     * arrange it separately.
     */
    public void shutdown() {
        f_runtime.shutdownXVM();
    }

    /**
     * @return the native container this connector booted
     *
     * <p>Lifted from the LSPAPI branch, where the control layer needs it to register a run's
     * console as a named native resource.</p>
     */
    public NativeContainer getNativeContainer() {
        return f_containerNative;
    }

    /**
     * @return container zero, for posting requests into a long-lived hosted application
     *
     * <p>Lifted from the LSPAPI branch. <b>Adapted:</b> their {@code getMainContainer} guards only
     * on {@code m_fStarted}, because in their usage container zero runs forever. This connector
     * additionally CLEARS {@code m_containerMain} in {@link #join()} once a run completes, so the
     * guard has to cover that too - otherwise a caller that joined a previous run gets a null and
     * a NullPointerException somewhere else entirely.</p>
     *
     * @throws IllegalStateException if the connector has not been started, or its main container
     *                               has already completed
     */
    public MainContainer getMainContainer() {
        if (!m_fStarted) {
            throw new IllegalStateException("the connector has not been started");
        }

        MainContainer container = m_containerMain;
        if (container == null) {
            throw new IllegalStateException(
                    "the main container has completed; a hosted application must still be running");
        }
        return container;
    }

    @Override
    public MainContainer diagnosticContainer() {
        return m_containerMain == null ? m_containerLast : m_containerMain;
    }


    // ----- data fields ---------------------------------------------------------------------------

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
     * Last completed main container retained only so opt-in same-JVM direct
     * diagnostics can validate owner-scoped state after {@link #join()} clears
     * the active connector state.
     */
    private MainContainer m_containerLast;

    /**
     * Status indicator.
     */
    private boolean m_fStarted;
}
