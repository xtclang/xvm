package org.xvm.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

/**
 * The Connector implementation using the interpreter.
 */
public class InterpreterConnector
        extends Connector {
    /**
     * Construct the Connector based on the specified ModuleRepository.
     */
    public InterpreterConnector(ModuleRepository repository) {
        super(repository);

        f_runtime         = new Runtime();
        f_containerNative = new NativeContainer(f_runtime, repository);
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

        m_containerMain = new MainContainer(f_runtime, f_containerNative, structApp.getModuleId());
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

        ConstantPool   pool        = ConstantPool.getCurrentPool();
        TypeConstant   typeStrings = pool.ensureArrayType(pool.typeString());
        ObjectHandle[] ahArg       = Utils.OBJECTS_NONE;

        switch (method.getRequiredParamCount()) {
        case 0:
            if (asArg.length > 0) {
                assert method.getParamCount() > 0;
                TypeConstant typeArg = method.getParam(0).getType();

                assert typeStrings.isA(typeArg);
                ahArg = new ObjectHandle[]{xString.makeArrayHandle(asArg)};
            }
            break;

        case 1: {
            TypeConstant typeArg = method.getParam(0).getType();
            assert typeStrings.isA(typeArg);
            // the method requires an array that we can supply
            ahArg = new ObjectHandle[]{xString.makeArrayHandle(asArg)};
            break;
        }
        }

        m_containerMain.invoke0(method.getName(), ahArg);
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
            Thread.sleep(500);
        } while (!f_runtime.isIdle() || !m_containerMain.isIdle());

        int nResult = m_containerMain.getResult();
        m_containerMain = null;
        return nResult;
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
     * Status indicator.
     */
    private boolean m_fStarted;
}
