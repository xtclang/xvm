package org.xvm.compiler.ast.bindings;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.xvm.asm.Component;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.Register;
import org.xvm.asm.constants.PropertyConstant;

import org.xvm.compiler.ast.NewExpression;

/**
 * Source origins of registers used to read captured variables. The compiler-owned collector
 * retains construction metadata; consumers read immutable identity snapshots of the associations.
 */
public final class AnonymousClassBindings {
    private final Map<Register, Register> origins = new IdentityHashMap<>();

    private AnonymousClassBindings() {}

    /**
     * @return an immutable snapshot, preserving register identity rather than register equality
     */
    public Map<Register, Register> getCaptureOrigins() {
        return Collections.unmodifiableMap(new IdentityHashMap<>(origins));
    }

    /**
     * Compiler-only writer for one anonymous construction's capture associations. Existing
     * register allocation and capture rewriting use its immutable name map; publishing facts
     * does not expose the writer or make the compiler objects safe for concurrent use.
     */
    public static final class Collector {
        private final NewExpression          owner;
        private final Map<String, Register>  registers;
        private final AnonymousClassBindings bindings = new AnonymousClassBindings();

        /**
         * Retain the result of ordinary capture analysis for one construction.
         *
         * @param owner      the construction that owns these captures
         * @param registers  captured names mapped to their enclosing source registers
         */
        public Collector(NewExpression owner, Map<String, Register> registers) {
            this.owner     = owner;
            this.registers = Map.copyOf(registers);
        }

        /**
         * Read facts only for their original construction, never a copied syntax node.
         *
         * @param owner  the construction whose facts are requested
         * @return the collected bindings, or null if another construction owns this collector
         */
        public AnonymousClassBindings getBindings(NewExpression owner) {
            return this.owner == owner ? bindings : null;
        }

        /**
         * Match generated capture properties to the registers discovered by capture analysis.
         *
         * @param component  the validated anonymous class
         * @return an immutable map from synthetic properties to enclosing source registers
         */
        public Map<PropertyConstant, Register> propertyOrigins(Component component) {
            var origins = new HashMap<PropertyConstant, Register>();
            registers.forEach((name, register) -> {
                if (component.getChild(name) instanceof PropertyStructure property && property.isSynthetic()) {
                    origins.put(property.getIdentityConstant(), register.getOriginalRegister());
                }
            });
            return Map.copyOf(origins);
        }

        /** @return the immutable name map used by ordinary capture rewriting */
        public Map<String, Register> registers() {
            return registers;
        }

        /**
         * Associate a generated capture-read register with its enclosing source register.
         *
         * @param name      the captured variable name established by capture analysis
         * @param captured  the register used to read that capture inside the anonymous class
         */
        public void bind(String name, Register captured) {
            bindings.origins.put(captured.getOriginalRegister(), registers.get(name).getOriginalRegister());
        }
    }
}
