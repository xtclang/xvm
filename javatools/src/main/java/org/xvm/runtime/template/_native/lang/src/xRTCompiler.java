package org.xvm.runtime.template._native.lang.src;


import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.Op;
import org.xvm.asm.XvmStructure;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.BuildRepository;
import org.xvm.compiler.InstantRepository;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.text.xString;

import org.xvm.runtime.template._native.fs.xOSFileNode.NodeHandle;

import org.xvm.runtime.template._native.reflect.xRTComponentTemplate.ComponentTemplateHandle;
import org.xvm.runtime.template._native.reflect.xRTFileTemplate;

import org.xvm.tool.Compiler;
import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.ModuleInfo;
import org.xvm.tool.ModuleInfo.Node;

import org.xvm.util.Severity;


/**
 * Native xRTCompiler implementation.
 */
public class xRTCompiler
        extends xService {
    public static xRTCompiler INSTANCE;

    public xRTCompiler(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public void initNative() {
        ClassStructure structRepo = f_container.getClassStructure("mgmt.ModuleRepository");
        GET_MODULE_ID = structRepo.findMethod("getModule", 2).getIdentityConstant();

        markNativeMethod("compileImpl", null, null);

        invalidateTypeInfo();
    }

    @Override
    public TypeConstant getCanonicalType() {
        return pool().ensureEcstasyTypeConstant("lang.src.Compiler");
    }

    @Override
    public ServiceHandle createServiceHandle(ServiceContext context, ClassComposition clz,
                                             TypeConstant typeMask) {
        CompilerOptions options = CompilerOptions.builder()
                .forceRebuild()
                .qualifyOutputNames()
                .build();
        CompilerHandle hCompiler =
                new CompilerHandle(clz.maskAs(typeMask), context, new CompilerAdapter(options));
        context.setService(hCompiler);
        return hCompiler;
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn) {
        CompilerHandle hCompiler = (CompilerHandle) hTarget;
        switch (method.getName()) {
        case "compileImpl":
            try {
                ObjectHandle   hRepo     = ahArg[0];
                ArrayHandle    haSources = (ArrayHandle) ahArg[1];
                ObjectHandle[] ahSource  = haSources.getTemplate().toArray(frame, haSources);
                return invokeCompileImpl(frame, hCompiler, hRepo, ahSource, aiReturn);
            } catch (ExceptionHandle.WrapperException e) {
                return frame.raiseException(e);
            }
        }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }

    /**
     * Native implementation of:
     *      "(FileTemplate[] modules, String[] errors)
     *          compileImpl(ModuleRepository repo, OSFileNode[] sources)"
     */
    protected int invokeCompileImpl(Frame frame, CompilerHandle hCompiler, ObjectHandle hRepo,
                                    ObjectHandle[] ahSources, int[] aiReturn) {
        CompilerAdapter compiler = hCompiler.fAdapter;

        List<File> listSources = new ArrayList<>();
        for (ObjectHandle hSource : ahSources) {
            listSources.add(((NodeHandle) hSource).getPath().toFile());
        }
        compiler.setSourceLocations(listSources);

        // create a new repository list based on the core repository parts
        LinkedRepository repoCore = (LinkedRepository) f_container.getModuleRepository();

        List<ModuleRepository> listNew = new ArrayList<>();
        listNew.add(new BuildRepository());

        for (ModuleRepository repo : repoCore.asList()) {
            // take all read-only repositories
            if (repo instanceof DirRepository  repoDir  && repoDir .isReadOnly() ||
                repo instanceof FileRepository repoFile && repoFile.isReadOnly()) {
                listNew.add(repo);
            }
        }

        compiler.setLibraryRepos(listNew);

        return doCompile(frame, compiler, hRepo, null, aiReturn);
    }

    private int doCompile(Frame frame, CompilerAdapter compiler,
                          ObjectHandle hRepo, String sMissingPrev, int[] aiReturn) {
        try {
            String sMissing = compiler.partialCompile(sMissingPrev != null);
            if (sMissing != null) {
                if (sMissing.equals(sMissingPrev) || hRepo == xNullable.NULL) {
                    return completeWithError(frame, compiler, sMissing, aiReturn);
                }
                CallChain chain = computeGetModuleChain(frame, hRepo);
                switch (chain.invoke(frame, hRepo, xString.makeHandle(sMissing), STACK_2)) {
                case Op.R_NEXT:
                    return popModuleStructure(frame, compiler)
                        ? doCompile(frame, compiler, hRepo, sMissing, aiReturn)
                        : completeWithError(frame, compiler, sMissing, aiReturn);

                case Op.R_CALL:
                    Frame.Continuation nextStep = frameCaller ->
                        popModuleStructure(frameCaller, compiler)
                            ? doCompile(frameCaller, compiler, hRepo, sMissing, aiReturn)
                            : completeWithError(frameCaller, compiler, sMissing, aiReturn);
                    frame.m_frameNext.addContinuation(nextStep);
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
            return completeCompilation(frame, compiler, null, aiReturn);
        } catch (Exception e) {
            return completeCompilation(frame, compiler, e, aiReturn);
        }
    }

    private CallChain computeGetModuleChain(Frame frame, ObjectHandle hRepo) {
        Object nid = GET_MODULE_ID.resolveNestedIdentity(
                        frame.poolContext(), frame.getGenericsResolver(true));

        TypeComposition clazz = hRepo.getComposition();
        CallChain       chain = clazz.getMethodCallChain(nid);
        if (chain.isEmpty()) {
            return new CallChain.ExceptionChain(xException.makeHandle(frame,
                "Missing method \"" + GET_MODULE_ID +
                "\" on " + hRepo.getType().getValueString()));
        }

        return chain;
    }

    /**
     * Obtain a ModuleStructure from the natural (conditional ModuleTemplate) return value on the
     * stack and add it to the CompilerAdapter's repository.
     *
     * @return true iff the module was successfully loaded and added
     */
    private boolean popModuleStructure(Frame frame, CompilerAdapter compiler) {
        ObjectHandle hReturn = frame.popStack();
        if (hReturn instanceof ComponentTemplateHandle hModule) {
            assert frame.popStack() == xBoolean.TRUE;
            compiler.addRepo((ModuleStructure) hModule.getComponent());
            return true;
        }

        assert hReturn == xBoolean.FALSE;
        return false;
    }

    private int completeWithError(Frame frame, CompilerAdapter compiler, String
                                  sMissing, int[] aiReturn) {
        // org.xvm.compiler.Compiler.MODULE_MISSING
        compiler.logError(Severity.FATAL, "MODULE_MISSING", new Object[] {sMissing});
        return completeCompilation(frame, compiler, null, aiReturn);
    }

    private int completeCompilation(Frame frame, CompilerAdapter compiler,
                                    Exception exception, int[] aiReturn) {
        List<ComponentTemplateHandle> listFiles = new ArrayList<>();
        List<String>                  listErrors = compiler.getErrors();
        boolean      fSuccess;
        if (exception == null) {
            fSuccess = compiler.getSeverity().compareTo(Severity.ERROR) < 0;
            if (fSuccess) {
                Container        container = frame.f_context.f_container;
                ModuleRepository repoBuild = compiler.getBuildRepository();
                for (String sModule : repoBuild.getModuleNames()) {
                    listFiles.add(xRTFileTemplate.makeHandle(container,
                            repoBuild.loadModule(sModule).getFileStructure()));
                }
            }
        } else {
            fSuccess   = false;
            listErrors = addError(exception, listErrors);
        }

        assert fSuccess || !listErrors.isEmpty(); // a compilation failure must report reasons

        ConstantPool    pool      = frame.poolContext();
        TypeConstant    typeArray = pool.ensureArrayType(xRTFileTemplate.FILE_TEMPLATE_TYPE);
        TypeComposition clzArray  = f_container.resolveClass(typeArray);

        ArrayHandle haTemplates;
        ArrayHandle haErrors;
        if (fSuccess) {
            haTemplates = xArray.makeArrayHandle(clzArray, listFiles.size(),
                            listFiles.toArray(Utils.OBJECTS_NONE), Mutability.Constant);
            haErrors    = xString.ensureEmptyArray();
        } else {
            haTemplates = xArray.makeArrayHandle(clzArray, 0, Utils.OBJECTS_NONE, Mutability.Constant);
            haErrors    = xString.makeArrayHandle(listErrors.toArray(Utils.NO_NAMES));
        }

        compiler.reset();
        return frame.assignValues(aiReturn, haTemplates, haErrors);
    }

    private List<String> addError(Exception exception, List<String> listErrors) {
        if (listErrors.isEmpty()) {
            listErrors = new ArrayList<>();
        }
        listErrors.add(exception.toString());
        return listErrors;
    }

    /**
     * Injection support.
     */
    public ObjectHandle ensureCompiler(Frame frame, ObjectHandle hOpts) {
        return createServiceHandle(
                f_container.createServiceContext("Compiler"),
                    getCanonicalClass(), getCanonicalType());
    }


    // ----- ObjectHandle --------------------------------------------------------------------------

    protected static class CompilerHandle
            extends ServiceHandle {
        protected final CompilerAdapter fAdapter;

        protected CompilerHandle(TypeComposition clazz, ServiceContext context, CompilerAdapter adapter) {
            super(clazz, context);

            fAdapter = adapter;
        }
    }

    /**
     * Adapter into the native compiler.
     */
    protected static class CompilerAdapter extends Compiler {

        private List<ModuleRepository> m_listRepos;
        private List<File>             m_listSources;
        private ModuleRepository       m_repoResults;

        // re-entry support
        private List<org.xvm.compiler.Compiler> m_compilers;
        private ModuleRepository                m_repoOutput;
        private List<Node>                      m_allNodes;

        // error collection
        private final ErrorList m_errorList;

        protected CompilerAdapter(CompilerOptions options) {
            super(options, null, m_errorList = new ErrorList(100));
        }

        // ----- accessors -------------------------------------------------------------------------

        protected void setLibraryRepos(List<ModuleRepository> listRepos) {
            m_listRepos = listRepos;
        }

        protected void addRepo(ModuleStructure module) {
            m_listRepos.add(new InstantRepository(module));
        }

        protected void setSourceLocations(List<File> listSources) {
            m_listSources = listSources;
        }

        @Override
        protected List<File> getInputLocations() {
            return m_listSources != null ? m_listSources : List.of();
        }

        protected Severity getSeverity() {
            return m_sevWorst;
        }

        protected List<String> getErrors() {
            return m_errorList.getErrors().stream()
                    .map(err -> err.getSeverity().desc() + ": " + err.getMessage())
                    .toList();
        }

        protected ModuleRepository getBuildRepository() {
            return m_repoResults;
        }

        protected void logError(Severity severity, String sCode, Object[] aoParam) {
            m_errorList.log(severity, sCode, aoParam, (XvmStructure) null);
        }

        /**
         * This method is basically a copy of {@link Compiler#process()} implementation that allows
         * a re-entry to the "link" stage.
         *
         * @param fReenter if true, skip all steps prior to module linking
         *
         * @return a name of a missing module if any
         */
        protected String partialCompile(boolean fReenter) {
            List<org.xvm.compiler.Compiler> compilers;
            ModuleRepository                repoLib;
            ModuleRepository                repoOutput;
            List<Node>                      allNodes;

            if (fReenter) {
                compilers  = m_compilers;
                repoLib    = configureLibraryRepo(null);
                repoOutput = m_repoOutput;
                allNodes   = m_allNodes;
            } else {
                var              opts         = options();
                List<File>       resourceDirs = opts.getResourceLocations();
                File             fileOutput   = opts.getOutputLocation().orElse(null);
                boolean          fRebuild     = opts.isForcedRebuild();
                List<ModuleInfo> listTargets  = selectTargets(getInputLocations(), resourceDirs, fileOutput);
                checkErrors();

                var mapTargets     = new LinkedHashMap<File, Node>(listTargets.size());
                int cSystemModules = 0;
                for (ModuleInfo moduleInfo : listTargets) {
                    Node node = moduleInfo.getSourceTree(this);
                    // short-circuit the compilation of any up-to-date modules
                    if (fRebuild || !moduleInfo.isUpToDate()) {
                        mapTargets.put(moduleInfo.getSourceFile(), node);
                        if (moduleInfo.isSystemModule()) {
                            ++cSystemModules;
                        }
                    }
                }

                if (mapTargets.isEmpty()) {
                    log(Severity.INFO, "All modules are up to date; terminating compiler");
                    return null;
                }

                allNodes = List.copyOf(mapTargets.values());
                flushAndCheckErrors(allNodes);

                // repository setup
                repoLib = configureLibraryRepo(opts.getModulePath());
                checkErrors();

                if (cSystemModules == 0) {
                    prelinkSystemLibraries(repoLib);
                }
                checkErrors();

                repoOutput = configureResultRepo(fileOutput);
                checkErrors();

                // the code below could be extracted if necessary: compile(allNodes, repoLib, repoOutput);
                var mapCompilers = resolveCompilers(allNodes, repoLib);
                flushAndCheckErrors(allNodes);
                compilers = new ArrayList<>(mapCompilers.values());
            }

            // inline linkModules() implementation
            for (var compiler : compilers) {
                ModuleConstant idMissing = compiler.linkModules(repoLib);
                if (idMissing != null) {
                    // save off the necessary state
                    m_compilers  = compilers;
                    m_repoOutput = repoOutput;
                    m_allNodes   = allNodes;
                    return idMissing.getName();
                }
            }

            resolveNames(compilers);
            flushAndCheckErrors(allNodes);

            injectNativeTurtle(repoLib);
            checkErrors();

            validateExpressions(compilers);
            flushAndCheckErrors(allNodes);

            generateCode(compilers);
            flushAndCheckErrors(allNodes);

            emitModules(allNodes, repoOutput);
            flushAndCheckErrors(allNodes);

            return null;
        }

        @Override
        protected void reset() {
            super.reset();

            m_listSources = null;
            m_listRepos   = null;
            m_repoResults = null;
            m_compilers   = null;
            m_repoOutput  = null;
            m_allNodes    = null;
            m_errorList.clear();
        }

        // ----- Compiler API ----------------------------------------------------------------------

        @Override
        protected ModuleRepository configureLibraryRepo(List<File> ignore) {
            return new LinkedRepository(true, m_listRepos.toArray(ModuleRepository.NO_REPOS));
        }

        @Override
        protected ModuleRepository configureResultRepo(File fileDest) {
            return m_repoResults = new BuildRepository();
        }

        @Override
        public int run() {
            throw new IllegalStateException("use partialCompile() instead");
        }

        @Override
        protected void linkModules(List<org.xvm.compiler.Compiler> compilers, ModuleRepository repo) {
            // inlined; should not be called
            throw new IllegalStateException("xRTCompiler.linkModules");
        }
    }

    // ----- constants -----------------------------------------------------------------------------

    private static final int[] STACK_2 = new int[] {Op.A_STACK, Op.A_STACK};

    private static MethodConstant GET_MODULE_ID;
}