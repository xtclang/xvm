package org.xvm.proto;

/**
 * TODO:
 *
 * @author gg 2017.02.21
 */
public class Ops
    {
    static final int ENTER     = 0x04; //                                                           ; variable scope begin
    static final int EXIT      = 0x05; //                                                           ; variable scope end

    static final int VAR       = 0x33; // CONST_CLASS                                               ; next register is an uninitialized anonymous variable
    static final int IVAR      = 0x34; // CONST_CLASS  CONST_*                                      ; next register is an initialized anonymous variable
    static final int NVAR      = 0x35; // CONST_CLASS, CONST_STRING                                 ; next register is an uninitialized named variable
    static final int INVAR     = 0x36; // CONST_CLASS, CONST_STRING, CONST_*                        ; next register is an initialized named variable

    static final int MOV       = 0x3A; // lvalue-dest, rvalue-src                                   ; move source value to destination

    static final int RETURN_0  = 0x4C; // return (no return value)
    static final int RETURN_1  = 0x4D; // return (single return value)

    static final int INVOKE_00 = 0x60; // rvalue-target, rvalue-method
    static final int INVOKE_01 = 0x61; // rvalue-target, rvalue-method, lvalue-return
    static final int INVOKE_0N = 0x62; // rvalue-target, rvalue-method, #returns:(lvalue)

    static final int ADD       = 0x70; // rvalue-target, rvalue-second, lvalue-return               ; T + T -> T
    static final int SUB       = 0x71; // rvalue-target, rvalue-second, lvalue-return               ; T - T -> T
    static final int MUL       = 0x72; // rvalue-target, rvalue-second, lvalue-return               ; T * T -> T
    static final int DIV       = 0x73; // rvalue-target, rvalue-second, lvalue-return               ; T / T -> T


    // satellite info
    static class OpInfo
        {
        final int groupId;
        byte[] javaOps; // Java byte codes for the corresponding method call (JIT)

        OpInfo(int iGroup)
            {
            groupId = iGroup;
            }

        }

    static final int GROUP_1 = 1;
    static final int GROUP_2 = 2;

    static final OpInfo[] s_aInfo = new OpInfo[256];
    static
        {
        s_aInfo[ENTER]      = new OpInfo(GROUP_1);
        s_aInfo[EXIT]       = new OpInfo(GROUP_1);
        }
    }
