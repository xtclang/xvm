package org.xvm.compiler.ast;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.xvm.asm.Component;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Register;
import org.xvm.asm.constants.PropertyConstant;

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

    /** Copy generated capture properties to their enclosing source registers. */
    Map<PropertyConstant, Register> propertyOrigins(Component component) {
        var origins = new HashMap<PropertyConstant, Register>();
        f_registers.forEach((name, register) -> {
            if (component.getChild(name) instanceof PropertyStructure property &&
                    property.isSynthetic()) {
                origins.put(property.getIdentityConstant(), register.getOriginalRegister());
            }
        });
        return Map.copyOf(origins);
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
