# Nullness Annotation Audit

Date: 2026-08-23

Scope: documentation-only audit of Java null guards in `javatools` and
`javatools_utils`, with emphasis on constructors and owner/runtime API
boundaries. No Java source was changed.

Representative scan commands:

```bash
rg -n "requireNonNull" javatools/src/main/java javatools_utils/src/main/java
rg -n "throw new IllegalArgumentException\\(\"[^\"]*(required|must not be null|value cannot be null|array is null|array element is null|null value|not be null|cannot be null)" javatools/src/main/java javatools_utils/src/main/java
rg -n "import org\\.jetbrains\\.annotations\\.(NotNull|Nullable)|@NotNull|@Nullable" javatools/src/main/java javatools/src/test/java javatools_utils/src/main/java javatools_utils/src/test/java
rg -n "jetbrains|annotations|NullAway|ErrorProne|Checker|Xlint|compilerArgs" gradle build-logic javatools javatools_utils -g '*.toml' -g '*.kts' -g '*.java'
```

## Baseline

`org.jetbrains.annotations.NotNull` is already available to the requested
source sets:

- `gradle/libs.versions.toml` declares `jetbrains-annotations = "26.1.0"` and
  `org.jetbrains:annotations`.
- `javatools/build.gradle.kts` adds `compileOnly(libs.jetbrains.annotations)`
  and `testCompileOnly(libs.jetbrains.annotations)`.
- `javatools_utils/build.gradle.kts` adds the same `compileOnly` and
  `testCompileOnly` dependencies.
- The requested projects only have `src/main` and `src/test` Java source
  directories, so both main and test code in this scope can already use
  JetBrains annotations.

Existing source usage proves the dependency works:

- `javatools_utils/src/main/java/org/xvm/util/Lazy.java` imports
  `org.jetbrains.annotations.NotNull` for `toString()`.
- `javatools/src/main/java/org/xvm/tool/XtcProjectCreator.java` already uses
  `@NotNull` and `@Nullable` on constructor parameters.
- `javatools/src/main/java/org/xvm/tool/LauncherOptions.java` uses
  `List<@NotNull String>`.
- `javatools/src/main/java/org/xvm/asm/BuildInfo.java` annotates non-null
  string-returning accessors.
- `javatools/src/main/java/org/xvm/asm/Op.java` already combines
  `@NotNull RegisterAST reg` with `Objects.requireNonNull(reg, "reg")`.

The build does not currently include a nullness analyzer such as NullAway,
Error Prone, Checker Framework, or JSpecify. The shared Java convention plugin
can enable `javac -Xlint:all`, but javac's lint set does not enforce JetBrains
nullness contracts. Adding annotations is still useful for IDE inspections,
external static analyzers, and future local linting, but it should not be
expected to create new javac nullness errors by itself.

## Rule For Runtime Guards

Keep runtime `Objects.requireNonNull(...)` checks at public APIs, protected
extension points, constructors, and owner/runtime boundaries even after adding
`@NotNull`.

The annotation documents the contract and gives tools a chance to report bad
callers. It does not enforce the contract at runtime, and this project currently
uses JetBrains annotations as `compileOnly`. Public and owner-sensitive code
should still fail fast with the existing message, especially where a null owner
could otherwise become a wrong-owner cache entry, hidden static fallback, or
later `NullPointerException` far from the bad call.

Small private helpers may eventually drop redundant runtime checks only when all
callers are local, annotated, and structurally proven. That is not the right
default for this branch.

## Must Annotate

These are small, correct, high-signal places where null is a programming error
and the runtime guard already encodes the contract.

