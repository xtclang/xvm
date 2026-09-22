package org.xvm.compiler.ast;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import org.xvm.asm.Register;

/** Captured variables and the source origins of registers used to read them. */
public final class AnonymousClassBindings {
    AnonymousClassBindings(NewExpression owner, Map<String, Register> registers) {
        f_owner     = owner;
        f_registers = Map.copyOf(registers);
    }

    /**
     * @return an immutable snapshot, preserving register identity rather than register equality
     */
    public Map<Register, Register> getCaptureOrigins() {
        return Collections.unmodifiableMap(new IdentityHashMap<>(f_origins));
    }

    boolean isFor(NewExpression owner) {
        return f_owner == owner;
    }

    Map<String, Register> registers() {
        return f_registers;
    }

    void bind(String name, Register captured) {
        f_origins.put(captured.getOriginalRegister(), f_registers.get(name).getOriginalRegister());
    }

    private final NewExpression           f_owner;
    private final Map<String, Register>   f_registers;
    private final Map<Register, Register> f_origins = new IdentityHashMap<>();
}
