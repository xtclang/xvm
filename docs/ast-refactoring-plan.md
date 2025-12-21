# AST Child Node Refactoring Plan

## Current State

The XVM compiler AST uses reflection to enumerate and manipulate child nodes:

```java
// Old approach - reflection based
protected Field[] getChildFields() {
    return CHILD_FIELDS;  // static array of Field objects
}

// ChildIterator uses reflection to:nod
// 1. Read fields: field.get(AstNode.this)
// 2. Write fields: field.set(AstNode.this, newChild)
// 3. Handle both scalars and collections dynamically
```

### Where Reflection Is Used

| Use Case | What It Needs | Current Impl |
|----------|---------------|--------------|
| `children()` iteration | Just traversal | ✅ Uses `forEachChild()` |
| `replaceChild()` | Find + write-back | ✅ Now explicit per-class |
| `copy()` (was `clone()`) | Deep copy of subtree | **Still uses reflection** |
| `getDumpChildren()` | Field names for debug | **Still uses reflection** |

**Important**: We do NOT use Java's `Cloneable` interface or `clone()` method. That API is
fundamentally broken (see Effective Java). We use `copy()` for deep copying nodes. 
### The ChildIterator Cursor Pattern

The original `ChildIterator` is not just iteration - it's a **mutable cursor** that tracks:
- Which field the current child came from
- Whether it's a scalar or collection
- Position within collection (for ListIterator)
- Legality of `remove()` or `replaceWith()` at current position

This is **not replaceable with flatMap/streams** because streams lose location context.

---

## Driving Goals (Cut Through Everything)

Every change in this refactoring must serve these core objectives:

### 1. LSP/Incremental Compilation Support
**Problem**: Current compiler is monolithic - compile everything or nothing.
**Goal**: Enable partial compilation where only affected nodes are reprocessed.
**Requires**:
- Immutable AST nodes (copy-on-write transformation)
- External caches keyed by node identity (not stored on nodes)
- Deterministic stage processing (no hidden state)

### 2. Thread Safety and Parallelism
**Problem**: Current code has zero synchronization - single-threaded only.
**Goal**: Safe parallel compilation of independent modules.
**Requires**:
- Immutable data structures (no defensive copying needed)
- Lock-free caching (ConcurrentHashMap.computeIfAbsent)
- No shared mutable state between compilation units

### 3. Speed and Stability
**Problem**: TypeInfo is a thread-choking bottleneck; fixpoint iteration is slow.
**Goal**: 2-4x faster parallel compilation; predictable performance.
**Requires**:
- Structural sharing for TypeInfo variants
- External TypeInfoCache with lock-free access
- Reduced memory churn (fewer temporary objects)

### 4. Testability at Class Level
**Problem**: No unit tests for AST transformations or ConstantPool operations.
**Goal**: Every class change has corresponding unit test coverage.
**Requires**:
- Test infrastructure created BEFORE refactoring
- Tests must pass on master, continue passing after changes
- Granular tests that isolate specific behaviors

---

## Safe Migration Policy

### Principle: Test First, Then Migrate

**NEVER change production code without corresponding test coverage.**

```
┌─────────────────────────────────────────────────────────────┐
│  For each refactoring step:                                 │
│                                                             │
│  1. Write unit tests on MASTER that verify current behavior │
│  2. Ensure tests pass (baseline)                            │
│  3. Make the refactoring change                             │
│  4. Verify tests still pass (no regression)                 │
│  5. Add new tests for new capabilities                      │
└─────────────────────────────────────────────────────────────┘
```

### Test Categories Required

#### A. AST Node Tests (per class)

```java
// Example: BiExpressionTest.java
@Test void testForEachChild_visitsLeftAndRight();
@Test void testWithChildren_createsNewInstance();
@Test void testWithChildren_preservesOperator();
@Test void testCopy_deepCopiesChildren();
@Test void testCopy_doesNotCopyDerivedFields();
@Test void testAdopt_setsParentOnChildren();
```

**Coverage for each AST node class:**
- [ ] forEachChild() visits all structural children in order
- [ ] withChildren() creates new instance with new children
- [ ] withChildren() preserves non-child fields (tokens, operators)
- [ ] copy() deep copies entire subtree
- [ ] @Derived fields are NOT copied
- [ ] adopt() properly links parent references

#### B. ConstantPool Tests

```java
// Example: ConstantPoolTransferTest.java
@Test void testTransferTo_samePool_returnsSame();
@Test void testTransferTo_differentPool_createsNew();
@Test void testTransferTo_registersInTargetPool();
@Test void testTransferTo_handlesCircularReferences();
@Test void testIsShared_upstreamPoolsAreShared();
```

**Coverage:**
- [ ] transferTo() same pool returns `this`
- [ ] transferTo() different pool creates new instance
- [ ] transferTo() recursively transfers child constants
- [ ] Circular constant references don't cause infinite loops
- [ ] isShared() correctly identifies shareable constants

#### C. TypeInfo Tests

```java
// Example: TypeInfoCacheTest.java
@Test void testEnsureTypeInfo_cachesResult();
@Test void testEnsureTypeInfo_threadSafe();
@Test void testLimitAccess_sharesBaseData();
@Test void testAsInto_sharesBaseData();
@Test void testPropertyLookup_cachesResults();
```

**Coverage:**
- [ ] TypeInfo is computed once and cached
- [ ] Concurrent access doesn't corrupt state
- [ ] Variant TypeInfo (public/private/into) shares base data
- [ ] Property/method lookups are cached
- [ ] Cache invalidation works correctly

#### D. Stage Processing Tests

```java
// Example: StageMgrTest.java
@Test void testFixpointIteration_resolvesCircularDeps();
@Test void testRequestRevisit_schedulesReprocessing();
@Test void testStageProgression_enforcesOrder();
```

**Coverage:**
- [ ] Fixpoint iteration terminates
- [ ] requestRevisit() causes reprocessing
- [ ] Stage prerequisites are enforced
- [ ] Error handling doesn't corrupt state

### Granularity Strategy

**Small, Isolated Changes:**
```
BAD:  "Refactor all 94 AST classes to use withChildren()"
GOOD: "Add withChildren() to BiExpression, with tests"
      "Add withChildren() to IfStatement, with tests"
      ... (one class at a time)
```

**Each PR should:**
1. Touch minimal files (ideally 1-3 production files)
2. Include corresponding test files
3. Be independently reviewable
4. Not break existing tests

### Rollback Points

Establish clear rollback points:
- After AST reflection removal (Phase 1-4) ✅ DONE
- After Copyable interface introduction (Phase 2)
- After ConstantPool transferTo() migration (Phase 5)
- After TypeInfo immutability (Phase 5.5)
- After external cache introduction

Each rollback point should have:
- All tests passing
- No performance regression (benchmark)
- Clean git tag for easy revert

### Test Infrastructure Setup (Do First)

Before ANY production changes:

```
1. Create test directory structure:
   javatools/src/test/java/org/xvm/compiler/ast/
   javatools/src/test/java/org/xvm/asm/constants/

2. Create base test utilities:
   - AstTestUtils.java (create test AST nodes)
   - ConstantPoolTestUtils.java (create test pools)
   - TypeInfoTestUtils.java (create test type info)

3. Create baseline tests on master:
   - Run and verify they pass
   - These are the regression safety net
```

### Three-Tier Testing Strategy

The XTC compiler has a bootstrapping challenge: to fully compile XTC code, you need the
Ecstasy library modules (ecstasy.xtc, etc.) which are themselves compiled XTC code. This
creates a chicken-and-egg problem for unit testing that requires name resolution or type checking.

**Solution: Three tiers of testing with increasing dependencies:**

#### Tier 1: Pure Structural Tests (No Dependencies)

These tests don't require any compiled XTC modules. They test the structural aspects of
AST nodes, ConstantPool operations, and internal algorithms.

**What can be tested:**

```java
// AST structural tests - no compilation needed
@Test void testBiExpression_forEachChild_visitsLeftAndRight() {
    Token plus = new Token(Token.Id.ADD, ...);
    LiteralExpression left = new LiteralExpression(Token.literal(1L));
    LiteralExpression right = new LiteralExpression(Token.literal(2L));
    BiExpression expr = new BiExpression(left, plus, right);

    List<AstNode> children = new ArrayList<>();
    expr.forEachChild((Consumer<AstNode>) children::add);

    assertEquals(2, children.size());
    assertSame(left, children.get(0));
    assertSame(right, children.get(1));
}

@Test void testBiExpression_withChildren_createsNewInstance() {
    BiExpression original = createBiExpression();
    LiteralExpression newLeft = new LiteralExpression(Token.literal(99L));

    BiExpression replaced = (BiExpression) original.withChildren(
        List.of(newLeft, original.getExpr2()));

    assertNotSame(original, replaced);
    assertSame(newLeft, replaced.getExpr1());
    assertSame(original.getExpr2(), replaced.getExpr2());
    assertSame(original.getOperator(), replaced.getOperator());
}

@Test void testAstNode_copy_deepCopiesChildren() {
    BiExpression original = createNestedExpression();
    BiExpression copy = (BiExpression) original.copy();

    assertNotSame(original, copy);
    assertNotSame(original.getExpr1(), copy.getExpr1());
    assertEquals(original.toString(), copy.toString());  // Structurally equal
}

@Test void testAstNode_adopt_setsParentReferences() {
    BiExpression parent = new BiExpression(
        new LiteralExpression(...),
        plusToken,
        new LiteralExpression(...)
    );
    parent.introduceParentage();

    assertSame(parent, parent.getExpr1().getParent());
    assertSame(parent, parent.getExpr2().getParent());
}
```

**ConstantPool tests (isolated):**

```java
@Test void testConstantPool_registerIntConstant_returnsSameForDuplicate() {
    ConstantPool pool = new ConstantPool();
    IntConstant c1 = pool.ensureIntConstant(42);
    IntConstant c2 = pool.ensureIntConstant(42);

    assertSame(c1, c2);  // Interning works
}

@Test void testConstant_transferTo_differentPool_createsNew() {
    ConstantPool poolA = new ConstantPool();
    ConstantPool poolB = new ConstantPool();
    IntConstant inA = poolA.ensureIntConstant(123);

    IntConstant inB = inA.transferTo(poolB);

    assertNotSame(inA, inB);
    assertEquals(inA.getValue(), inB.getValue());
    assertSame(poolB, inB.getConstantPool());
}

@Test void testConstant_transferTo_samePool_returnsSame() {
    ConstantPool pool = new ConstantPool();
    IntConstant c = pool.ensureIntConstant(456);

    assertSame(c, c.transferTo(pool));
}
```

**StageMgr/fixpoint tests (with mock nodes):**

```java
@Test void testStageMgr_requestRevisit_addsToRevisitList() {
    MockAstNode node = new MockAstNode() {
        @Override
        public void resolveNames(StageMgr mgr, ErrorListener errs) {
            if (firstVisit) {
                mgr.requestRevisit();
                firstVisit = false;
            } else {
                // Complete on second visit
            }
        }
    };

    StageMgr mgr = new StageMgr(node, Stage.Resolved, new ErrorList(10));

    assertFalse(mgr.processComplete());  // First pass - requested revisit
    assertTrue(mgr.processComplete());   // Second pass - completed
}

@Test void testStageMgr_processComplete_terminatesAfterMaxIterations() {
    MockAstNode infiniteLoop = new MockAstNode() {
        @Override
        public void resolveNames(StageMgr mgr, ErrorListener errs) {
            mgr.requestRevisit();  // Always request revisit
        }
    };

    StageMgr mgr = new StageMgr(infiniteLoop, Stage.Resolved, new ErrorList(10));
    mgr.fastForward(10);  // Max 10 iterations

    assertTrue(mgr.isLastAttempt());  // Should have hit limit
}
```

**Utility class for creating test nodes:**

```java
public class AstTestUtils {
    public static Token plusToken() {
        return new Token(0, 0, Token.Id.ADD, null);
    }

    public static LiteralExpression intLiteral(long value) {
        return new LiteralExpression(Token.literal(value));
    }

    public static BiExpression addExpr(Expression left, Expression right) {
        return new BiExpression(left, plusToken(), right);
    }

    // Mock node for testing stage processing
    public static class MockAstNode extends AstNode {
        private boolean firstVisit = true;

        @Override
        public <T> T forEachChild(Function<AstNode, T> visitor) {
            return null;  // No children
        }

        @Override
        protected AstNode withChildren(List<AstNode> children) {
            return this;  // No children to replace
        }

        // Override stage methods for testing
        @Override
        public void resolveNames(StageMgr mgr, ErrorListener errs) {
            // Default: complete immediately
        }
    }
}
```

#### Tier 2: Parser-Level Tests (Syntax Only)

These tests parse XTC source code and verify the resulting AST structure without
performing name resolution or type checking.

```java
@Test void testParser_simpleClass_createsCorrectStructure() {
    String source = "class Foo { Int x; void bar() {} }";
    Source src = new Source(source);
    ErrorList errs = new ErrorList(10);
    Parser parser = new Parser(src, errs);

    Statement stmt = parser.parseSource();

    assertTrue(stmt instanceof StatementBlock);
    // Verify structure without name resolution
}

@Test void testParser_nestedExpression_preservesParentheses() {
    String source = "module T { void f() { return (1 + 2) * 3; } }";
    // Parse and verify the AST structure
}

@Test void testAstNode_toString_roundTripsForParsedCode() {
    String original = "if (x > 0) { return x; } else { return -x; }";
    Source src = new Source("module T { void f(Int x) { " + original + " } }");
    // Parse, get the if statement, verify toString matches
}
```

#### Tier 3: Integration Tests (Full XDK Required)

These tests require the pre-compiled XDK (ecstasy.xtc and friends). They run after
`./gradlew installDist` and test the full compilation pipeline.

**Located in:** `xdk/src/test/java/org/xvm/xdk/`

```java
@Test void testCompile_simpleModule_resolvesNames() {
    String source = """
        module Test {
            void run() {
                Int x = 42;
                @Inject Console console;
                console.print(x.toString());
            }
        }
        """;

    FileStructure struct = compileWithXdk(source);

    // Verify that names resolved correctly
    assertNotNull(struct.getModule());
    // Can access type info because ecstasy.xtc is available
}

@Test void testTypeInfo_intType_hasExpectedMethods() {
    // This test needs the compiled Ecstasy library
    TypeConstant intType = getIntTypeFromXdk();
    TypeInfo info = intType.ensureTypeInfo();

    assertNotNull(info.findMethod("add"));
    assertNotNull(info.findMethod("toString"));
}
```

### Benchmarking Strategy

**Goal:** Prove that immutability and caching improvements make the compiler faster,
not just cleaner code.

#### Baseline Measurements (Before Refactoring)

Create benchmarks that measure current performance:

```java
@Benchmark
public void compileEcstasyModule(Blackhole bh) {
    // Measure time to compile a significant XTC module
    FileStructure struct = compileModule("path/to/test/module.x");
    bh.consume(struct);
}

@Benchmark
public void typeInfoBuilding(Blackhole bh) {
    // Measure TypeInfo construction time
    TypeConstant type = getComplexGenericType();
    type.invalidateTypeInfo();  // Clear cache
    TypeInfo info = type.ensureTypeInfo();
    bh.consume(info);
}

@Benchmark
public void constantPoolTransfer(Blackhole bh) {
    // Measure constant transfer between pools
    ConstantPool source = createPoolWithManyConstants();
    ConstantPool target = new ConstantPool();
    for (Constant c : source.getConstants()) {
        bh.consume(c.transferTo(target));
    }
}

@Benchmark
public void astCopyDeep(Blackhole bh) {
    // Measure deep copy performance
    AstNode tree = parseComplexModule();
    bh.consume(tree.copy());
}
```

#### Metrics to Track

| Metric | Why It Matters | Target Improvement |
|--------|----------------|-------------------|
| Full module compile time | Overall performance | ≥10% faster |
| TypeInfo build time | Major bottleneck | ≥50% reduction in lock contention |
| Memory per compilation | Cache efficiency | No increase, ideally 10-20% decrease |
| Parallel compilation speedup | Thread safety | Near-linear with core count |
| AST copy time | Copy-on-write foundation | Baseline for structural sharing |

#### Benchmark Infrastructure

```java
// javatools/src/test/java/org/xvm/benchmark/
public class CompilerBenchmarkHarness {
    private static File xdkLib;
    private static File testModules;

    @BeforeAll
    static void setupXdk() {
        // Same setup as XdkIntegrationTest
        xdkLib = new File("build/install/xdk/lib");
    }

    public FileStructure compileModule(String path) {
        CompilerOptions opts = new CompilerOptions.Builder()
            .addModulePath(xdkLib)
            .addInputFile(new File(path))
            .build();
        return new Compiler(opts, null, new ErrorList(10)).compile();
    }

    // JMH-style annotations for microbenchmarks
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkName() { ... }
}
```

#### Continuous Performance Tracking

1. **Run benchmarks on each PR** that touches core compiler code
2. **Store results** in a simple JSON/CSV file for trend tracking
3. **Alert on regressions** > 5% in any key metric
4. **Celebrate improvements** with concrete numbers in commit messages

**Example performance tracking entry:**
```json
{
  "commit": "abc123",
  "date": "2025-01-15",
  "metrics": {
    "compileEcstasyModule_ms": 1234,
    "typeInfoBuild_ms": 45,
    "astCopyDeep_ms": 12,
    "memoryMB": 256
  }
}
```

#### Performance Goals by Phase

| Phase | Expected Performance Impact |
|-------|----------------------------|
| Phase 1 (forEachChild) | Slight overhead from Function calls, <5% |
| Phase 2 (Copyable) | Neutral - just explicit what was implicit |
| Phase 3-4 (Copy-on-Write) | +10-20% for incremental edits, slight overhead for full compile |
| Phase 5 (Constants) | +5% from reduced cloning |
| Phase 5.5 (TypeInfo) | **+50-100% parallel speedup** due to lock contention removal |
| Phase 6-8 (Cleanup) | Neutral to slight improvement |

### ConstantPool and Component Testing Strategy

The ConstantPool and Component hierarchies are critical infrastructure that the entire
compiler depends on. They currently have:
- No @NotNull/@Nullable annotations
- Mutable arrays exposed through public APIs
- Implicit null handling buried in control flow
- Limited unit test coverage

**There are almost certainly bugs hiding here.**

#### Testing ConstantPool Edge Cases

```java
// Test null handling (currently implicit, should be explicit)
@Test void testConstantPool_registerNull_throwsNPE() {
    ConstantPool pool = new ConstantPool();
    assertThrows(NullPointerException.class, () -> pool.register(null));
}

@Test void testConstantPool_ensureStringConstant_nullValue() {
    ConstantPool pool = new ConstantPool();
    // What happens? NPE? Null constant? Error?
    // This should be documented and tested
}

// Test circular references
@Test void testConstantPool_circularTypeReference_doesNotStackOverflow() {
    // Create: class Foo<T extends Foo<T>>
    ConstantPool pool = new ConstantPool();
    // Should terminate, not infinite loop
}

// Test pool isolation
@Test void testConstantPool_constantsFromDifferentPools_cannotMix() {
    ConstantPool pool1 = new ConstantPool();
    ConstantPool pool2 = new ConstantPool();
    IntConstant c1 = pool1.ensureIntConstant(42);

    // What happens if you try to use c1 directly in pool2?
    // Should require explicit transfer
}

// Test interning consistency
@Test void testConstantPool_sameValueDifferentPath_sameConstant() {
    ConstantPool pool = new ConstantPool();
    // Create "Int" type via two different code paths
    TypeConstant t1 = pool.ensureEcstasyTypeConstant("Int");
    TypeConstant t2 = /* different code path that also creates Int */;

    assertSame(t1, t2);  // Must be interned
}
```

#### Testing Component Hierarchy

```java
// Test parent-child invariants
@Test void testComponent_childAdded_parentIsSet() {
    ClassStructure parent = createTestClass();
    MethodStructure child = parent.createMethod(...);

    assertSame(parent, child.getParent());
}

@Test void testComponent_removeChild_parentIsCleared() {
    ClassStructure parent = createTestClass();
    MethodStructure child = parent.createMethod(...);
    parent.removeChild(child);

    assertNull(child.getParent());  // Or throws?
}

// Test immutability violations (find the bugs!)
@Test void testComponent_getChildren_returnsDefensiveCopy() {
    ClassStructure clz = createTestClass();
    List<Component> children = clz.children();
    int originalSize = children.size();

    try {
        children.add(new MethodStructure(...));  // Modify returned list
        fail("Should throw UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
        // Good - list is properly protected
    }

    // Verify original wasn't modified
    assertEquals(originalSize, clz.children().size());
}

// Test conditional compilation
@Test void testComponent_bifurcation_createsIndependentCopy() {
    ClassStructure original = createTestClass();
    ConditionalConstant condition = createTestCondition();

    ClassStructure bifurcated = original.copyForCondition(condition);

    assertNotSame(original, bifurcated);
    // Changes to bifurcated should not affect original
}
```

#### Null Safety Enforcement

**Every public method should have explicit null contracts.**

```java
// CURRENT (dangerous - nullability unclear):
public TypeConstant getType() {
    return m_type;  // Can this be null? Who knows!
}

// REQUIRED (explicit contract):
public @NotNull TypeConstant getType() {
    return Objects.requireNonNull(m_type, "Type not resolved");
}

// Or if null is valid:
public @Nullable TypeConstant getTypeIfResolved() {
    return m_type;
}
```

**Testing null contracts:**

```java
// Find every method that can return null but doesn't say so
@Test void testTypeConstant_allPublicMethods_documentNullability() {
    // Use reflection to find all public methods
    // For each method that returns an object:
    // - Either @NotNull or @Nullable annotation must be present
    // This is a meta-test for code quality
}

// Test null parameter handling
@ParameterizedTest
@MethodSource("provideNullSensitiveMethods")
void testMethod_nullParameter_throwsOrHandles(Method method, Object[] args) {
    // For each method that doesn't accept null, verify it throws NPE
    // For each method that does accept null, verify it handles correctly
}
```

#### Parameter Hygiene and Validation

**Current antipattern:**
```java
// Found all over the codebase
public void setChildren(TypeConstant[] types) {
    m_types = types;  // Caller can modify after setting!
}
```

**Required pattern:**
```java
public void setChildren(@NotNull List<TypeConstant> types) {
    Objects.requireNonNull(types, "types cannot be null");
    if (types.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException("types cannot contain null");
    }
    this.types = List.copyOf(types);  // Defensive copy, immutable
}
```

**Tests for parameter hygiene:**

