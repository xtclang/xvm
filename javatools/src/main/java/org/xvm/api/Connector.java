package org.xvm.api;


import java.io.File;

import java.util.List;
import java.util.Map;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.DirRepository;
import org.xvm.asm.FileRepository;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;
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
        m_repository      = repository;
        f_runtime         = new Runtime();
        f_containerNative = new NativeContainer(f_runtime, repository);
        }

    /**
     * Add a module repository.
     */
    public void addModuleRepository(File fileRepo)
        {
        if (m_containerMain != null)
            {
            throw new IllegalStateException("Connector is already activated");
            }

        ModuleRepository repo = fileRepo.isFile()
            ? new FileRepository(fileRepo, true)
            : new DirRepository(fileRepo, true);

        if (m_repository == null)
            {
            m_repository = repo;
            }
        else if (m_repository instanceof LinkedRepository)
            {
            List<ModuleRepository> listRepo = ((LinkedRepository) m_repository).asList();
            listRepo.add(repo);

            m_repository = new LinkedRepository(listRepo.toArray(ModuleRepository.NO_REPOS));
            }
        else
            {
            m_repository = new LinkedRepository(m_repository, repo);
            }
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

        ModuleStructure moduleApp = m_repository.loadModule(sAppName);
        if (moduleApp == null)
            {
            throw new IllegalStateException("Unable to load module \"" + sAppName + "\"");
            }

        FileStructure structApp = f_containerNative.createFileStructure(moduleApp);
        String        sMissing  = structApp.linkModules(m_repository, true);
        if (sMissing != null)
            {
            throw new IllegalStateException("Unable to load module \"" + sMissing + "\"");
            }

        ModuleConstant idApp = (ModuleConstant) structApp.
                getChild(moduleApp.getName()).getIdentityConstant();

        m_containerMain = new MainContainer(f_runtime, f_containerNative, idApp);
        }

    /**
     * Obtain the ConstantPool for the container associated with this Connector.
     */
    public ConstantPool getConstantPool()
        {
        return m_containerMain.getModule().getConstantPool();
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
     * <p/>
     * The injection map values must be either {@code String} or {@code String[]}.
     *
     * @param mapInjections (optional) a map of custom injections
     */
    public void start(Map<String, ?> mapInjections)
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
     * Invoke the test method with the specified arguments.
     *
     * @return {@code true} if XUnit was present to execute tests, otherwise {@code false}
     */
    public boolean invokeTest0()
        {
        if (!m_fStarted)
            {
            throw new IllegalStateException("The container has not been started");
            }
        return m_containerMain.invokeTest0();
        }

    /**
     * Wait for the container termination.
     */
    public void join()
            throws InterruptedException
        {
        // extremely naive; replace
        do  {
            Thread.sleep(500);
            }
        while (!f_runtime.isIdle() || !m_containerMain.isIdle());

        m_containerMain = null;
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The module repository.
     */
    private ModuleRepository m_repository;

    /**
     * The runtime associated with this Connector.
     */
    private final Runtime f_runtime;

    /**
     * The native container associated with this Connector.
     */
    protected NativeContainer f_containerNative;

    /**
     * The main container currently associated with this Connector.
     */
    private MainContainer m_containerMain;

    /**
     * Status indicator.
     */
    private boolean m_fStarted;
    }