 cat > /tmp/testsimple_versions/CATEGORIZED_LIST.txt << 'EOF'
   TestSimple.x Versions - Categorized by Feature/Bug Type
   ========================================================

   COMPILER TYPE INFERENCE BUGS (15 versions)
   -------------------------------------------
   91b65ea9 - Ternary expression type calculation
   f6d69ffb - Ternary expression empty array types
   d411f0ae - Map de-serialization logic
   9bd4e96f - Generic type-driven numeric conversions
   240aad71 - NameExpression.narrowType() logic
   be6e6824 - ListExpression type inference logic
   65fe4cae - Condition negations
   babb1c03 - Inference analysis for while/do-while loops
   a3c2a060 - TypeInfo for colliding interface methods
   4f0ddd66 - Bjarne-lambda code generation
   2bfcb646 - Run-time assertion (Console injection)
   d3152f8e - Identity mode typedefs
   21352ecc - Typedef method resolution
   e87b1d32 - Tuple return support for functions
   8c99cbcd - Compiler NPE on lambda returns

   COMPILER CODE GENERATION BUGS (9 versions)
   ------------------------------------------
   1e094dc0 - CmpChainExpression code generation
   c72951e7 - BAST production for unreachable for/while
   eefdd14c - AST production bug for array slices
   73f0ed5f - Compiler assertion (general)
   36ddc4e1 - "isA" for annotations on implicit services
   ac9f3644 - Anonymous class construction compilation
   e417538c - Method type related compiler issues
   68e822ba - Inner property compilation bugs
   13516d60 - IPAddress parsing and validation

   DEFINITE ASSIGNMENT ANALYSIS (5 versions)
   -----------------------------------------
   e5d0155c6 - Complex definite assignment with switch
   39ffabe9 - Definite assignment bug (conditional)
   ed7f0d7b1 - Definite assignment bug in WhileStatement
   5b366af8 - Multi-condition IfStatements
   babb1c03 - Inference for while/do-while loops

   SERVICE & CONCURRENCY (4 versions)
   ----------------------------------
   2a402c6e - Multi-service fiber association (COMPLEX)
   f1dfe1df - Service reentrancy during const init
   cc5a5fce - Fiber leak with Timer.schedule
   ce1a6b95 - Auto Future annotations for async calls

   ANNOTATION PROCESSING (4 versions)
   ----------------------------------
   f3eba470 - Annotations on inner classes/properties
   b0dfe6bb - Virtual annotation constructions check
   a603922e - Warning for unreachable annotated methods
   c45ed52b - Virtual constructors ops/BAST

   LANGUAGE FEATURES (8 versions)
   ------------------------------
   91380a22 - Keyword types (immutable, const, etc)
   3307615b - Typedef support
   ea398ee7 - Imports inside nested blocks
   9de8e8d8 - Version literal parsing
   5531b330 - IntLiteral parsing (COMPREHENSIVE)
   86d4889f - Property named "default" for consts
   576abf16 - Short-hand property declarations
   68d66d72d - Resource handling and module rebuild

   WEB/NETWORKING (3 versions)
   ---------------------------
   8de586e9 - URI matching
   5feab380 - Base64 codec (COMPREHENSIVE FUZZING)
   13516d60 - IPAddress class usage

   NATIVE METHOD BUGS (3 versions)
   -------------------------------
   a642318a - Call chain with native bodies
   969f6ec7 - Random.fill() native implementation
   9647bfe4 - Map.putAll() transient implementation

   COLLECTION APIs (3 versions)
   ----------------------------
   e1f487b0 - ArrayOrderedSet usage
   57194a40 - Set operations (addAll)
   9647bfe4 - Map.putAll() operations

   MINIMAL COMPILER BUGS (8 versions)
   ----------------------------------
   957ca4e3 - Element type resolution (union types)
   2bfcb646 - Console injection assertion
   8c99cbcd - Lambda NPE
   d3152f8e - Typedef assertion
   21352ecc - Typedef method call
   1e094dc0 - Comparison chain
   a642318a - Null.makeImmutable()
   2dd5d4f1 - @Lazy property override

   PROPERTY & CLASS FEATURES (3 versions)
   --------------------------------------
   36d1b3d1 - Nested property handling
   e0d4a757 - Outer/Inner type handling
   2dd5d4f1 - @Lazy property override

   MISCELLANEOUS (3 versions)
   --------------------------
   5e640838 - Circular constant initialization
   5b3e169c - Console API (readLine)
   b45e0bab - Type.dump() usage

   HISTORICAL FOUNDATION (2 versions)
   ----------------------------------
   2e67fd09 - Early directory structure test (2020-06-25)
   f1dfe1df - Early service test (2020-07-06)

   SUMMARY BY SIZE:
   ================
   Very Small (< 250):  11 versions (minimal reproductions)
   Small (250-500):     21 versions (focused tests)
   Medium (500-1000):   11 versions (multi-feature)
   Large (1000+):        6 versions (comprehensive)

   RECOMMENDATIONS:
   ================
   Priority 1 (Must Include): 6 versions
     - 5531b330 (IntLiteral)
     - 2a402c6e (Concurrency)
     - 5feab380 (Base64)
     - e1f487b0 (Collections)
     - e5d0155c6 (Definite Assignment)
     - 68d66d72d (Resources)

   Priority 2 (High Value): 14 versions
     - All annotation processing tests
     - All language feature tests
     - Complex type inference cases

   Priority 3 (Consider): 29 versions
     - Simple bug reproductions
     - May consolidate similar patterns
   EOF
   cat /tmp/testsimple_versions/CATEGORIZED_LIST.txt
   Create categorized reference