```java
@Test void testSetChildren_arrayModifiedAfterSet_noEffectOnComponent() {
    ClassStructure clz = new ClassStructure();
    TypeConstant[] original = { type1, type2 };
    clz.setTypeParams(original);

    original[0] = null;  // Modify original array

    // Component should be unaffected
    assertNotNull(clz.getTypeParams().get(0));
}

@Test void testConstructor_allFieldsInitialized_noNullFields() {
    ClassStructure clz = new ClassStructure("Test", ...);

    // Every field that should be non-null must be initialized
    assertNotNull(clz.getName());
    assertNotNull(clz.getAccess());
    // etc.
}
```

#### Final Fields Are Best

**Every field that can be final, should be final.**

```java
// CURRENT (mutable - bugs can hide):
private TypeConstant m_type;

public void setType(TypeConstant type) {
    m_type = type;  // Can be set multiple times? When? By whom?
}

// BETTER (immutable - no hidden mutation):
private final TypeConstant type;

public Component(TypeConstant type, ...) {
    this.type = Objects.requireNonNull(type);
}

// If you need to change it, create a new instance:
public Component withType(TypeConstant newType) {
    return new Component(newType, this.other, this.fields);
}
```

**Testing immutability:**

```java
@Test void testClassStructure_allFieldsAreFinal() {
    Class<ClassStructure> clz = ClassStructure.class;
    for (Field field : clz.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers())) {
            assertTrue(Modifier.isFinal(field.getModifiers()),
                "Field " + field.getName() + " should be final");
        }
    }
}
```

#### The Lazy<T> Class - Final Fields with Lazy Initialization

The `org.xvm.util.Lazy<T>` class enables the "final + lazy" pattern: fields can be
`final` (immutable once set) while still being computed on first access.

**Location:** `javatools_utils/src/main/java/org/xvm/util/Lazy.java`

**Three Variants for Different Use Cases:**

| Variant | Factory Method | Thread-Safe? | Use Case |
|---------|---------------|--------------|----------|
| `ThreadSafeLazy` | `Lazy.of(supplier)` | ✅ Yes | Default for shared state |
| `UnsafeLazy` | `Lazy.ofUnsafe(supplier)` | ❌ No | Single-threaded paths |
| `Initialized` | `Lazy.ofValue(value)` | ✅ Yes | Testing, pre-computed values |

**Converting Lazy Null-Check Patterns:**

```java
// BEFORE: The classic antipattern
// Problems:
// 1. Field is mutable (can't be final)
// 2. Not thread-safe (racing threads may compute twice)
// 3. Unclear if null means "not computed" or "no value"
// 4. Computation can happen multiple times if not synchronized

private TypeInfo m_typeInfo;  // Mutable, nullable

public TypeInfo getTypeInfo() {
    if (m_typeInfo == null) {
        m_typeInfo = computeTypeInfo();  // Race condition!
    }
    return m_typeInfo;
}

// AFTER: Final field with Lazy
// Benefits:
// 1. Field is final (immutable)
// 2. Thread-safe (computation happens exactly once)
// 3. Clear intent (Lazy = "computed on first access")
// 4. Supplier is released after computation (GC-friendly)

private final Lazy<TypeInfo> typeInfo = Lazy.of(this::computeTypeInfo);

public TypeInfo getTypeInfo() {
    return typeInfo.get();
}
```

**When to Use Each Variant:**

**1. `Lazy.of()` - Thread-Safe (Default)**

Use when the lazy value might be accessed from multiple threads:

```java
// TypeConstant - accessed during parallel type resolution
private final Lazy<TypeInfo> typeInfo = Lazy.of(this::buildTypeInfo);

// Component - may be queried from multiple compilation threads
private final Lazy<List<MethodStructure>> methods = Lazy.of(this::collectMethods);
```

Implementation uses double-checked locking with volatile:
- First check: non-synchronized, fast path for initialized values
- Second check: synchronized, ensures exactly-once computation
- Releases supplier after computation for GC

**2. `Lazy.ofUnsafe()` - Single-Threaded**

Use when you're sure only one thread accesses the value (avoids sync overhead):

```java
// Parser is single-threaded
private final Lazy<Token> peekToken = Lazy.ofUnsafe(this::scanNextToken);

// AstNode during initial construction (before shared)
private final Lazy<String> sourceText = Lazy.ofUnsafe(() ->
    source.substring(startPos, endPos));
```

Implementation is simpler with no synchronization:
- Just null-check and compute
- Smaller memory footprint (no volatile)
- ~2x faster than thread-safe version

**3. `Lazy.ofValue()` - Pre-Initialized**

Use for testing or when value is known at construction:

```java
// In tests - avoid computing, provide known value
TypeInfo mockInfo = createMockTypeInfo();
typeConstant.setTypeInfoForTest(Lazy.ofValue(mockInfo));

// When value is sometimes known upfront
private final Lazy<TypeInfo> typeInfo;

public TypeConstant(TypeInfo knownInfo) {
    this.typeInfo = Lazy.ofValue(knownInfo);  // Pre-initialized
}

public TypeConstant() {
    this.typeInfo = Lazy.of(this::buildTypeInfo);  // Lazy
}
```

**Checking Initialization State:**

```java
// Check without triggering computation
if (!typeInfo.isInitialized()) {
    // Still lazy - hasn't been accessed yet
}

// Useful for debugging, cache statistics, etc.
```

**Common Migration Patterns in XVM:**

```java
// 1. TypeConstant.m_typeinfo → Lazy<TypeInfo>
// BEFORE:
private transient volatile TypeInfo m_typeinfo;
public TypeInfo ensureTypeInfo() {
    TypeInfo info = m_typeinfo;
    if (info == null || info != m_typeinfo) {  // Complex racy check!
        synchronized (this) {
            // Build it...
        }
    }
    return m_typeinfo;
}

// AFTER:
private final Lazy<TypeInfo> typeInfo = Lazy.of(this::buildTypeInfo);
public TypeInfo ensureTypeInfo() {
    return typeInfo.get();  // That's it!
}

// 2. TypeInfo lazy caches → Lazy<Map<...>>
// BEFORE:
private transient Map<String, PropertyInfo> m_mapPropertiesByName;
public PropertyInfo findProperty(String name) {
    if (m_mapPropertiesByName == null) {
        m_mapPropertiesByName = buildPropertyMap();
    }
    return m_mapPropertiesByName.get(name);
}

// AFTER:
private final Lazy<Map<String, PropertyInfo>> propsByName =
    Lazy.of(this::buildPropertyMap);
public PropertyInfo findProperty(String name) {
    return propsByName.get().get(name);
}

// 3. AstNode.m_aLabels (lazily allocated array) → Lazy<List<Label>>
// BEFORE:
protected transient Label[] m_aLabels;
protected Label[] getLabels() {
    if (m_aLabels == null) {
        m_aLabels = allocateLabels();
    }
    return m_aLabels;
}

// AFTER:
protected final Lazy<List<Label>> labels = Lazy.ofUnsafe(this::allocateLabels);
protected List<Label> getLabels() {
    return labels.get();
}
```

**Finding Candidates for Lazy Migration:**

```bash
# Find "if null then compute" patterns
grep -rn "if.*== null\)" --include="*.java" | grep -v "// " | head -50

# Find mutable fields with transient (often lazy caches)
grep -rn "transient.*m_" --include="*.java" | grep -v "@Derived"

# Find synchronized lazy init
grep -rn "synchronized.*{" --include="*.java" -A 3 | grep "if.*null"
```

**Classes with Significant Lazy Initialization (Candidates for Lazy<T>):**

| Class | Lazy Patterns | Priority | Notes |
|-------|---------------|----------|-------|
| `ConstantPool.java` | **156** | HIGH | Biggest win - all type/class accessors |
| `MethodStructure.java` | 30 | HIGH | Method body compilation caches |
| `InvocationExpression.java` | 14 | MEDIUM | Method resolution caches |
| `CaseManager.java` | 11 | LOW | Switch statement handling |
| `LambdaExpression.java` | 9 | MEDIUM | Lambda capture computation |
| `Component.java` | 9 | HIGH | Base class for all structures |
| `ForEachStatement.java` | 8 | LOW | Iteration caches |
| `Scope.java` | 8 | MEDIUM | Variable scope tracking |
| `NameExpression.java` | 7 | MEDIUM | Name resolution caches |
| `Context.java` | 7 | HIGH | Validation context |
| `PropertyBody.java` | 6 | MEDIUM | Property implementation |
| `TypeCollector.java` | 5 | MEDIUM | Type parameter collection |
| `Register.java` | 4 | LOW | Register allocation |

**ConstantPool is the #1 target - 156 patterns like this:**

```java
// Current: 156 repetitive lazy patterns in ConstantPool.java
public ClassConstant clzObject() {
    ClassConstant c = m_clzObject;
    if (c == null) {
        m_clzObject = c = (ClassConstant) getImplicitlyImportedIdentity("Object");
    }
    return c;
}

// With Lazy<T>: Single field declaration + simple getter
private final Lazy<ClassConstant> clzObject =
    Lazy.of(() -> (ClassConstant) getImplicitlyImportedIdentity("Object"));

public ClassConstant clzObject() {
    return clzObject.get();
}
```

**Benefits of Lazy<T> migration in ConstantPool:**
- 156 mutable fields → 156 final fields
- 156 patterns of "check-then-assign" → 156 simple `.get()` calls
- Thread-safe by default (ConstantPool is shared between modules)
- Clear intent: "lazy-computed well-known constant"

**Migration order:**
1. **ConstantPool** - biggest win, most patterns, shared state
2. **Component/MethodStructure** - core infrastructure
3. **TypeInfo** - move caches external first (Phase 5.5)
4. **AST nodes** - lower priority, often single-threaded anyway

**Future: Resettable Lazy (for Cache Invalidation)**

For cases where the cached value might need to be recomputed:

```java
public abstract class ResettableLazy<T> extends Lazy<T> {
    public abstract void reset();  // Force recomputation on next get()
}

// Usage:
private final ResettableLazy<TypeInfo> typeInfo = ResettableLazy.of(this::build);

public void invalidateTypeInfo() {
    typeInfo.reset();  // Next get() will recompute
}
```

**Future: Weak/Soft Reference Lazy (for Memory-Sensitive Caches)**

For caches that can be discarded under memory pressure:

```java
public abstract class SoftLazy<T> extends Lazy<T> {
    // Value held via SoftReference - can be GC'd and recomputed
}

// Usage for large cached data:
private final SoftLazy<List<MethodInfo>> allMethods =
    SoftLazy.of(this::collectAllMethods);  // Can be reclaimed
```

#### Coverage Analysis for Hidden Bugs

**Use coverage tools to find untested paths:**

1. **JaCoCo integration** - add to Gradle build
2. **Identify low-coverage areas** - especially null-handling branches
3. **Focus tests on edge cases**:
   - Empty collections
   - Single-element collections
   - Null values (where allowed)
   - Maximum size inputs
   - Circular references

```java
// Test generator for exhaustive edge cases
@ParameterizedTest
@CsvSource({
    "0, 0",           // empty
    "1, 1",           // single
    "1000, 1000",     // large
    "-1, exception",  // invalid
})
void testMethodWithBoundaries(int input, String expected) {
    // Test boundary conditions systematically
}
```

#### The "Null-or-Single-or-Many" Antipattern

**PROBLEM:** The codebase uses three different representations for the same concept:

```java
// Found throughout the codebase - 4 different states to handle:
private Object m_value;  // Could be:
                         // 1. null (no values)
                         // 2. Single object (one value)
                         // 3. Object[] (many values)
                         // 4. List<Object> (also many values, sometimes)

// Caller code becomes a nightmare:
if (m_value == null) {
    // handle zero case
} else if (m_value instanceof SomeType single) {
    // handle one case
} else if (m_value instanceof SomeType[] array) {
    for (SomeType item : array) {
        // handle each
    }
} else if (m_value instanceof List<?> list) {
    for (Object item : list) {
        // handle each (with cast!)
    }
}
```

**SOLUTION:** Always use collections. Period.

```java
// BEFORE (antipattern):
private TypeConstant m_typeReturn;       // Single return? Or null?
private TypeConstant[] m_atypeReturns;   // Multiple returns?

// This leads to:
TypeConstant[] returns = m_atypeReturns != null
    ? m_atypeReturns
    : m_typeReturn != null
        ? new TypeConstant[] { m_typeReturn }
        : TypeConstant.NO_TYPES;

// AFTER (clean):
private final List<TypeConstant> returns;  // Always a list. Always non-null.

// Usage is trivial:
if (returns.isEmpty()) { ... }
if (returns.size() == 1) { TypeConstant single = returns.get(0); }
for (TypeConstant ret : returns) { ... }
```

**Why this matters:**

| Representation | Problems |
|----------------|----------|
| `null` = empty | Forces null checks everywhere, NPE risk |
| `single` = 1 | Type branching (`instanceof`), can't use loops |
| `array` = many | Mutable, no type safety, different API than List |
| `List` = many | Finally correct, but now you have 4 cases! |

**Migration pattern:**

```java
// Step 1: Change field to final List
private final List<TypeConstant> returns;

// Step 2: Constructor normalizes input
public MethodInfo(TypeConstant[] returnTypes) {
    this.returns = returnTypes == null || returnTypes.length == 0
        ? List.of()                    // Empty, not null
        : List.of(returnTypes);        // Defensive copy
}

// Step 3: Single accessor, always returns List
public @NotNull @Unmodifiable List<TypeConstant> getReturns() {
    return returns;
}

// Step 4: Remove all the variant accessors
// DELETE: public TypeConstant getReturn()
// DELETE: public TypeConstant[] getReturnArray()
// DELETE: public boolean hasReturns()  // Use !getReturns().isEmpty()
```

**Finding the antipattern:**

```bash
# Find fields that store "single or null"
grep -r "private.*TypeConstant m_" --include="*.java" | grep -v "private.*TypeConstant\[\]"

# Find "null or array" patterns
grep -r "!= null \? .* : NO_TYPES" --include="*.java"

# Find instanceof checks for array vs single
grep -r "instanceof.*\[\]" --include="*.java"
```

**Tests to enforce the pattern:**

```java
@Test void testMethodInfo_emptyReturns_isEmptyList() {
    MethodInfo info = new MethodInfo(/* no returns */);

    List<TypeConstant> returns = info.getReturns();

    assertNotNull(returns);           // Never null
    assertTrue(returns.isEmpty());    // Empty, not null
    assertThrows(UnsupportedOperationException.class,
        () -> returns.add(null));     // Immutable
}

@Test void testMethodInfo_singleReturn_isSingletonList() {
    TypeConstant intType = ...;
    MethodInfo info = new MethodInfo(new TypeConstant[] { intType });

    List<TypeConstant> returns = info.getReturns();

    assertEquals(1, returns.size());
    assertSame(intType, returns.get(0));
}

@Test void testMethodInfo_nullReturnsParam_becomesEmptyList() {
    MethodInfo info = new MethodInfo((TypeConstant[]) null);

    assertNotNull(info.getReturns());
    assertTrue(info.getReturns().isEmpty());
}
```

#### Continuous Null Safety Analysis

**Add to CI/CD pipeline:**

1. **SpotBugs/FindBugs** - catches many null-related bugs
2. **Error Prone** - Google's static analysis
3. **NullAway** - annotation-based null analysis
4. **IntelliJ inspections** - can export as warnings

**Example Gradle configuration:**
```kotlin
plugins {
    id("com.github.spotbugs") version "6.0.0"
}

spotbugs {
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
    // Focus on null-related bugs
    includeFilterConfig.set(resources.text.fromString("""
        <FindBugsFilter>
            <Match>
                <Bug pattern="NP_*"/>  <!-- All null pointer patterns -->
            </Match>
        </FindBugsFilter>
    """))
}
```

---

## Modern Compiler AST Patterns

### Roslyn (C#) - Red-Green Trees

The gold standard for incremental, immutable AST design:

**Green Tree (Internal)**
- Immutable and truly persistent
- No parent references, no absolute positions
- Built bottom-up, stores only relative widths
- Only ~O(log n) nodes rebuilt per edit

**Red Tree (Public API)**
- Immutable facade built top-down on demand
- Manufactures parent references during traversal
- Computes absolute positions from widths
- Rebuilt after each edit, then discarded

**Key Insight**: Separation of concerns. Consumers only see the red tree; persistence is hidden.

**Transformation**: `With*` methods return new nodes; `ReplaceNode` re-spins parents up the tree.

### Clang (C++) - Visitor Pattern with CRTP

- No common AST ancestor - separate hierarchies (Stmt, Decl, Type)
- Each node implements `children()` returning `child_range` iterator
- **Mutable AST** - nodes can be modified in place
- `RecursiveASTVisitor<T>` with CRTP for custom traversal
- Explicit `Visit*`, `Traverse*`, `WalkUpFrom*` hooks

### TypeScript - forEachChild / visitEachChild

```typescript
// Read-only traversal
ts.forEachChild(node, callback)

// Transformation (returns new/modified node)
ts.visitEachChild(node, visitor, context)
```

**Key Insight**: Clear separation between iteration (forEachChild) and transformation (visitEachChild).

---

## XVM Compiler Stages - Detailed Analysis

The compiler processes AST nodes through multiple stages:

```
Initial
  ↓
Registering → Registered    // Pass 1: Create component structures
  ↓
Loading → Loaded            // Pass 2: Link modules (mostly no-op for AST)
  ↓
Resolving → Resolved        // Pass 2: Resolve names
  ↓
Validating → Validated      // Pass 3: Type checking, validation
  ↓
Emitting → Emitted          // Pass 4: Generate bytecode
  ↓
Discarded                   // Node no longer needed
```

### Stage 1: Registering → Registered

**Purpose**: Create the skeleton component structure (FileStructure, ModuleStructure, ClassStructure, etc.)

**What happens**:
- `introduceParentage()` - set parent references on all nodes
- Create Component structures for modules, packages, classes, methods, properties
- Register type parameters (needed before name resolution)
- NO name resolution yet - just structure creation

**Data produced**:
- `Component` structures in `org.xvm.asm` package
- Stored on AST nodes via `setComponent(component)`

**Example** (TypeCompositionStatement.registerStructures):
```java
// Creates FileStructure, ModuleStructure, PackageStructure, ClassStructure
FileStructure struct = new FileStructure(sModule);
component = struct.getModule();
setComponent(component);  // Store on AST node
```

### Stage 2: Loading → Loaded

**Purpose**: Link modules together based on declared dependencies

**What happens**:
- Mostly handled at Compiler level, not individual AST nodes
- Most AST nodes just pass through this stage
- Module imports are verified

**Data produced**:
- Module fingerprints and dependencies resolved

### Stage 3: Resolving → Resolved

**Purpose**: Resolve all names to their target components/types

**What happens**:
- Names resolved to Components, TypeConstants, etc.
- **Fixpoint iteration**: Nodes can call `mgr.requestRevisit()` if dependencies aren't ready
- Cross-module dependencies may require multiple passes

**Data produced** (stored on AST nodes):
- `NamedTypeExpression`: `m_constId` (resolved IdentityConstant)
- `NameExpression`: resolved meaning, target component
- Various `m_*` fields for resolved references

**The Revisit Pattern**:
```java
public void resolveNames(StageMgr mgr, ErrorListener errs) {
    if (!dependencyReady()) {
        mgr.requestRevisit();  // "Come back later"
        return;
    }
    // Do actual resolution
}
```

### Stage 4: Validating → Validated

**Purpose**: Type checking, constant evaluation, semantic validation

**What happens**:
- Expressions validated via `validate(Context, TypeConstant, ErrorListener)`
- Statements validated via `validate(Context, ErrorListener)`
- Type inference and checking
- Constant folding
- **Structural transforms**: TraceExpression wrapping, SyntheticExpression

**Data produced** (stored on Expression nodes):
```java
private TypeFit m_fit;      // How well expression fits expected type
private Object m_oType;     // Resolved type(s) - TypeConstant or TypeConstant[]
private Object m_oConst;    // Constant value(s) if compile-time constant
```

**Context object** carries:
- Variable assignments and narrowings
- Type inference state
- Error recovery state

**Context.enter()/exit() pattern**:
The Context uses a stack-based pattern for scope tracking during validation:

```java
ctx = ctx.enter();       // Push new scope
// validate children in nested scope
ctx = ctx.exit();        // Pop scope, promote info to parent
```

This is used for:
- **Variable scoping**: Track which variables are visible at each point
- **Definite assignment analysis**: Ensure variables are assigned before use
- **Type narrowing**: After `if (x instanceof T)`, x is known to be T in the if-block
- **Reachability tracking**: Mark code after `return`/`throw` as unreachable

Specialized contexts exist for different control flow: `enterIf()`, `enterFork(boolean)`,
`enterAnd()`, `enterOr()`, `enterLoop()`, `enterNot()`, etc.

**Implications for AST refactoring**: **NONE**
- Context is about **validation state**, not AST structure
- Context objects are NOT part of the AST tree - they're temporary during validation
- The AST immutability refactoring is orthogonal to Context mechanism
- Context will continue to work the same way regardless of AST mutability

### Stage 5: Emitting → Emitted

**Purpose**: Generate bytecode

**What happens**:
- `generateCode(StageMgr, ErrorListener)` on each node
- Method bodies compiled via `compileMethod(Code, ErrorListener)`
- Bytecode ops generated into `MethodStructure.Code`

**Data produced**:
- Bytecode in `MethodStructure`
- Constants registered in `ConstantPool`

---

## The StageMgr Orchestration Pattern

StageMgr manages the fixpoint iteration:

```java
public boolean processComplete() {
    for (AstNode node : takeRevisitList()) {
        processInternal(node);  // May add back to revisit list
    }
    return m_listRevisit.isEmpty();  // Done when nothing left
}
```

**Key mechanisms**:
- `requestRevisit()` - node can't complete yet, try again later
- `deferChildren()` - don't automatically process children
- `markLastAttempt()` - no more retries, report errors

**Why fixpoint?** Cross-module circular dependencies:
- Module A references type from Module B
- Module B references type from Module A
- Neither can fully resolve until the other does
- Iterate until stable (or max iterations exceeded)

---

## Data Stored on AST Nodes by Stage

| Stage | Data Stored | Location |
|-------|-------------|----------|
| Registered | Component reference | `AstNode.m_component` |
| Resolved | Resolved constants, types | Various `m_*` fields per node type |
| Validated | TypeFit, resolved types, constants | `Expression.m_fit`, `m_oType`, `m_oConst` |
| Validated | Context (transient) | `Statement.m_ctx` |
| Emitted | (bytecode in Component) | `MethodStructure.Code` |

---

## Problems with Current Stage Model

### For Current Use

