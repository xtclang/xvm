package org.xvm.compiler.ast;


import java.io.File;
import java.io.IOException;

import java.lang.reflect.Field;

import java.nio.file.Files;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.constants.FSNodeConstant;
import org.xvm.asm.constants.FileStoreConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

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
     * @param type  one of FileStore, Directory, File, or null (implies Directory or File)
     * @param path  a token that contains the entire path that specifies the directory or file
     * @param file  the resolved directory or file corresponding to the path for which this
     *              expression exists
     */
    public FileExpression(TypeExpression type, Token path, File file)
        {
        this.type = type;
        this.path = path;

        m_file = file;
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
        assert m_file != null && m_file.exists();

        ConstantPool pool  = pool();
        String       sType = getSimpleTypeName();
        if (sType == null)
            {
            if (m_file == null || !m_file.exists())
                {
                return pool.typePath();
                }

            return m_file.isDirectory()
                    ? pool.typeFileStore()  // or directory or path ...
                    : pool.typeFile();      // or path ...
            }
        else
            {
            return switch (sType)
                {
                case "FileStore" -> pool.typeFileStore();
                case "Directory" -> pool.typeDirectory();
                case "File"      -> pool.typeFile();
                case "Path"      -> pool.typePath();
                default          -> throw new IllegalStateException("type=" + sType);
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
            }

        return nConsumes;
        }

    private int calcProduces()
        {
        String sType = getSimpleTypeName();
        if (sType == null)
            {
            if (m_file == null || !m_file.exists())
                {
                return PATH;
                }
            else if (m_file.isDirectory())
                {
                return FS | DIR | PATH;
                }
            else
                {
                return FILE | PATH;
                }
            }
        else
            {
            return switch (sType)
                {
                case "FileStore" -> FS;
                case "Directory" -> DIR;
                case "File" -> FILE;
                case "Path" -> PATH;
                default -> throw new IllegalStateException("type=" + sType);
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
                    break;

                case FS:
                    typeActual = pool.typeFileStore();
                    constVal   = buildFileStoreConstant();
                    break;

                case DIR:
                    typeActual = pool.typeDirectory();
                    constVal   = buildDirectoryConstant(pool(), m_file);
                    break;

                case FILE:
                    typeActual = pool.typeFile();
                    constVal   = buildFileConstant(pool(), m_file);
                    break;

                case PATH:
                    typeActual = pool.typePath();
                    constVal   = pool.ensureLiteralConstant(Constant.Format.Path, (String) path.getValue());
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
    public ExprAST<Constant> getExprAST()
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
        FSNodeConstant constDir = buildDirectoryConstant(pool, m_file);
        return pool.ensureFileStoreConstant(sPath, constDir);
        }

    /**
     * @return an empty FileStore constant
     */
    protected Constant buildEmptyConstant(Context ctx)
        {
        ConstantPool pool  = pool();
        String       sPath = (String) path.getValue();
        File         file  = m_file == null ? new File("error") : m_file;
        String       sType = getImplicitType(ctx).getSingleUnderlyingClass(true).getName();

        return switch (sType)
            {
            case "FileStore" ->
                pool().ensureFileStoreConstant(sPath, pool.ensureDirectoryConstant(file.getName(),
                    createdTime(pool, file), modifiedTime(pool, file), FSNodeConstant.NO_NODES));

            case "Directory" ->
                pool.ensureDirectoryConstant(file.getName(),
                    createdTime(pool, file), modifiedTime(pool, file), FSNodeConstant.NO_NODES);

            case "File" ->
                pool.ensureFileConstant(file.getName(),
                    createdTime(pool, file), modifiedTime(pool, file), Handy.EMPTY_BYTE_ARRAY);

            case "Path" ->
                pool.ensureLiteralConstant(Constant.Format.Path, "/");

            default ->
                throw new IllegalStateException("type=" + sType);
            };
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
                createdTime(pool, dir), modifiedTime(pool, dir), aConsts);
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
                createdTime(pool, file), modifiedTime(pool, file), ab);
        }

    /**
     * Determine the created date/time value for the specified directory or file.
     *
     * @param pool  the ConstantPool
     * @param file  the directory or file to obtain the date/time value for
     *
     * @return the FileTime for the date/time that the file was created
     */
    public static FileTime createdTime(ConstantPool pool, File file)
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
    public static FileTime modifiedTime(ConstantPool pool, File file)
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

    private static final int FS   = 0x1;
    private static final int DIR  = 0x2;
    private static final int FILE = 0x4;
    private static final int PATH = 0x8;
    private static final int ALL  = FS | DIR | FILE | PATH;
    private static final int NONE = 0x0;

    /**
     * The (optional) type for the expression.
     */
    protected TypeExpression type;

    /**
     * The path string that was the basis for this expression.
     */
    protected Token path;

    /**
     * The File for the directory or file that the path string was resolved to.
     */
    private final File m_file;

    private static final Field[] CHILD_FIELDS = fieldsForNames(FileExpression.class, "type");
    }