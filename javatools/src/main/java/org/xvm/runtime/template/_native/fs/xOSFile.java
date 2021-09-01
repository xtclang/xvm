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

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.TemplateRegistry;
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

    public xOSFile(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

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

        ClassTemplate   templateFile = f_templates.getTemplate("fs.File");
        TypeComposition clzOSFile    = ensureClass(templateFile.getCanonicalType());

        s_clzOSFileStruct = clzOSFile.ensureAccess(Constants.Access.STRUCT);
        s_constructorFile = getStructure().findConstructor();
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

                try
                    {
                    byte[] ab = Handy.readFileBytes(path.toFile());
                    return frame.assignValue(iReturn,
                        xArray.makeByteArrayHandle(ab, Mutability.Constant));
                    }
                catch (IOException e)
                    {
                    return raisePathException(frame, e, hFile.f_path);
                    }
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
                Path path = hFile.f_path;
                try (FileOutputStream out = new FileOutputStream(path.toFile()))
                    {
                    out.write(xByteArray.getBytes((ArrayHandle) hValue));
                    }
                catch (IOException e)
                    {
                    return raisePathException(frame, e, hFile.f_path);
                    }
                return Op.R_NEXT;
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
                Path             path = hFile.f_path;
                try (FileOutputStream out = new FileOutputStream(path.toFile(), /*append*/ true))
                    {
                    out.write(xByteArray.getBytes((ArrayHandle) hArg));
                    }
                catch (IOException e)
                    {
                    return raisePathException(frame, e, path);
                    }

                return Op.R_NEXT;
                }

            case "truncateImpl": // void truncateImpl(Int newSize)
                {
                Path path   = hFile.f_path;
                File file   = path.toFile();
                long cOld   = file.length();
                long cNew   = ((ObjectHandle.JavaLong) hArg).getValue();
                if (cNew > cOld || cNew < 0)
                    {
                    return frame.raiseException(xException.outOfBounds(frame, cNew, cOld));
                    }

                try (FileChannel channel = cNew == 0
                        ? FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
                        : FileChannel.open(path, StandardOpenOption.WRITE))
                    {
                    if (cNew > 0)
                        {
                        channel.truncate(cNew);
                        }
                    }
                catch (IOException e)
                    {
                    return raisePathException(frame, e, path);
                    }

                return Op.R_NEXT;
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
        TypeComposition clzStruct   = s_clzOSFileStruct;
        MethodStructure constructor = s_constructorFile;

        NodeHandle     hStruct = new NodeHandle(clzStruct, path.toAbsolutePath(), hOSStore);
        ObjectHandle[] ahVar   = Utils.ensureSize(Utils.OBJECTS_NONE, constructor.getMaxVars());

        return proceedConstruction(frame, constructor, true, hStruct, ahVar, iReturn);
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
            // the write default is Write
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
            return xOSFileChannel.createHandle(frame, channel, path, iReturn);
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

    private static TypeComposition s_clzOSFileStruct;
    private static MethodStructure s_constructorFile;
    }