1. **Nodes are mutable** - no structural sharing possible
2. **Stage per-node** - siblings can be at different stages
3. **Data mixed with syntax** - semantic info on syntax nodes
4. **Fixpoint complexity** - hard to reason about partial states

### For IntelliSense/IDE Support

1. **No incremental reparse** - any change rebuilds everything
2. **No cached results** - can't reuse type info for unchanged code
3. **No partial results** - must complete all stages
4. **Structural transforms break sharing** - TraceExpression wrapping mutates tree

---

## Alternative Stage Architecture (Future)

### Separation of Concerns

```
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│  Syntax Tree    │   │  Symbol Table   │   │  Semantic Model │
│  (immutable)    │──▶│  (Components)   │──▶│  (types, etc.)  │
│  from parser    │   │  from Register  │   │  from Validate  │
└─────────────────┘   └─────────────────┘   └─────────────────┘
         │                                           │
         │         ┌─────────────────┐              │
         └────────▶│  Lowered Tree   │◀─────────────┘
                   │  (with Traces)  │
                   │  for Emit       │
                   └─────────────────┘
```

### Stage Data External to Nodes

```java
// Instead of storing on nodes:
class CompilationContext {
    Map<AstNode, Component> components;      // from Register
    Map<AstNode, TypeConstant> resolvedTypes; // from Resolve
    Map<Expression, TypeFit> typeFits;        // from Validate
    Map<Expression, Constant> constants;      // from Validate
}

// Nodes remain pure syntax
public final class BiExpression extends Expression {
    private final Expression expr1;  // immutable
    private final Token operator;
    private final Expression expr2;
    // NO m_type, m_fit, etc.
}
```

### Benefits

1. **Syntax tree reusable** - unchanged code keeps same nodes
2. **Parallel stage processing** - no mutation conflicts
3. **Incremental updates** - only recompute changed parts
4. **Clear data flow** - each stage produces distinct output
5. **Testable stages** - can test each stage in isolation

---

## Why adopt(), introduceParentage(), and transferTo() Exist

Understanding these patterns is essential for designing the copy-on-write architecture.

### Parent References in AST Nodes

**What it does:**
```java
// introduceParentage() - called after parsing
protected void introduceParentage() {
    forEachChild(node -> {
        node.setParent(this);          // Set parent reference
        node.introduceParentage();      // Recurse
    });
}

// adopt() - shorthand for setting parent on children
protected void adopt(Iterable<? extends AstNode> children) {
    for (AstNode child : children) {
        child.setParent(this);
    }
}
```

**Why it's needed - Navigation up the tree:**
```java
// Finding the enclosing method
AstNode node = this;
while (node != null && !(node instanceof MethodDeclarationStatement)) {
    node = node.getParent();
}

// Getting the containing class's type
Component container = getParent().getComponent();
TypeConstant typeContainer = container.getIdentityConstant().getType();

// Checking context for code generation
if (getParent() instanceof ReturnStatement) {
    // Direct return - can optimize
}
```

**Why parent is reassigned during compilation:**
```java
// SyntheticExpression inserts BETWEEN a node and its parent
public SyntheticExpression(Expression expr) {
    expr.getParent().adopt(this);  // Synthetic becomes child of expr's old parent
    this.adopt(expr);              // expr becomes child of Synthetic
}

// Before: parent -> expr
// After:  parent -> Synthetic -> expr
```

This is **AST mutation** - the tree structure changes during validation. Other examples:
- `TraceExpression` wrapping for debugging
- `TupleExpression` adoption during validation
- `NameExpression` → `NamedTypeExpression` conversion

### Constant Pool Transfers (adoptedBy / transferTo)

**What it does:**
```java
// When a Constant from one module's pool is used in another module
protected Constant adoptedBy(ConstantPool pool) {
    Constant that = (Constant) super.clone();  // Shallow copy
    that.setContaining(pool);                   // Set new pool
    that.resetRefs();                           // Clear derived state
    return that;
}
```

**Why it's needed - Module isolation:**
```java
// Module A defines class Foo
// Module B imports and uses Foo

// Module B's pool needs its OWN reference to Foo's type
// Can't share directly - each pool tracks its own constants

ConstantPool poolA = moduleA.getConstantPool();
ConstantPool poolB = moduleB.getConstantPool();

TypeConstant typeFooInA = poolA.ensureClassType(fooClass);
TypeConstant typeFooInB = typeFooInA.adoptedBy(poolB);  // New constant in poolB

// typeFooInA.getConstantPool() == poolA
// typeFooInB.getConstantPool() == poolB
```

**Why pools are separate:**
1. **Serialization** - Each .xtc file has its own pool
2. **Incremental compilation** - Modify module A without touching module B's pool
3. **Reference counting** - Track which constants are used in each module
4. **Interning** - Each pool interns its own constants

### Component Cloning (cloneBody)

**What it does:**
```java
// For conditional compilation (bifurcation)
protected Component cloneBody() {
    Component that = (Component) super.clone();
    // Deep clone contributions
    that.m_listContribs = deepClone(m_listContribs);
    that.m_sibling = null;     // Clear sibling chain
    that.m_childByName = null; // Clear child map
    return that;
}
```

**Why it's needed - Conditional variants:**
```java
// if (DEBUG) { class Foo { void debug() {...} } }
// else       { class Foo { } }

// Creates TWO versions of Foo:
Component fooDebug = foo.cloneBody();
fooDebug.setCondition(debugCondition);

Component fooRelease = foo.cloneBody();
fooRelease.setCondition(notDebugCondition);
```

### Can Components Be Immutable?

**Current mutations during compilation:**

| Stage | Mutations to Component |
|-------|----------------------|
| Register | Add type parameters, create child structures |
| Resolve | Resolve name references, add contributions |
| Validate | Mark as abstract, set property flags |
| Emit | Generate code, finalize structure |

**The fundamental problem:**
```java
// Components are BUILT incrementally
ClassStructure clz = parent.createClass("Foo", access);  // Basic shell
clz.addTypeParam("T");                                   // Add type param
clz.addContribution(mixin);                               // Add mixin
clz.createMethod("bar", ...);                             // Add method
// ... many more mutations during various stages
```

**Options for immutable Components:**

**Option A: Builder Pattern (Two-Phase)**
```java
ClassStructureBuilder builder = ClassStructure.builder();
builder.addTypeParam("T");
builder.addMethod(...);
ClassStructure clz = builder.build();  // Immutable after build
```

**Pros**: Clear separation of construction vs usage
**Cons**: Requires redesigning all Component creation; still mutable during build

**Option B: Copy-on-Write Components**
```java
ClassStructure clz1 = new ClassStructure("Foo", ...);
ClassStructure clz2 = clz1.withTypeParam("T");     // Returns new ClassStructure
ClassStructure clz3 = clz2.withMethod(method);      // Returns new ClassStructure
```

**Pros**: True immutability, enables structural sharing
**Cons**: Many intermediate objects, need to track "current" version

**Option C: Mutable Construction, Frozen After**
```java
ClassStructure clz = new ClassStructure("Foo", ...);
clz.addTypeParam("T");      // OK during construction
clz.addMethod(method);      // OK during construction
clz.freeze();               // Now immutable

clz.addMethod(other);       // Throws IllegalStateException
```

**Pros**: Minimal API change, clear transition point
**Cons**: Runtime checks, easy to forget freeze()

**Recommendation for Phase 5:**

Start with **Option C (Freeze after construction)** because:
1. Minimal disruption to existing code
2. Clearly identifies where mutations happen
3. Can evolve toward Option B later

The flow would be:
```
Parse → Create mutable Components → Validate → FREEZE → Emit
```

After freeze, any attempt to mutate throws. This catches bugs immediately.

### How adopt() Changes in Copy-on-Write World

**Current (Mutating):**
```java
// Parser creates bottom-up
Expression expr = new BiExpression(left, op, right);
// expr.getParent() == null

// Later, parent is set
parent.adopt(expr);
// expr.getParent() == parent (mutation!)
```

**Copy-on-Write Option A: Parent in Constructor**
```java
// Can't set parent after construction
Expression expr = new BiExpression(parent, left, op, right);
// expr.getParent() == parent from birth
```

**Problem**: When copying, must recreate entire parent chain up to root.

**Copy-on-Write Option B: No Stored Parent (Roslyn Green Tree)**
```java
// Green nodes have no parent
Expression expr = new BiExpression(left, op, right);
// expr.getParent() throws - no parent reference

// Red tree manufactures parent on demand
RedNode red = redRoot.findNode(expr);
AstNode parent = red.getParent();  // Computed, not stored
```

**Problem**: Significant architecture change. Need wrapper layer.

**Copy-on-Write Option C: External Parent Map**
```java
// Parent stored in CompilationContext, not on node
Map<AstNode, AstNode> parents = context.getParentMap();

Expression expr = new BiExpression(left, op, right);
parents.put(left, expr);
parents.put(right, expr);

// Lookup
AstNode parent = context.getParent(expr);
```

**This is the most pragmatic approach for incremental adoption.**

### How transferTo() Changes

**Current (Using clone()):**
```java
Constant adoptedBy(ConstantPool pool) {
    Constant that = (Constant) super.clone();  // Shallow copy
    that.setContaining(pool);
    return that;
}
```

**Explicit transferTo():**
```java
public IntConstant transferTo(ConstantPool pool) {
    if (pool == getConstantPool()) return this;
    return pool.register(new IntConstant(pool, getFormat(), m_value));
}

public ParameterizedTypeConstant transferTo(ConstantPool pool) {
    if (pool == getConstantPool()) return this;
    return pool.register(new ParameterizedTypeConstant(
        pool,
        m_type.transferTo(pool),               // Transfer child
        transferArrayTo(pool, m_params)));     // Transfer array
}
```

**Benefits:**
- Explicit about what gets copied
- No hidden clone() magic
- Each constant knows its own structure
- Can optimize (return `this` if already in pool)

---

## Unusual Patterns in Current Implementation

### 1. The "Current Model as Red-Green" Question

Could the current model be adapted to red-green?

**Conceptual mapping**:
- **Green = Pure syntax from parser** (but currently mutable)
- **Red = AstNode with parent refs** (currently the same object)
- **Semantic Model = m_type, m_fit, etc.** (currently stored on nodes)

**Blockers**:
1. Parent references stored on nodes (prevents sharing)
2. Stage data stored on nodes (prevents sharing)
3. Structural transforms mutate tree (TraceExpression, SyntheticExpression)
4. Fixpoint iteration assumes mutation

### 2. Structural Mutations During Validation

The AST isn't just annotated - it's **transformed**:

```java
// TraceExpression wrapping (Expression.java:1097)
parent.replaceChild(this, exprTrace);

// SyntheticExpression insertion
expr.getParent().adopt(this);
this.adopt(expr);

// TupleExpression adoption
expr0.getParent().adopt(this);
```

**In a red-green world**: These would create new nodes, not mutate existing ones.
The green tree would stay stable; a "lowered" tree would have the transforms.

### 3. The Component Hierarchy (Parallel Structure)

There are TWO tree structures:

1. **AST nodes** (`org.xvm.compiler.ast.*`) - syntax tree from parser
2. **Component structures** (`org.xvm.asm.*`) - semantic/output structure

The Component hierarchy is:
```
FileStructure
  └── ModuleStructure
        └── PackageStructure
              └── ClassStructure
                    ├── MethodStructure
                    └── PropertyStructure
```

AST nodes link to their Component via `getComponent()`.
This is already a form of separation - but the AST nodes still store semantic data.

### 4. Cross-Module Fixpoint (Unlike Roslyn)

Roslyn compiles one assembly at a time with explicit references.
XVM has **circular module dependencies** requiring fixpoint iteration.

```
Module A imports Module B
Module B imports Module A
```

Both must be partially resolved before either can complete.
StageMgr handles this with `requestRevisit()`.

**Implication**: Can't have simple "stage as function" model.
Need either:
- Multiple compilation units coordinated externally
- Or accept fixpoint iteration within a stage

### 5. Different Nodes at Different Stages

Unlike a pure stage-transformer model where the whole tree advances together,
XVM allows nodes to be at different stages:

```java
// One node fully validated, sibling still resolving
node1.getStage() == Stage.Validated
node2.getStage() == Stage.Resolving  // same parent!
```

This is because of the fixpoint model - some nodes complete while others wait.

---

## Proposed Direction

### Phase 1: Replace Reflection (Current Work)

Replace reflection-based child access with explicit implementations.

#### Design Decision: Function vs Consumer

We use `Function<AstNode, T>` rather than `Consumer<AstNode>` for the primitive operation.
This follows TypeScript's `forEachChild` pattern which returns the first truthy value.

**Why this matters:**

1. **Early termination / search** - Many traversals want to find the first match and stop:
   ```java
   // Find first expression that is traceworthy
   Expression found = node.forEachChild(child ->
       child instanceof Expression e && e.isTraceworthy() ? e : null);
   ```

2. **Zero allocation for simple traversal** - Return `null` to continue:
   ```java
   // Visit all children (same efficiency as Consumer)
   node.forEachChild(child -> { doSomething(child); return null; });
   ```

3. **Copy-on-write preparation** - The same pattern enables transformation:
   ```java
   // Transform children - return non-null to replace
   AstNode transformed = node.mapChildren(child ->
       shouldTransform(child) ? transform(child) : child);
   ```

4. **Aggregation patterns** - Find-and-accumulate becomes natural:
   ```java
   // Collect all errors
   List<Error> errors = new ArrayList<>();
   node.forEachChild(child -> {
       if (child.hasError()) errors.add(child.getError());
       return null;  // continue
   });
   ```

**Implementation:**

```java
/**
 * Visit each child of this node. If the visitor returns a non-null value,
 * iteration stops and that value is returned. This enables both traversal
 * (return null to continue) and search (return result to stop).
 *
 * @param visitor  the function to invoke on each child node
 * @param <T>      the result type
 * @return the first non-null result from the visitor, or null if all returned null
 */
public abstract <T> T forEachChild(Function<AstNode, T> visitor);

// Convenience overload for side-effect-only traversal
public void forEachChild(Consumer<AstNode> visitor) {
    forEachChild(child -> { visitor.accept(child); return null; });
}

// For read-only List access (compatibility)
public List<AstNode> children() {
    List<AstNode> list = new ArrayList<>();
    forEachChild((Consumer<AstNode>) list::add);
    return list;
}
```

**Example implementation in a node class:**

```java
@Override
public <T> T forEachChild(Function<AstNode, T> visitor) {
    T result;
    if (condition != null && (result = visitor.apply(condition)) != null) {
        return result;
    }
    if (annotations != null) {
        for (var anno : annotations) {
            if ((result = visitor.apply(anno)) != null) {
                return result;
            }
        }
    }
    if (params != null) {
        for (var param : params) {
            if ((result = visitor.apply(param)) != null) {
                return result;
            }
        }
    }
    return null;
}
```

**Benefits:**
- No intermediate list for simple iteration
- Each class handles its fields explicitly
- Type-safe, no reflection
- Early exit without exceptions
- Foundation for future transformation support
- Single primitive serves both iteration and search use cases

### Phase 1b: VisitResult and Transform (Copy-on-Write Foundation)

Building on `forEachChild`, add a sealed result type that enables copy-on-write transformation:

```java
sealed interface VisitResult<T> permits Continue, Stop, Replace, SkipChildren {
    record Continue<T>() implements VisitResult<T> {}
    record Stop<T>(T value) implements VisitResult<T> {}
    record Replace<T>(AstNode newNode) implements VisitResult<T> {}
    record SkipChildren<T>() implements VisitResult<T> {}
}
```

**Why sealed VisitResult matters:**

1. **Transformation via return values** - No mutation. The framework controls tree reconstruction.
2. **Structural sharing** - `Continue` means "keep this node". Only changed paths get new objects.
3. **Stateless visitors** - Pure functions: `node → VisitResult`. Thread-safe, cacheable, composable.

**The transform method:**

```java
// In AstNode
public AstNode transform(Function<AstNode, VisitResult<?>> visitor) {
    VisitResult<?> result = visitor.apply(this);

    return switch (result) {
        case Replace(var newNode) -> newNode;
        case SkipChildren() -> this;
        case Stop(var _) -> this;
        case Continue() -> {
            // Visit children, collect any replacements
            boolean[] changed = {false};
            List<AstNode> newChildren = new ArrayList<>();
            forEachChild(child -> {
                AstNode transformed = child.transform(visitor);
                newChildren.add(transformed);
                if (transformed != child) changed[0] = true;
            });
            // If any child changed, create new node with new children
            // Otherwise return this (structural sharing!)
            yield changed[0] ? withChildren(newChildren) : this;
        }
    };
}

// Each node implements this to create a copy with new children
protected abstract AstNode withChildren(List<AstNode> children);
```

**How this enables LSP/IntelliSense:**

1. **Edit → New Tree**: User types, you transform the affected subtree. Unchanged nodes are *the same objects*.

2. **Semantic Cache**: `Map<AstNode, TypeConstant>` keyed by node identity. Unchanged nodes = cache hit.

3. **Incremental Recomputation**:
   - Parse only the changed region
   - Transform returns `Continue` for unchanged, `Replace` for changed
   - Only recompute types for new nodes

4. **Time Travel / Undo**: Since transforms return new trees and don't mutate,
   you can keep references to old tree versions. This enables:
   - **Undo/Redo**: Just swap which tree version is "current"
   - **Diff**: Compare old and new trees structurally
   - **Incremental compilation**: Only recompile changed subtrees
   - **Debugging**: Step back through compilation stages
   - **Speculative parsing**: Try a parse, discard if it fails, no cleanup needed

5. **Thread Safety**: Pure functions, no mutation, parallel analysis is safe.

**Relationship to Roslyn Red-Green:**

This is essentially the Roslyn pattern expressed through the visitor API:
- Green tree = the immutable nodes returned from transform
- Red tree = manufactured on-demand with parent refs (could be a thin wrapper)
- Structural sharing = nodes returning `Continue` keep same identity

### Phase 1c: @Derived Annotation for Non-Structural Fields

Currently, fields like `m_label`, `m_ctx`, `m_type` are marked with Java's `transient` keyword.
This is wrong - `transient` is Java serialization baggage and doesn't convey our intent.

**Add a semantic annotation:**

```java
package org.xvm.compiler.ast;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as derived/computed rather than part of the AST structure.
 * These fields are populated during compilation stages and should NOT be
 * copied when creating a new node with different children.
 *
 * Examples: resolved types, jump labels, cached computations, validation context.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Derived {}
```

**Usage:**

```java
// Before (wrong - Java serialization concept)
protected transient Label m_label;

// After (semantic - describes our intent)
@Derived
protected Label m_label;
```

**Benefits:**
- Clear intent: "this field is computed, not structural"
- No dependency on Java serialization semantics
- Can be used for tooling, documentation, or runtime inspection if needed
- Prepares for future where structural fields become `final`

### Phase 2: Unified Copy Architecture

**Important**: We do NOT use Java's `Cloneable` interface or `clone()` method.
That API is fundamentally broken (see Effective Java, Item 13).

#### 2.1: The Copyable Interface

All three major hierarchies (AstNode, Constant, Component) share a common copying pattern:

```java
/**
 * Interface for structures that support explicit copying.
 *
 * Two types of copy operations exist:
 * 1. copy() - Structural copy within same context (AST deep copy, Constant in same pool)
 * 2. transferTo() - Pool transfer for Constants (replaces adoptedBy())
 *
 * Implementations must:
 * 1. Only copy STRUCTURAL fields (not @Derived)
 * 2. Return a new instance (or 'this' for immutable objects)
 * 3. Handle children explicitly, not via reflection
 */
public interface Copyable<T> {
    /**
     * Create a structural copy. @Derived fields are NOT copied.
     * For immutable objects, may return 'this'.
     */
    T copy();
}

/**
 * Extended interface for Constants that can transfer between pools.
 * This replaces the old adoptedBy() pattern.
 */
public interface PoolTransferable<T extends Constant> extends Copyable<T> {
    /**
     * Transfer to a different ConstantPool.
     * Returns 'this' if already in target pool.
     * Returns new instance registered in target pool otherwise.
     *
     * @param pool the target pool
     * @return constant belonging to target pool
     */
    T transferTo(ConstantPool pool);

    /**
     * Check if this constant can be shared with the given pool
     * without requiring a transfer (copy).
     */
    default boolean isSharedWith(ConstantPool pool) {
        return getConstantPool() == pool;
    }
}
```

**Why separate copy() from transferTo()?**

| Operation | copy() | transferTo(pool) |
|-----------|--------|------------------|
| **Use case** | Deep clone for transformation | Cross-module constant reference |
| **Children** | Recursively copied | Recursively transferred to pool |
| **Pool** | Same as original | Target pool |
| **Caches** | Not copied (@Derived) | Not copied (recomputed in new pool) |
| **When used** | AST copy-on-write | Module linking, ConstantPool.register() |

#### 2.2: AstNode Copying

```java
public abstract class AstNode implements Copyable<AstNode> {

    @Override
    public final AstNode copy() {
        // Deep copy using withChildren - each child is recursively copied
        List<AstNode> copiedChildren = new ArrayList<>();
        forEachChild(child -> { copiedChildren.add(child.copy()); return null; });
        return withChildren(copiedChildren);
    }

    // Each class implements withChildren to create a new instance
    protected abstract AstNode withChildren(List<AstNode> children);
}

// Example implementation
@Override
protected BiExpression withChildren(List<AstNode> children) {
    return new BiExpression(
        (Expression) children.get(0),
        operator,  // Token is immutable, shared
        (Expression) children.get(1)
    );
}
```

#### 2.3: Constant Copying (with Pool Transfer)

```java
public abstract class Constant implements PoolTransferable<Constant> {

    // Simple copy (same pool) - for immutable constants, returns 'this'
    @Override
    public Constant copy() {
        return this;  // Constants are value-immutable, safe to share
    }

    /**
     * Transfer to a different pool. This replaces adoptedBy().
     * Each subclass implements explicitly - no reflection.
     */
    @Override
    public abstract Constant transferTo(ConstantPool pool);

    @Override
    public boolean isSharedWith(ConstantPool pool) {
        return getConstantPool() == pool || isShared(pool);
    }
}

// Example: Simple constant
@Override
public IntConstant transferTo(ConstantPool pool) {
    return pool == getConstantPool()
        ? this
        : pool.register(new IntConstant(pool, getFormat(), m_pint));
}

// Example: Composite constant with children
@Override
public ParameterizedTypeConstant transferTo(ConstantPool pool) {
    if (pool == getConstantPool()) return this;

    // Transfer children first
    TypeConstant   typeTransferred   = m_constType.transferTo(pool);
    TypeConstant[] paramsTransferred = transferArrayTo(pool, m_atypeParams);

    return pool.register(new ParameterizedTypeConstant(pool, typeTransferred, paramsTransferred));
}

// Helper for array transfer
protected static TypeConstant[] transferArrayTo(ConstantPool pool, TypeConstant[] atype) {
    if (atype == null || atype.length == 0) return atype;
    TypeConstant[] result = new TypeConstant[atype.length];
    for (int i = 0; i < atype.length; i++) {
        result[i] = atype[i].transferTo(pool);
    }
    return result;
}
```

