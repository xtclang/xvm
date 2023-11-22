package org.xvm.compiler.ast;


import java.io.File;
import java.io.IOException;

import java.lang.reflect.Field;

import java.nio.file.Files;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import java.util.Set;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.constants.FSNodeConstant;
import org.xvm.asm.constants.FileStoreConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.tool.ResourceDir;

import org.xvm.util.Handy;
import org.xvm.util.Severity;


/**
 * A file expression is an expression that causes an entire file or directory to be "vacuumed" into
 * a module as a resource, which will be exposed as a FileStore, Directory, or File object to the
 * program.
 */
public class FileExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a FileExpression.
     *
     * @param type      one of FileStore, Directory, File, or null (implies Directory or File)
     * @param path      a token that contains the entire path that specifies the directory or file
     * @param resource  the resolved directory or file corresponding to the path for which this
     *                  expression exists
     */
    public FileExpression(TypeExpression type, Token path, Object resource)
        {
        this.type = type;
        this.path = path;

        if (resource instanceof File file)
            {
            m_file = file;
            }
        else if (resource instanceof ResourceDir dir)
            {
            m_dir = dir;
            }
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return one of "FileStore", "Directory", "File", or null (implies Directory or File)
     */
    public String getSimpleTypeName()
        {
        if (type == null)
            {
            return null;
            }

        String sType = type.toString();
        int of = sType.indexOf('<');
        return of < 0
                ? sType
                : sType.substring(0, of);
        }

    @Override
    public long getStartPosition()
        {
        return type == null
                ? path.getStartPosition()
                : type.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return path.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        ConstantPool pool  = pool();
        String       sType = getSimpleTypeName();
        if (sType == null)
            {
            if (m_dir != null)
                {
                return pool.typeFileStore();    // or directory or path ...
                }

            if (m_file != null && m_file.exists())
                {
                return m_file.isDirectory()
                        ? pool.typeFileStore()  // or directory or path ...
                        : pool.typeFile();      // or path ...
                }

            return pool.typePath();
            }
        else
            {
            return switch (sType)
                {
                case "FileStore"   -> pool.typeFileStore();
                case "Directory"   -> pool.typeDirectory();
                case "File"        -> pool.typeFile();
                case "Path"        -> pool.typePath();
                case "String"      -> pool.typeString();
                case "Array<Byte>" -> pool.typeByteArray();
                case "Byte[]"      -> pool.typeByteArray();
                default            -> throw new IllegalStateException("type=" + sType);
                };
            }
        }

    private int calcConsumes(TypeConstant typeRequired)
        {
        ConstantPool pool = pool();

        int nConsumes = NONE;
        if (typeRequired == null)
            {
            nConsumes = ALL;
            }
        else
            {
            if (pool.typeFileStore().isA(typeRequired))
                {
                nConsumes |= FS;
                }

            if (pool.typeDirectory().isA(typeRequired))
                {
                nConsumes |= DIR;
                }

            if (pool.typeFile().isA(typeRequired))
                {
                nConsumes |= FILE;
                }

            if (pool.typePath().isA(typeRequired))
                {
                nConsumes |= PATH;
                }

            if (pool.typeString().isA(typeRequired))
                {
                nConsumes |= STRING;
                }

            if (pool.typeByteArray().isA(typeRequired))
                {
                nConsumes |= BINARY;
                }
            }

        return nConsumes;
        }

    private int calcProduces()
        {
        String sType = getSimpleTypeName();
        if (sType == null)
            {
            if (m_dir != null)
                {
                return FS | DIR | PATH;
                }

            if (m_file != null && m_file.exists())
                {
                return m_file.isDirectory()
                        ? FS | DIR | PATH
                        : FILE | PATH | STRING | BINARY;
                }

            return PATH;
            }
        else
            {
            return switch (sType)
                {
                case "FileStore"   -> FS;
                case "Directory"   -> DIR;
                case "File"        -> FILE;
                case "Path"        -> PATH;
                case "String"      -> STRING;
                case "Array<Byte>" -> BINARY;
                case "Byte[]"      -> BINARY;
                default            -> throw new IllegalStateException("type=" + sType);
                };
            }
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, boolean fExhaustive,
                           ErrorListener errs)
        {
        int nConsumes = calcConsumes(typeRequired);
        int nProduces = calcProduces();
        return (nConsumes & nProduces) == NONE
                ? TypeFit.NoFit
                : TypeFit.Fit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool pool = pool();

        TypeFit      fit        = TypeFit.Fit;
        TypeConstant typeActual = null;
        Constant     constVal   = null;
        int          nConsumes  = calcConsumes(typeRequired);
        int          nProduces  = calcProduces();
        try
            {
            switch (nConsumes & nProduces)
                {
                case NONE:
                    if ((nProduces & FS) != 0)
                        {
                        typeActual = pool.typeFileStore();
                        }
                    if ((nProduces & DIR) != 0)
                        {
                        typeActual = typeActual == null
                                ? pool.typeDirectory()
                                : pool.ensureUnionTypeConstant(typeActual, pool.typeDirectory());
                        }
                    if ((nProduces & FILE) != 0)
                        {
                        typeActual = typeActual == null
                                ? pool.typeFile()
                                : pool.ensureUnionTypeConstant(typeActual, pool.typeFile());
                        }
                    if ((nProduces & PATH) != 0)
                        {
                        typeActual = typeActual == null
                                ? pool.typePath()
                                : pool.ensureUnionTypeConstant(typeActual, pool.typePath());
                        }
                    if ((nProduces & STRING) != 0)
                        {
                        typeActual = typeActual == null
                                ? pool.typeString()
                                : pool.ensureUnionTypeConstant(typeActual, pool.typeString());
                        }
                    if ((nProduces & BINARY) != 0)
                        {
                        typeActual = typeActual == null
                                ? pool.typeByteArray()
                                : pool.ensureUnionTypeConstant(typeActual, pool.typeByteArray());
                        }
                    break;

                case FS:
                    typeActual = pool.typeFileStore();
                    constVal   = buildFileStoreConstant();
                    break;

                case DIR:
                    typeActual = pool.typeDirectory();
                    constVal   = m_dir == null
                            ? buildDirectoryConstant(pool, m_file)
                            : buildDirectoryConstant(pool, m_dir);
                    break;

                case FILE:
                    typeActual = pool.typeFile();
                    constVal   = buildFileConstant(pool(), m_file);
                    break;

                case PATH:
                    typeActual = pool.typePath();
                    constVal   = pool.ensureLiteralConstant(Constant.Format.Path, (String) path.getValue());
                    break;

                case STRING:
                    typeActual = pool.typeString();
                    constVal   = pool.ensureStringConstant(new String(Handy.readFileChars(m_file)));
                    break;

                case BINARY:
                    typeActual = pool.typeByteArray();
                    constVal   = pool.ensureByteStringConstant(Handy.readFileBytes(m_file));
                    break;

                default:
                    // multiple matches; ambiguous
                    log(errs, Severity.ERROR, Compiler.AMBIGUOUS_PATH_TYPE);
                    fit = TypeFit.NoFit;
                    break;
                }
            }
        catch (IOException e)
            {
            log(errs, Severity.ERROR, Compiler.FATAL_ERROR, e.getMessage());
            fit        = TypeFit.NoFit;
            typeActual = null;
            constVal   = null;
            }

        return finishValidation(ctx, typeRequired, typeActual, fit, constVal, errs);
        }

    @Override
    public ExprAST getExprAST()
        {
        assert isConstant();
        return toExprAst(toConstant());
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        String sType = getSimpleTypeName();
        String sPath = (String) path.getValue();

        return sType == null
                ? sPath
                : sType + ':' + sPath;
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
        String         sPath    = (String) path.getValue();
        FSNodeConstant constDir = m_dir == null
                ? buildDirectoryConstant(pool, m_file)
                : buildDirectoryConstant(pool, m_dir);
        return pool.ensureFileStoreConstant(sPath, constDir);
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
    public static FSNodeConstant buildDirectoryConstant(ConstantPool pool, File dir)
            throws IOException
        {
        File[] aFiles = dir.listFiles();
        if (aFiles == null)
            {
            throw new IOException("failed to obtain contents of directory: " + dir);
            }

        int              cFiles  = aFiles.length;
        FSNodeConstant[] aConsts = new FSNodeConstant[cFiles];
        for (int i = 0; i < cFiles; ++i)
            {
            File file = aFiles[i];
            aConsts[i] = file.isDirectory()
                    ? buildDirectoryConstant(pool, file)
                    : buildFileConstant(pool, file);
            }

        return pool.ensureDirectoryConstant(dir.getName(),
                                            createdTime(dir), modifiedTime(dir), aConsts);
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
    public static FSNodeConstant buildDirectoryConstant(ConstantPool pool, ResourceDir dir)
            throws IOException
        {
        Set<String>      children = dir.getNames();
        int              cConsts  = children.size();
        FSNodeConstant[] aConsts  = new FSNodeConstant[cConsts];
        int              iConst   = 0;
        for (String child : children)
            {
            assert iConst < cConsts;
            Object resource = dir.getByName(child);
            if (resource instanceof ResourceDir subdir)
                {
                aConsts[iConst++] = buildDirectoryConstant(pool, subdir);
                }
            else if (resource instanceof File file)
                {
                aConsts[iConst++] = buildFileConstant(pool, file);
                }
            else
                {
                throw new IllegalStateException("unknown resource \"" + child + "\" from " + dir +
                                                " : " + resource);
                }
            }
        assert iConst == cConsts;

        return pool.ensureDirectoryConstant(dir.getName(),
                dir.getCreatedTime(), dir.getModifiedTime(), aConsts);
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
    public static FSNodeConstant buildFileConstant(ConstantPool pool, File file)
            throws IOException
        {
        byte[] ab = Handy.readFileBytes(file);
        return pool.ensureFileConstant(file.getName(),
                                       createdTime(file), modifiedTime(file), ab);
        }

    /**
     * Determine the created date/time value for the specified directory or file.
     *
     * @param file the directory or file to obtain the date/time value for
     *
     * @return the FileTime for the date/time that the file was created
     */
    public static FileTime createdTime(File file)
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
     * @param file the directory or file to obtain the date/time value for
     *
     * @return the FileTime for the date/time that the file was modified
     */
    public static FileTime modifiedTime(File file)
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

    private static final int FS     = 0x01;
    private static final int DIR    = 0x02;
    private static final int FILE   = 0x04;
    private static final int PATH   = 0x08;
    private static final int STRING = 0x10;
    private static final int BINARY = 0x20;
    private static final int ALL    = FS | DIR | FILE | PATH  | STRING | BINARY;
    private static final int NONE   = 0x0;

    /**
     * The (optional) type for the expression.
     */
    protected TypeExpression type;

    /**
     * The path string that was the basis for this expression.
     */
    protected Token path;

    /**
     * The File that the path string was resolved to.
     */
    private File m_file;

    /**
     * The ResourceDir that the path string was resolved to.
     */
    private ResourceDir m_dir;

    private static final Field[] CHILD_FIELDS = fieldsForNames(FileExpression.class, "type");
    }