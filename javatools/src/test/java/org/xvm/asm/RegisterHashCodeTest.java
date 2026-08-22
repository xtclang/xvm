package org.xvm.asm;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Guards the equals/hashCode contract for compiler register keys.
 */
public class RegisterHashCodeTest {
    @Test
    public void registerHashMatchesEquality() {
        var pool = new FileStructure("test").getConstantPool();
        var type = pool.typeObject();

        var reg1 = new Register(type, "value", 3);
        var reg2 = new Register(type, "value", 3);

        assertEquals(reg1, reg2);
        assertEquals(reg1.hashCode(), reg2.hashCode());
    }

    @Test
    public void shadowRegisterHashMatchesEquality() {
        var pool = new FileStructure("test").getConstantPool();

        var reg1 = new Register(pool.typeObject(), "value", 3).narrowType(pool.typeString());
        var reg2 = new Register(pool.typeObject(), "value", 3).narrowType(pool.typeString());

        assertEquals(reg1, reg2);
        assertEquals(reg1.hashCode(), reg2.hashCode());
    }
}
