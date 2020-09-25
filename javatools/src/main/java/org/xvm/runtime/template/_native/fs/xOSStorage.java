package org.xvm.runtime.template._native.fs;


import java.io.IOException;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * Native OSStorage implementation.
 */
public class xOSStorage
        extends xService
    {
    public xOSStorage(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        s_methodOnEvent = getStructure().findMethod("onEvent", null, null);

        markNativeProperty("homeDir");
        markNativeProperty("curDir");
        markNativeProperty("tmpDir");

        markNativeMethod("find", new String[] {"_native.fs.OSFileStore", "text.String"}, null);
        markNativeMethod("names", STRING, null);
        markNativeMethod("createDir", STRING, BOOLEAN);
        markNativeMethod("createFile", STRING, BOOLEAN);
        markNativeMethod("delete", STRING, BOOLEAN);
        markNativeMethod("watch", STRING, VOID);
        markNativeMethod("unwatch", STRING, VOID);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    protected int postValidate(Frame frame, ObjectHandle hStruct)
        {
        return Op.R_NEXT;
        }

    @Override
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        if (idProp.getName().equals("fileStore"))
            {
            // optimize out the cross-service call
            return frame.assignValue(iReturn,
                ((ServiceHandle) hTarget).getField("fileStore"));
            }

        return super.getPropertyValue(frame, hTarget, idProp, iReturn);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ServiceHandle hStorage = (ServiceHandle) hTarget;
        ObjectHandle  hStore   = hStorage.getField("fileStore");

        // the handles below are cached by the Container.initResources()
        switch (sPropName)
            {
            case "homeDir":
                return OSFileNode.createHandle(frame, hStore,
                    Paths.get(System.getProperty("user.home")), true, iReturn);

            case "curDir":
                return OSFileNode.createHandle(frame, hStore,
                    Paths.get(System.getProperty("user.dir")), true, iReturn);

            case "tmpDir":
                return OSFileNode.createHandle(frame, hStore,
                    Paths.get(System.getProperty("java.io.tmpdir")), true, iReturn);
            }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        ServiceHandle hStorage = (ServiceHandle) hTarget;

        if (frame.f_context != hStorage.f_context)
            {
            return xRTFunction.makeAsyncNativeHandle(method).
                call1(frame, hTarget, new ObjectHandle[] {hArg}, iReturn);
            }

        switch (method.getName())
            {
            case "names":
                {
                StringHandle hPathString = (StringHandle) hArg;

                Path     path   = Paths.get(hPathString.getStringValue());
                String[] asName = path.toFile().list();
                int      cNames = asName == null ? 0 : asName.length;

                StringHandle[] ahName = new StringHandle[cNames];
                int i = 0;
                for (String sName : asName)
                    {
                    ahName[i++] = xString.makeHandle(sName);
                    }

                return frame.assignValue(iReturn, xArray.makeStringArrayHandle(ahName));
                }

            case "createFile":  // (pathString)
                {
                StringHandle hPathString = (StringHandle) hArg;

                Path path = Paths.get(hPathString.getStringValue());
                if (Files.exists(path) && !Files.isDirectory(path))
                    {
                    return frame.assignValue(iReturn, xBoolean.FALSE);
                    }

                try
                    {
                    return frame.assignValue(iReturn,
                        xBoolean.makeHandle(path.toFile().createNewFile()));
                    }
                catch (IOException e)
                    {
                    return raisePathException(frame, e, path.toString());
                    }
                }

            case "createDir":  // (pathString)
                {
                StringHandle hPathString = (StringHandle) hArg;

                Path path = Paths.get(hPathString.getStringValue());
                if (Files.exists(path) && Files.isDirectory(path))
                    {
                    return frame.assignValue(iReturn, xBoolean.FALSE);
                    }

                return frame.assignValue(iReturn,
                    xBoolean.makeHandle(path.toFile().mkdirs()));
                }

            case "delete":  // (pathString)
                {
                StringHandle hPathString = (StringHandle) hArg;

                Path path = Paths.get(hPathString.getStringValue());
                if (!Files.exists(path))
                    {
                    return frame.assignValue(iReturn, xBoolean.FALSE);
                    }

                return frame.assignValue(iReturn,
                    xBoolean.makeHandle(path.toFile().delete()));
                }

            case "watch":  // (pathStringDir)
                {
                StringHandle hPathStringDir = (StringHandle) hArg;

                Path pathDir = Paths.get(hPathStringDir.getStringValue());
                try
                    {
                    ensureWatchDaemon(pool()).register(pathDir, hStorage);
                    return Op.R_NEXT;
                    }
                catch (IOException e)
                    {
                    return raisePathException(frame, e, pathDir.toString());
                    }
                }
            }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        ServiceHandle hStorage = (ServiceHandle) hTarget;

        if (frame.f_context != hStorage.f_context)
            {
            // for now let's make sure all the calls are processed on the service fibers
            return xRTFunction.makeAsyncNativeHandle(method).call1(frame, hTarget, ahArg, iReturn);
            }

        switch (method.getName())
            {
            }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        ServiceHandle hStorage = (ServiceHandle) hTarget;

        if (frame.f_context != hStorage.f_context)
            {
            // for now let's make sure all the calls are processed on the service fibers
            return xRTFunction.makeAsyncNativeHandle(method).callN(frame, hTarget, ahArg, aiReturn);
            }

        switch (method.getName())
            {
            case "find":  // (store, pathString)
                {
                ObjectHandle hStore      = ahArg[0];
                StringHandle hPathString = (StringHandle) ahArg[1];

                Path path = Paths.get(hPathString.getStringValue());
                if (Files.exists(path))
                    {
                    return Utils.assignConditionalResult(
                        frame,
                        OSFileNode.createHandle(frame, hStore, path, Files.isDirectory(path), Op.A_STACK),
                        aiReturn);
                    }
                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                }
            }
        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- helper methods ------------------------------------------------------------------------

    protected static synchronized WatchServiceDaemon ensureWatchDaemon(ConstantPool pool)
        {
        WatchServiceDaemon daemonWatch = s_daemonWatch;
        if (daemonWatch == null)
            {
            try
                {
                daemonWatch = s_daemonWatch = new WatchServiceDaemon(pool);
                daemonWatch.start();
                }
            catch (IOException e)
                {
                return null;
                }
            }
        return daemonWatch;
        }

    protected int raisePathException(Frame frame, IOException e, String sPath)
        {
        // TODO: how to get the natural Path efficiently from sPath?
        return frame.raiseException(xException.pathException(frame, e.getMessage(), xNullable.NULL));
        }

    protected static class WatchServiceDaemon
            extends Thread
        {
        public WatchServiceDaemon(ConstantPool pool)
                throws IOException
            {
            super("WatchServiceDaemon");

            setDaemon(true);

            f_pool       = pool;
            f_service    = FileSystems.getDefault().newWatchService();
            f_mapWatches = new ConcurrentHashMap<>();
            }

        public void register(Path pathDir, ServiceHandle hStorage)
                throws IOException
            {
            // on Mac OS the WatchService implementation simply polls every 10 seconds;
            // for Java 9 and above there is no way to configure that
            WatchKey key = pathDir.register(
                f_service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
                );

            f_mapWatches.put(key, new WatchContext(pathDir, hStorage));
            }

        @Override
        public void run()
            {
            try (var x = ConstantPool.withPool(f_pool))
                {
                while (true)
                    {
                    processKey(f_service.take());
                    }
                }
            catch (InterruptedException e)
                {
                // TODO ?
                }
            }

        protected void processKey(WatchKey key)
            {
            if (key == null)
                {
                return;
                }

            for (WatchEvent event : key.pollEvents())
                {
                int iKind = getKindId(event.kind());
                if (iKind < 0)
                    {
                    continue;
                    }

                WatchContext context = f_mapWatches.get(key);

                Path pathDir      = context.pathDir;
                Path pathRelative = (Path) event.context();
                Path pathAbsolute = pathDir.resolve(pathRelative);

                FunctionHandle hfnOnEvent =
                        xRTFunction.makeHandle(s_methodOnEvent).bindTarget(context.hStorage);

                StringHandle hPathDir  = xString.makeHandle(pathDir.toString());
                StringHandle hPathNode = xString.makeHandle(pathAbsolute.toString());

                ObjectHandle[] ahArg = new ObjectHandle[]
                    {
                    hPathDir, hPathNode, xBoolean.TRUE, xInt64.makeHandle(iKind)
                    };
                context.hStorage.f_context.callLater(hfnOnEvent, ahArg, true);
                }
            key.reset();
            }

        /**
         * @return 0 - for CREATE, 1 - for MODIFY, 2 - for DELETE, -1 for OVERFLOW;
         *        -2 for anything else
         */
        private int getKindId(WatchEvent.Kind kind)
            {
            if (kind == StandardWatchEventKinds.ENTRY_CREATE)
                {
                return 0;
                }
            if (kind == StandardWatchEventKinds.ENTRY_MODIFY)
                {
                return 1;
                }
            if (kind == StandardWatchEventKinds.ENTRY_DELETE)
                {
                return 2;
                }
            if (kind == StandardWatchEventKinds.OVERFLOW)
                {
                return -1;
                }
            return -2;
            }

        // ----- WatchContext class --------------------------------------------------------------

        private static class WatchContext
            {
            public WatchContext(Path pathDir, ServiceHandle hStorage)
                {
                this.pathDir  = pathDir;
                this.hStorage = hStorage;
                }
            public final Path          pathDir;
            public final ServiceHandle hStorage;
            }

        private final ConstantPool                f_pool;
        private final Map<WatchKey, WatchContext> f_mapWatches;
        private final WatchService                f_service;
        }

    // ----- constants -----------------------------------------------------------------------------

    private static MethodStructure s_methodOnEvent;

    private static WatchServiceDaemon s_daemonWatch;
    }