#### 2.4: Component Copying

```java
public abstract class Component implements Copyable<Component> {

    @Override
    public abstract Component copy();

    /**
     * Copy for bifurcation (conditional compilation).
     * Creates a variant with a specific condition.
     */
    public abstract Component copyForCondition(ConditionalConstant condition);
}
```

#### 2.5: The @Derived Contract

All three hierarchies use the same annotation to mark non-structural fields:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Derived {
    // Fields marked @Derived are:
    // 1. NOT copied by copy()/copyTo()/withChildren()
    // 2. Recomputed lazily after copy
    // 3. Can use Lazy<T> for final + lazy pattern
}
```

| Hierarchy | Structural Fields | @Derived Fields |
|-----------|------------------|-----------------|
| AstNode | children, tokens, source positions | m_stage, m_ctx, m_label, resolved types |
| Constant | value data, child constants | m_iPos, m_cRefs, m_oValue, TypeInfo cache |
| Component | structure, children, names | cache maps, format state |

**Why withChildren() instead of copyImpl():**
- Same method serves both `copy()` and `transform()`
- Enables structural sharing in transforms
- Single implementation point per class
- Prepares for immutable nodes

### Phase 3: Transformation Support

Add transformation method alongside iteration:

```java
// Returns new node if any child changed, else 'this'
public AstNode transformChildren(UnaryOperator<AstNode> transformer) {
    // Each class implements - checks if children changed
    // If yes, creates new node with transformed children
    // If no, returns this
}
```

This prepares for copy-on-write without requiring it immediately.

### Phase 4: Copy-on-Write (Future)

Eventually move to immutable nodes with structural sharing:

```java
// All fields final
public final class BiExpression extends Expression {
    private final Expression expr1;
    private final Token operator;
    private final Expression expr2;

    // "With" methods for modification
    public BiExpression withExpr1(Expression newExpr1) {
        return expr1 == newExpr1 ? this
            : new BiExpression(newExpr1, operator, expr2);
    }
}
```

**Time Travel and Persistence Benefits:**

Copy-on-write with structural sharing is fundamentally a **persistent data structure** pattern.
This unlocks powerful capabilities:

1. **Undo/Redo Stack**: Each edit creates a new tree. Keep a list of tree roots.
   Undo = pop to previous root. No "undo logic" needed - it's just pointer swapping.

2. **Branching Edits**: Fork a tree, try multiple transformations in parallel,
   pick the best result. Failed attempts are garbage collected automatically.

3. **Incremental Recompilation**: When user edits line 50, only nodes on the
   path from root to line 50 are recreated. Nodes for lines 1-49 and 51+ are
   shared with the old tree. Semantic analysis can skip unchanged subtrees.

4. **Debugger Time Travel**: Store tree snapshots at each compilation stage.
   User can "step back" to see the AST before a transform was applied.

5. **Speculative Parsing**: Try parsing an ambiguous construct one way.
   If it fails, discard the new tree (GC handles cleanup) and try another way.
   No explicit "rollback" code needed.

6. **Diff/Delta Compression**: Two trees that share structure can be diffed
   efficiently - only walk paths where nodes differ.

**Considerations:**
- Stage information would need to move outside nodes (compilation context)
- Parent references would be manufactured on-demand (like Roslyn red tree)
- Significant refactoring of stage processing


### Phase 5: Component and Constant Hierarchy Refactoring

The `org.xvm.asm` package also uses Java's `Cloneable`/`clone()` pattern extensively.
This must be addressed to achieve the goals of incremental compilation and LSP support.

#### 5.1: Understanding the Current Clone Usage

**Who clones constants and why?**

1. **`adoptedBy(ConstantPool)`** - The primary use case:
   - When a constant from one module's pool is referenced in another module
   - Creates a shallow copy with the new pool as its container
   - Called from `ConstantPool.register()` when `constant.getContaining() != this`

2. **Defensive array cloning** - When modifying arrays that might be shared:
   - `TypeConstant[].clone()` in many places
   - `Parameter[].clone()` in MethodStructure
   - `PropertyBody[].clone()` in PropertyInfo
   - Pattern: clone-on-modify to avoid corrupting shared state

3. **Component bifurcation** - For conditional compilation:
   - `ComponentBifurcator` clones components to create conditional variants
   - Each condition gets its own component tree

**Affected classes implementing `Cloneable`:**

| Class | Clone Purpose | Transient Fields |
|-------|---------------|------------------|
| `Constant` | `adoptedBy()` for pool transfer | `m_iPos`, `m_cRefs`, `m_oValue` |
| `Component` | Bifurcation, conditional compilation | Format/cache fields |
| `Component.Contribution` | Component cloning | None |
| `Parameter` (asm) | Method cloning | None |
| `MethodStructure.Source` | Method cloning | None |

**TypeConstant and subclasses** - Many transient fields for caching:
```java
private transient boolean m_fValidated;
private transient volatile TypeInfo m_typeinfo;
private transient volatile int m_cInvalidations;
private transient volatile Map<TypeConstant, Relation> m_mapRelations;
private transient Map<String, Usage> m_mapConsumes;
private transient Map<String, Usage> m_mapProduces;
private transient TypeConstant m_typeNormalized;
// ... more
```

#### 5.2: The Problem with Current Design

**State juggling everywhere:**
- Constants appear immutable but have mutable cached state
- `transient` fields create invisible dependencies
- Clone creates new objects but cached state is lost/wrong
- No clear contract for what clone does vs doesn't copy

**Thread-safety issues:**
- `volatile` fields indicate concurrent access concerns
- Clone doesn't preserve happens-before relationships
- Cached TypeInfo can be stale after clone

**For LSP/Incremental compilation:**
- Can't share constant pools between compilation units
- Type resolution is tied to specific pool instance
- No way to incrementally update types

#### 5.3: Proposed Solution

**A. Replace `@Derived` pattern in asm package:**

Create `@Derived` annotation in `org.xvm.asm` (or share from ast package):

```java
// In Constant.java - replace transient with @Derived
@Derived private int m_iPos = -1;
@Derived private int m_cRefs;
@Derived private Object m_oValue;
```

```java
// In TypeConstant.java
@Derived private boolean m_fValidated;
@Derived private volatile TypeInfo m_typeinfo;
@Derived private volatile Map<TypeConstant, Relation> m_mapRelations;
// ... etc
```

**B. Replace `adoptedBy()` with explicit `copyTo()`:**

```java
// In Constant.java
/**
 * Create a copy of this constant for a different pool.
 * Only copies structural data - derived/cached fields are NOT copied.
 */
public Constant copyTo(ConstantPool pool) {
    // Each subclass implements this explicitly
    throw new UnsupportedOperationException(getClass().getName());
}

// Example in IntConstant
@Override
public IntConstant copyTo(ConstantPool pool) {
    return new IntConstant(pool, getFormat(), m_pint);
}

// Example in ParameterizedTypeConstant
@Override
public ParameterizedTypeConstant copyTo(ConstantPool pool) {
    TypeConstant   typeCopied   = m_constType.copyTo(pool);
    TypeConstant[] paramsCopied = copyArrayTo(pool, m_atypeParams);
    return new ParameterizedTypeConstant(pool, typeCopied, paramsCopied);
}