| Site | Contract to annotate | Reason |
| --- | --- | --- |
| `javatools_utils/src/main/java/org/xvm/util/Lazy.java` public factories: `of`, `ofOwner`, `ofUnsynchronized`, `ofNullable`, `ofOptional`, `ofExpiring`, `synchronizedSupplier` | Annotate the supplier/function/unit parameters as `@NotNull`. | These are utility API boundaries and all guarded supplier/function objects must be present. Do not annotate supplier result type as non-null; `Lazy<T>` can cache null values and `ofNullable` explicitly allows nullable supplier results. |
| `Lazy.Owner.get(O owner)` and `Lazy.Owner.get(Object owner, Class<R> clz)` | Annotate `owner` and `clz` parameters. | The owner is part of the owner-passed lazy invariant; null owner would defeat the check that a computed lazy value belongs to one owner. |
| `javatools_utils/src/main/java/org/xvm/util/Scope.java` `requireNonNull()` | Annotate the return as `@NotNull`. | The method is a terminal null guard; callers should gain flow information after it returns. The wrapped `Scope` value itself remains nullable elsewhere. |
| `javatools_utils/src/main/java/org/xvm/util/TransientThreadLocal.java` `withInitial(Supplier<? extends S> supplier)` | Annotate `supplier`. | Mirrors `ThreadLocal.withInitial`; a null supplier is always a programming error. |
| `javatools_utils/src/main/java/org/xvm/util/concurrent/BlockingQueueAdapter.java` constructor and guarded methods | Annotate `delegate`, `drainTo` target collection, `offer` value, and timeout `unit`. | `BlockingQueue` implementations do not permit null elements, and the adapter cannot operate without its delegate or time unit. `poll`, `peek`, and timed `poll` returns must stay nullable because null means empty/timeout. |
| `javatools_utils/src/main/java/org/xvm/util/CooperativelyCleanableReference.java` constructor | Annotate `cleaner`; do not annotate `referent` unless a separate API decision forbids null weak references. | The cleanup action is required. The referent follows `WeakReference` semantics and may be intentionally nullable. |
| `javatools/src/main/java/org/xvm/runtime/MainContainer.java` `start(Map<String, List<String>> mapInjections)` | Annotate `mapInjections` as `@NotNull`. | This is the inner runtime boundary. `Connector.start(...)` accepts null, and `InterpreterConnector.start(...)` normalizes null to `Map.of()` before calling this method. |
| `javatools/src/main/java/org/xvm/runtime/Fiber.java` constructor | Annotate `context` and `msgCall`. | A fiber is meaningless without its service context and call message; both are guarded immediately. |
| `javatools/src/main/java/org/xvm/runtime/NativeTemplateRef.java` factory/constructor | Annotate `sName` and `clzTemplate`. | Native template references are immutable lookup keys; both key parts are required. |
| `javatools/src/main/java/org/xvm/runtime/NativeTemplates.java` constructor and `get(Container)`, `get(Frame)`, `get(ClassTemplate)` | Annotate owner parameters. | These are central owner-local native-template lookup boundaries. Null owner inputs should fail before any fallback to stale global state is possible. |
| `javatools/src/main/java/org/xvm/runtime/ConstHeap.java` methods with explicit owner parameters | Annotate `container` in `ensureConstHandle`, `getConstHandle`, `saveConstHandle`, and `relocateConst`. | The explicit container parameter is the whole point of the owner-local heap. Return values for `getConstHandle` and `relocateConst` remain nullable by contract. |
| `javatools/src/main/java/org/xvm/runtime/OwnershipDiagnostics.java` public validation/dump boundaries | Annotate `root`, `expected`, `path`, `handle`, and the varargs container array. Consider element nullness separately. | The public diagnostics entry points reject null roots/handles. Helper methods such as `containerName(null)` and `identity(null)` intentionally accept null for reporting and should not be annotated. |
| `javatools/src/main/java/org/xvm/asm/ConstantAdoptionValidator.java` `validate(Constant source, Constant adopted)` | Annotate `source` and `adopted`. | This is an adoption boundary; the validator cannot reason about a missing source or adopted constant. |
| `javatools/src/main/java/org/xvm/asm/Parameter.java` `copyFor(MethodStructure method)` | Annotate `method`. | The cloned parameter must be owned by a concrete cloned method. |
| `javatools/src/main/java/org/xvm/asm/constants/TypeConstant.java` `isCovariantReturn` and `isContravariantParameter` | Annotate `pool`. | The current branch deliberately passes an explicit owner pool rather than using ambient current-pool state. `typeCtx` remains nullable by design. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTType.java` `makeHandle` and `makeForeignHandle` | Annotate `container` and `type`. | These factories create owner-bearing type handles and already reject null owners/types. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/reflect/xRTFunction.java` guarded factory and constructor boundaries | Annotate `NativeFunctionHandle`'s `op`, `makeAsyncNativeHandle`'s `frame` and `method`, `makeInternalHandle`'s `frame`/`container`/`function`, `makeHandle(Frame, MethodStructure)`'s `frame`, and owner-only helpers such as `ensureListMapType(Container)`. | Function handles carry owner-specific compositions and method metadata. The branch comments already identify container ownership as part of the API contract. |
| `javatools/src/main/java/org/xvm/runtime/template/_native/mgmt/xCoreRepository.java` `makeHandle(Container container)` | Annotate `container`. | The method validates that the requested owner matches the template owner. The field guard for `m_clzRepo` is not a parameter annotation target. |
| `javatools/src/main/java/org/xvm/javajit/registers/MultiSlot.java` constructor | Annotate `flavor`, `type`, `cd`, and `slotCds`; leave `slots` nullable. | Null `slots` has a documented fallback to Java-stack slots. The other guarded descriptors are required to model the register. |
| `javatools/src/main/java/org/xvm/tool/ResourceDir.java` public constructors | Annotate `resourceLoc`, `resourcePath`, and ideally list elements. | The Javadoc already says the file/list/list elements are non-null; the constructor rejects null locations. |
| `javatools/src/main/java/org/xvm/compiler/Compiler.java`, `Parser.java`, and `Lexer.java` constructors | Annotate guarded constructor parameters such as `stmtModule`, `errs`, `source`, and `errorListener`. | These are compiler front-end API boundaries where null is immediately rejected. |
| `javatools/src/main/java/org/xvm/type/Decimal32.java`, `Decimal64.java`, and `Decimal128.java` constructors | Annotate guarded `byte[]` and `BigDecimal` parameters. | Public value constructors reject null before format/range validation. |

