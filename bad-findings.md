# Remaining Global Must-Fix / Must-Audit

This is the short reviewer-facing index for the remaining global-state and
runtime-ownership risks on `lagergren/lazy-instance`.

The detailed sources are:

- `docs/reentrancy/must-audit-backlog.md`
- `docs/reentrancy/runtime-ownership-hardening-ledger.md`
- `docs/reentrancy/plans/xvm-memory-model-hygiene.md`
- `docs/reentrancy/plans/clone-free-adoption-plan.md`

## Remaining Must-Fix / Must-Audit Items

1. `Constant.adoptedBy(...)` base shallow clone contract

   Still architecturally unsafe. Every `Constant` subclass with owner-local
   helper state must explicitly declare copy/adoption behavior. The validator
   catches known bad categories, but the base shallow clone remains a bad
   default until adoption is explicit per subclass.

2. Runtime-published `ConstantPool` mutation

   The known `ClassComposition.ensureAccess(...)` late-registration cases are
   fixed, but first-time construction after runtime publication can still grow
   or rewrite a pool. The correct larger fix is a freeze/warmup boundary, or a
   hard split between mutable compiler/linker pools and immutable runtime pools.

3. `ConstantPool.register(...)` publishes before recursive registration ends

   A constant can enter list/map storage before child registration and
   owner-sensitive rewrites complete. That is not a safe publication model. It
   needs a recursive build/adopt phase before map/list publication, or a
   single-owner registration phase with explicit assertions.

4. Remaining scoped/ambient owner bridges

   Semantic `ConstantPool.getCurrentPool()` lookup is removed, but `withPool(...)`,
   `ThreadLocal`, `TransientThreadLocal`, and `ScopedValue` style context remain
   transitional mechanisms. Each remaining use needs proof or replacement with
   explicit owner parameters.

5. Live runtime handles embedded in constants

   `HandleConstant` now rejects movement of already-owned live handles, but the
   full annotated/runtime type path still needs audit. Runtime handles are owner
   state, not pure serialized constant data.

6. Remaining `ObjectHandle.cloneAs(...)` and `ConstHeap` clone paths

   Cross-owner direct masking and same-owner `GenericHandle` inflated-ref view
   backing are fixed in this branch. The remaining audit is the base
   `ObjectHandle.cloneAs(...)` contract for other subclasses and clone fallback
   paths such as `ConstHeap.relocateConst(...)`.

7. Runtime `Op` address/link lifecycle

   Frame-derived owner caches, owner-bearing switch tables, `JumpNFirst`,
   `JumpNSample`, first decoded-code publication, and guard descriptor runtime
   writes are fixed. The remaining audit is the broader
   `MethodStructure.Code` lifecycle: mutable compiler/assembly code should
   eventually be split from an immutable runtime `ResolvedCode` snapshot or an
   explicit runtime publication/freeze phase.

8. Owner-bearing metadata caches

   `ClassComposition`, `TypeConstant`, `TypeInfoReal`, `ConstantPool` volatile
   maps, and field-layout caches still need owner/key/invalidation
   classification. Some are likely fine after warmup; the API does not yet
   encode that assumption.

9. Weak/identity registries

   `Runtime.f_containers` is fixed. Remaining examples include
   `ServiceContext.m_mapTransient`, `ServiceContext.f_mapOpInfo`, and
   `ConstantPool.f_setValidPools`.

10. JIT global/static owner model

    JIT still has generated statics, `Ctx.Current`, `Ctx.MD_inject`, and
    `Xvm` constructor publication concerns. That is a separate JIT
    proof/refactor before anyone can claim JIT reentrancy.

11. Compiler/global state backlog

    Compiler counters are separate branch/PR territory. Compiler AST/context
    mutation remains must-audit for incremental and parallel compilation.
