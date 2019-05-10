package org.xvm.runtime.template._native.fs;


import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.xvm.asm.ClassStructure;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xInt64;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xString;


/**
 * Native base for OSFile and OSDirectory implementations.
 */
public abstract class OSFileNode
        extends ClassTemplate
    {
    protected OSFileNode(TemplateRegistry templates, ClassStructure structure)
        {
        super(templates, structure);
        }

    @Override
    public void initDeclared()
        {
        markNativeProperty("storage");
        markNativeProperty("pathString");
        markNativeProperty("exists");
        markNativeProperty("createdMillis");
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        NodeHandle hNode = (NodeHandle) hTarget;
        switch (sPropName)
            {
            case "storage":
                return frame.assignValue(iReturn, hNode.f_hOSStorage);

            case "pathString":
                return frame.assignValue(iReturn, xString.makeHandle(hNode.f_path.toString()));

            case "exists":
                return frame.assignValue(iReturn, xBoolean.makeHandle(hNode.f_path.toFile().exists()));

            case "createdMillis":
                {
                try
                    {
                    BasicFileAttributes attr = Files.readAttributes(hNode.f_path, BasicFileAttributes.class);
                    return frame.assignValue(iReturn, xInt64.makeHandle(attr.creationTime().toMillis()));
                    }
                catch (IOException e)
                    {
                    // TODO: how to get the natural Path efficiently?
                    return frame.raiseException(xException.pathException(e.getMessage(), xNullable.NULL));
                    }
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }


    /**
     * @return a new NodeHandle representing the specified file or directory
     */
    static NodeHandle makeHandle(ObjectHandle hOSStorage, Path path, boolean fDir)
        {
        return fDir
                ? new NodeHandle(xOSDirectory.INSTANCE.getCanonicalClass(), path, hOSStorage)
                : new NodeHandle(xOSFile.INSTANCE.getCanonicalClass(),      path, hOSStorage);
        }

    // ----- ObjectHandle --------------------------------------------------------------------------

    protected static class NodeHandle
            extends ObjectHandle
        {
        protected final Path         f_path;
        protected final ObjectHandle f_hOSStorage;

        // TODO: lazy file channel, etc.

        protected NodeHandle(TypeComposition clazz, Path path, ObjectHandle hOSStorage)
            {
            super(clazz);

            f_path       = path;
            f_hOSStorage = hOSStorage;

            // these handles need be sent across the service lines; mark as immutable
            makeImmutable();
            }
        }

    // ----- constants -----------------------------------------------------------------------------


    // ----- data members --------------------------------------------------------------------------

    }