## Should Annotate

These are probably correct, but they are broader, noisier, or likely to trigger
transitive annotation work.

| Site family | Why it is useful | Why not first |
| --- | --- | --- |
| ASM constant constructors with `"required"` guards, such as `Annotation`, `SignatureConstant`, `MapConstant`, `RangeConstant`, `ArrayConstant`, `NamedConstant`, `MethodConstant`, and many type-constant leaves | Most guarded constructor arguments are real non-null logical identities. | This is a wide API surface. Many parameters are arrays or element-sensitive collections, and some constructors have conditional null semantics. Do it package-by-package with tests. |
| Opcode constructors under `javatools/src/main/java/org/xvm/asm/op` | Many op constructors reject null `Argument`, `MethodConstant`, `StringConstant`, or array inputs. | The op hierarchy is large and mechanically repetitive. A broad annotation patch would be noisy and should follow any generated/factory audit. |
| `PropertyInfo` and `MethodInfo` owner-copy constructors | The body arrays and their elements must be non-null. | The constructors are private/internal and already have asserts plus element checks inside `Arrays.setAll`. Type-use array annotations would add imports/churn for limited caller benefit. |
| `ConstHeap` non-owner parameters such as `constValue`, `hValue`, and `constant` | These methods generally assume real constants/handles. | Only the owner `container` is explicitly guarded today. Annotate the owner first, then decide whether to add more guards or annotations after reviewing callers. |
| `NativeTemplates` accessor return types | Accessors such as `array()`, `type()`, and `module()` should return non-null templates. | There are many accessors. Return annotations are correct but mostly IDE documentation unless a nullness analyzer is added. |
| `OwnershipDiagnostics` varargs element nullness | The implementation rejects `containers[i] == null`. | Java varargs/type-use syntax can be hard to read. Add the parameter annotation first and only add element nullness if the chosen analyzer understands it well. |
| `Handy.checkElementsNonNull(Object[] ao)` and `Handy.require(String name, Object value)` | These are explicit guard helpers. | The whole point is to accept an unknown value and test it. Annotating `value` as `@NotNull` would make bad callers look impossible to static tools. A return annotation may be useful, but the parameter should stay nullable. |
| JIT descriptor constructors such as `JitTypeDesc` and `JitParamDesc` | Type/flavor/class descriptors are probably required. | Some JIT paths use null to mean unresolved or not yet representable. Add annotations only after a focused JIT nullness pass. |

## Do Not Annotate

Do not add `@NotNull` where null is meaningful or the guard is only local flow
control.

