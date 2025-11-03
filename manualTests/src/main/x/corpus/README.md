# TestSimple.x Test Corpus

This directory contains 49 unique test cases extracted from the complete git history of `TestSimple.x`, spanning over 5 years of development (2020-06-25 to 2025-10-18).

## Overview

Gene used `TestSimple.x` as a scratch file to create minimal reproduction cases for compiler bugs, runtime issues, and language feature tests. This corpus preserves all the unique, well-formed test cases from that history.

## Statistics

- **Total commits analyzed**: 662
- **Unique test versions**: 49 (100% unique content)
- **Date range**: June 2020 - October 2025 (5+ years)
- **Test coverage**: Compiler bugs, runtime issues, language features, stdlib APIs
- **All tests**: Well-formed, compilable XTC modules
- **Runnable tests**: 82% (40/49 have run() methods)

## Test Categories

### Priority 1: Most Valuable (6 tests)
Comprehensive tests with significant code and complexity:

1. **IntLiteralParserWithRadixSupport.x** (4,013 bytes) - Complete IntLiteral parser with radix support, sign handling, and error handling
2. **MultiServiceConcurrencyWithTimers.x** (3,624 bytes) - 10 concurrent services with timers, futures, and coordination
3. **Base64CodecComprehensiveFuzzing.x** (1,686 bytes) - Base64 codec with 10,000 iteration fuzzing test
4. **ArrayOrderedSetUsagePatterns.x** (1,648 bytes) - Comprehensive ArrayOrderedSet usage patterns
5. **ComplexDefiniteAssignmentWithSwitch.x** (1,277 bytes) - Complex definite assignment with switch/conditionals
6. **ResourceHandlingModuleRebuild.x** (671 bytes) - Resource handling and module rebuild detection

### Compiler Type Inference Bugs (8 tests)
- TernaryExpressionTypeCalculation.x
- TernaryExpressionEmptyArrayTypes.x
- GenericTypeDrivenNumericConversions.x
- InferenceAnalysisWhileDoWhile.x
- BjarneLambdaCodeGeneration.x
- NameExpressionNarrowType.x
- ConditionNegations.x
- UnionTypesStringableRandom.x

### Compiler Code Generation Bugs (6 tests)
- CmpChainExpressionCodeGeneration.x
- BASTProductionForUnreachableLoops.x
- ASTProductionArraySliceBug.x
- IsAAnnotationsImplicitServices.x
- AnonymousClassConstructionCompilation.x
- IPAddressParsingAndValidation.x

### Definite Assignment Analysis (3 tests)
- DefiniteAssignmentConditional.x
- MultiConditionIfStatements.x
- (ComplexDefiniteAssignmentWithSwitch.x - see Priority 1)

### Annotation Processing (4 tests)
- AnnotationsOnInnerClassProperties.x
- VirtualAnnotationConstructionCheck.x
- UnreachableAnnotatedMethodsWarning.x
- AutoFutureAnnotationsAsyncCalls.x

### Language Features (6 tests)
- KeywordTypesImmutableConst.x
- TypedefSupport.x
- ImportsInsideNestedBlocks.x
- VersionLiteralParsing.x
- PropertyNamedDefaultForConsts.x
- ShortHandPropertyDeclarations.x

### Service & Concurrency (3 tests)
- ServiceReentrancyDuringConstInit.x
- FiberLeakWithTimerSchedule.x
- (MultiServiceConcurrencyWithTimers.x - see Priority 1)

### Web/Networking (2 tests)
- URIMatching.x
- (Base64CodecComprehensiveFuzzing.x - see Priority 1)

### Native Method Bugs (3 tests)
- CallChainWithNativeBodies.x
- RandomFillNativeImplementation.x
- MapPutAllTransientImplementation.x

### Collection APIs (2 tests)
- SetOperationsAddAll.x
- (ArrayOrderedSetUsagePatterns.x - see Priority 1)

### Simple Compiler Bugs (6 tests)
Minimal reproduction cases for specific issues:
- ElementTypeResolutionUnionTypes.x
- ConsoleInjectionAssertion.x
- LambdaNPE.x
- TypedefAssertion.x
- TypedefMethodCall.x
- LazyPropertyOverride.x

### Property & Class Features (1 test)
- NestedPropertyHandling.x

### Miscellaneous (3 tests)
- CircularConstantInitialization.x
- TypeDumpUsage.x
- EarlyDirectoryStructureTest.x (historical - from 2020-06-25)

## Size Distribution

- **Very Small (< 250 bytes)**: 11 tests - Minimal reproduction cases
- **Small (250-500 bytes)**: 21 tests - Focused single-feature tests
- **Medium (500-1000 bytes)**: 11 tests - Multi-feature tests
- **Large (1000+ bytes)**: 6 tests - Comprehensive feature tests

## Language Features Covered

Across all tests, the following XTC language features appear:
- Module declarations (100%)
- Annotations (@Inject, @Future, @Lazy, etc.) (94%)
- Basic types (Int, String, Boolean, etc.) (86%)
- Tuples (86%)
- Void methods (86%)
- Conditionals (53%)
- Loops (37%)
- Classes (31%)
- Imports (31%)
- Assertions (22%)
- Exception handling (16%)
- Ternary operator (14%)
- Increment/decrement (14%)
- Services (12%)
- Static members (12%)
- Async/Futures (10%)
- Mixins (8%)
- Switch statements (8%)
- Lambdas (8%)
- Interfaces (6%)

## Temporal Evolution

### Early Period (2020-2021): Foundation
- Basic language features
- Service and concurrency testing
- Type system fundamentals
- Definite assignment logic

### Middle Period (2021-2022): Maturation
- Complex type inference scenarios
- Lambda and function types
- Annotation processing
- URI and web-related features

### Recent Period (2023-2025): Refinement
- Edge cases and corner cases
- Typedef support
- Compiler assertion fixes
- Native method handling
- Identity mode types

## Usage

Each test file is a standalone, well-formed XTC module that can be:
1. Compiled with the XTC compiler to verify compilation behavior
2. Run (if it has a run() method) to test runtime behavior
3. Used as a regression test for the specific bug/feature it tests
4. Referenced as an example of XTC language patterns

## Source

All tests were extracted from the git history of `manualTests/src/main/x/TestSimple.x` using git log analysis and content deduplication. Each test represents a unique point in time where Gene was debugging a specific issue or testing a specific feature.

## Naming Convention

Test files are named descriptively based on what they test:
- Feature tests: Named after the feature (e.g., TypedefSupport.x)
- Bug reproductions: Named after the bug type (e.g., CmpChainExpressionCodeGeneration.x)
- API tests: Named after the API (e.g., Base64CodecComprehensiveFuzzing.x)

The goal is to make it immediately clear what each test is for without needing to read the commit history.
