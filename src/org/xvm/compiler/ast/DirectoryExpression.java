package org.xvm.compiler.ast;


import java.io.File;
import java.io.IOException;

import java.nio.file.Files;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.FSNodeConstant;
import org.xvm.asm.constants.FileStoreConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Handy;
import org.xvm.util.Severity;


/**
 * A directory expression is an expression that causes an entire directory to be "vacuumed" into a
 * module as a resource, which will be exposed as a FileStore instance to the program.
 */
public class DirectoryExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a DirectoryExpression.
     *
     * @param tokPath  a token that contains the entire path that specifies the directory
     * @param dir      the resolved directory corresponding to the path for which this expression
     *                 exists
     */
    public DirectoryExpression(Token tokPath, File dir)
        {
        this.m_tokPath = tokPath;
        this.m_dir = dir;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return m_tokPath.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return m_tokPath.getEndPosition();
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return pool().typeFileStore();
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeConstant typeActual = getImplicitType(ctx);
        Constant     constVal;
        try
            {
            constVal = buildFileStoreConstant();
            }
        catch (IOException e)
            {
            log(errs, Severity.ERROR, Compiler.FATAL_ERROR, e.getMessage());
            constVal = buildEmptyConstant();
            }

        assert constVal != null;
        return finishValidation(typeRequired, typeActual, TypeFit.Fit, constVal, errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return (String) m_tokPath.getValue();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return the FileStore constant value
     *
     * @throws IOException if some low level issue occurs attempting to vacuum in the directory
     */
    protected FileStoreConstant buildFileStoreConstant()
            throws IOException
        {
        ConstantPool   pool     = pool();
        String         sPath    = (String) m_tokPath.getValue();
        FSNodeConstant constDir = buildDirectoryConstant(pool, m_dir);
        return pool.ensureFileStoreConstant(sPath, constDir);
        }

    /**
     * @return an empty FileStore constant
     */
    protected FileStoreConstant buildEmptyConstant()
        {
        ConstantPool   pool     = pool();
        String         sPath    = (String) m_tokPath.getValue();
        FSNodeConstant constDir = pool.ensureDirectoryConstant(m_dir.getName(),
                createdDateTime(pool, m_dir), modifiedDateTime(pool, m_dir), FSNodeConstant.NO_NODES);
        return pool().ensureFileStoreConstant(sPath, constDir);
        }

    /**
     * Build a directory FSNodeConstant for the specified directory.
     *
     * @param pool  the ConstantPool
     * @param dir   the directory
     *
     * @return a directory {@link FSNodeConstant}
     *
     * @throws IOException if some low level issue occurs attempting to vacuum in the directory
     */
    protected FSNodeConstant buildDirectoryConstant(ConstantPool pool, File dir)
            throws IOException
        {
        File[] afiles  = dir.listFiles();
        if (afiles == null)
            {
            throw new IOException("failed to obtain contents of directory: " + dir);
            }

        int              cfiles  = afiles.length;
        FSNodeConstant[] aconsts = new FSNodeConstant[cfiles];
        for (int i = 0; i < cfiles; ++i)
            {
            File file = afiles[i];
            aconsts[i] = file.isDirectory()
                    ? buildDirectoryConstant(pool, file)
                    : buildFileConstant(pool, file);
            }

        return pool.ensureDirectoryConstant(dir.getName(),
                createdDateTime(pool, dir), modifiedDateTime(pool, dir),  aconsts);
        }

    /**
     * Build a file FSNodeConstant for the specified file.
     *
     * @param pool  the ConstantPool
     * @param file  the file
     *
     * @return a file {@link FSNodeConstant}
     *
     * @throws IOException if some low level issue occurs attempting to vacuum in the file
     */
    protected FSNodeConstant buildFileConstant(ConstantPool pool, File file)
            throws IOException
        {
        byte[] ab = Handy.readFileBytes(file);
        return pool.ensureFileConstant(file.getName(),
                createdDateTime(pool, file), modifiedDateTime(pool, file), ab);
        }

    /**
     * Determine the created date/time value for the specified directory or file.
     *
     * @param pool  the ConstantPool
     * @param file  the directory or file to obtain the date/time value for
     *
     * @return the FileTime for the date/time that the file was created
     */
    static public FileTime createdDateTime(ConstantPool pool, File file)
        {
        try
            {
            BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            return attr.creationTime();
            }
        catch (IOException e)
            {
            return null;
            }
        }

    /**
     * Determine the modified date/time value for the specified directory or file.
     *
     * @param pool  the ConstantPool
     * @param file  the directory or file to obtain the date/time value for
     *
     * @return the FileTime for the date/time that the file was modified
     */
    static public FileTime modifiedDateTime(ConstantPool pool, File file)
        {
        try
            {
            BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            return attr.lastModifiedTime();
            }
        catch (IOException e)
            {
            return null;
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The path string that was the basis for this expression.
     */
    private Token m_tokPath;

    /**
     * The File for the directory that the path string was resolved to.
     */
    private File  m_dir;
    }