| Site | Do not annotate | Reason |
| --- | --- | --- |
| `javatools/src/main/java/org/xvm/api/Connector.java` `start(Map<String, List<String>> mapInjections)` | `mapInjections` as `@NotNull` | The API Javadoc explicitly permits null for no custom injections. `InterpreterConnector.start(...)` preserves that contract and normalizes null to `Map.of()`. |
| `javatools/src/main/java/org/xvm/api/InterpreterConnector.java` `start(...)` | `mapInjections` as `@NotNull` | This override is the nullable external boundary. The inner `MainContainer.start(...)` is the non-null boundary. |
| `javatools/src/main/java/org/xvm/runtime/template/reflect/xModule.java` `resolveClass` / `resolveType` | `structTS` as unconditional `@NotNull` | `structTS` is required only when `module == null`. Annotating it non-null would hide a real two-argument contract. |
| `javatools/src/main/java/org/xvm/asm/constants/IdentityConstant.java` `NestedIdentity(ConstantPool pool, GenericTypeResolver resolver)` | `pool` as unconditional `@NotNull` | `pool` is required only when `resolver != null`; when `resolver == null`, null pool is valid and stored as null. |
| `javatools/src/main/java/org/xvm/asm/Register.java` second constructor | `type` as unconditional `@NotNull` | Null type is permitted for special register IDs such as `A_DEFAULT`, `A_IGNORE`, and `A_IGNORE_ASYNC`. The first constructor can annotate `type`; the second cannot. |
| `javatools_utils/src/main/java/org/xvm/util/Handy.java` null-tolerant helpers | Parameters for `parentOf`, `resolveFile`, `navigateTo`, `listFiles`, `getExtension`, `toPathString`, `lazyAdd*`, and similar methods | These APIs intentionally use null as empty/default/no-result input. Annotating them non-null would contradict the Javadoc and behavior. |
| `javatools_utils/src/main/java/org/xvm/util/ListSet.java` element operations | Element `E e` as blanket `@NotNull` | Null is only rejected when `m_fSuppressNull` is true. Other instances can contain null. |
| `javatools_utils/src/main/java/org/xvm/util/Lazy.java` values and `get()` results | `T` result values as blanket `@NotNull` | `Lazy<T>` can cache null. Only the supplier/function objects are non-null, not necessarily their results. |
| Tool flow assertions in `Launcher`, `Runner`, and `Disassembler` | Local variables narrowed by nearby checks | These `requireNonNull` calls are mostly local flow assertions after option/module/path validation, not API contracts. They can stay as runtime assertions without parameter annotations. |
| `Objects.requireNonNullElse*` and `requireNonNullElse` sites | Fallback operands as `@NotNull` without a separate review | These sites default null values; they are not evidence that the original input should be non-null. |

## Likely Lint And Build Impact

Expected low impact:

- No dependency change is needed for `javatools` or `javatools_utils` main/test
  sources.
- `compileOnly` already matches existing source usage and avoids adding a
  runtime dependency to packaged artifacts.
- Plain javac, even with `-Xlint:all`, should not start enforcing JetBrains
  nullness contracts.

Likely warning sources if a nullness analyzer is added later:

- Annotating `Lazy<T>` too aggressively will create false positives because
  values may be null even when suppliers/functions are not.
- Annotating varargs and arrays can require type-use annotations for both the
  array reference and elements. A future analyzer may treat
  `@NotNull Container... containers` differently from non-null elements.
- Adding `@NotNull` to override methods from JDK interfaces, especially
  `BlockingQueueAdapter`, should be limited to contracts that the JDK interface
  permits. Null-returning methods such as `poll()` and `peek()` must stay
  nullable/unannotated.
- Public API annotations in class files may help external tooling, but
  `compileOnly` means downstream source projects do not automatically receive an
  annotation dependency from this artifact's published runtime dependencies.
  That is already the established pattern in this repo.
- Broad ASM/op constructor annotation patches will add many imports and may
  expose inconsistent historical null contracts. Keep those package-scoped and
  review conditional-null constructors before applying a mechanical rule.

## Recommended First Patch

A small implementation PR should avoid a repository-wide nullness sweep. The
lowest-risk first wave is:

1. Add `@NotNull` imports and parameter annotations to `Lazy`,
   `TransientThreadLocal`, `BlockingQueueAdapter`, `CooperativelyCleanableReference`,
   and `Scope.requireNonNull()` return.
2. Add owner-boundary annotations to `MainContainer`, `Fiber`,
   `NativeTemplateRef`, `NativeTemplates`, `ConstHeap`, `OwnershipDiagnostics`,
   `xRTType`, `xRTFunction`, `xCoreRepository`, and `TypeConstant` owner-pool
   methods.
3. Add isolated public constructor annotations for `ResourceDir`,
   `Compiler`/`Parser`/`Lexer`, and decimal value constructors.
4. Leave runtime guards in place.
5. Run focused compile checks for the touched projects, for example:

   ```bash
   ./gradlew :javatools_utils:compileJava :javatools:compileJava --info
   ```

Do not combine `clean` with any other Gradle task.
