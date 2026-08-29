package org.xvm.runtime.template._native.fs;


import java.io.IOException;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
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

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.NativeType;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;

import org.xvm.util.Lazy;

/**
 * Native OSStorage implementation.
 */
public class xOSStorage
        extends xService {
    public xOSStorage(Container container, ClassStructure structure) {
        super(container, structure);
    }

    /** The receiver type: OSStorage is a service, represented by a ServiceHandle. */
    private static final NativeType<ServiceHandle> SELF =
            NativeType.of("_native.fs.OSStorage", ServiceHandle.class);

    /** The {@code text.String} parameter type, carrying the handle class that represents it. */
    private static final NativeType<StringHandle> STRING_TYPE =
            NativeType.of("text.String", StringHandle.class);

    @Override
    public void initNative() {
        markNativeProperty("homeDir");
        markNativeProperty("curDir");
        markNativeProperty("tmpDir");

        markNativeMethod("find", new String[] {"_native.fs.OSFileStore", "text.String"}, null);
        markNativeMethod1("names", SELF, STRING_TYPE, null,
                (frame, hStorage, hPathString, iReturn) -> names(frame, hPathString, iReturn));
        markNativeMethod1("createDir", SELF, STRING_TYPE, BOOLEAN,
                (frame, hStorage, hPath, iReturn) -> createDir(frame, hPath, iReturn));
        markNativeMethod1("createFile", SELF, STRING_TYPE, BOOLEAN,
                (frame, hStorage, hPath, iReturn) -> createFile(frame, hPath, iReturn));
        markNativeMethod1("delete", SELF, STRING_TYPE, BOOLEAN,
                (frame, hStorage, hPath, iReturn) -> delete(frame, hPath, iReturn));
        markNativeMethod1("watch", SELF, STRING_TYPE, VOID,
                (frame, hStorage, hPathDir, iReturn) -> watch(frame, hStorage, hPathDir));
        markNativeMethod("unwatch", STRING, VOID);
        markNativeMethod("instance", VOID, THIS);

        invalidateTypeInfo();
    }

    @Override
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn) {
        if ("fileStore".equals(idProp.getName())) {
            // optimize out the cross-service call
            return frame.assignValue(iReturn,
                ((ServiceHandle) hTarget).getField(frame, "fileStore"));
        }

        return super.getPropertyValue(frame, hTarget, idProp, iReturn);
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        ServiceHandle hStorage = (ServiceHandle) hTarget;
        ObjectHandle  hStore   = hStorage.getField(frame, "fileStore");

        // the handles below are cached by the Container.initResources()
        switch (sPropName) {
        case "homeDir":
            return xOSDirectory.getInstance(frame.container()).createHandle(frame, hStore,
                Paths.get(System.getProperty("user.home")), iReturn);

        case "curDir":
            return xOSDirectory.getInstance(frame.container()).createHandle(frame, hStore,
                Paths.get(System.getProperty("user.dir")), iReturn);

        case "tmpDir":
            return xOSDirectory.getInstance(frame.container()).createHandle(frame, hStore,
                Paths.get(System.getProperty("java.io.tmpdir")), iReturn);
        }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    /**
     * Native {@code names(String pathString)}, bound with typed handles: the declaration names the
     * parameter {@code text.String} and {@link #STRING_TYPE} carries the handle class for it, so
     * this body receives a {@link StringHandle} rather than casting one out of an
     * {@code ObjectHandle}.
     */
    private static int names(Frame frame, StringHandle hPathString, int iReturn) {
        try {
            Path     path   = Paths.get(hPathString.getStringValue());
            String[] asName = path.toFile().list();
            int      cNames = asName == null ? 0 : asName.length;

            Container container = frame.container();
            return cNames == 0
                     ? frame.assignValue(iReturn, xString.ensureEmptyArray(container))
                     : frame.assignValue(iReturn, xString.makeArrayHandle(container, asName));
        } catch (InvalidPathException e) {
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
        }
    }

    /** Native {@code createFile(String pathString)}. */
    private static int createFile(Frame frame, StringHandle hPathString, int iReturn) {
        try {
            Path path = Paths.get(hPathString.getStringValue());
            if (Files.exists(path) && !Files.isDirectory(path)) {
                return frame.assignValue(iReturn, xBoolean.falseHandle(frame));
            }

            Path parent = path.getParent();
            if (!Files.exists(parent)) {
                return frame.raiseException(xException.ioException(frame,
                        "Cannot create file, parent directory does not exist: " + path));
            }
            if (!Files.isDirectory(parent)) {
                return frame.raiseException(xException.ioException(frame,
                        "Cannot create file, parent is not a directory: " + path));
            }
            return frame.assignValue(iReturn,
                xBoolean.makeHandle(frame, path.toFile().createNewFile()));
        } catch (IOException|InvalidPathException e) {
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
        }
    }

    /** Native {@code createDir(String pathString)}. */
    private static int createDir(Frame frame, StringHandle hPathString, int iReturn) {
        try {
            Path path = Paths.get(hPathString.getStringValue());
            if (Files.exists(path) && Files.isDirectory(path)) {
                return frame.assignValue(iReturn, xBoolean.falseHandle(frame));
            }

            return frame.assignValue(iReturn,
                xBoolean.makeHandle(frame, path.toFile().mkdirs()));
        } catch (InvalidPathException e) {
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
        }
    }

    /** Native {@code delete(String pathString)}. */
    private static int delete(Frame frame, StringHandle hPathString, int iReturn) {
        Path path = Paths.get(hPathString.getStringValue());
        if (!Files.exists(path)) {
            return frame.assignValue(iReturn, xBoolean.falseHandle(frame));
        }

        return frame.assignValue(iReturn,
            xBoolean.makeHandle(frame, path.toFile().delete()));
    }

    /** Native {@code watch(String pathStringDir)}; needs the receiver to register the watch. */
    private int watch(Frame frame, ServiceHandle hStorage, StringHandle hPathStringDir) {
        try {
            Path pathDir = Paths.get(hPathStringDir.getStringValue());
            ensureWatchDaemon().register(pathDir, hStorage);
            return Op.R_NEXT;
        } catch (IOException|InvalidPathException e) {
            return frame.raiseException(xException.ioException(frame, e.getMessage()));
        }
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        ServiceHandle hStorage = (ServiceHandle) hTarget;

        if (frame.f_context != hStorage.f_context) {
            return xRTFunction.makeAsyncNativeHandle(frame, method).
                call1(frame, hTarget, new ObjectHandle[] {hArg}, iReturn);
        }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        ServiceHandle hStorage = (ServiceHandle) hTarget;

        if (hStorage != null && frame.f_context != hStorage.f_context) {
            // for now let's make sure all the calls are processed on the service fibers
            return xRTFunction.makeAsyncNativeHandle(frame, method).call1(frame, hTarget, ahArg, iReturn);
        }

        switch (method.getName()) {
        case "instance":
            return frame.assignValue(iReturn,
                    ((NativeContainer) f_container).ensureOSStorage(frame, null));
        }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        ServiceHandle hStorage = (ServiceHandle) hTarget;

        if (frame.f_context != hStorage.f_context) {
            // for now let's make sure all the calls are processed on the service fibers
            return xRTFunction.makeAsyncNativeHandle(frame, method).callN(frame, hTarget, ahArg, aiReturn);
        }

        switch (method.getName()) {
        case "find": { // (store, pathString)
            ObjectHandle hStore      = ahArg[0];
            StringHandle hPathString = (StringHandle) ahArg[1];

            try {
                Path path = Paths.get(hPathString.getStringValue());
                if (Files.exists(path)) {
                    return Utils.assignConditionalResult(frame,
                        xOSFileNode.createHandle(frame, hStore, path, Files.isDirectory(path), Op.A_STACK),
                        aiReturn);
                }
                return frame.assignValue(aiReturn[0], xBoolean.falseHandle(frame));
            } catch (InvalidPathException e) {
                return frame.raiseException(xException.ioException(frame, e.getMessage()));
            }
        }
        }
        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }


    // ----- helper methods ------------------------------------------------------------------------

    protected static WatchServiceDaemon ensureWatchDaemon() throws IOException {
        return WATCH_DAEMON.ensure();
    }

    /**
     * Owner of the single JVM watch daemon.
     * <p/>
     * The old static daemon field captured the ConstantPool from whichever container called
     * "watch" first. That made later events for other containers run under the wrong ambient pool.
     * Keep the OS watcher process-wide for the underlying Java WatchService, but make daemon
     * creation an explicit synchronized holder and bind the ConstantPool per watched storage handle
     * when an event is delivered.
     */
    protected static class WatchDaemonHolder {
        public synchronized WatchServiceDaemon ensure() throws IOException {
            WatchServiceDaemon daemon = this.daemon;
            if (daemon == null) {
                daemon = new WatchServiceDaemon();
                daemon.start();
                this.daemon = daemon;
            }
            return daemon;
        }

        private WatchServiceDaemon daemon;
    }

    protected static class WatchServiceDaemon
            extends Thread {
        public WatchServiceDaemon()
                throws IOException {
            super("WatchServiceDaemon");

            setDaemon(true);

            f_service    = FileSystems.getDefault().newWatchService();
            f_mapWatches = new ConcurrentHashMap<>();
        }

        public void register(Path pathDir, ServiceHandle hStorage)
                throws IOException {
            // on macOS the WatchService implementation simply polls every 10 seconds;
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
        public void run() {
            try {
                while (true) {
                    processKey(f_service.take());
                }
            } catch (InterruptedException e) {
                // TODO ?
            }
        }

        protected void processKey(WatchKey key) {
            if (key == null) {
                return;
            }

            for (WatchEvent event : key.pollEvents()) {
                int iKind = getKindId(event.kind());
                if (iKind < 0) {
                    continue;
                }

                WatchContext context = f_mapWatches.get(key);
                if (context == null) {
                    continue;
                }

                Container container = context.hStorage.f_context.f_container;
                ConstantPool pool = container.getConstantPool();
                Path pathDir      = context.pathDir;
                Path pathRelative = (Path) event.context();
                Path pathAbsolute = pathDir.resolve(pathRelative);

                xOSStorage     templateStorage = context.hStorage.getTemplate(xOSStorage.class);
                FunctionHandle hfnOnEvent =
                        xRTFunction.makeInternalHandle(container,
                                templateStorage.ensureOnEventMethod()).
                                bindTarget(null, context.hStorage);

                StringHandle hPathDir  = xString.makeHandle(container, pathDir.toString());
                StringHandle hPathNode = xString.makeHandle(container, pathAbsolute.toString());

                ObjectHandle[] ahArg = {
                    hPathDir, hPathNode, xBoolean.trueHandle(container),
                    xInt64.makeHandle(container, iKind)
                };
                context.hStorage.f_context.callLater(hfnOnEvent, ahArg);
            }
            key.reset();
        }

        /**
         * @return 0 - for CREATE, 1 - for MODIFY, 2 - for DELETE, -1 for OVERFLOW;
         *        -2 for anything else
         */
        private int getKindId(WatchEvent.Kind kind) {
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                return 0;
            }
            if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                return 1;
            }
            if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                return 2;
            }
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                return -1;
            }
            return -2;
        }

        // ----- WatchContext class --------------------------------------------------------------

        private record WatchContext(Path pathDir, ServiceHandle hStorage) {}

        private final Map<WatchKey, WatchContext> f_mapWatches;
        private final WatchService                f_service;
    }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * Owner-scoped replacement for the old static event callback method cache. Watch events are
     * delivered through the watched storage handle, so resolving the method from that handle's
     * template preserves the old callback binding without a JVM-global MethodStructure.
     */
    private final Lazy.Bound<xOSStorage, MethodStructure> f_methodOnEvent =
            Lazy.ofBound(owner -> owner.getStructure().findMethodDeep("onEvent", Utils.ANY));

    private MethodStructure ensureOnEventMethod() {
        return f_methodOnEvent.get(this);
    }

    /**
     * Process-wide watch-service holder. This is final and ownerless by design; per-container
     * ownership is carried by each registered storage handle and restored around event delivery.
     */
    private static final WatchDaemonHolder WATCH_DAEMON = new WatchDaemonHolder();
}