// Helper for array copying
protected static TypeConstant[] copyArrayTo(ConstantPool pool, TypeConstant[] atype) {
    TypeConstant[] result = new TypeConstant[atype.length];
    for (int i = 0; i < atype.length; i++) {
        result[i] = atype[i].copyTo(pool);
    }
    return result;
}
```

**C. Remove `Cloneable` interface:**

```java
// Before
public abstract class Constant
        extends XvmStructure
        implements Comparable<Constant>, Cloneable, Argument {

// After
public abstract class Constant
        extends XvmStructure
        implements Comparable<Constant>, Argument {
```

**D. Update `ConstantPool.register()`:**

```java
// Before
if (constant.getContaining() != this) {
    constant = (T) constant.adoptedBy(this);
}

// After
if (constant.getContaining() != this) {
    constant = (T) constant.copyTo(this);
}
```

#### 5.4: Migration Strategy for Constants

**Phase 5a: Add `@Derived` annotation usage**
- Tag all `transient` fields with `@Derived`
- Keep `transient` temporarily for compatibility
- Document which fields are derived

**Phase 5b: Implement `copyTo()` on leaf classes first**
- Start with simple constants: IntConstant, StringConstant, etc.
- These have no child constants

**Phase 5c: Implement `copyTo()` on composite constants**
- TypeConstant subclasses
- SignatureConstant
- MethodConstant, PropertyConstant

**Phase 5d: Update callers and remove `adoptedBy()`**
- Update ConstantPool.register()
- Remove Cloneable interface
- Remove old clone() overrides

**Phase 5e: Component hierarchy**
- Apply same pattern to Component
- Replace bifurcation clone with explicit copy

#### 5.5: Relationship Between AST and Constants

The AST and ConstantPool are tightly coupled:

```
AST Node (e.g., NamedTypeExpression)
    └── resolves to ──→ IdentityConstant (in pool)
                              └── has ──→ TypeConstant
                                            └── caches ──→ TypeInfo
```

**For LSP/Incremental to work:**
1. AST nodes must be copy-on-write (Phase 1-4)
2. Constants must support pool transfer via `copyTo()` (Phase 5)
3. Semantic caches (TypeInfo) must be keyed externally, not on the constant

**Future consideration:** Move TypeInfo and semantic caches to an external
`CompilationContext` rather than storing on TypeConstant. This enables:
- Sharing types across compilation units
- Invalidating only affected type info on edits
- Parallel analysis without lock contention

### Phase 5.5: TypeInfo Performance and Immutability

**The Problem:** TypeInfo is a major thread contention bottleneck during compilation.

#### 5.5.1: Current Issues (Investigation Findings)

**Synchronization Bottlenecks:**
1. `TypeConstant.ensureTypeInfo()` - synchronized method holds lock across entire build
2. The deferred TypeInfo building loop (multiple recursive calls) under single lock
3. `TypeInfo.getOpMethodInfos()`, `findConversion()` - synchronized methods
4. `ensureCaches()` synchronizes on shared ConcurrentHashMap

**Mutable State After Construction:**
```java
// TypeInfo fields mutated after construction:
private boolean m_fImplicitAbstract;           // set in ensureCaches()
private boolean m_fCacheReady;                 // set in ensureCaches()
private transient Map<String, PropertyInfo> m_mapPropertiesByName;  // lazy
private transient Map<SignatureConstant, MethodInfo> m_mapMethodsBySignature;
private transient TypeInfo m_into, m_delegates;  // lazy variants
private transient Set<MethodInfo> m_setAuto, m_setOps;
private transient volatile Map<String, Set<MethodConstant>> m_mapOps;
```

**Memory Churn:**
- `limitAccess()`, `asInto()`, `asDelegates()` create full copies of all maps
- No structural sharing between TypeInfo variants for same type
- Each variant has own f_mapProps, f_mapMethods (duplicated data)

**Redundant Computation:**
- `selectVisible()` walks class/interface chains for EACH conflicting property
- `getMethodBySignature()` iterates ALL methods for complex matching
- Triple signature comparison per method in lookup

#### 5.5.2: Proposed Improvements

**A. True Immutability for TypeInfo Core:**

```java
@Immutable
public final class TypeInfo {
    // ALL structural data computed in constructor
    private final Map<PropertyConstant, PropertyInfo> f_mapProps;
    private final Map<MethodConstant, MethodInfo> f_mapMethods;
    private final List<Map<...>> f_listmapClassChain;
    // NO mutable fields - caches moved to external TypeInfoCache
}
```

**B. External TypeInfoCache:**

```java
/**
 * Separate cache storage, keyed by (TypeConstant, AccessLevel, Variant).
 * Lives on CompilationContext, not on TypeConstant.
 */
public class TypeInfoCache {
    // ConcurrentHashMap with computeIfAbsent for lock-free lazy init
    private final ConcurrentHashMap<CacheKey, TypeInfo> cache;

    // Variant caches (public, private, into, delegates) share base data
    private final ConcurrentHashMap<TypeInfo, TypeInfo> intoCache;
    private final ConcurrentHashMap<TypeInfo, TypeInfo> delegatesCache;

    // Lookup caches (not stored on TypeInfo)
    private final ConcurrentHashMap<TypeInfo, Map<String, PropertyInfo>> propsByName;
    private final ConcurrentHashMap<TypeInfo, Map<SignatureConstant, MethodInfo>> methodsBySig;
}
```

**C. Structural Sharing for Variants:**

```java
public final class TypeInfo {
    // Base TypeInfo that variants share
    private final TypeInfo f_base;  // null for full TypeInfo, non-null for variants

    // Variant-specific filtered views (computed, not copied)
    private final Predicate<PropertyInfo> f_propFilter;  // e.g., access level filter
    private final Predicate<MethodInfo> f_methodFilter;

    // Methods use filter if present
    public Map<PropertyConstant, PropertyInfo> getProperties() {
        if (f_base == null) return f_mapProps;
        return f_base.f_mapProps.entrySet().stream()
            .filter(e -> f_propFilter.test(e.getValue()))
            .collect(...);  // cached in external cache
    }
}
```

**D. Lock-Free Building Pattern:**

```java
// In TypeConstant - replace synchronized ensureTypeInfo()
public TypeInfo ensureTypeInfo() {
    TypeInfo info = m_typeinfo;  // volatile read
    if (info != null && info.isUpToDate()) {
        return info;
    }

    // Use CompilationContext cache with computeIfAbsent
    CompilationContext ctx = getCompilationContext();
    return ctx.getTypeInfoCache().computeIfAbsent(this, TypeInfo::build);
}
```

**E. Incremental TypeInfo for LSP:**

For partial compilation, TypeInfo can be:
1. **Lazily computed** - Only build what's needed for current operation
2. **Incrementally updated** - Change only affected parts on edit
3. **Externally invalidated** - CompilationContext tracks what needs rebuild

```java
public class IncrementalTypeInfo {
    private final TypeConstant type;
    private final CompilationContext ctx;

    // Lazy property lookup - only computes what's requested
    public PropertyInfo getProperty(String name) {
        return ctx.getPropertyCache().computeIfAbsent(
            new PropKey(type, name),
            k -> computeProperty(k.name())
        );
    }
}
```

#### 5.5.3: Migration Steps

1. [ ] Create TypeInfoCache class in org.xvm.asm.constants
2. [ ] Move lazy caches from TypeInfo fields to TypeInfoCache
3. [ ] Add TypeInfoCache to ConstantPool or CompilationContext
4. [ ] Make TypeInfo fields truly final (compute all in constructor)
5. [ ] Implement structural sharing for TypeInfo variants
6. [ ] Replace synchronized methods with ConcurrentHashMap.computeIfAbsent
7. [ ] Benchmark: measure lock contention reduction
8. [ ] Add IncrementalTypeInfo for LSP use case

#### 5.5.4: Expected Benefits

| Improvement | Standalone Compiler | LSP/IDE |
|-------------|---------------------|---------|
| Lock-free caching | 2-4x faster parallel compilation | Responsive type queries |
| Structural sharing | 30-50% less memory for variants | Faster access checks |
| External cache | Easier cache invalidation | Incremental rebuilds |
| True immutability | Safe sharing, no defensive copies | Concurrent edits |

### Phase 6: Array to List Migration

#### 6.1: Investigation Results - Bridge/Runtime Array Requirements

**Arrays ARE required at the runtime/bridge boundary:**
- `Parameter[] getParamArray()` - Used by `xRTMethodTemplate` for indexed access
- `Parameter[] getReturnArray()` - Used for method return type handling
- `Annotation[] getAnnotations()` - Used by reflection APIs
- `TypeConstant[] getRawParams()` / `getRawReturns()` in `SignatureConstant`

**Why arrays at the boundary:**
1. **Indexed access protocol**: Bridge code uses `getParameter(Int index)` pattern
2. **Native interop**: C/Java interop requires fixed-size arrays
3. **Serialization**: Binary format naturally uses arrays
4. **Performance**: Direct indexed access without wrapper overhead

**Key conversion points:**
- `Utils.CreateParameters` (runtime/Utils.java:1474) - converts `Parameter[]` to handles
- `xRTFunction.java` - binds parameters to function calls
- `xRTType.java` - constructor parameter handling

#### 6.2: Migration Strategy

**Keep arrays at boundary, use Lists internally:**

```java
// BEFORE: Internal storage as array
private Annotation[] m_aAnnotations;

public Annotation[] getAnnotations() {
    return m_aAnnotations;  // Exposes mutable array!
}

// AFTER: Internal storage as List, array conversion at boundary
private final List<Annotation> annotations;  // immutable list

public List<Annotation> getAnnotations() {
    return annotations;  // Already immutable
}

// Bridge method - only for runtime/bridge callers
@Deprecated  // TODO: Remove once bridge is updated
public Annotation[] getAnnotationsAsArray() {
    return annotations.toArray(Annotation[]::new);
}
```

**Deprecation approach:**
1. Add new List-based API
2. Mark array API as `@Deprecated`
3. Update internal callers to use List API
4. Bridge/runtime callers can continue using array API
5. Eventually update bridge to use List API or keep thin wrapper

#### 6.3: Specific Migrations

| Current Array API | New List API | Bridge Compatibility |
|-------------------|--------------|----------------------|
| `getParamArray()` | `getParams()` | Keep `getParamArray()` deprecated |
| `getReturnArray()` | `getReturns()` | Keep `getReturnArray()` deprecated |
| `getAnnotations()` (array) | `annotations()` | Keep old as deprecated |
| `TypeConstant[]` params | `List<TypeConstant>` | Conversion at boundary |

---

### Phase 7: Null Safety and Defensive Coding

#### 7.1: The Problem with Null

Null is used with at least 5 different meanings in the codebase:
1. **"Not yet initialized"** - Field will be set later
2. **"Empty/absent"** - No value, same as empty collection
3. **"Unknown"** - Value hasn't been computed yet
4. **"Optional"** - May or may not have a value
5. **"Error state"** - Something went wrong

**Examples of confusion:**
```java
// Does null mean "no annotations" or "annotations not yet computed"?
private Annotation[] m_aAnnotations;

// Null and empty treated inconsistently
if (list == null || list.isEmpty()) { ... }  // Sometimes
if (list != null && !list.isEmpty()) { ... } // Other times
```

#### 7.2: Annotation Strategy

Add JetBrains annotations (already used in some files):

```java
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

// Constructor parameters - mark non-nullable
public NameExpression(
        @Nullable Expression left,      // Can be null (no prefix)
        @NotNull Token name,            // Required - never null
        @Nullable List<TypeExpression> params) {  // Optional
    this.left = left;
    this.name = Objects.requireNonNull(name);
    this.params = params == null ? List.of() : List.copyOf(params);
}

// Return values - mark nullability
public @Nullable Expression getLeft() { return left; }
public @NotNull Token getName() { return name; }
public @NotNull @Unmodifiable List<TypeExpression> getParams() { return params; }
```

#### 7.3: Never Null Collections Pattern

**BEFORE (antipattern):**
```java
List<Error> listErrors = null;
for (Node child : children) {
    if (child.hasError()) {
        if (listErrors == null) {
            listErrors = new ArrayList<>();  // Lazy init mid-loop
        }
        listErrors.add(child.getError());
    }
}
if (listErrors != null) { ... }  // Null check hell
```

**AFTER (clean):**
```java
List<Error> errors = new ArrayList<>();  // Always start with empty
for (Node child : children) {
    if (child.hasError()) {
        errors.add(child.getError());
    }
}
if (!errors.isEmpty()) { ... }  // Simple isEmpty check
// Or even better with streams:
List<Error> errors = children.stream()
    .filter(Node::hasError)
    .map(Node::getError)
    .toList();
```

#### 7.4: Common Antipatterns Found

**Lazy list initialization in loops** (~35 occurrences found):
- `PropertyStructure.java:296` - `List<Annotation> listPropAnno = null`
- `ClassStructure.java:205` - `List<Annotation> listAnnos = null`
- `MethodInfo.java:179,250,326,369` - Multiple `ArrayList<MethodBody> listMerge = null`
- `PropertyInfo.java:142,372` - Similar patterns
- `TypeConstant.java:2122,2701,4317` - Various list patterns

**Files needing cleanup:**
- `PropertyStructure.java` - 5 occurrences
- `ClassStructure.java` - 4 occurrences
- `MethodInfo.java` - 6 occurrences
- `TypeConstant.java` - 3 occurrences
- `Parser.java` - 2 occurrences

#### 7.5: Immutable Collection Returns

**BEFORE (dangerous):**
```java
public List<Parameter> getParams() {
    return m_listParams;  // Caller can modify!
}
```

**AFTER (safe):**
```java
public @NotNull @Unmodifiable List<Parameter> getParams() {
    return Collections.unmodifiableList(m_listParams);
    // Or better: store as immutable from the start
}
```

**Best practice: Store immutable, return immutable:**
```java
private final List<Parameter> params;  // Stored immutable

public Constructor(..., List<Parameter> params) {
    this.params = params == null ? List.of() : List.copyOf(params);
}

public @NotNull @Unmodifiable List<Parameter> getParams() {
    return params;  // Already immutable, no wrapping needed
}
```

---

### Phase 8: Generic Types and Raw Type Elimination

#### 8.1: The Cardinal Sins of Type Erasure

The codebase has several patterns that destroy type safety:

**Sin #1: Double-cast type erasure**
```java
// Found in Parser.java:1572, TypeCompositionStatement.java:933,1290
(List<Statement>) (List) init  // DESTROYS type information!
(List<Component>) (List) componentList  // Same antipattern

// This compiles but is type-unsafe. The intermediate (List) erases
// generic type, allowing any List to be cast to any other List.
```

**Sin #2: Raw Iterator cast in ListMap**
```java
// ListMap.java:127 - inside entrySet()
return (Iterator) m_list.iterator();  // Raw cast!
// Loses type: Iterator<SimpleEntry<K,V>> → Iterator → Iterator<Entry<K,V>>
```

**Sin #3: Raw list cast in ListMap.asList()**
```java
// ListMap.java:73
List<Entry<K,V>> list = (List) m_list;  // Raw cast!
```

**These patterns exist because the authors didn't parameterize properly.**

#### 8.2: LinkedIterator - A Misguided Utility

`LinkedIterator` combines multiple iterators into one. The problem is how it's used:

```java
// FileStructure.java:852 - loses type information
return new LinkedIterator(
    Collections.singleton(m_pool).iterator(),  // Iterator<ConstantPool>
    children().iterator());                     // Iterator<Component>
// Return type: Iterator<? extends XvmStructure>

// ClassStructure.java:3357 - same pattern
return new LinkedIterator(
    super.getContained(),      // Iterator<? extends XvmStructure>
    listAnno.iterator());      // Iterator<Annotation>
```

**The Real Problem:** These uses combine iterators of DIFFERENT types!
- If types were properly constrained, you couldn't do this
- The solution: DON'T combine heterogeneous iterators
- Use proper collection concatenation or explicit type handling

**Better Approach: Just use List concatenation**
```java
// BEFORE: LinkedIterator destroys type info
return new LinkedIterator(iter1, iter2);

// AFTER: Proper list concatenation (when types match)
List<XvmStructure> result = new ArrayList<>(collection1);
result.addAll(collection2);
return result.iterator();

// Or with streams:
return Stream.concat(stream1, stream2).iterator();
```

**When types don't match, it's a design smell** - the caller should handle
heterogeneous types explicitly, not hide it behind a raw iterator.

#### 8.3: ListMap - Should Be Deprecated

`ListMap` is a custom ordered map used **208 times** in the codebase.

**What it does:**
- Maintains insertion order (like `LinkedHashMap`)
- O(n) lookup (linear search through ArrayList)
- Has `asList()` and `entryAt()` methods for indexed access

**Why it's problematic:**
1. **O(n) lookup vs O(1)**: `LinkedHashMap` is O(1) for lookup
2. **Raw type usage**: `ListMap.EMPTY` is raw `ListMap` (no generics)
3. **Custom API**: `asList()`, `entryAt()` create API lock-in

**Bridge/Runtime dependency check:**
- `javatools_bridge` has 2 files mentioning ListMap but in `.x` files (Ecstasy)
- **ListMap is NOT used by native bridge code** - it's purely internal Java

**Memory comparison (per entry):**
| Structure | Memory Overhead |
|-----------|----------------|
| ListMap (ArrayList<SimpleEntry>) | ~32 bytes/entry (ArrayList + SimpleEntry objects) |
| LinkedHashMap | ~48 bytes/entry (Entry + before/after links) |

LinkedHashMap uses ~50% more memory per entry, but:
- Provides O(1) vs O(n) lookup
- For maps with >10 entries, time savings vastly outweigh memory cost
- Most ListMaps in the code are small, so memory difference is negligible

**Migration Strategy:**
1. Create `OrderedMap<K,V>` interface with `asList()` and `entryAt()` methods
2. Implement `OrderedMap` using `LinkedHashMap` with index access via `List.copyOf(entrySet())`
3. Deprecate `ListMap`
4. Migrate 208 usages over time

```java
// New interface
public interface OrderedMap<K, V> extends Map<K, V> {
    List<Entry<K, V>> asList();  // Returns unmodifiable list
    Entry<K, V> entryAt(int index);
}

// Implementation (thin wrapper)
public class LinkedOrderedMap<K, V>
        extends LinkedHashMap<K, V>
        implements OrderedMap<K, V> {

    @Override
    public List<Entry<K, V>> asList() {
        return List.copyOf(entrySet());
    }

    @Override
    public Entry<K, V> entryAt(int index) {
        // For small maps, linear is fine. For large, cache the list.
        return asList().get(index);
    }
}
```

#### 8.4: Raw Type Usage Patterns Found

**Files with `@SuppressWarnings("unchecked")`:**
- `AstNode.java:263` - Generic child handling
- `LambdaExpression.java:87` - Type casting
- `ListMap.java:35` - EMPTY_ARRAY_LIST cast
- `LinkedIterator.java:28-30` - Empty array cast

**Raw type casts (`(List)`, `(Map)`, `(Iterator)`):**
- `ListMap.java:73,127` - Internal raw casts
- `Parser.java:1572` - ForStatement init list
- `TypeCompositionStatement.java:933,1290` - Component lists
- `NativeContainer.java:494` - System.getProperties() cast
- `AstNode.java:2086` - Iterator cast in child iteration

#### 8.5: Generic Improvements

```java
// BEFORE: Casting everywhere
Object result = map.get(key);
if (result instanceof TypeConstant) {
    TypeConstant type = (TypeConstant) result;
}

// AFTER: Proper generics
Map<String, TypeConstant> typeMap = new HashMap<>();
TypeConstant type = typeMap.get(key);  // No cast needed
```

#### 8.6: Helper Method Generics

Many helper methods can be made generic for better type safety:

```java
// BEFORE
protected boolean tryReplaceInList(AstNode oldChild, AstNode newChild, List list) {
    // Unsafe casts inside
}

// AFTER
protected <T extends AstNode> boolean tryReplaceInList(
        T oldChild, T newChild, List<T> list) {
    // Type-safe
}
```

#### 8.7: Object as Generic Escape Hatch

**The `Map<Object, ...>` antipattern is used extensively:**

```java
// TypeInfo.java - fields typed as Object
Map<Object, PropertyInfo> f_mapVirtProps;   // keyed by "nested id"
Map<Object, MethodInfo>   f_mapVirtMethods; // keyed by "nested id"
Map<Object, ParamInfo>    f_mapTypeParams;  // keyed by String OR NestedIdentity
```

**What is a "nested identity"?**
The key can be one of:
- `String` - property name for directly nested properties
- `SignatureConstant` - method signature for directly nested methods
- `NestedIdentity` - inner class of IdentityConstant for deeply nested members

**Why Object was used:**
```java
// This is the pattern throughout TypeConstant.java
Object nid = id.resolveNestedIdentity(pool, resolver);
mapVirtProps.put(nid, propInfo);  // nid could be String OR NestedIdentity
```

**The Proper Solution: A common interface**
```java
// Define an interface for all things that can be nested identity keys
public sealed interface NestedId
        permits PropertyNestedId, MethodNestedId, DeepNestedId {
    int hashCode();
    boolean equals(Object obj);
}

// Wrapper for String property names
record PropertyNestedId(String name) implements NestedId {}

// Wrapper for method signatures
record MethodNestedId(SignatureConstant sig) implements NestedId {}

// The existing NestedIdentity refactored
record DeepNestedId(IdentityConstant id, GenericTypeResolver resolver)
        implements NestedId {}

// Then maps become properly typed:
Map<NestedId, PropertyInfo> mapVirtProps;
Map<NestedId, MethodInfo>   mapVirtMethods;
```

**Files with `Map<Object, ...>` patterns:**
- `TypeInfo.java` - f_mapVirtProps, f_mapVirtMethods, f_mapTypeParams, f_cacheByNid
- `TypeConstant.java` - mapVirtProps, mapVirtMethods throughout layerOn* methods
- `ConstantPool.java` - m_mapLocators (Map<Object, Constant>)
- `PropertyClassTypeConstant.java` - same patterns
- `ClassStructure.java` - mapFields (Map<Object, FieldInfo>)

#### 8.8: Mutable HashMap Keys - A Java Contract Violation

**The Problem:**

`NestedIdentity` is used as a HashMap key but is **mutable**:

```java
public class NestedIdentity {
    private final GenericTypeResolver m_resolver;  // FINAL but...

    // ... the resolver can be a TypeConstant which IS mutable!

    @Override
    public int hashCode() {
        // Uses m_resolver.resolve() which can return different values
        // if the resolver's state changes!
    }

    public boolean isCacheable() {
        return m_resolver == null || m_resolver instanceof TypeConstant;
    }
}
```

**Java HashMap Contract Violation:**
> "If an object's hashCode changes while it's a key in a HashMap,
> the entry becomes unreachable even though it's still there."

**Current workaround:** The `isCacheable()` check, but it's not enforced.

**Constant's Lazy HashCode:**
```java
// Constant.java
private int m_iHash;  // Lazy-computed, cached

public int hashCode() {
    int iHash = m_iHash;
    if (iHash == 0) {
        iHash = computeHashCode();  // Expensive computation
        m_iHash = iHash;
    }
    return iHash;
}
```

This is **safe** because:
1. hashCode never changes after first computation
2. Constants are structurally immutable (content doesn't change)

But NestedIdentity's resolver CAN affect the hash, making it **unsafe** as a key
when the resolver is mutable.

**Recommended Fix:**
1. Make NestedIdentity a record (immutable by design)
2. Pre-resolve the identity at construction time
3. Store only the resolved values, not the resolver
4. Use the `NestedId` sealed interface approach above

---

### Phase 9: Final Fields and Immutability Preparation

#### 9.1: The Lazy Utility

A new `Lazy<T>` utility class has been added to `org.xvm.util` to enable final fields
for lazily-computed values. This replaces the "null-check in getter" pattern:

```java
// BEFORE: Mutable field with null-check (can't be final)
private TypeInfo m_typeInfo;

public TypeInfo getTypeInfo() {
    if (m_typeInfo == null) {
        m_typeInfo = computeTypeInfo();  // Reassignment prevents final
    }
    return m_typeInfo;
}

// AFTER: Final field with Lazy
private final Lazy<TypeInfo> typeInfo = Lazy.of(this::computeTypeInfo);

public TypeInfo getTypeInfo() {
    return typeInfo.get();
}
```

**Lazy<T> Features:**
- `Lazy.of(supplier)` - Thread-safe memoization (double-checked locking)
- `Lazy.ofUnsafe(supplier)` - Non-thread-safe for single-threaded contexts
- `Lazy.ofValue(value)` - Pre-initialized (for testing/API compatibility)
- `isInitialized()` - Check if value has been computed

**Benefits:**
1. Field can be `final` - enables true immutability
2. Thread-safe without synchronization at every call site
3. Computation happens exactly once
4. Clear intent - "lazy" not "maybe null"
5. Delegate is released after computation (GC friendly)

#### 9.2: Fields That Could Be Final

Many constructor-initialized fields could be `final`:

```java
// BEFORE: Mutable field, never reassigned
private Token name;
private List<Parameter> params;

// AFTER: Immutable by design
private final Token name;
private final List<Parameter> params;
```

**Identification strategy:**
1. Find fields initialized in constructor
2. Check if ever reassigned (grep for `this.field =` outside constructor)
3. Mark as `final` if never reassigned
4. Add `// TODO: make final when state is removed` for fields that need refactoring

#### 9.3: Converting Lazy Null-Check Patterns

Many fields follow this pattern and can use `Lazy<T>`:

```java
// Pattern: Check for null, compute if needed
private TypeInfo m_typeInfo;

public TypeInfo getTypeInfo() {
    TypeInfo info = m_typeInfo;
    if (info == null) {
        m_typeInfo = info = buildTypeInfo();
    }
    return info;
}

// Convert to:
private final Lazy<TypeInfo> typeInfo = Lazy.of(this::buildTypeInfo);

public TypeInfo getTypeInfo() {
    return typeInfo.get();
}
```

**Candidates for Lazy conversion:**
- TypeConstant.m_typeinfo
- TypeInfo.m_mapOps
- TypeInfo cached method lookups
- Component.m_mapChildByName
- Various cached computations in constants

#### 9.4: State That Prevents Finality

Some fields can't be final because of the current stage-based model:

```java
// Can't be final - set during registration phase
private Component m_component;  // TODO: make final when stage data externalized

// Can't be final - computed during validation
private TypeConstant m_type;  // TODO: make final when using copy-on-write
```

Add comments to track these:

```java
// TODO: [IMMUTABILITY] This field prevents node immutability.
// Move to external CompilationContext or use copy-on-write transform.
private transient Context m_ctx;
```

---

### Phase 10: Thread Safety Documentation

#### 10.1: Current Thread Safety Issues

**The asm package has significant thread safety concerns:**

| Location | Issue | Impact |
|----------|-------|--------|
| `ConstantPool:178` | `synchronized (this)` during register | Contention during parallel compilation |
| `TypeConstant:1632` | `synchronized` on `ensureTypeInfo` | Blocks concurrent type resolution |
| `TypeInfo:962,1003,1758,1940,2020,2113,2142` | Multiple `synchronized` methods | Serializes type info access |
| `Component:727` | `synchronized ensureChildByNameMap()` | Blocks component traversal |

**Volatile fields indicating concurrent access:**
- `TypeConstant.m_typeinfo` - TypeInfo cache
- `TypeConstant.m_mapRelations` - Relation cache
- `TypeConstant.m_cInvalidations` - Invalidation counter
- `ConstantPool.m_mapConstants` - Constant lookup
- `ConstantPool.m_cInvalidated` - Invalidation tracking

#### 10.2: Why Immutability Fixes This

With immutable nodes and copy-on-write:

```java
// CURRENT: Mutable, requires synchronization
public synchronized TypeInfo ensureTypeInfo(ErrorListener errs) {
    if (m_typeinfo == null) {
        m_typeinfo = buildTypeInfo(errs);  // Race condition without sync
    }
    return m_typeinfo;
}

// FUTURE: Immutable, no synchronization needed
// TypeInfo stored in external cache keyed by immutable type identity
public TypeInfo getTypeInfo(CompilationContext ctx) {
    return ctx.getTypeInfoCache().computeIfAbsent(this, t -> buildTypeInfo(ctx));
}
```

**Benefits of immutability for thread safety:**
1. No synchronization needed for immutable data
2. Lock-free read access to type information
3. Parallel compilation without contention
4. Safe caching with identity-based keys

#### 10.3: Documentation Strategy

Add comments explaining thread safety issues:

```java
// THREAD-SAFETY: This synchronized block is required because TypeInfo
// is cached on the mutable TypeConstant. With immutable nodes and external
// caching, this synchronization would be unnecessary.
// See: Phase 10 in ast-refactoring-plan.md
public synchronized TypeInfo ensureTypeInfo(...) {
```

---

### Virtual List Nodes (Alternative/Addition)

Instead of `List<Parameter>` as a field, have explicit list nodes:

```java
class ParameterList extends AstNode {
    private final List<Parameter> elements;

    @Override
    public void forEachChild(Consumer<AstNode> visitor) {
        elements.forEach(visitor);
    }
}
```

**Benefits:**
- Every child is a single AstNode reference
- Clone is uniform
- Better matches grammar structure
- Cleaner for copy-on-write

---

## Open Questions

1. **Stage information**: Where does it live in a copy-on-write world?
   - External map keyed by node identity?
   - Separate "annotated tree" wrapper?

2. **Parent references**: How to handle?
   - Roslyn: manufacture on-demand during traversal
   - Trade-off: convenience vs memory

3. **replaceChild use case**: Only used for TraceExpression wrapping
   - Could this be handled differently?
   - Maybe a post-validation transform pass?

4. **Incremental compilation**: Is this a goal?
   - Roslyn-style persistence only matters for incremental
   - If not needed, simpler approaches work

---

## Next Steps

### AST Refactoring (Phase 1-4)

1. [x] Implement `forEachChild(Function<AstNode, T>)` across all AST nodes
2. [x] Keep `children()` as convenience method built on `forEachChild`
3. [x] Implement `replaceChild()` explicitly per class
4. [x] Add `@Derived` annotation for non-structural fields
5. [x] Implement `withChildren(List<AstNode>)` on each node class
6. [x] Add `copy()` method in base class using `withChildren()`
7. [x] Replace `transient` keyword with `@Derived` on computed fields in AST
8. [x] Remove old reflection-based `clone()` method from AstNode
9. [x] Remove `getChildFields()`, `fieldsForNames()`, `ChildIterator`, `ChildIteratorImpl`
10. [x] Update `getDumpChildren()` to use explicit field access (uses forEachChild now)
11. [ ] Add `transform()` method with `VisitResult` pattern
12. [ ] Consider virtual list nodes for cleaner structure

### Constant Pool Refactoring (Phase 5)

13. [ ] Create/share `@Derived` annotation for `org.xvm.asm` package
14. [ ] Tag `transient` fields with `@Derived` in Constant subclasses
15. [ ] Tag `transient` fields with `@Derived` in TypeConstant subclasses
16. [ ] Implement `copyTo(ConstantPool)` on leaf constants (IntConstant, StringConstant, etc.)
17. [ ] Implement `copyTo(ConstantPool)` on TypeConstant subclasses
18. [ ] Implement `copyTo(ConstantPool)` on IdentityConstant subclasses
19. [ ] Implement `copyTo(ConstantPool)` on SignatureConstant
20. [ ] Update `ConstantPool.register()` to use `copyTo()` instead of `adoptedBy()`
21. [ ] Remove `adoptedBy()` method from Constant
22. [ ] Remove `Cloneable` interface from Constant
23. [ ] Tag `transient` fields with `@Derived` in Component hierarchy
24. [ ] Implement explicit copy for Component bifurcation
25. [ ] Remove `Cloneable` interface from Component, Parameter, etc.

### Array to List Migration (Phase 6)

26. [ ] Add List-based `getParams()` alongside `getParamArray()` in MethodStructure
27. [ ] Add List-based `getReturns()` alongside `getReturnArray()` in MethodStructure
28. [ ] Mark array-returning methods as `@Deprecated`
29. [ ] Update internal callers to use List APIs
30. [ ] Convert internal array storage to List storage where safe
31. [ ] Document bridge/runtime boundary methods that must stay as arrays

### Null Safety (Phase 7)

32. [ ] Add `@NotNull` to constructor parameters that must not be null
33. [ ] Add `@Nullable` to parameters and returns that may be null
34. [ ] Convert null-or-empty collections to always-empty pattern in PropertyStructure
35. [ ] Convert null-or-empty collections to always-empty pattern in ClassStructure
36. [ ] Convert null-or-empty collections to always-empty pattern in MethodInfo
37. [ ] Convert null-or-empty collections to always-empty pattern in PropertyInfo
38. [ ] Convert null-or-empty collections to always-empty pattern in TypeConstant
39. [ ] Add `@Unmodifiable` to List/Set/Map return types
40. [ ] Store collections as immutable using `List.copyOf()` in constructors

### Generic Types and Raw Type Elimination (Phase 8)

41. [ ] Fix double-cast type erasure in Parser.java:1572 (`(List<Statement>) (List)`)
42. [ ] Fix double-cast type erasure in TypeCompositionStatement.java:933,1290
43. [ ] Fix raw type cast in NativeContainer.java:494
44. [ ] Create `OrderedMap<K,V>` interface with `asList()` and `entryAt()` methods
45. [ ] Create `LinkedOrderedMap<K,V>` implementation wrapping `LinkedHashMap`
46. [ ] Deprecate `ListMap` class
47. [ ] Migrate ListMap usages to LinkedOrderedMap (208 usages)
48. [ ] Fix raw Iterator cast in AstNode.java:2086
49. [ ] Fix raw casts in ListMap.java (internal)
50. [ ] Eliminate `LinkedIterator` usage - replace with proper list concatenation
51. [ ] Eliminate `@SuppressWarnings("unchecked")` in AstNode.java
52. [ ] Eliminate `@SuppressWarnings("unchecked")` in LambdaExpression.java
53. [ ] Add proper generics to helper methods
54. [ ] Create `NestedId` sealed interface for type-safe nested identity keys
55. [ ] Replace `Map<Object, PropertyInfo>` with `Map<NestedId, PropertyInfo>` in TypeInfo
56. [ ] Replace `Map<Object, MethodInfo>` with `Map<NestedId, MethodInfo>` in TypeInfo
57. [ ] Replace `Map<Object, ...>` patterns in TypeConstant, ConstantPool, ClassStructure
58. [ ] Convert NestedIdentity to immutable record (pre-resolve at construction)
59. [ ] Audit all HashMap key classes for mutability issues

### Final Fields and Immutability (Phase 9)

60. [x] Create `Lazy<T>` utility class in `org.xvm.util`
61. [ ] Identify constructor-initialized fields that can be `final`
62. [ ] Make Token fields final across AST nodes
63. [ ] Make child list fields final (store as immutable)
64. [ ] Convert TypeConstant.m_typeinfo to use `Lazy<TypeInfo>`
65. [ ] Convert TypeInfo cached lookups to use `Lazy<T>`
66. [ ] Convert Component.m_mapChildByName to use `Lazy<Map>`
67. [ ] Add `// TODO: [IMMUTABILITY]` comments to mutable state fields
68. [ ] Document which fields block copy-on-write transformation

### Thread Safety Documentation (Phase 10)

69. [ ] Add `// THREAD-SAFETY:` comments to synchronized methods in ConstantPool
70. [ ] Add `// THREAD-SAFETY:` comments to synchronized methods in TypeConstant
71. [ ] Add `// THREAD-SAFETY:` comments to synchronized methods in TypeInfo
72. [ ] Document volatile fields and their purpose
73. [ ] Create issue tracking thread safety concerns for future resolution

### Phase 11: Error Handling Architecture for LSP/DAP

The current error handling is fragmented and incompatible with LSP/DAP requirements.
This phase designs a unified error handling architecture with IDE integration as the
primary concern.

#### 11.1: Current Error Handling Problems

**Multiple conflicting patterns:**

| Pattern | Location | Problem |
|---------|----------|---------|
| `ErrorListener` interface | Compiler/AST | Can be null, swapped for BLACKHOLE |
| `RuntimeException` throws | Everywhere (~250 in compiler) | Breaks control flow, can't recover |
| `CompilerException` | Parser/Compiler | Extends RuntimeException, unchecked |
| `LauncherException` | Tool layer | Also RuntimeException |
| `log()` with FATAL throws | Launcher | Hides control flow in log method |
| BLACKHOLE listener | 40+ usages | Silently swallows errors |

**Specific issues:**

1. **ErrorListener can be null** - Many methods accept nullable ErrorListener
2. **BLACKHOLE swallows errors** - Used to "probe" operations but loses all diagnostics
3. **RuntimeExceptions everywhere** - `IllegalStateException`, `IllegalArgumentException`
4. **Severity-based abort** - ErrorList triggers abort after N errors, but abort via return value
5. **Log-throws-exception** - `log(FATAL, ...)` throws, breaking control flow analysis
6. **No structured diagnostic API** - ErrorInfo is a class, not structured for LSP

#### 11.2: What LSP/DAP Needs

**LSP (Language Server Protocol):**
- All diagnostics collected and returned, never thrown
- Diagnostics associated with source locations (file, line, column, range)
- Diagnostics persisted between compilations for unchanged files
- Incremental updates: only new/changed diagnostics sent to client
- Diagnostic severity: Error, Warning, Information, Hint
- Diagnostic codes for filtering and quick-fixes
- Related information (e.g., "did you mean X?")

**DAP (Debug Adapter Protocol):**
- Runtime errors as structured exceptions
- Stack traces with source locations
- Evaluation errors for watch expressions
- Clean error messages for user display

**Key insight from Roslyn:**
> "The Compiler API layer exposes diagnostics through an extensible API that allows
> user-defined analyzers to be plugged into the compilation process."
> - [Roslyn Architecture](https://learn.microsoft.com/en-us/dotnet/csharp/roslyn-sdk/compiler-api-model)

**Key insight from LLVM:**
> "`Expected<T>` is a tagged union holding either a T or an Error... All Error instances
> must be checked before destruction, even if they're Success values."
> - [LLVM Error Handling](https://llvm.org/doxygen/classllvm_1_1Expected.html)

#### 11.3: Proposed Architecture

**A. DiagnosticBag - The Core Abstraction**

```java
/**
 * Thread-safe, immutable-after-build collection of diagnostics.
 * This is the OUTPUT of compilation, not a mutable accumulator.
 */
public interface DiagnosticBag extends Iterable<Diagnostic> {

    /** All diagnostics in this bag */
    List<Diagnostic> all();

    /** Diagnostics for a specific source file */
    List<Diagnostic> forSource(Source source);

    /** Check severity levels */
    boolean hasErrors();
    boolean hasWarnings();
    Severity maxSeverity();

    /** For LSP: diagnostics changed since a previous compilation */
    DiagnosticDelta deltaFrom(DiagnosticBag previous);
}
```

**B. Diagnostic - Structured for LSP**

```java
/**
 * A single diagnostic (error, warning, hint).
 * Immutable record designed for LSP serialization.
 */
public record Diagnostic(
    @NotNull Severity severity,
    @NotNull String code,           // e.g., "XTC-101"
    @NotNull String message,        // Already formatted
    @NotNull Source source,
    @NotNull SourceRange range,     // Start and end positions
    @Nullable List<DiagnosticRelatedInfo> relatedInfo,
    @Nullable List<String> tags     // e.g., "deprecated", "unnecessary"
) {
    public record SourceRange(int startLine, int startCol, int endLine, int endCol) {}
    public record DiagnosticRelatedInfo(Source source, SourceRange range, String message) {}
}
```

**C. DiagnosticCollector - Mutable Builder**

```java
/**
 * Collects diagnostics during compilation. This is passed around
 * and MUST NOT be null. Use DiagnosticCollector.NULL_SAFE for probing.
 */
public final class DiagnosticCollector {

    /** Add a diagnostic */
    public void add(Diagnostic diag);

    /** Add with builder pattern */
    public DiagnosticBuilder error(String code);
    public DiagnosticBuilder warning(String code);

    /** Check if we should abort (after too many errors) */
    public boolean shouldAbort();

    /** Build immutable result */
    public DiagnosticBag build();

    /**
     * For probing operations - collects but doesn't report.
     * Unlike BLACKHOLE, diagnostics are still accessible!
     */
    public DiagnosticCollector fork();

    /** Merge forked diagnostics back (or discard) */
    public void merge(DiagnosticCollector forked);
    public void discard(DiagnosticCollector forked);

    /**
     * Safe "probing" pattern - replaces BLACKHOLE usage.
     * Returns both the result AND any diagnostics produced.
     */
    public static <T> ProbeResult<T> probe(Supplier<T> operation);

    public record ProbeResult<T>(T value, DiagnosticBag diagnostics) {
        public boolean succeeded() { return !diagnostics.hasErrors(); }
    }
}
```

**D. CompilationResult - Never Throws, Always Returns**

```java
/**
 * The result of a compilation operation.
 * This is what LSP receives - no exceptions to catch.
 */
public sealed interface CompilationResult permits
        CompilationResult.Success,
        CompilationResult.Failure {

    /** Diagnostics are ALWAYS available, even on failure */
    DiagnosticBag diagnostics();

    record Success(
        FileStructure module,
        DiagnosticBag diagnostics
    ) implements CompilationResult {}

    record Failure(
        DiagnosticBag diagnostics,
        @Nullable Throwable internalError  // Only for catastrophic failures
    ) implements CompilationResult {}
}
```

#### 11.4: Exception Strategy

**Checked exceptions for recoverable errors:**

```java
/**
 * Checked exception for errors that callers MUST handle.
 * Used for I/O errors, missing files, etc.
 */
public class CompilationIOException extends Exception {
    public CompilationIOException(String message, IOException cause) {
        super(message, cause);
    }
}
```

**Unchecked exceptions ONLY for programmer errors:**

```java
/**
 * For truly impossible states - bugs in the compiler itself.
 * NOT for user errors or recoverable situations.
 */
public class CompilerBugException extends RuntimeException {
    public CompilerBugException(String message) {
        super("INTERNAL ERROR: " + message + " - please report this bug");
    }
}
```

**Never throw for user errors:**

```java
// BEFORE: Throws for user errors
if (expr == null) {
    throw new CompilerException("expression required");
}

// AFTER: Collect diagnostic and return null/Optional
if (expr == null) {
    collector.error("XTC-101").message("expression required").at(node);
    return Optional.empty();
}
```

#### 11.5: LSP Integration API

```java
/**
 * API for Language Server implementation.
 */
public interface CompilerService {

    /**
     * Compile a source file and return diagnostics.
     * NEVER throws - all errors are in the result.
     */
    CompilationResult compile(
        Source source,
        ModuleRepository repo,
        CompilerOptions options
    );

    /**
     * Incremental recompile after edit.
     * Only recompiles affected files.
     */
    CompilationResult recompile(
        Source changedSource,
        CompilationResult previous
    );

    /**
     * Get diagnostics for a range (for LSP textDocument/diagnostic).
     * Returns cached diagnostics if available.
     */
    DiagnosticBag getDiagnostics(Source source);

    /**
     * Validate an expression in a context (for DAP evaluate).
     * Returns errors in result, never throws.
     */
    EvaluationResult validateExpression(
        String expression,
        Context context
    );
}
```

#### 11.6: Replacing BLACKHOLE

The 40+ usages of BLACKHOLE fall into categories:

| Category | Current | Replacement |
|----------|---------|-------------|
| Type probing | `type.ensureTypeInfo(BLACKHOLE)` | `DiagnosticCollector.probe(() -> ...)` |
| Tentative resolution | `resolveName(ctx, BLACKHOLE)` | `collector.fork()` / `merge()`/`discard()` |
| Suppressing cascading errors | `errs = BLACKHOLE` | `collector.suppressCascades(true)` |
| Expected failures | `selectCommonType(t1, t2, BLACKHOLE)` | Return `Optional<TypeConstant>` |

**Key principle:** Never lose diagnostic information. Even "probing" operations
should capture what went wrong for debugging and LSP hints.

#### 11.7: Logging Architecture

**Separate logging from diagnostics:**

```java
/**
 * Logging is for INTERNAL compiler debugging, not user-visible errors.
 * Use SLF4J for consistency with Java ecosystem.
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TypeResolver {
    private static final Logger LOG = LoggerFactory.getLogger(TypeResolver.class);

    public TypeConstant resolve(NameExpression name, DiagnosticCollector collector) {
        LOG.debug("Resolving name: {}", name);  // Debug output

        if (!canResolve(name)) {
            collector.error("XTC-202")
                .message("Cannot resolve name: {0}", name.getName())
                .at(name);
            return null;  // Don't throw!
        }

        // ...
    }
}
```

**Never throw from log methods:**

```java
// BEFORE: log() can throw for FATAL - breaks control flow analysis
log(FATAL, "Something bad happened");  // Throws LauncherException!

// AFTER: Explicit returns for control flow
if (catastrophicFailure) {
    LOG.error("Internal error: {}", details);
    collector.fatal("XTC-001").message("Internal compiler error").at(node);
    return CompilationResult.internalError(details);
}
```

#### 11.7a: Code Modernization (Java 17+ Idioms)

As refactoring proceeds, modernize legacy patterns incrementally:

**StringBuilder and String Construction:**

```java
// BEFORE: Java 1.0 style
StringBuilder sb = new StringBuilder();
boolean isFirst = true;
for (Element e : elements) {
    if (isFirst) {
        isFirst = false;
    } else {
        sb.append(", ");
    }
    sb.append(e.toString());
}
return sb.toString();

// AFTER: Modern Java
return elements.stream()
    .map(Object::toString)
    .collect(Collectors.joining(", "));
```

**Complex toString() methods:**
- Use text blocks for templates where appropriate
- Use `String.format()` or `MessageFormat` for complex formatting
- Replace imperative loops with stream collectors
- Use `var` for StringBuilder: `var sb = new StringBuilder()`

**When to use `var`:**
- Clear from RHS: `var pool = getConstantPool()` ✓
- Constructor call: `var sb = new StringBuilder()` ✓
- Generic instantiation: `var list = new ArrayList<String>()` ✓
- NOT when type is unclear: `var result = process()` ✗

**Patterns to flag for modernization:**
- `boolean isFirst = true` loops for comma-separating
- Manual StringBuilder concatenation of list elements
- Nested if-else chains that should be switch expressions
- Pre-Java 8 collection manipulation

**Immutability preferences:**
- Fields should be `final` unless there's a specific reason to mutate
- Prefer `List<T>` over `T[]` for parameters and return values
- Use `List.of()` or `Collections.unmodifiableList()` for immutable lists
- Consider `@Unmodifiable` annotation for non-modifiable collection returns

**Null safety patterns to investigate:**
- `Expression.getType()` can return null - callers should check after validation
- `TypeConstant.getParamType(0)` assumes parameterized type - needs null check
- Example in IsExpression.validateMulti(): `exprTest.getType().getParamType(0)` chains
  could NPE if getType() returns null - needs defensive check or @NotNull guarantee

**Ternary Operator (?:) Hygiene:**

The ternary operator combines dataflow and control flow in a single expression, which is
problematic when type and null hygiene is poor. Frivolous use creates hard-to-debug issues.

```java
// PROBLEMATIC - nested ternaries, unclear null semantics
String result = a != null ? a.getValue() : b != null ? b.getValue() : defaultValue;

// PROBLEMATIC - side effects in branches
TypeConstant type = expr == null ? getDefault() : expr.validate(ctx, errs).getType();

// PROBLEMATIC - null checks that hide control flow
return value != null ? value : computeExpensiveDefault();  // when is default called?
```

**Approved patterns:**
```java
// OK: Simple null guards with Objects utility
this.name = Objects.requireNonNull(name, "name is required");
this.value = Objects.requireNonNullElse(value, DEFAULT);
this.list = Objects.requireNonNullElseGet(list, ArrayList::new);

// OK: Simple selection with clear types and no null involvement
int max = (a > b) ? a : b;

// OK: Simple boolean-based selection
String label = isEnabled ? "ON" : "OFF";

// OK: Coalesce pattern when both sides are provably non-null
TypeConstant effectiveType = typeExplicit != null ? typeExplicit : typeInferred;
```

**Patterns to refactor:**
```java
// BEFORE: Nested ternaries
return a != null ? a : b != null ? b : c;

// AFTER: Explicit if-else (clearer control flow)
if (a != null) return a;
if (b != null) return b;
return c;

// AFTER: Or use Objects utility
return Stream.of(a, b, c).filter(Objects::nonNull).findFirst().orElse(c);

// BEFORE: Ternary with method calls that might NPE
TypeConstant type = expr.getType() != null ? expr.getType().getParamType(0) : null;

// AFTER: Explicit null check with early return
TypeConstant exprType = expr.getType();
if (exprType == null) {
    return null;
}
TypeConstant type = exprType.getParamType(0);

// BEFORE: Ternary hiding validation
return isValid ? process(value) : handleError();

// AFTER: Explicit branching
if (isValid) {
    return process(value);
}
return handleError();
```

**Audit criteria for ternary usage:**
1. Are both branches provably non-null or is null explicitly intended?
2. Is there any method call in the expression that could return null?
3. Are there nested ternaries? (Always refactor)
4. Does either branch have side effects? (Prefer if-else)
5. Is the condition itself complex? (Extract to named boolean)

**Files with complex ternary usage to audit:**
- TypeConstant.java - many type resolution ternaries
- NameExpression.java - resolution logic
- Context.java - variable narrowing

#### 11.8: Defensive Exception Elimination

Many `throw new IllegalArgumentException` and `throw new IllegalStateException` calls are
defensive checks that indicate:
1. **Lack of trust in callers** - Parameters should be sanitized at API boundaries
2. **Missing @NotNull annotations** - If null is never valid, declare it
3. **Redundant checks** - If caller already validated, receiver doesn't need to re-validate

**Strategy: Sanitize at API boundaries, trust internally**

```java
// BEFORE: Defensive checks everywhere
public ClassStructure(ConstantPool pool, ...) {
    if (pool == null) {
        throw new IllegalArgumentException("pool required");  // Redundant!
    }
    // ...
}

// AFTER: @NotNull at boundary, no runtime check
public ClassStructure(@NotNull ConstantPool pool, ...) {
    this.pool = pool;  // Trusted - caller validated
}
```

**When to keep checks:**
- Public API entry points (user-facing)
- Deserialization from untrusted sources
- Bridge layer receiving values from runtime

**When to remove checks:**
- Internal constructor calls where caller already validated
- Methods called only from within the same package
- Parameters that are clearly non-null from context

**Parameter lifecycle tracking:**
For each major class, document:
1. Where is this type created? (factory, parser, user)
2. What validates inputs at creation time?
3. What invariants hold after construction?

This enables removing downstream defensive checks.

#### 11.9: Migration Strategy

**Phase 11a: Add @NotNull to ErrorListener parameters**
- Find all nullable ErrorListener parameters
- Add @NotNull annotations
- Pass DiagnosticCollector.NO_OP for "don't care" cases (NOT null)

**Phase 11b: Create Diagnostic/DiagnosticBag**
- New classes in `org.xvm.asm` or new `org.xvm.diagnostic` package
- ErrorInfo → Diagnostic migration helper
- DiagnosticBag built from ErrorList

**Phase 11c: Replace RuntimeException throws in compiler**
- IllegalStateException for impossible states → CompilerBugException
- CompilerException for user errors → Diagnostic + return null
- LauncherException → explicit returns

**Phase 11d: Replace BLACKHOLE with fork/merge pattern**
- Create DiagnosticCollector.probe() utility
- Migrate each BLACKHOLE usage to capture diagnostics

**Phase 11e: Add SLF4J logging**
- Add SLF4J dependency
- Replace System.out/err debug output
- Keep log() for Launcher but never throw

**Phase 11f: Create CompilerService API**
- New interface for LSP/DAP consumers
- Wrap existing Compiler class
- Never expose exceptions in API

#### 11.9: Tasks

80. [ ] Add @NotNull to all ErrorListener parameters in compiler package
81. [ ] Add @NotNull to all ErrorListener parameters in asm package
82. [ ] Create Diagnostic record with LSP-compatible structure
83. [ ] Create DiagnosticBag interface and immutable implementation
84. [ ] Create DiagnosticCollector with fork/merge pattern
85. [ ] Create DiagnosticCollector.probe() for BLACKHOLE replacement
86. [ ] Replace BLACKHOLE in TypeConstant (18 usages)
87. [ ] Replace BLACKHOLE in NameExpression (6 usages)
88. [ ] Replace BLACKHOLE in Context (2 usages)
89. [ ] Replace BLACKHOLE in other locations (~14 usages)
90. [ ] Create CompilerBugException for impossible states
91. [ ] Replace IllegalStateException in asm package with CompilerBugException
92. [ ] Replace CompilerException throws with Diagnostic collection
93. [ ] Replace LauncherException throws with explicit returns
94. [ ] Remove throw from Launcher.log(FATAL, ...)
95. [ ] Add SLF4J dependency to build.gradle
96. [ ] Create Logger instances in major classes
97. [ ] Replace System.out debug output with LOG.debug
98. [ ] Create CompilerService interface
99. [ ] Implement CompilerService wrapping existing Compiler
100. [ ] Create CompilationResult sealed interface
101. [ ] Update tool.Compiler to use new error handling
102. [ ] Update tool.Runner to use new error handling
103. [ ] Document error handling patterns in CLAUDE.md or separate doc
104. [ ] Audit IllegalArgumentException in ConstantPool - identify redundant checks
105. [ ] Audit IllegalArgumentException in ClassStructure - identify redundant checks
106. [ ] Audit IllegalStateException in compiler/ast - categorize by removable vs needed
107. [ ] Document API boundary validation points for AST nodes
108. [ ] Document API boundary validation points for Constants
109. [ ] Add @NotNull to constructor parameters where callers always validate
110. [ ] Remove redundant null checks after adding @NotNull annotations
111. [ ] Create parameter lifecycle documentation for major types

---

### Future Work (Phase 12+)

104. [ ] Move TypeInfo cache from TypeConstant to external CompilationContext
105. [ ] Design incremental type invalidation strategy
106. [ ] Implement LSP-friendly compilation API
107. [ ] Remove deprecated array APIs (after bridge migration)
108. [ ] Remove deprecated ListMap (after migration complete)
109. [ ] Remove deprecated LinkedIterator (after migration complete)

---

## Phase 12: Static Analysis Tools Integration

### 12.1: Overview

Static analysis tools can automatically detect many of the antipatterns identified in this plan.
This section documents which tools to use, what they find, and how to integrate them without
disrupting the normal build.

**Key principle:** Tools are configured but **disabled by default**. Developers opt-in via
Gradle properties or explicit task invocation. This prevents "freaking out" other developers
with thousands of new warnings while allowing incremental adoption.

### 12.2: Tool Selection and Capabilities

| Tool | Focus | Build-time | Best For |
|------|-------|-----------|----------|
| **Error Prone** | Bug patterns in source | Low (~5%) | General bugs, API misuse |
| **NullAway** | Null safety | Low (~5%) | NPE prevention, `@Nullable` enforcement |
| **SpotBugs** | Bytecode analysis | Medium | Concurrency bugs, resource leaks |
| **PMD** | Source patterns | Low | Dead code, complexity, style |
| **Checkstyle** | Formatting/style | Very low | Coding standards enforcement |

**Redundancy analysis:**
- Error Prone + NullAway: Complementary, both required for null safety
- SpotBugs vs Error Prone: ~30% overlap on null checks; SpotBugs finds bytecode-level issues
- PMD vs Checkstyle: Minimal overlap; PMD = logic patterns, Checkstyle = formatting
- Recommendation: Use all four, they catch different classes of bugs

### 12.3: What Each Tool Finds (Mapped to Plan Goals)

#### Error Prone Checks Relevant to This Plan

| Check | Plan Goal | What It Catches |
|-------|-----------|-----------------|
| `NullAway` | Null safety (11.7a) | NPE chains like `getType().getParamType(0)` |
| `Var` | Use `var` (11.7a) | Suggests where `var` can replace verbose types |
| `ImmutableEnumChecker` | Immutability (11.7a) | Mutable fields in enums |
| `MutableConstantField` | Final fields (11.7a) | Non-final static fields |
| `UnnecessaryBoxedVariable` | Performance | Boxed primitives that should be primitives |
| `StringBuilderInitWithChar` | StringBuilder (11.7a) | `new StringBuilder('c')` bug |
| `LoopOverCharArray` | Modernization | `str.toCharArray()` loops → streams |
| `EqualsGetClass` | Bug patterns | `getClass()` in equals (should use instanceof) |

#### SpotBugs Checks Relevant to This Plan

| Bug Pattern | Plan Goal | What It Catches |
|-------------|-----------|-----------------|
| `NP_*` | Null safety | Null dereferences, null returns |
| `RCN_*` | Null safety | Redundant null checks (proves non-null) |
| `OS_*` | Resource management | Unclosed streams |
| `IS2_*` | Concurrency | Inconsistent synchronization |
| `EI_*` | Immutability | Exposing internal representation |
| `MS_*` | Immutability | Mutable static fields |
| `PZLA_*` | Arrays → Lists | Prefer zero-length arrays (signals array usage) |

#### PMD Checks Relevant to This Plan

| Rule | Plan Goal | What It Catches |
|------|-----------|-----------------|
| `UnusedPrivateField` | Dead code | Fields that can be removed |
| `UnusedPrivateMethod` | Dead code | Methods that can be removed |
| `UnusedLocalVariable` | Dead code | Variables to remove |
| `AvoidReassigningParameters` | Immutability | Parameter mutation |
| `UseVarargs` | Modernization | Array params that should be varargs |
| `UseTryWithResources` | Modernization | Manual close() calls |
| `SimplifiableTestAssertion` | Modernization | Assert patterns to simplify |

### 12.4: Gradle Integration (Opt-In by Default)

```kotlin
// build.gradle.kts (common configuration)

// ============================================================
// STATIC ANALYSIS - Disabled by default, opt-in via properties
// ============================================================
// Run with: ./gradlew build -Panalyze
// Or specific: ./gradlew build -Panalyze.nullaway

val analyzeEnabled = project.hasProperty("analyze")
val nullawayEnabled = analyzeEnabled || project.hasProperty("analyze.nullaway")
val spotbugsEnabled = analyzeEnabled || project.hasProperty("analyze.spotbugs")
val pmdEnabled = analyzeEnabled || project.hasProperty("analyze.pmd")

plugins {
    id("net.ltgt.errorprone") version "4.1.0"
    id("net.ltgt.nullaway") version "2.1.0"
    id("com.github.spotbugs") version "6.0.7"
    id("pmd")
}

dependencies {
    errorprone("com.google.errorprone:error_prone_core:2.28.0")
    errorprone("com.uber.nullaway:nullaway:0.11.0")
    compileOnly("org.jspecify:jspecify:1.0.0")  // Null annotations
}

// Configure Error Prone and NullAway
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        // Always-on lightweight checks (no annotations needed)
        enable("StringBuilderInitWithChar")
        enable("EqualsGetClass")

        if (nullawayEnabled) {
            enable("NullAway")
            option("NullAway:AnnotatedPackages", "org.xvm")
            option("NullAway:TreatGeneratedAsUnannotated", "true")
            // Start as warnings, promote to errors as code is cleaned
            option("NullAway:ErrorLevel", "WARN")
        } else {
            disable("NullAway")
        }
    }
}

// Configure SpotBugs (disabled by default)
spotbugs {
    ignoreFailures = true  // Report but don't fail build
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    enabled = spotbugsEnabled
    reports {
        html.required = true
        xml.required = false
    }
}

// Configure PMD (disabled by default)
pmd {
    isIgnoreFailures = true
    toolVersion = "7.0.0"
    rulesets = listOf(
        "category/java/bestpractices.xml",
        "category/java/errorprone.xml",
        "category/java/codestyle.xml"
    )
}

tasks.withType<Pmd>().configureEach {
    enabled = pmdEnabled
}
```

### 12.5: Usage Patterns

```bash
# Normal build (no static analysis overhead)
./gradlew build

# Full static analysis
./gradlew build -Panalyze

# Just null safety checks
./gradlew build -Panalyze.nullaway

# Just SpotBugs
./gradlew spotbugsMain -Panalyze.spotbugs

# Generate reports without failing
./gradlew check -Panalyze --continue
```

### 12.6: Incremental Adoption Strategy

**Phase 1: Baseline (No code changes)**
110. [ ] Add Error Prone plugin, enable only non-disruptive checks
111. [ ] Add SpotBugs plugin (disabled by default)
112. [ ] Add PMD plugin (disabled by default)
113. [ ] Run full analysis, capture baseline report

**Phase 2: Low-Hanging Fruit**
114. [ ] Fix all `StringBuilderInitWithChar` issues
115. [ ] Fix all `EqualsGetClass` issues
116. [ ] Review SpotBugs `NP_NULL_ON_SOME_PATH` for obvious NPEs

**Phase 3: Null Safety Foundation**
117. [ ] Add JSpecify dependency (`org.jspecify:jspecify:1.0.0`)
118. [ ] Enable NullAway in WARN mode on `org.xvm.util` (smallest package)
119. [ ] Add `@Nullable` annotations to `org.xvm.util`, fix issues
120. [ ] Expand to `org.xvm.asm.constants` (next largest impact)

**Phase 4: Full Null Safety**
121. [ ] Expand NullAway to `org.xvm.asm`
122. [ ] Expand NullAway to `org.xvm.compiler.ast`
123. [ ] Promote NullAway from WARN to ERROR
124. [ ] Add `@NullMarked` package annotations for annotated packages

**Phase 5: Code Quality Enforcement**
125. [ ] Enable PMD unused-code rules
126. [ ] Enable SpotBugs concurrency rules
127. [ ] Add pre-commit hook for Error Prone (optional)

### 12.7: Custom Rules for Plan-Specific Patterns

Some patterns from this plan need custom detection:

**Ternary Hygiene (Custom PMD Rule):**
```xml
<rule name="AvoidNestedTernary"
      message="Nested ternary operators are hard to read; use if-else"
      class="net.sourceforge.pmd.lang.rule.xpath.XPathRule">
  <properties>
    <property name="xpath">
      <value>//ConditionalExpression//ConditionalExpression</value>
    </property>
  </properties>
</rule>
```

**StringBuilder in Loops (Custom Error Prone Check):**
Consider writing a custom Error Prone check for:
- `new StringBuilder()` inside loops (should be outside)
- `boolean isFirst` pattern (should use `Collectors.joining`)

### 12.8: JSpecify Annotations Reference

JSpecify provides standard null annotations for Java 9+:

```java
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;      // Usually implicit (default)
import org.jspecify.annotations.NullMarked;   // Package-level annotation

// Package annotation (package-info.java)
@NullMarked
package org.xvm.compiler.ast;

// Field/parameter/return annotations
public class Example {
    private @Nullable TypeConstant cachedType;  // May be null

    public TypeConstant getType() {             // Never returns null (default)
        return Objects.requireNonNull(cachedType, "not validated");
    }

    public @Nullable TypeConstant tryGetType() { // May return null
        return cachedType;
    }
}
```

### 12.9: Report Locations

When analysis is enabled, reports are generated at:
- Error Prone: Inline in compiler output
- SpotBugs: `build/reports/spotbugs/main.html`
- PMD: `build/reports/pmd/main.html`

---

## Appendix A: Binary AST (BAST) Analysis

### A.1: What is BAST?

The `org.xvm.asm.ast` package contains **Binary AST** nodes - a serializable AST format
designed for "an AST interpreter or back end compiler" (per BinaryAST.java:24).

**Key insight:** BAST preserves structured control flow, unlike XTC bytecode which uses
goto-style jumps. This is similar to WebAssembly's structured control flow design and
avoids the "bytecode verification hell" that Java suffers from.

**Current BAST node count:** ~53 classes in `org.xvm.asm.ast`

### A.2: Current Architecture

```
Source Code
     ↓ (Parser)
AST (org.xvm.compiler.ast.*)
     ↓ (emit phase)
  ┌──┴──────────────┐
  ↓                 ↓
XTC Bytecode     BinaryAST (BAST)
(Op, Code)       (org.xvm.asm.ast.*)
  ↓                 ↓
Interpreter      ??? (unused currently)
(runtime/)
  ↓
JavaJIT
(javajit/)
```

**Critical finding:** The JavaJIT (`org.xvm.javajit`) does NOT use BAST. It uses the
legacy XTC bytecode (`Op` classes). The BAST is generated and serialized but not consumed
by any current backend.

### A.3: Storage in MethodStructure

```java
// MethodStructure.java
private byte[] m_abAst;           // Serialized BAST bytes
private transient BinaryAST m_ast; // Deserialized BAST tree
```

The BAST is:
1. Generated during compilation (`setAst()` called from AST emit phase)
2. Serialized to bytes for storage in `.xtc` files
3. Can be deserialized back via `getAst()`
4. But never actually consumed for execution

### A.4: Array Usage in BAST Nodes

BAST nodes extensively use arrays internally:

| File | Array Fields |
|------|--------------|
| `StmtBlockAST` | `BinaryAST[] stmts` |
| `CallableExprAST` | `TypeConstant[] retTypes`, `ExprAST[] args` |
| `MultiExprAST` | `ExprAST[] exprs` |
| `SwitchAST` | `Constant[] cases`, `BinaryAST[] bodies`, `TypeConstant[] resultTypes` |
| `TryCatchStmtAST` | `ExprAST[] resources`, `BinaryAST[] catches` |
| `MapExprAST` | `ExprAST[] keys`, `ExprAST[] values` |
| `ForEachStmtAST` | `ExprAST[] specialRegs` |
| `WhileStmtAST` | `ExprAST[] specialRegs`, `ExprAST[] declaredRegs` |
| ... | (many more) |

### A.5: Could BAST Nodes Be Records?

**Yes, with significant refactoring.** Analysis of IfStmtAST as example:

```java
// CURRENT: Mutable class with separate read/write
public class IfStmtAST extends BinaryAST {
    private ExprAST   cond;
    private BinaryAST thenStmt;
    private boolean   hasElse;
    private BinaryAST elseStmt;

    IfStmtAST(NodeType nodeType) { ... }  // For deserialization

    public IfStmtAST(ExprAST cond, BinaryAST thenStmt, BinaryAST elseStmt) { ... }

    protected void readBody(DataInput in, ...) { ... }  // Mutates fields
    protected void writeBody(DataOutput out, ...) { ... }
}

// IDEAL: Immutable record
public record IfStmtAST(
    ExprAST cond,
    BinaryAST thenStmt,
    @Nullable BinaryAST elseStmt  // null means no else
) implements BinaryAST {

    public static IfStmtAST read(DataInput in, ConstantResolver res) {
        // Factory method for deserialization
        res.enter();
        ExprAST cond = ExprAST.read(in, res);
        res.enter();
        BinaryAST thenStmt = BinaryAST.read(in, res);
        res.exit();
        BinaryAST elseStmt = in.readBoolean() ? BinaryAST.read(in, res) : null;
        if (elseStmt != null) res.exit();
        res.exit();
        return new IfStmtAST(cond, thenStmt, elseStmt);
    }
}
```

**Key changes needed:**
1. Replace mutable fields with record components
2. Replace `readBody()` mutation with static factory methods
3. Replace arrays with `List<T>` (immutable)
4. Make `BinaryAST` a sealed interface instead of abstract class

### A.6: Array → List Migration for BAST

```java
// CURRENT
public class StmtBlockAST extends BinaryAST {
    private BinaryAST[] stmts;

    public BinaryAST[] getStmts() {
        return stmts; // note: caller must not modify!
    }
}

// IDEAL
public record StmtBlockAST(
    List<BinaryAST> stmts,
    boolean hasScope
) implements BinaryAST {

    public StmtBlockAST {
        stmts = List.copyOf(stmts);  // Immutable
    }
}
```

### A.7: BAST vs XTC Bytecode for JIT

**Why BAST might be better for JavaJIT:**

| Aspect | XTC Bytecode | BAST |
|--------|-------------|------|
| Control flow | Unstructured (goto) | Structured (if/while/for) |
| Optimization | Harder (CFG reconstruction) | Easier (structure preserved) |
| Type info | Encoded in ops | Explicit TypeConstants |
| SSA conversion | Complex | Simpler (scopes explicit) |
| Inlining | Difficult | Natural (tree structure) |

**Current JavaJIT approach:**
```java
// Builder.java uses Op (XTC bytecode)
import org.xvm.asm.Op;
// ... generates Java bytecode from Op sequence
```

**Potential BAST approach:**
```java
// Hypothetical BastCompiler.java
public class BastCompiler {
    void compile(IfStmtAST ifStmt, CodeBuilder code) {
        compile(ifStmt.cond(), code);
        Label elseLabel = code.newLabel();
        code.ifeq(elseLabel);
        compile(ifStmt.thenStmt(), code);
        if (ifStmt.elseStmt() != null) {
            Label endLabel = code.newLabel();
            code.goto_(endLabel);
            code.labelBinding(elseLabel);
            compile(ifStmt.elseStmt(), code);
            code.labelBinding(endLabel);
        } else {
            code.labelBinding(elseLabel);
        }
    }
}
```

### A.8: Recommendation

**Priority:** This is orthogonal to the main refactoring but could be valuable long-term.

**Phase 1:** Convert BAST nodes to records with List<T> instead of arrays
- Start with leaf nodes (ConstantExprAST, RegisterAST)
- Move up to compound nodes (IfStmtAST, WhileStmtAST)
- Finally complex nodes (SwitchAST)

**Phase 2:** Create a BAST → Java bytecode compiler
- Would replace or complement the XTC bytecode → Java bytecode path
- Benefits: cleaner code, better optimization opportunities
- Can leverage the structured control flow

**Phase 3:** Consider removing XTC bytecode entirely
- BAST provides everything needed
- Simpler architecture
- Smaller `.xtc` files (potentially)

---

## ConstantPool Locator Refactoring

### The Problem: Untyped Locators

The ConstantPool uses "locators" for fast constant lookup. Instead of creating a full Constant
just to check if one exists, constants provide a lightweight lookup key:

```java
// Current API - returns Object!
protected Object getLocator() {
    return null;  // Default: no locator
}

// In StringConstant:
@Override
public Object getLocator() {
    return m_sVal;  // Returns the String value
}

// In IntConstant:
@Override
public Object getLocator() {
    return m_pint;  // Returns PackedInteger
}

// In TerminalTypeConstant:
@Override
protected Object getLocator() {
    return constId;  // Returns another Constant!
}
```

**Why this is broken:**
- No type safety - locator can be String, Integer, Constant, Format enum, etc.
- The map is `Map<Object, Constant>` with heterogeneous keys
- Relationship between constant type and locator type is implicit
- Impossible to statically verify correctness
- Runtime `ClassCastException` waiting to happen

### Current Locator Types by Constant Class

| Constant Class | Locator Type | Notes |
|----------------|--------------|-------|
| **Primitive Values** | | |
| `StringConstant` | `String` | The string value itself |
| `CharConstant` | `Character` | Only for ASCII ≤ 0x7F |
| `IntConstant` | `PackedInteger` | The integer value |
| `ByteConstant` | `Integer` | Uses Integer cache |
| `DecimalConstant` | `Decimal` | Decimal32/64/128 |
| `Float*Constant` | `Float`/`Double` | Boxed primitives |
| `LiteralConstant` | `String` | The literal text |
| `RegExConstant` | `String` | The regex pattern |
| **Type References** | | |
| `TerminalTypeConstant` | `Constant` | The underlying identity constant |
| `AccessTypeConstant` | `TypeConstant` | Only for PUBLIC access |
| `ImmutableTypeConstant` | `TypeConstant` | The wrapped type |
| `ServiceTypeConstant` | `TypeConstant` | The wrapped type |
| `ParameterizedTypeConstant` | `TypeConstant` | Only if no type params |
| `DecoratedClassConstant` | `TypeConstant` | The type being decorated |
| **Pseudo Constants** | | |
| `ThisClassConstant` | `IdentityConstant` | The class identity |
| `ParentClassConstant` | `IdentityConstant` | The child identity |
| `SingletonConstant` | `IdentityConstant` | The class constant |
| **Conditions** | | |
| `NamedCondition` | `String` | The condition name |
| `PresentCondition` | `Constant` | The identity constant |
| `VersionedCondition` | `VersionConstant` | The version constant |
| `NotCondition` | `ConditionalConstant` | The negated condition |
| **Special** | | |
| `KeywordConstant` | `Format` | Enum singleton |
| `MatchAnyConstant` | `TypeConstant` | The matched type |
| Most others | `null` | No locator optimization |

### Proposed Refactoring

#### Option 1: Generic Interface per Constant (Recommended)

```java
/**
 * Interface for constants that support fast lookup by a locator key.
 *
 * @param <L> the locator type (e.g., String for StringConstant)
 */
public interface Locatable<L> {
    /**
     * Get the locator key for fast lookup.
     *
     * @return the locator, or null if this constant doesn't support locator lookup
     */
    @Nullable L getLocator();
}

// StringConstant becomes:
public class StringConstant extends ValueConstant implements Locatable<String> {
    @Override
    public String getLocator() {
        return m_sVal;
    }
}

// IntConstant becomes:
public class IntConstant extends ValueConstant implements Locatable<PackedInteger> {
    @Override
    public PackedInteger getLocator() {
        return m_pint;
    }
}

// TerminalTypeConstant - locator is another Constant:
public class TerminalTypeConstant extends TypeConstant implements Locatable<Constant> {
    @Override
    public Constant getLocator() {
        Constant constId = ensureResolvedConstant();
        return constId.getFormat() == Format.UnresolvedName ? null : constId;
    }
}
```

**Pros:**
- Type-safe at compile time
- Each constant class declares its locator type
- IDE can show what type of locator each constant uses

**Cons:**
- Can't enforce at `Map` level since locator types vary
- Need instanceof checks when processing heterogeneous locators

#### Option 2: Type-Keyed Lookup Maps

```java
// Instead of one Map<Object, Constant>, use typed maps:
public class ConstantPool {
    private final Map<String, StringConstant> stringLocators = new ConcurrentHashMap<>();
    private final Map<PackedInteger, IntConstant> intLocators = new ConcurrentHashMap<>();
    private final Map<Constant, TypeConstant> typeLocators = new ConcurrentHashMap<>();
    // ... one per locator type

    public StringConstant lookupString(String value) {
        return stringLocators.get(value);
    }

    public IntConstant lookupInt(PackedInteger value) {
        return intLocators.get(value);
    }

    // etc.
}
```

**Pros:**
- Fully type-safe maps
- No casts needed
- Clear separation of concerns

**Cons:**
- Many maps to manage
- Register must know which map to update
- Doesn't fit the current Format-keyed structure

#### Option 3: Sealed Locator Hierarchy (Most Type-Safe)

```java
/**
 * Sealed hierarchy of locator types.
 */
public sealed interface ConstantLocator<C extends Constant>
    permits ValueLocator, ConstantRefLocator, FormatLocator {

    C lookupIn(ConstantPool pool);
}

public record ValueLocator<V, C extends Constant>(V value, Format format)
    implements ConstantLocator<C> {

    @Override
    public C lookupIn(ConstantPool pool) {
        return pool.lookupByValue(format, value);
    }
}

public record StringLocator(String value) extends ValueLocator<String, StringConstant> {
    public StringLocator(String value) {
        super(value, Format.String);
    }
}

public record IntLocator(PackedInteger value, Format format)
    extends ValueLocator<PackedInteger, IntConstant> {}

public record ConstantRefLocator<C extends Constant>(Constant ref, Format format)
    implements ConstantLocator<C> {

    @Override
    public C lookupIn(ConstantPool pool) {
        return pool.lookupByConstantRef(format, ref);
    }
}
```

**Pros:**
- Pattern matching with sealed types
- Each locator knows how to look itself up
- Very type-safe

**Cons:**
- More complex object model
- Allocates locator objects (though could be cached)
- Significant refactoring effort

### Recommended Migration Path

**Phase 1: Add Locatable Interface (Non-breaking)**
```java
// Add interface without changing existing code
public interface Locatable<L> {
    @Nullable L getLocator();
}

// Make each constant implement it with its specific type
// Old getLocator() still works, new typed version available
```

**Phase 2: Add Typed Lookup Methods to ConstantPool**
```java
public class ConstantPool {
    // Keep old method for compatibility
    @Deprecated
    private Map<Object, Constant> ensureLocatorLookup(Format format) { ... }

    // Add new typed methods
    public <L, C extends Constant & Locatable<L>> C lookupByLocator(
            Format format, L locator) {
        @SuppressWarnings("unchecked")
        C result = (C) ensureLocatorLookup(format).get(locator);
        return result;
    }

    // Specific typed lookups for common cases
    public StringConstant lookupString(String value) {
        return (StringConstant) ensureLocatorLookup(Format.String).get(value);
    }

    public IntConstant lookupInt(Format format, PackedInteger value) {
        return (IntConstant) ensureLocatorLookup(format).get(value);
    }
}
```

**Phase 3: Migrate ensure* Methods**
```java
// BEFORE:
public StringConstant ensureStringConstant(String s) {
    StringConstant constant = (StringConstant) ensureLocatorLookup(Format.String).get(s);
    if (constant == null) {
        constant = register(new StringConstant(this, s));
    }
    return constant;
}

// AFTER:
public StringConstant ensureStringConstant(String s) {
    StringConstant constant = lookupString(s);
    if (constant == null) {
        constant = register(new StringConstant(this, s));
    }
    return constant;
}
```

**Phase 4: Split the Locator Map (Optional, for full type safety)**
```java
// Replace single Map<Object, Constant> with typed maps
private final Map<String, StringConstant> stringLocators;
private final Map<PackedInteger, IntConstant> int16Locators;
private final Map<PackedInteger, IntConstant> int32Locators;
// etc.
```

### Testing the Refactoring

```java
@Test void testLocatable_stringConstant_returnsString() {
    StringConstant sc = pool.ensureStringConstant("hello");
    assertInstanceOf(String.class, sc.getLocator());
    assertEquals("hello", sc.getLocator());
}

@Test void testLocatable_intConstant_returnsPackedInteger() {
    IntConstant ic = pool.ensureIntConstant(42);
    assertInstanceOf(PackedInteger.class, ic.getLocator());
    assertEquals(PackedInteger.valueOf(42), ic.getLocator());
}

@Test void testLookupString_existingConstant_returnsSame() {
    StringConstant sc1 = pool.ensureStringConstant("test");
    StringConstant sc2 = pool.lookupString("test");
    assertSame(sc1, sc2);
}

@Test void testLookupString_nonExistent_returnsNull() {
    assertNull(pool.lookupString("does not exist"));
}

// Verify all constants that have locators implement Locatable
@Test void testAllLocatableConstants_implementInterface() {
    // Use reflection to find all Constant subclasses
    // For each one that overrides getLocator() returning non-null:
    // Verify it implements Locatable<SomeType>
}
```

### Priority

**Medium-High** - This is a type safety improvement that:
1. Makes the code more self-documenting
2. Enables IDE type checking
3. Reduces runtime ClassCastException risk
4. Should be done alongside the `transferTo()` migration

---

## Lazy Evaluation and Immutability Architecture

This section describes the architectural patterns for lazy/eager evaluation, immutability, and
stateless compilation that enable LSP integration, parallel compilation, and reduced complexity.

### Core Principles

#### 1. Immutable by Default, Copy-on-Write Transforms

All AST nodes, constants, and type information should be treated as immutable after construction.
Transformations produce new objects rather than mutating existing ones:

```java
// Instead of:
node.setType(resolvedType);  // ❌ Mutation

// Use:
Node newNode = node.withType(resolvedType);  // ✅ Copy-on-write
```

**Benefits**:
- Thread-safe without locks
- Safe structural sharing (multiple references to same subtree)
- Predictable debugging (state doesn't change under you)
- Natural undo/redo and incremental compilation

#### 2. Lazy Evaluation with Memoization

Expensive computations should be deferred until needed and cached:

```java
// Pattern: Lazy field with thread-safe initialization
private volatile TypeConstant m_typeResolved;  // null = not computed

public TypeConstant getResolvedType() {
    TypeConstant type = m_typeResolved;
    if (type == null) {
        m_typeResolved = type = computeType();  // Safe double-check
    }
    return type;
}
```

**When to use lazy evaluation**:
- Type resolution (may not be needed for all code paths)
- TypeInfo building (expensive, cache-worthy)
- Constant pool lookups for derived types
- Any computation that depends on external state that may not be available

**When to use eager evaluation**:
- Simple field access
- Format-derived properties (constant time)
- Anything needed for serialization

#### 3. External Caches vs Embedded State

Caches should be external to the cached objects:

```java
// Instead of embedding cache in the object:
class TypeConstant {
    private TypeInfo m_cachedInfo;  // ❌ Embedded cache
}

// Use external cache:
class TypeInfoCache {
    private final Map<TypeConstant, TypeInfo> cache = new ConcurrentHashMap<>();

    public TypeInfo getInfo(TypeConstant type) {
        return cache.computeIfAbsent(type, this::compute);  // ✅ External
    }
}
```

**Benefits**:
- Cache lifetime independent of object lifetime
- Multiple cache strategies (LRU, weak refs, etc.)
- Easy cache invalidation
- LSP can maintain separate caches per edit session

### Avoiding Recursive Initialization

**Problem**: During constant pool initialization, creating one constant may recursively
require creating other constants. This causes issues with:
- `ConcurrentHashMap.computeIfAbsent()` (doesn't allow recursive calls)
- Circular dependencies (A needs B needs A)
- Stack overflow in deeply nested cases

**Solution**: Separate creation from registration with lazy binding:

```java
// Phase 1: Create structural constants (no type resolution)
StringConstant strConst = new StringConstant(pool, "hello");

// Phase 2: Register (may recursively register dependencies)
strConst = pool.register(strConst);

// Phase 3: Lazy type resolution (deferred until actually needed)
TypeConstant type = strConst.getType();  // Computed on first access
```

**Key insight**: For value constants (StringConstant, IntConstant, etc.), the type is
implicit from the format. Don't register the type during `registerConstants()` - it will
be resolved lazily when `getType()` is called.

### Stateless Stage Processing

Compilation stages should be pure functions:

```java
// Instead of stateful visitor:
class TypeResolver extends Visitor {
    private Context currentContext;  // ❌ Mutable state

    void visit(Expression e) {
        e.setType(resolve(e, currentContext));  // ❌ Mutation
    }
}

// Use stateless transformation:
class TypeResolver {
    // Pure function: (AST, Context) -> AST
    static Expression resolve(Expression e, Context ctx) {
        TypeConstant type = computeType(e, ctx);
        return e.withType(type);  // ✅ New immutable node
    }
}
```

**Benefits for LSP**:
- Can re-run any stage with new input
- Results are cacheable by input hash
- Easy to test in isolation
- No "which stage are we in" confusion

### Cache Hierarchy for LSP

```
┌─────────────────────────────────────────────────────────────────┐
│                        LSP Server                                │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐           │
│  │ Parse Cache │   │ Type Cache  │   │ Semantic    │           │
│  │ (per file)  │   │ (global)    │   │ Cache       │           │
│  └─────────────┘   └─────────────┘   └─────────────┘           │
│         │                 │                 │                   │
│         ▼                 ▼                 ▼                   │
│  ┌─────────────────────────────────────────────────┐           │
│  │           Immutable Constant Pool                │           │
│  │  (shared across all compilation contexts)        │           │
│  └─────────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────────┘
```

**Cache invalidation strategy**:
- Parse cache: Invalidate on file change (cheap to rebuild)
- Type cache: Invalidate on signature change, keep on body-only edits
- Semantic cache: Fingerprint-based invalidation
- Constant pool: Never invalidate (immutable), may grow

### Implications for Current Codebase

#### ConstantPool Changes

1. **Typed ensure methods should not use computeIfAbsent with register()**:
   ```java
   // ❌ Causes recursive update exception
   m_mapStrings.computeIfAbsent(s, k -> register(new StringConstant(this, k)));

   // ✅ Safe pattern - check then create
   StringConstant c = m_mapStrings.get(s);
   if (c == null) {
       c = register(new StringConstant(this, s));
   }
   return c;
   ```

2. **Type registration should be lazy**:
   - `ValueConstant.registerConstants()` should NOT register the type
   - Type is derived from format, computed on first `getType()` call
   - Breaks circular dependency: string → type → class → module → string

#### AST Node Changes

1. **All setters should become `withX()` methods**:
   ```java
   // Transform: setType(t) → withType(t)
   public Expression withType(TypeConstant type) {
       return this == type ? this : copy().setTypeInternal(type);
   }
   ```

2. **Computed properties should be memoized**:
   ```java
   private transient volatile Boolean m_fConst;

   public boolean isConstant() {
       Boolean f = m_fConst;
       if (f == null) {
           m_fConst = f = computeConstant();
       }
       return f;
   }
   ```

#### TypeInfo Changes

1. **External TypeInfoCache**:
   - Move cache from TypeConstant into standalone cache
   - Enable multiple cache instances (per-LSP-session)
   - Support weak references for memory management

2. **Incremental TypeInfo building**:
   - Base TypeInfo is immutable
   - Derived info (with contributions) creates new instance
   - Structural sharing for unchanged portions

### Migration Path

**Phase 1: Fix Immediate Issues**
- Remove eager type registration from ValueConstant ✓
- Fix computeIfAbsent recursion in ensure methods
- Add typed locator maps to ConstantPool ✓

**Phase 2: Introduce Lazy Patterns**
- Add `Lazy<T>` utility class for memoization
- Convert expensive getters to lazy initialization
- Add `@LazyInit` annotation for documentation

**Phase 3: Copy-on-Write AST**
- Convert setters to `withX()` methods
- Implement structural sharing
- Add immutability annotations

**Phase 4: External Caches**
- Extract TypeInfoCache from TypeConstant
- Create cache hierarchy for LSP
- Add cache invalidation hooks

**Phase 5: Stateless Stages**
- Refactor visitors to return new trees
- Add stage result caching
- Enable incremental compilation

### Testing Lazy Evaluation

```java
@Test void testLazyType_notComputedUntilNeeded() {
    // Create constant - type should NOT be computed yet
    StringConstant sc = new StringConstant(pool, "test");

    // Verify no recursive type creation occurred
    // (Implementation detail: track type creation count)

    // Now access type - should compute lazily
    TypeConstant type = sc.getType();
    assertNotNull(type);
}

@Test void testLazyType_cachedAfterFirstAccess() {
    StringConstant sc = new StringConstant(pool, "test");

    TypeConstant type1 = sc.getType();
    TypeConstant type2 = sc.getType();

    assertSame(type1, type2, "Type should be cached");
}
```

### Priority

**Critical** - This architectural pattern is foundational for:
1. Fixing the current recursion issues in ConstantPool
2. Enabling LSP integration
3. Supporting parallel compilation
4. Reducing debugging complexity

---

## ConstantPool Cleanup: From 4000 Lines to Clarity

The `ConstantPool` class has 191 `ensure*` methods totaling ~4000 lines with repeated patterns:

```java
// Current pattern (repeated 191 times with variations):
public TypeConstant ensureFoo(Bar bar, Baz baz) {
    if (bar == null) bar = DEFAULT;           // 1. Null normalization
    if (already.has(bar, baz)) return it;     // 2. Early return
    TypeConstant c = locator.get(key);        // 3. Locator lookup
    if (c == null) {
        c = register(new FooConstant(...));   // 4. Creation
    }
    return c;
}
```

### Proposed Refactoring

#### 1. Typed Ensure Pattern

Extract the common pattern into a generic helper:

```java
// Generic ensure pattern - lookup, create if absent, register
private <C extends Constant, K> C ensure(
        Map<K, C> cache,
        K key,
        Supplier<C> factory) {
    C existing = cache.get(key);
    if (existing != null) {
        return existing;
    }
    C created = register(factory.get());
    cache.putIfAbsent(key, created);
    return created;
}

// Usage becomes one-liner:
public StringConstant ensureStringConstant(String s) {
    return ensure(m_mapStrings, s, () -> new StringConstant(this, s));
}

public IntConstant ensureIntConstant(PackedInteger pint, Format format) {
    return ensure(ensureIntMap(format), pint, () -> new IntConstant(this, format, pint));
}
```

#### 2. Null Handling at Boundaries

Move null checks to method entry with `Objects.requireNonNull` or explicit defaults:

```java
// Instead of scattered null checks:
public TypeConstant ensureAccessTypeConstant(TypeConstant constType, Access access) {
    if (access == null) access = Access.PUBLIC;  // ❌ Buried null handling
    ...
}

// Explicit at API boundary:
public TypeConstant ensureAccessTypeConstant(TypeConstant constType, Access access) {
    Objects.requireNonNull(constType, "constType");
    access = access != null ? access : Access.PUBLIC;
    return ensureAccessTypeConstantImpl(constType, access);  // ✅ Clean impl
}

// Or use @NonNull annotations and let IDE/compiler check
public TypeConstant ensureAccessTypeConstant(
        @NonNull TypeConstant constType,
        @NonNull Access access) { ... }
```

#### 3. Separate Concerns with Private Helpers

Split validation, normalization, and caching:

```java
// Current monolithic method:
public TypeConstant ensureAccessTypeConstant(TypeConstant constType, Access access) {
    // 40 lines mixing validation, recursion, caching, creation
}

// Cleaner separation:
public TypeConstant ensureAccessTypeConstant(TypeConstant constType, Access access) {
    // 1. Normalize
    access = normalizeAccess(access);

    // 2. Short-circuit if already correct
    if (hasAccess(constType, access)) {
        return constType;
    }

    // 3. Unwrap if necessary
    constType = unwrapExistingAccess(constType);

    // 4. Wrap with new access
    return wrapWithAccess(constType, access);
}

private Access normalizeAccess(Access access) {
    return access != null ? access : Access.PUBLIC;
}

private boolean hasAccess(TypeConstant type, Access access) {
    return type.isAccessSpecified() && type.getAccess() == access;
}

private TypeConstant unwrapExistingAccess(TypeConstant type) {
    return type instanceof AccessTypeConstant atc ? atc.getUnderlyingType() : type;
}

private TypeConstant wrapWithAccess(TypeConstant type, Access access) {
    return access == Access.PUBLIC
        ? ensure(m_mapAccessTypes, type, () -> new AccessTypeConstant(this, type, access))
        : register(new AccessTypeConstant(this, type, access));
}
```

#### 4. Builder Pattern for Complex Type Construction

For methods that chain multiple ensure calls:

```java
// Current chaining:
public TypeConstant ensureClassTypeConstant(Constant constClass,
                                            Access access, TypeConstant... params) {
    TypeConstant t = ensureTerminalType(constClass);
    if (params != null) t = ensureParameterized(t, params);
    if (access != null && access != PUBLIC) t = ensureAccess(t, access);
    return t;
}

// Builder pattern:
public TypeConstant ensureClassTypeConstant(Constant constClass,
                                            Access access, TypeConstant... params) {
    return TypeBuilder.from(this, constClass)
        .withParams(params)
        .withAccess(access)
        .build();
}

// Or fluent methods on TypeConstant itself:
public TypeConstant parameterizedWith(TypeConstant... params) {
    return getConstantPool().ensureParameterizedTypeConstant(this, params);
}

public TypeConstant withAccess(Access access) {
    return getConstantPool().ensureAccessTypeConstant(this, access);
}
```

#### 5. Group Related Methods

Organize the 191 methods into logical groups:

```java
public class ConstantPool {

    // ===== Primitive Value Constants =====
    public StringConstant ensureStringConstant(String s) { ... }
    public IntConstant ensureIntConstant(long n) { ... }
    public CharConstant ensureCharConstant(int ch) { ... }
    // ...

    // ===== Type Constants =====
    public TypeConstant ensureTerminalTypeConstant(Constant id) { ... }
    public TypeConstant ensureAccessTypeConstant(TypeConstant type, Access access) { ... }
    public TypeConstant ensureParameterizedTypeConstant(TypeConstant type, TypeConstant... params) { ... }
    // ...

    // ===== Identity Constants =====
    public ModuleConstant ensureModuleConstant(String name) { ... }
    public ClassConstant ensureClassConstant(IdentityConstant parent, String name) { ... }
    // ...

    // ===== Conditional Constants =====
    public NamedCondition ensureNamedCondition(String name) { ... }
    public VersionedCondition ensureVersionedCondition(VersionConstant ver) { ... }
    // ...
}
```

#### 6. Consider Extracting Factories

Move constant creation logic to dedicated factory classes:

```java
// Instead of 191 ensure methods in ConstantPool:
public class ConstantPool {
    public final ValueConstantFactory values = new ValueConstantFactory(this);
    public final TypeConstantFactory types = new TypeConstantFactory(this);
    public final IdentityConstantFactory identities = new IdentityConstantFactory(this);
}

// Usage:
pool.values.string("hello");
pool.types.access(baseType, Access.PRIVATE);
pool.identities.module("ecstasy.xtclang.org");
```

### Migration Strategy

1. **Don't refactor all 191 methods at once** - High risk
2. **Start with most-used methods** - StringConstant, IntConstant, TypeConstants
3. **Add typed maps incrementally** - Already started with m_mapStrings, m_mapInts
4. **Extract helper methods first** - Keep public API stable
5. **Add tests before each refactoring** - Ensure behavior preservation

### Code Size Goals

| Current | Goal | Reduction |
|---------|------|-----------|
| ~4000 lines | ~1500 lines | 60% |
| 191 methods | 191 methods | Same API surface |
| 0 typed maps | ~20 typed maps | Type safety |
| ~50 inline null checks | ~10 boundary checks | Clarity |

---

## Lexer, Parser, and Compiler Cleanup

### Overview

| File | Lines | Methods | Avg Lines/Method | Null Checks |
|------|-------|---------|------------------|-------------|
| Lexer.java | 2,859 | ~50 | ~57 | 18 |
| Parser.java | 5,772 | 40 | ~144 | 268 |
| Compiler.java | 1,373 | ~30 | ~46 | ~20 |

### Lexer Analysis (2,859 lines)

#### Current Issues

1. **Giant switch statement in `eatToken()`** (~620 lines):
   - Single method handling all token types
   - Deep nesting (switch within switch within if)
   - Repeated `source.hasNext()` / `source.rewind()` patterns

2. **Scattered string literal handling**:
   - `eatStringLiteral()`, `eatMultilineLiteral()`, `eatTemplateLiteral()`
   - Similar logic with subtle differences
   - Could share common infrastructure

3. **Date/time parsing complexity**:
   - `eatDate()`, `eatTimeOfDay()`, `eatTime()`, `eatTimeZone()`, `eatDuration()`
   - Each method ~50-100 lines
   - Complex state management for partial matches

#### Refactoring Opportunities

```java
// Current: monolithic switch
protected Token eatToken() {
    switch (chInit) {
    case '{': return new Token(..., Id.L_CURLY);
    case '}': return new Token(..., Id.R_CURLY);
    case '.':
        if (source.hasNext()) {
            switch (nextChar()) {
            case '.':
                if (source.hasNext()) {
                    switch (nextChar()) {
                    // ... 600 more lines
                    }
                }
            }
        }
    }
}

// Proposed: dispatch table + handler methods
private static final Map<Character, TokenHandler> HANDLERS = Map.of(
    '{', (lexer, pos) -> new Token(pos, lexer.pos(), Id.L_CURLY),
    '}', (lexer, pos) -> new Token(pos, lexer.pos(), Id.R_CURLY),
    '.', Lexer::eatDotSequence,
    '$', Lexer::eatDollarSequence,
    // ...
);

protected Token eatToken() {
    long pos = source.getPosition();
    char ch = nextChar();
    TokenHandler handler = HANDLERS.get(ch);
    return handler != null ? handler.handle(this, pos) : eatIdentifier(pos, ch);
}
```

#### LSP Considerations

- **Token positions must be immutable**: Already good - tokens store start/end positions
- **Incremental lexing**: Could add `lexFrom(position)` for partial re-lexing
- **Error recovery**: Add synchronization points for continuing after errors

### Parser Analysis (5,772 lines)

#### Critical Issues

1. **God method: `parseClassExpression()`** (~1,650 lines!):
   - From line 215 to line 1870
   - Single method parsing all expression types
   - Impossible to unit test individual constructs

2. **268 null checks scattered throughout**:
   - Defensive programming gone wrong
   - Mix of "valid null" and "error null" semantics
   - Example: `if (expr != null && expr.isConstant())`

3. **Mutable state during parsing**:
   - `m_token`, `m_tokenPrev` - current/previous tokens
   - `m_fDone` - completion flag
   - Makes it impossible to parse in parallel or cache results

4. **Mixed concerns**:
   - Parsing, validation, and error recovery interleaved
   - Hard to distinguish syntax errors from semantic errors

#### Refactoring Opportunities

```java
// Current: 1650-line method
private Expression parseClassExpression() {
    // ... 1650 lines handling every expression type
}

// Proposed: Pratt parser with precedence table
//
// Pratt parsing (Vaughan Pratt, 1973) is a top-down operator precedence technique where:
// - Each token has a "binding power" (precedence level)
// - Tokens have "nud" (null denotation) - how to parse as prefix (e.g., -x, !x)
// - Tokens have "led" (left denotation) - how to parse as infix (e.g., x + y)
// Benefits: Simple, elegant, easy to add new operators, naturally handles precedence
// See: https://journal.stuffwithstuff.com/2011/03/19/pratt-parsers-expression-parsing-made-easy/

private Expression parseExpression(int minPrecedence) {
    Expression left = parsePrefix();
    while (precedenceOf(peek()) >= minPrecedence) {
        left = parseInfix(left);
    }
    return left;
}

private Expression parsePrefix() {
    return switch (peek().getId()) {
        case LIT_INT -> parseLiteral();
        case IDENTIFIER -> parseNameExpression();
        case L_PAREN -> parseParenthesized();
        case NEW -> parseNewExpression();
        // Each case is a separate 20-50 line method
        default -> throw syntaxError("Expected expression");
    };
}
```

#### Null Safety Improvements

```java
// Current: nullable returns with checks everywhere
Expression expr = parseExpression();
if (expr != null && expr.isConstant()) { ... }

// Proposed: Optional or Result types
Optional<Expression> expr = tryParseExpression();
expr.filter(Expression::isConstant).ifPresent(e -> ...);

// Or: Never-null with explicit error handling
record ParseResult<T>(T value, List<ParseError> errors) {}
ParseResult<Expression> result = parseExpression();
```

#### LSP Integration

For LSP, the parser needs to:

1. **Continue after errors**: Current parser often gives up too early
2. **Provide partial ASTs**: Return what was parsed even if incomplete
3. **Track edit positions**: Map source changes to AST nodes
4. **Support incremental parsing**: Only re-parse changed portions

```java
// LSP-friendly parser interface
public interface IncrementalParser {
    // Parse with error recovery
    ParseResult parseWithRecovery(Source source, CancellationToken cancel);

    // Re-parse after edit
    ParseResult reparse(ParseResult previous, TextEdit edit);

    // Get AST node at position
    Optional<AstNode> nodeAt(ParseResult result, long position);
}
```

### Compiler Analysis (1,373 lines)

#### Current Architecture

The Compiler is a state machine with stages:
```
Initial → Registering → Registered → Loading → Loaded →
Resolving → Resolved → Validating → Validated → Emitting → Emitted
```

#### Issues

1. **Stateful stage transitions**:
   - Each stage mutates the AST nodes
   - Hard to re-run stages or cache results
   - No way to partially compile

2. **Coupled to single module**:
   - One Compiler per module
   - Cross-module dependencies require coordination
   - No parallel compilation support

3. **Error handling mixed with compilation**:
   - Errors accumulate in `ErrorList`
   - Hard to distinguish which stage produced which errors
   - Recovery is all-or-nothing

#### Proposed Architecture for LSP

```java
// Stateless compilation phases
interface CompilationPhase<I, O> {
    O process(I input, CompilationContext ctx);
}

class ParsePhase implements CompilationPhase<Source, StatementBlock> { ... }
class RegisterPhase implements CompilationPhase<StatementBlock, RegisteredAST> { ... }
class ResolvePhase implements CompilationPhase<RegisteredAST, ResolvedAST> { ... }
class ValidatePhase implements CompilationPhase<ResolvedAST, ValidatedAST> { ... }
class EmitPhase implements CompilationPhase<ValidatedAST, FileStructure> { ... }

// Compilation as pipeline
class CompilationPipeline {
    FileStructure compile(Source source) {
        return source
            .pipe(parsePhase)
            .pipe(registerPhase)
            .pipe(resolvePhase)
            .pipe(validatePhase)
            .pipe(emitPhase)
            .result();
    }
}

// LSP can cache intermediate results
class LSPCompilationCache {
    Map<Source, StatementBlock> parsedCache;
    Map<StatementBlock, ResolvedAST> resolvedCache;

    // Only re-run phases after edit point
    FileStructure recompile(Source source, TextEdit edit) {
        // ... incremental compilation
    }
}
```

### Cross-Cutting Concerns

#### 1. God Classes Problem

| Class | Lines | Responsibility |
|-------|-------|----------------|
| ConstantPool | ~4,000 | Constant interning, type creation, caching |
| Parser | ~5,700 | Lexing coordination, all parsing, error recovery |
| TypeConstant | ~2,500 | Type representation, subtyping, TypeInfo |
| TypeInfo | ~3,000 | Type metadata, method/property info |

**Solution**: Apply Single Responsibility Principle
- Extract ConstantFactory from ConstantPool
- Split Parser into ExpressionParser, StatementParser, TypeParser
- Extract TypeRelations from TypeConstant
- Extract TypeInfoBuilder from TypeInfo

#### 2. Input Sanitization Layer

Current: Validation scattered throughout
```java
public Foo createFoo(Bar bar, Baz baz) {
    if (bar == null) throw new IAE("bar required");  // Here
    if (baz == null) baz = DEFAULT;                   // Here
    if (!bar.isValid()) throw new IAE("invalid bar"); // Here
    // ... actual logic
}
```

Proposed: Validation at API boundaries only
```java
// Public API - validates
public Foo createFoo(@NonNull Bar bar, @Nullable Baz baz) {
    requireNonNull(bar, "bar");
    return createFooImpl(bar, baz != null ? baz : DEFAULT);
}

// Internal - trusts callers
private Foo createFooImpl(Bar bar, Baz baz) {
    // ... actual logic, no validation
}
```

#### 3. Immutability for LSP

**Current mutable patterns**:
```java
class AstNode {
    private TypeConstant m_type;  // Set during resolve phase
    public void setType(TypeConstant type) { m_type = type; }
}
```

**Proposed immutable patterns**:
```java
class AstNode {
    private final TypeConstant m_type;  // Set at construction

    public AstNode withType(TypeConstant type) {
        return new AstNode(this, type);  // Copy-on-write
    }
}

// Or use persistent data structures
class ResolvedAST {
    private final PersistentMap<AstNode, TypeConstant> types;

    public TypeConstant getType(AstNode node) {
        return types.get(node);
    }
}
```

#### 4. Performance Opportunities

| Opportunity | Current | Proposed | Expected Gain |
|-------------|---------|----------|---------------|
| Parallel module compilation | Single-threaded | Parallel phases | 2-4x on multi-core |
| Incremental parsing | Full re-parse | Tree-sitter style | 10-100x for edits |
| TypeInfo caching | Embedded in TypeConstant | External cache | Memory reduction |
| Constant interning | HashMap lookup | Typed maps | Better cache locality |
| String interning | Per-pool | Global weak cache | Memory reduction |

### Migration Priority

1. **High Priority (LSP blockers)**:
   - Parser error recovery
   - Stateless compilation phases
   - Immutable AST nodes

2. **Medium Priority (Code quality)**:
   - Split god classes
   - Extract eatToken() handlers in Lexer
   - Add input validation layer

3. **Lower Priority (Performance)**:
   - Parallel compilation
   - Incremental parsing
   - Cache optimizations

---

## Appendix B: Related Work

- [WebAssembly Structured Control Flow](https://webassembly.github.io/spec/core/syntax/instructions.html#control-instructions)
- [GraalVM Truffle AST Interpreter](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/)
- [Kotlin IR (similar concept)](https://kotlinlang.org/docs/whatsnew14.html#new-ir-compiler-backend)

---

## References

- [Roslyn Red-Green Trees](https://ericlippert.com/2012/06/08/red-green-trees/) - Eric Lippert
- [Roslyn SyntaxNode.cs](https://github.com/dotnet/roslyn/blob/main/src/Compilers/Core/Portable/Syntax/SyntaxNode.cs)
- [Clang RecursiveASTVisitor](https://clang.llvm.org/docs/RAVFrontendAction.html)
- [TypeScript Compiler API](https://github.com/microsoft/TypeScript/wiki/Using-the-Compiler-API)
- [Clang AST Introduction](https://clang.llvm.org/docs/IntroductionToTheClangAST.html)
