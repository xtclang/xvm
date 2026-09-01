package org.xvm.asm;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Gate for {@code MethodStructure.getDescription()}, which {@code XvmStructure.toString()} funnels
 * every method rendering through.
 *
 * <p>It used to report {@code line-count=} from {@code Source.getLineCount()}, and that calls
 * {@code Source.normalize()}: the source text is chopped into lines and ONE {@code StringConstant}
 * PER LINE is interned into the {@link ConstantPool}, with {@code m_aconstSrc}/{@code m_anIndents}
 * published unsynchronized. Expanding a method node in a debugger therefore grew the pool by the
 * size of that method's own source.</p>
 */
public class MethodDisplayPurityTest {
    private static final String SOURCE = """
            void hello() {
                @Inject Console console;
                console.print("hello");
            }
            """;

    @Test
    public void renderingAMethodDoesNotNormalizeItsSourceIntoThePool() {
        var file   = new FileStructure("PurityTest");
        var pool   = file.getConstantPool();
        var method = file.getModule().createMethod(true, Constants.Access.PUBLIC, null,
                Parameter.NO_PARAMS, "hello", Parameter.NO_PARAMS, true, false);
        method.configureSource(SOURCE, 1);

        int    cBefore     = pool.size();
        String description = method.getDescription();

        assertTrue(description.contains("hasSource=true"), description);
        assertTrue(description.contains("line-count=<deferred>"),
                "an un-normalized source must report a deferred line count rather than chopping "
                + "itself up to answer: " + description);
        assertEquals(cBefore, pool.size(),
                "rendering a method interned its source lines into the ConstantPool");

        // the forced path is still there and still works when someone asks for it deliberately
        assertEquals(5, method.getSourceLineCount());
        assertTrue(pool.size() > cBefore,
                "negative control failed: normalizing the source did not grow the pool, so the "
                + "purity assertion above proves nothing");

        // and once it IS normalized, the description reports the real count
        assertTrue(method.getDescription().contains("line-count=5"), method.getDescription());
    }
}
