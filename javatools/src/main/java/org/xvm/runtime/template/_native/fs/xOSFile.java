package org.xvm.runtime.template._native.fs;


import com.sun.nio.file.ExtendedOpenOption;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.nio.ByteBuffer;

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
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean.BooleanHandle;
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
        extends xOSFileNode
    {
    public static xOSFile INSTANCE;

    public xOSFile(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        markNativeProperty("contents");

        markNativeMethod("readImpl", null, BYTES);
        markNativeMethod("appendImpl", null, VOID);
        markNativeMethod("truncateImpl", null, VOID);
        markNativeMethod("open", null, null);

        invalidateTypeInfo();

        s_constructor = getStructure().findConstructor();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        NodeHandle hFile = (NodeHandle) hTarget;
        switch (sPropName)
            {
            case "contents":
                return getPropertyContents(frame, hFile, iReturn);
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
                return setPropertyContents(frame, hFile, (ArrayHandle) hValue);
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
            case "readImpl":
                {
                GenericHandle hRange = (GenericHandle) hArg;

                long    ixLower  = ((JavaLong)      hRange.getField(frame, "lowerBound")).getValue();
                long    ixUpper  = ((JavaLong)      hRange.getField(frame, "upperBound")).getValue();
                boolean fExLower = ((BooleanHandle) hRange.getField(frame, "lowerExclusive")).get();
                boolean fExUpper = ((BooleanHandle) hRange.getField(frame, "upperExclusive")).get();

                if (fExLower)
                    {
                    // exclusive lower
                    ++ixLower;
                    }

                if (ixLower < 0)
                    {
                    return frame.raiseException(xException.outOfBounds(frame, ixLower, 0));
                    }

                if (fExUpper)
                    {
                    // exclusive upper
                    --ixUpper;
                    }

                return ixUpper > ixLower
                        ? invokeReadImpl(frame, hFile, ixLower, ixUpper, iReturn)
                        : frame.assignValue(iReturn, xArray.ensureEmptyByteArray());
                }

            case "appendImpl":
                return invokeAppendImpl(frame, hFile, (ArrayHandle) hArg);

            case "truncateImpl":
                return invokeTruncateImpl(frame, hFile, (JavaLong) hArg);
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
        NodeHandle hStruct = new NodeHandle(clz.ensureAccess(Constants.Access.STRUCT),
                                path.toAbsolutePath(), hOSStore);
        ObjectHandle[] ahVar = Utils.ensureSize(Utils.OBJECTS_NONE, s_constructor.getMaxVars());

        return proceedConstruction(frame, s_constructor, true, hStruct, ahVar, iReturn);
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation of "Byte[] contents.get()".
     */
    private int getPropertyContents(Frame frame, NodeHandle hFile, int iReturn)
        {
        Path path = hFile.f_path;

        // TODO how to limit the consumption? Need to ask the container...
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

    /**
     * Implementation of "void contents.set(Byte[] value)".
     */
    private int setPropertyContents(Frame frame, NodeHandle hFile, ArrayHandle hValue)
        {
        Path   path = hFile.f_path;
        byte[] ab   = xByteArray.getBytes(hValue);

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

    /**
     * Implementation for: readImpl(Range<Int> range).
     */
    private int invokeReadImpl(Frame frame, NodeHandle hFile, long ixFrom, long ixTo, int iReturn)
        {
        Path path  = hFile.f_path;
        long cSize = path.toFile().length();
        if (ixTo >= cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, ixTo, cSize));
            }

        // TODO how to limit the consumption? Need to ask the container...
        int        cCapacity = (int) (ixTo - ixFrom + 1);
        ByteBuffer buffer    = ByteBuffer.allocate(cCapacity);

        Callable<Integer> task = () ->
            {
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ))
                {
                return channel.read(buffer, ixFrom);
                }
            };

        CompletableFuture<Integer> cfRead = frame.f_context.f_container.scheduleIO(task);

        Frame.Continuation continuation = frameCaller ->
            {
            try
                {
                if (cfRead.get() == cCapacity)
                    {
                    return frameCaller.assignValue(iReturn,
                        xArray.makeByteArrayHandle(buffer.array(), Mutability.Constant));
                    }
                else
                    {
                    return frameCaller.raiseException(
                            xException.ioException(frameCaller, "Read failed"));
                    }
                }
            catch (Throwable e)
                {
                return raisePathException(frameCaller, e, path);
                }
            };

        return frame.waitForIO(cfRead, continuation);
        }

    /**
     * Implementation for: "void truncateImpl(Int newSize)"
     */
    private int invokeTruncateImpl(Frame frame, NodeHandle hFile, JavaLong hNewSize)
        {
        Path path = hFile.f_path;
        File file = path.toFile();
        long cOld = file.length();
        long cNew = hNewSize.getValue();

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

    /**
     * Implementation for: "void appendImpl(Byte[] contents)".
     */
    private int invokeAppendImpl(Frame frame, NodeHandle hFile, ArrayHandle hContents)
        {
        Path   path = hFile.f_path;
        byte[] ab   = xByteArray.getBytes(hContents);

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

    /**
     * Implementation for: {@code FileChannel open(ReadOption read=Read, WriteOption[] write = [Write])}.
     */
    private int invokeOpen(Frame frame, NodeHandle hFile, ObjectHandle[] ahArg, int iReturn)
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

    private static final OpenOption[]  NO_ACCESS  = new OpenOption[0];
    private static final OpenOption[]  READ_ONLY  = new OpenOption[] {StandardOpenOption.READ};
    private static final OpenOption[]  WRITE_ONLY = new OpenOption[] {StandardOpenOption.WRITE};
    private static final OpenOption[]  READ_WRITE = new OpenOption[] {StandardOpenOption.READ, StandardOpenOption.WRITE};

    private static MethodStructure s_constructor;
    }