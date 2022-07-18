package org.xvm.runtime.template._native.fs;


import com.sun.nio.file.ExtendedOpenOption;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.nio.channels.FileChannel;

import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.util.Handy;


/**
 * Native OSFile implementation.
 */
public class xOSFile
        extends OSFileNode
    {
    public static xOSFile INSTANCE;

    public xOSFile(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        super.initNative();

        markNativeProperty("contents");

        markNativeMethod("open", null, null);
        markNativeMethod("appendImpl", null, VOID);
        markNativeMethod("truncateImpl", null, VOID);

        getCanonicalType().invalidateTypeInfo();

        s_constructor = getStructure().findConstructor();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        NodeHandle hFile = (NodeHandle) hTarget;
        switch (sPropName)
            {
            case "contents":
                {
                Path path = hFile.f_path;

                Callable<byte[]> task = () ->
                        Handy.readFileBytes(path.toFile());

                CompletableFuture<byte[]> cfRead = frame.f_context.f_container.scheduleIO(task);
                Frame.Continuation continuation = frameCaller ->
                    {
                    try
                        {
                        return frameCaller.assignValue(iReturn,
                            xArray.makeByteArrayHandle(cfRead.get(), Mutability.Constant));
                        }
                    catch (Throwable e)
                        {
                        return raisePathException(frameCaller, e, path);
                        }
                    };

                return frame.waitForIO(cfRead, continuation);
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeSet(Frame frame, ObjectHandle hTarget, String sPropName,
                               ObjectHandle hValue)
        {
        NodeHandle hFile = (NodeHandle) hTarget;

        switch (sPropName)
            {
            case "contents":
                {
                Path   path = hFile.f_path;
                byte[] ab   = xByteArray.getBytes((ArrayHandle) hValue);

                Callable<Void> task = () ->
                    {
                    try (FileOutputStream out = new FileOutputStream(path.toFile()))
                        {
                        out.write(ab);
                        return null;
                        }
                    };

                CompletableFuture cfWrite = frame.f_context.f_container.scheduleIO(task);

                Frame.Continuation continuation = frameCaller ->
                    {
                    try
                        {
                        cfWrite.get();
                        return Op.R_NEXT;
                        }
                    catch (Throwable e)
                        {
                        return raisePathException(frameCaller, e, path);
                        }
                    };

                return frame.waitForIO(cfWrite, continuation);
                }
            }
        return super.invokeNativeSet(frame, hTarget, sPropName, hValue);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        NodeHandle hFile = (NodeHandle) hTarget;

        switch (method.getName())
            {
            case "appendImpl": // void appendImpl(Byte[] contents)
                {
                Path   path = hFile.f_path;
                byte[] ab   = xByteArray.getBytes((ArrayHandle) hArg);

                Callable<Void> task = () ->
                    {
                    try (FileOutputStream out = new FileOutputStream(path.toFile(), /*append*/ true))
                        {
                        out.write(ab);
                        return null;
                        }
                    };

                CompletableFuture cfAppend = frame.f_context.f_container.scheduleIO(task);

                Frame.Continuation continuation = frameCaller ->
                    {
                    try
                        {
                        cfAppend.get();
                        return Op.R_NEXT;
                        }
                    catch (Throwable e)
                        {
                        return raisePathException(frameCaller, e, path);
                        }
                    };

                return frame.waitForIO(cfAppend, continuation);
                }

            case "truncateImpl": // void truncateImpl(Int newSize)
                {
                Path path = hFile.f_path;
                File file = path.toFile();
                long cOld = file.length();
                long cNew = ((JavaLong) hArg).getValue();

                if (cNew > cOld || cNew < 0)
                    {
                    return frame.raiseException(xException.outOfBounds(frame, cNew, cOld));
                    }

                Callable<Void> task = () ->
                    {
                    try (FileChannel channel = cNew == 0
                            ? FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
                            : FileChannel.open(path, StandardOpenOption.WRITE))
                        {
                        if (cNew > 0)
                            {
                            channel.truncate(cNew);
                            }
                        return null;
                        }
                    };

                CompletableFuture cfTruncate = frame.f_context.f_container.scheduleIO(task);

                Frame.Continuation continuation = frameCaller ->
                    {
                    try
                        {
                        cfTruncate.get();
                        return Op.R_NEXT;
                        }
                    catch (Throwable e)
                        {
                        return raisePathException(frameCaller, e, path);
                        }
                    };

                return frame.waitForIO(cfTruncate, continuation);
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        NodeHandle hFile = (NodeHandle) hTarget;

        switch (method.getName())
            {
            case "open":
                {
                return invokeOpen(frame, hFile, ahArg, iReturn);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Construct a new {@link NodeHandle} representing the specified file.
     *
     * @param frame      the current frame
     * @param hOSStore   the "host" OSStore handle
     * @param path       the node's path
     * @param iReturn    the register id to place the created handle into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    public int createHandle(Frame frame, ObjectHandle hOSStore, Path path, int iReturn)
        {
        TypeComposition clz = ensureClass(frame.f_context.f_container,
                                getCanonicalType(), frame.poolContext().typeFile());

        NodeHandle     hStruct = new NodeHandle(clz.ensureAccess(Constants.Access.STRUCT),
                                path.toAbsolutePath(), hOSStore);
        ObjectHandle[] ahVar   = Utils.ensureSize(Utils.OBJECTS_NONE, s_constructor.getMaxVars());

        return proceedConstruction(frame, s_constructor, true, hStruct, ahVar, iReturn);
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code FileChannel open(ReadOption read=Read, WriteOption[] write = [Write])}.
     */
    protected int invokeOpen(Frame frame, NodeHandle hFile, ObjectHandle[] ahArg, int iReturn)
        {
        ObjectHandle hReadOption  = ahArg[0];
        ObjectHandle hWriteOption = ahArg[1];

        OpenOption[] aOpenOpt = NO_ACCESS;

        ReadOption optRead = hReadOption == ObjectHandle.DEFAULT
                ? ReadOption.Read
                : ReadOption.values()[((EnumHandle) hReadOption).getOrdinal()];

        // the most common two cases are Read/Write and Read/NoWrite
        if (hWriteOption == ObjectHandle.DEFAULT)
            {
            // the default is "Read/Write"
            switch (optRead)
                {
                case NoRead:
                    aOpenOpt = WRITE_ONLY;
                    break;

                case Read:
                    aOpenOpt = READ_WRITE;
                    break;

                case Exclusive:
                    aOpenOpt = new OpenOption[] {ExtendedOpenOption.NOSHARE_READ, StandardOpenOption.WRITE};
                    break;
                }
            }
        else
            {
            ArrayHandle    haWriteOpt = (ArrayHandle) hWriteOption;
            ObjectHandle[] ahWriteOpt;
            try
                {
                ahWriteOpt = haWriteOpt.getTemplate().toArray(frame, haWriteOpt);
                }
            catch (ExceptionHandle.WrapperException e)
                {
                return frame.raiseException(e);
                }
            int cWriteOpts = ahWriteOpt.length;

            if (cWriteOpts == 0)
                {
                switch (optRead)
                    {
                    case NoRead:
                        break;

                    case Read:
                        aOpenOpt = READ_ONLY;
                        break;

                    case Exclusive:
                        aOpenOpt = new OpenOption[] {ExtendedOpenOption.NOSHARE_READ};
                        break;
                    }
                }
            else
                {
                List<OpenOption> listOpt = new ArrayList<>(cWriteOpts);
                switch (optRead)
                    {
                    case NoRead:
                        break;

                    case Read:
                        listOpt.add(StandardOpenOption.READ);
                        break;

                    case Exclusive:
                        listOpt.add(ExtendedOpenOption.NOSHARE_READ);
                        break;
                    }

                listOpt.add(StandardOpenOption.WRITE);

                for (int i = 0; i < cWriteOpts; i++)
                    {
                    EnumHandle  hWrite   = (EnumHandle) ahWriteOpt[i];
                    WriteOption optWrite = WriteOption.values()[hWrite.getOrdinal()];
                    switch (optWrite)
                        {
                        case Write:
                            break;
                        case Ensure:
                            listOpt.add(StandardOpenOption.CREATE);
                            break;
                        case Create:
                            listOpt.add(StandardOpenOption.CREATE_NEW);
                            break;
                        case Sparse:
                            listOpt.add(StandardOpenOption.SPARSE);
                            break;
                        case Temp:
                            listOpt.add(StandardOpenOption.DELETE_ON_CLOSE);
                            break;
                        case Truncate:
                            listOpt.add(StandardOpenOption.TRUNCATE_EXISTING);
                            break;
                        case Append:
                            listOpt.add(StandardOpenOption.APPEND);
                            break;
                        case Exclusive:
                            aOpenOpt[i] = ExtendedOpenOption.NOSHARE_WRITE;
                            break;
                        case SyncData:
                            listOpt.add(StandardOpenOption.DSYNC);
                            break;
                        case SyncAll:
                            listOpt.add(StandardOpenOption.SYNC);
                            break;
                        }
                    }
                aOpenOpt = listOpt.toArray(NO_ACCESS);
                }
            }

        Path path = hFile.f_path;
        try
            {
            FileChannel channel = FileChannel.open(path, aOpenOpt);
            return xOSFileChannel.INSTANCE.createHandle(frame, channel, path, iReturn);
            }
        catch (IOException e)
            {
            return raisePathException(frame, e, path);
            }
        }


    // ----- constants -----------------------------------------------------------------------------

    private enum ReadOption  {NoRead, Read, Exclusive}
    private enum WriteOption {Write, Ensure, Create, Sparse, Temp, Truncate, Append, Exclusive, SyncData, SyncAll}

    private final static OpenOption[]  NO_ACCESS  = new OpenOption[0];
    private final static OpenOption[]  READ_ONLY  = new OpenOption[] {StandardOpenOption.READ};
    private final static OpenOption[]  WRITE_ONLY = new OpenOption[] {StandardOpenOption.WRITE};
    private final static OpenOption[]  READ_WRITE = new OpenOption[] {StandardOpenOption.READ, StandardOpenOption.WRITE};

    private static MethodStructure s_constructor;
    }