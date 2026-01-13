package org.xvm.javajit;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.constants.TypeConstant;

import static java.lang.constant.ConstantDescs.CD_char;

import static org.xvm.javajit.Builder.CD_Char;
import static org.xvm.javajit.Builder.CD_String;
import static org.xvm.javajit.Builder.MD_Char_addInt;
import static org.xvm.javajit.Builder.MD_Char_subInt;
import static org.xvm.javajit.Builder.MD_StringOf;

/**
 * A "mixin" interface to generate bytecodes for operations on Ecstasy text types such as Char
 * and String.
 */
public interface TextSupport {
    /**
     * Generate the byte codes to add an Int, Char, or String to a Char.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     *
     * @return the type of the result of the operation
     */
    default TypeConstant buildAddToChar(BuildContext bctx,
                                        CodeBuilder  code,
                                        RegisterInfo regTarget,
                                        int          nArgValue) {
        TypeConstant typeArg   = bctx.getArgumentType(nArgValue);
        String       typeName  = typeArg.getValueString();
        return switch (typeName) {
            case "Int"    -> buildAddIntToChar(bctx, code, regTarget, nArgValue);
            case "Char"   -> buildAddCharToChar(bctx, code, regTarget, nArgValue);
            case "String" -> buildAddStringToChar(bctx, code, regTarget, nArgValue);
            default       ->  throw new UnsupportedOperationException("Cannot add " + typeName
                    + " to Char");
        };
    }

    /**
     * Generate the byte codes to add an Int to a Char.
     * <p>
     * Validation is performed to ensure the addition would not result in a code point outside
     * the valid Unicode range. If it does, an OutOfBounds exception is thrown.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     *
     * @return the type of the result of the operation
     */
    default TypeConstant buildAddIntToChar(BuildContext bctx,
                                           CodeBuilder  code,
                                           RegisterInfo regTarget,
                                           int          nArgValue) {
        bctx.loadCtx(code);
        regTarget.load(code);
        bctx.loadArgument(code, nArgValue);
        code.invokestatic(CD_Char, "$addInt", MD_Char_addInt);
        return regTarget.type();
    }

    /**
     * Generate the byte codes to add a Char to a Char, resulting on a String on the stack.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     *
     * @return the type of the result of the operation
     */
    default TypeConstant buildAddCharToChar(BuildContext bctx,
                                            CodeBuilder  code,
                                            RegisterInfo regTarget,
                                            int          nArgValue) {
        regTarget.load(code);
        code.i2c();
        bctx.loadArgument(code, nArgValue);
        code.i2c();
        int slot = bctx.buildAndStoreString(code, "\u0001\u0001", CD_char, CD_char);
        bctx.loadCtx(code);
        code.aload(slot);
        code.invokestatic(CD_String, "of", MD_StringOf);
        return bctx.pool().typeString();
    }

    /**
     * Generate the byte codes to add a String to a Char, resulting on a String on the stack.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     *
     * @return the type of the result of the operation
     */
    default TypeConstant buildAddStringToChar(BuildContext bctx,
                                              CodeBuilder  code,
                                              RegisterInfo regTarget,
                                              int          nArgValue) {
        regTarget.load(code);
        code.i2c();
        bctx.loadArgument(code, nArgValue);
        int slot = bctx.buildAndStoreString(code, "\u0001\u0001", CD_char, CD_String);
        bctx.loadCtx(code);
        code.aload(slot);
        code.invokestatic(CD_String, "of", MD_StringOf);
        return bctx.pool().typeString();
    }

    /**
     * Generate the byte codes to subtract an Int or Char from a Char.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     *
     * @return the type of the result of the operation
     */
    default TypeConstant buildSubFromChar(BuildContext bctx,
                                          CodeBuilder  code,
                                          RegisterInfo regTarget,
                                          int          nArgValue) {
        TypeConstant typeArg   = bctx.getArgumentType(nArgValue);
        String       typeName  = typeArg.getValueString();
        return switch (typeName) {
            case "Int"    -> buildSubIntFromChar(bctx, code, regTarget, nArgValue);
            case "Char"   -> buildSubCharFromChar(bctx, code, regTarget, nArgValue);
            default       ->  throw new UnsupportedOperationException("Cannot subtract "
                    + typeName + " from Char");
        };
    }

    /**
     * Generate the byte codes to subtract an Int from a Char.
     * <p>
     * Validation is performed to ensure the subtraction would not result in a code point outside
     * the valid Unicode range. If it does, an OutOfBounds exception is thrown.
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     *
     * @return the type of the result of the operation
     */
    default TypeConstant buildSubIntFromChar(BuildContext bctx,
                                             CodeBuilder  code,
                                             RegisterInfo regTarget,
                                             int          nArgValue) {
        bctx.loadCtx(code);
        regTarget.load(code);
        bctx.loadArgument(code, nArgValue);
        code.invokestatic(CD_Char, "$subInt", MD_Char_subInt);
        return regTarget.type();
    }

    /**
     * Implementation for Char.x
     * <pre>
     *     UInt32 sub(Char ch) = this.codepoint - ch.codepoint;
     * </pre>
     * <p>
     * Neither the target nor argument should have been loaded to the stack.
     *
     * @param bctx       the current build context
     * @param code       the code builder to add the op codes to
     * @param regTarget  the register containing the target of the operation
     * @param nArgValue  the register containing the operation argument
     *
     * @return the type of the result of the operation, which should be UInt32
     */
    default TypeConstant buildSubCharFromChar(BuildContext bctx,
                                              CodeBuilder  code,
                                              RegisterInfo regTarget,
                                              int          nArgValue) {
        regTarget.load(code);
        bctx.loadArgument(code, nArgValue);
        code.isub();
        return bctx.pool().typeUInt32();
    }
}
