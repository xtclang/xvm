package org.xvm.asm.constants;

import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;

import org.xvm.asm.ast.ExprAST;

import org.xvm.compiler.ast.Context;


/**
 * FormalConstant is a Constant that represents a formal type, which could be generic class level
 * type parameter, such as Map.Key, a method type parameter (such as Object.equals.CompileType)
 * or a formal type child constant, such as Map.equals.CompileType.Key)
 */
public abstract class FormalConstant
        extends NamedConstant
    {
    /**
     * Construct a constant whose purpose is to identify a structure of a formal constant
     * of the specified name that exists within its parent structure.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the module, package, class, or method that contains this identity
     * @param sName        the name associated with this formal constant
     */
    public FormalConstant(ConstantPool pool, IdentityConstant constParent, String sName)
        {
        super(pool, constParent, sName);
        }

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public FormalConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);
        }

    /**
     * Obtain the constraint type of the formal type represented by this constant.
     *
     * @return the constraint type
     */
    public abstract TypeConstant getConstraintType();

    /**
     * Resolve the formal type represented by this constant using the specified resolver.
     *
     * @param resolver  the resolver to use
     *
     * @return a resolved type or null if this constant cannot be resolved
     */
    public abstract TypeConstant resolve(GenericTypeResolver resolver);

    /**
     * Convert this formal constant to a {@link ExprAST binary expression AST}.
     *
     * @return the resulting {@link ExprAST} or null if the conversion is not feasible
     */
    public abstract ExprAST toExprAst(Context ctx);
    }
