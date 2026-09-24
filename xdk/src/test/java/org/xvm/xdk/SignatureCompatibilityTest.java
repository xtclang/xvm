package org.xvm.xdk;

import java.io.IOException;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.IntersectionTypeConstant;
import org.xvm.asm.constants.ParameterizedTypeConstant;
import org.xvm.asm.constants.TypeCollector;
import org.xvm.asm.constants.TypeConstant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-pool signature comparisons against the libraries provisioned by the XDK test task.
 * These tests require real generic inheritance and auto-narrowing metadata, not an execution VM.
 */
class SignatureCompatibilityTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void genericReturnUsesTheDestinationPool(boolean bindAmbient) throws IOException {
        var fixture = fixture();
        var source = library.pool();
        var resolved = new ParameterizedTypeConstant(source, source.typeList(),
                new TypeConstant[] {library.element()});
        var implementation = source.ensureSignatureConstant("value", ConstantPool.NO_TYPES,
                new TypeConstant[] {library.array()});
        var contract = source.ensureSignatureConstant("value", ConstantPool.NO_TYPES,
                new TypeConstant[] {library.formalList()});

        assertFalse(library.array().isA(library.formalList()));
        assertNull(fixture.destination().getConstant(resolved));
        var ambient = bindAmbient ? fixture.ambient() : null;
        try (var ignored = ConstantPool.withPool(ambient)) {
            assertTrue(implementation.isSubstitutableFor(
                    fixture.destination(), contract, library.array()));
            assertOwnedResolution(fixture, resolved);
            assertFalse(source.typeString().isCovariantReturn(
                    fixture.destination(), library.formalList(), library.array()));
            assertSame(ambient, ConstantPool.getCurrentPool());
        }
        assertSame(source, implementation.getConstantPool());
        assertSame(source, contract.getConstantPool());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void genericParameterUsesTheDestinationPool(boolean bindAmbient) throws IOException {
        var fixture = fixture();
        var source = library.pool();
        var resolved = source.ensureParameterizedTypeConstant(source.typeList(), library.element());
        var implementation = source.ensureSignatureConstant("accept",
                new TypeConstant[] {resolved}, ConstantPool.NO_TYPES);
        var contract = source.ensureSignatureConstant("accept",
                new TypeConstant[] {library.formalList()}, ConstantPool.NO_TYPES);

        assertFalse(library.formalList().isA(resolved));
        assertNull(fixture.destination().getConstant(resolved));
        var ambient = bindAmbient ? fixture.ambient() : null;
        try (var ignored = ConstantPool.withPool(ambient)) {
            assertTrue(implementation.isSubstitutableFor(
                    fixture.destination(), contract, library.array()));
            assertOwnedResolution(fixture, resolved);
            assertFalse(source.typeString().isContravariantParameter(
                    fixture.destination(), library.formalList(), library.array()));
            assertSame(ambient, ConstantPool.getCurrentPool());
        }
        assertSame(source, resolved.getConstantPool());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void autoNarrowingUnionUsesTheDestinationPool(boolean bindAmbient) throws IOException {
        var fixture = fixture();
        var source = library.pool();
        var thisArray = source.ensureThisTypeConstant(library.arrayClass().getIdentityConstant(), null);
        var list = source.ensureParameterizedTypeConstant(source.typeList(), library.element());
        var union = source.ensureUnionTypeConstant(library.array(), list);
        var intersection = new IntersectionTypeConstant(source,
                union.getUnderlyingType(), union.getUnderlyingType2());
        var implementation = source.ensureSignatureConstant("value", ConstantPool.NO_TYPES,
                new TypeConstant[] {thisArray});
        var contract = source.ensureSignatureConstant("value", ConstantPool.NO_TYPES,
                new TypeConstant[] {library.array()});

        assertFalse(thisArray.isA(library.array()));
        assertNull(fixture.destination().getConstant(intersection));
        var ambient = bindAmbient ? fixture.ambient() : null;
        try (var ignored = ConstantPool.withPool(ambient)) {
            assertTrue(implementation.isSubstitutableFor(fixture.destination(), contract, union));
            assertSame(ambient, ConstantPool.getCurrentPool());
        }
        assertOwnedResolution(fixture, intersection);
        assertSame(source, thisArray.getConstantPool());
        assertSame(source, union.getConstantPool());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void inferredCommonTypeUsesTheRequestedDestination(boolean bindAmbient) throws IOException {
        var fixture = fixture();
        try (var scope = ConstantPool.withPool(bindAmbient ? fixture.ambient() : null)) {
            var result = TypeCollector.inferFrom(new TypeConstant[] {library.array()}, fixture.destination());
            assertNotNull(result);
            assertSame(fixture.destination(), result.getConstantPool());
            assertSame(library.pool(), library.array().getConstantPool());
        }
    }

    private static void assertOwnedResolution(Fixture fixture, TypeConstant expected) {
        var resolved = fixture.destination().getConstant(expected);
        assertNotNull(resolved, "Resolution must register its result in the explicit destination");
        assertSame(fixture.destination(), resolved.getConstantPool());
        assertNull(fixture.ambient().getConstant(expected));
    }

    /**
     * Load and initialize source metadata once. Each comparison still receives a fresh destination
     * pool, so prior comparisons cannot satisfy the assertions about destination registration.
     * The turtle module supplies NakedRef metadata required by ordinary type analysis.
     */
    @BeforeAll
    static void loadSourceTypes() throws IOException {
        var installed = Path.of("build", "install", "xdk");
        var sourceFile = new FileStructure(installed.resolve("lib/ecstasy.xtc").toFile(), false);
        var source = sourceFile.getConstantPool();
        var repository = new LinkedRepository(
                new DirRepository(installed.resolve("lib").toFile(), true),
                new DirRepository(installed.resolve("javatools").toFile(), true));
        var turtle = repository.loadModule(Constants.TURTLE_MODULE);
        assertNotNull(turtle, "The XDK test task must provision the turtle module");
        assertNull(turtle.getFileStructure().linkModules(repository, false));
        var nakedRef = ((ClassStructure) turtle.getChild("NakedRef")).getFormalType();
        source.setNakedRefType(nakedRef);
        turtle.getConstantPool().setNakedRefType(nakedRef);

        var element = sourceFile.getModule().createClass(
                Access.PUBLIC, Format.CLASS, "CompatibilityMarker", null).getCanonicalType();
        var arrayClass = (ClassStructure) ((IdentityConstant) source.typeArray().getDefiningConstant()).getComponent();
        var array = source.ensureArrayType(element);
        var formalList = source.ensureParameterizedTypeConstant(
                source.typeList(), arrayClass.getFormalType().getParamType(0));
        try (var ignored = ConstantPool.withPool(source)) {
            array.ensureTypeInfo();
            formalList.ensureTypeInfo();
        }
        library = new LibraryTypes(source, nakedRef, element, arrayClass, array, formalList);
    }

    /**
     * Copy the same definitions so source types are shareable with the destination. The unrelated
     * ambient pool deliberately has no copy of those definitions.
     */
    private static Fixture fixture() throws IOException {
        var file = new FileStructure(Path.of("build", "install", "xdk", "lib", "ecstasy.xtc").toFile(), false);
        file.getModule().createClass(Access.PUBLIC, Format.CLASS, "CompatibilityMarker", null);
        file.getConstantPool().setNakedRefType(library.nakedRef());
        return new Fixture(file.getConstantPool(), new FileStructure("Unrelated").getConstantPool());
    }

    private record Fixture(ConstantPool destination, ConstantPool ambient) {}

    private record LibraryTypes(ConstantPool pool, TypeConstant nakedRef, TypeConstant element,
                                ClassStructure arrayClass, TypeConstant array, TypeConstant formalList) {}

    private static LibraryTypes library;
}
