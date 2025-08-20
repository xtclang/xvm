package org.xvm.api;


import java.util.Map;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.ModuleConstant;

import org.xvm.runtime.MainContainer;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Runtime;


/**
 * The API between Java host environment and an XVM runtime.
 *
 * For a given Connector there is one and only one Runtime and one and only one top level
 * Container. All underlying Containers will use the same Runtime.
 *
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
public class Connector
    {
    /**
     * Construct the Connector based on the specified ModuleRepository.
     */
    public Connector(ModuleRepository repository)
        {
        f_repository      = repository;
        f_runtime         = new Runtime();
        f_containerNative = new NativeContainer(f_runtime, repository);
        }

    /**
     * Create the main container for the specified module.
     */
    public void loadModule(String sAppName)
        {
        if (m_containerMain != null)
            {
            throw new IllegalStateException("Connector is already activated");
            }

        ModuleStructure moduleApp = f_repository.loadModule(sAppName);
        if (moduleApp == null)
            {
            throw new IllegalStateException("Unable to load module \"" + sAppName + "\"");
            }

        FileStructure  structApp = f_containerNative.createFileStructure(moduleApp);
        ModuleConstant idMissing = structApp.linkModules(f_repository, true);
        if (idMissing != null)
            {
            throw new IllegalStateException("Unable to load module \"" + idMissing.getName() + "\"");
            }

        m_containerMain = new MainContainer(f_runtime, f_containerNative, structApp.getModuleId());
        }

    /**
     * Obtain the ConstantPool for the container associated with this Connector.
     */
    public ConstantPool getConstantPool()
        {
        return m_containerMain.getConstantPool();
        }

    /**
     * Obtain the container associated with this Connector.
     */
    public MainContainer getContainer()
        {
        return m_containerMain;
        }

    /**
     * Start the Runtime and the main Container.
     */
    public void start(Map<String, String> mapInjections)
        {
        if (!m_fStarted)
            {
            f_runtime.start();
            m_fStarted = true;
            }

        m_containerMain.start(mapInjections);
        }

    /**
     * Invoke a method with a void return and specified arguments.
     *
     * @param sMethodName  the method name
     * @param ahArg        arguments
     */
    public void invoke0(String sMethodName, ObjectHandle... ahArg)
        {
        if (!m_fStarted)
            {
            throw new IllegalStateException("The container has not been started");
            }
        m_containerMain.invoke0(sMethodName, ahArg);
        }

    /**
     * Wait for the container termination.
     *
     * @return zero if the main method was void or the return type not an int-convertible; otherwise
     *              the return value
     */
    public int join()
            throws InterruptedException
        {
        // extremely naive; replace
        do  {
            Thread.sleep(500);
            }
        while (!f_runtime.isIdle() || !m_containerMain.isIdle());

        int nResult = m_containerMain.getResult();
        m_containerMain = null;
        return nResult;
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The module repository.
     */
    private final ModuleRepository f_repository;

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