package org.xvm.runtime.template._native.lang.src;


import java.io.File;

import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.Version;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.CompilerException;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray.GenericArrayHandle;

import org.xvm.runtime.template.text.xString;

import org.xvm.runtime.template._native.fs.OSFileNode.NodeHandle;

import org.xvm.runtime.template._native.mgmt.xCoreRepository.CoreRepoHandle;

import org.xvm.tool.Compiler;

import org.xvm.util.Severity;


/**
 * Native xRTCompiler implementation.
 */
public class xRTCompiler
        extends xService
    {
    public xRTCompiler(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        markNativeMethod("compile", null, null);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        CompilerHandle hCompiler = (CompilerHandle) hTarget;
        switch (method.getName())
            {
            case "compile":
                {
                GenericArrayHandle haSources = (GenericArrayHandle) ahArg[0];
                return invokeCompile(frame, hCompiler, haSources, aiReturn);
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    /**
     * Implementation for: {@code (Boolean success, String errors) compile((File|Directory)[])}.
     */
    protected int invokeCompile(Frame frame, CompilerHandle hCompiler,
                                GenericArrayHandle haSources, int[] aiReturn)
        {
        CompilerAdapter compiler  = hCompiler.fAdapter;
        ObjectHandle    hLibRepo  = hCompiler.getField("libRepo");
        NodeHandle      hDirOut   = (NodeHandle) hCompiler.getField("outputDir");

        if (hDirOut == null)
            {
            return frame.raiseException("Destination is not specified");
            }

        List<File> listSources = new ArrayList<>();
        for (int i = 0, c = haSources.m_cSize; i < c; i++)
            {
            NodeHandle hSource = (NodeHandle) haSources.getElement(i);
            listSources.add(hSource.getPath().toFile());
            }
        compiler.setSourceLocations(listSources);

        if (hLibRepo == null || hLibRepo instanceof CoreRepoHandle)
            {
            compiler.setLibraryRepo(f_templates.f_repository);
            }
        else
            {
            throw new UnsupportedOperationException(); // TODO
            }
        compiler.setResultLocation(hDirOut.getPath().toFile());

        boolean fError;
        try
            {
            compiler.run();

            fError = compiler.getSeverity().compareTo(Severity.ERROR) >= 0;
            }
        catch (CompilerException e)
            {
            fError  = true;
            }

        return frame.assignValues(aiReturn,
                xBoolean.makeHandle(!fError),
                xString.makeHandle(compiler.getErrors()));
        }

    @Override
    public ServiceHandle createServiceHandle(ServiceContext context, ClassComposition clz, TypeConstant typeMask)
        {
        CompilerHandle hCompiler = new CompilerHandle(clz.maskAs(typeMask), context, new CompilerAdapter());
        context.setService(hCompiler);
        return hCompiler;
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    static class CompilerHandle
            extends ServiceHandle
        {
        protected final CompilerAdapter fAdapter;

        public CompilerHandle(TypeComposition clazz, ServiceContext context, CompilerAdapter adapter)
            {
            super(clazz, context);

            fAdapter = adapter;
            }
        }

    /**
     * Adapter into the native compiler.
     */
    protected static class CompilerAdapter
            extends Compiler
        {
        public CompilerAdapter()
            {
            super(null);
            }

        // ----- accessors -------------------------------------------------------------------------

        public ModuleRepository getLibraryRepo()
            {
            return m_repoLib;
            }

        public void setLibraryRepo(ModuleRepository repoLibs)
            {
            m_repoLib = repoLibs;
            }

        public List<File> getSourceLocations()
            {
            return m_listSources;
            }

        public void setSourceLocations(List<File> listSources)
            {
            m_listSources = listSources;
            }

        public File getResultLocation()
            {
            return m_fileResult;
            }

        public void setResultLocation(File fileResult)
            {
            m_fileResult = fileResult;
            }

        public Version getVersion()
            {
            return m_version;
            }

        public void setVersion(Version version)
            {
            m_version = version;
            }

        public boolean isForcedRebuild()
            {
            return m_fRebuild;
            }

        public void setForcedRebuild(boolean fRebuild)
            {
            m_fRebuild = fRebuild;
            }

        public Severity getSeverity()
            {
            return m_sevWorst;
            }

        public String getErrors()
            {
            return m_sbErrs.toString();
            }

        public ModuleRepository getBuildRepository()
            {
            return m_repoResults;
            }

        public void setBuildRepository(ModuleRepository repoResults)
            {
            m_repoResults = repoResults;
            }

        // ----- Compiler API ----------------------------------------------------------------------

        @Override
        protected ModuleRepository configureLibraryRepo(List<File> path)
            {
            // TODO: this is a temporary hack
            LinkedRepository repoCore = (LinkedRepository) getLibraryRepo();
            List<ModuleRepository> list = repoCore.asList();
            ModuleRepository[] repos = new ModuleRepository[3];
            repos[0] = makeBuildRepo();
            repos[1] = list.get(1); // ecstasy
            repos[2] = list.get(2); // _native
            return new LinkedRepository(true, repos);
            }

        @Override
        public void run()
            {
            process();
            }

        @Override
        protected void emitModules(Node[] allNodes, ModuleRepository repoOutput)
            {
            super.emitModules(allNodes, repoOutput);

            setBuildRepository(repoOutput);
            }

        @Override
        protected void log(Severity sev, String sMsg)
            {
            if (sev.compareTo(m_sevWorst) > 0)
                {
                m_sevWorst = sev;
                }

            if (sev.compareTo(Severity.WARNING) >= 0)
                {
                m_sbErrs.append(sev.desc())
                        .append(": ")
                        .append(sMsg)
                        .append('\n');
                }
            }

        @Override
        protected void abort(boolean fError)
            {
            throw new CompilerException("");
            }

        // ----- Options adapter -------------------------------------------------------------------

        @Override
        protected Compiler.Options instantiateOptions()
            {
            return new Options();
            }

        /**
         * A non-command-line Options implementation.
         */
        class Options
                extends Compiler.Options
            {
            @Override
            public List<File> getInputLocations()
                {
                return CompilerAdapter.this.getSourceLocations();
                }

            @Override
            public File getOutputLocation()
                {
                return CompilerAdapter.this.getResultLocation();
                }

            @Override
            public Version getVersion()
                {
                return CompilerAdapter.this.getVersion();
                }

            @Override
            public boolean isOutputFilenameQualified()
                {
                return true;
                }

            @Override
            public boolean isForcedRebuild()
                {
                return CompilerAdapter.this.isForcedRebuild();
                }
            }

        // ----- fields ----------------------------------------------------------------------------

        private ModuleRepository m_repoLib;
        private List<File>       m_listSources;
        private File             m_fileResult;
        private Version          m_version  = new Version("CI");
        private boolean          m_fRebuild = true;
        private StringBuffer     m_sbErrs   = new StringBuffer();
        private ModuleRepository m_repoResults;
        }
    }
