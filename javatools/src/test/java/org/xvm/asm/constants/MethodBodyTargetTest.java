package org.xvm.asm.constants;


import org.junit.jupiter.api.Test;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.FileStructure;

import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.MethodBody.Target;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Guards the typed {@link MethodBody.Target} payload union. Master modeled the payload as a bare
 * {@code Object} whose legal shapes lived in a constructor assert switch, so with {@code -da} any
 * Object was accepted for any Implementation and every reader re-proved the shape with a cast
 * (including a raw {@code MethodInfo[]} for the two union legs, which could be constructed with a
 * wrong length or null legs). The sealed union makes the payload shape compile-checked, makes a
 * union with missing legs unconstructible, and makes implementation/payload mispairing fail
 * loudly on every JVM.
 */
public class MethodBodyTargetTest {
    @Test
    public void implementationPayloadMispairingFailsLoudly() {
        var fixture = new Fixture();

        // a Field body cannot carry a method origin, and a FromInto body cannot carry a property
        assertThrows(IllegalArgumentException.class, () -> new MethodBody(
                fixture.id, fixture.sig, Implementation.Field,
                new Target.Origin(fixture.nativeInfo())));
        assertThrows(IllegalArgumentException.class, () -> new MethodBody(
                fixture.id, fixture.sig, Implementation.FromInto,
                new Target.Prop(fixture.propId())));
        // payload-free implementations must stay payload-free
        assertThrows(IllegalArgumentException.class, () -> new MethodBody(
                fixture.id, fixture.sig, Implementation.Native,
                new Target.Origin(fixture.nativeInfo())));
    }

    @Test
    public void unionLegsAreNonNullByConstruction() {
        var fixture = new Fixture();
        var info    = fixture.nativeInfo();

        assertThrows(NullPointerException.class, () -> new Target.Union(null, info));
        assertThrows(NullPointerException.class, () -> new Target.Union(info, null));

        var body = new MethodBody(fixture.id, fixture.sig, info, info);
        assertSame(info, body.getUnionLeft());
        assertSame(info, body.getUnionRight());
    }

    @Test
    public void narrowingBoundaryAcceptsOnlyTheLegacyNidUnion() {
        var fixture = new Fixture();

        assertEquals(new Target.BySignature(fixture.sig), Target.narrowing(fixture.sig));
        assertThrows(IllegalArgumentException.class, () -> Target.narrowing(null));
        // a String is no longer even expressible here: Target.narrowing takes a Nid, so the
        // case this used to assert at runtime is now rejected by the compiler
        assertThrows(IllegalArgumentException.class, () -> Target.narrowing(Nid.of("just a string")));

        var capped = new MethodBody(fixture.id, fixture.sig, Implementation.Capped,
                Target.narrowing(fixture.sig));
        assertSame(fixture.sig, capped.getNarrowingNestedIdentity(),
                "the accessor must keep returning the raw nid for the legacy Object-keyed maps");
    }

    @Test
    public void propPayloadAnswersThePropertyAccessor() {
        var fixture = new Fixture();
        var idProp  = fixture.propId();

        var body = new MethodBody(fixture.id, fixture.sig, Implementation.Field,
                new Target.Prop(idProp));
        assertSame(idProp, body.getPropertyConstant());
        assertNull(new MethodBody(fixture.id, fixture.sig, Implementation.Declared)
                .getPropertyConstant());
    }

    private static final class Fixture {
        final ConstantPool      pool;
        final ClassStructure    struct;
        final SignatureConstant sig;
        final MethodConstant    id;

        Fixture() {
            var file = new FileStructure("test");
            pool   = file.getConstantPool();
            struct = file.getModule().createClass(Access.PUBLIC, Format.CLASS, "Test", null);
            sig    = pool.ensureSignatureConstant("test", ConstantPool.NO_TYPES, ConstantPool.NO_TYPES);
            id     = pool.ensureMethodConstant(struct.getIdentityConstant(), sig);
        }

        MethodInfo nativeInfo() {
            return MethodInfo.create(new MethodBody(id, sig, Implementation.Native), 0);
        }

        PropertyConstant propId() {
            return pool.ensurePropertyConstant(struct.getIdentityConstant(), "prop");
        }
    }
}
