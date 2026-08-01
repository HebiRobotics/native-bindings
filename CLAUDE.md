# CLAUDE.md

Guidance for working in this repository. The README covers usage. This file covers the
things that are easy to get wrong.

## What this is

An annotation processor that turns annotated Java into a GraalVM C ABI plus C, C++,
Python and MATLAB wrappers. Extracted from a closed-source HEBI codebase (`native-util`
and `native-generator`) to be open sourced.

| Module | Contents |
|---|---|
| `bindings-runtime` | Annotations and runtime support. The only module an annotated library needs |
| `bindings-generator` | The processor and the four language generators. Used as an `annotationProcessorPath` |
| `bindings-tests` | Annotated test API, reference file comparison, and the four language suites |

## Reference repositories

These sit next to this one and are **read-only**. Never modify them.

| Path | What it is |
|---|---|
| `../native-util`, `../native-generator` | The original implementation this was extracted from |
| `../native-charts` | The real consumer of the annotations. Generated output in `target/image` |
| `../hebi-charts-examples` | C++, Python and MATLAB usage examples |
| `../protocol` | Source of `LibraryDescription.proto` |
| `../publish-native-charts.yml` | The release CI. The reference for how the two-stage build works |

## Origin mapping

| Original | Here |
|---|---|
| `us.hebi.ffi.generator.annotations.*` (annotation types) | `us.hebi.graalvm.bindings.annotations.*` in bindings-runtime |
| `us.hebi.ffi.generator.annotations.*` (Annotated* model classes) | `us.hebi.graalvm.bindings.generator.annotations.*` |
| `us.hebi.ffi.generator.{generators,bindings}.*` | `us.hebi.graalvm.bindings.generator.{generators,bindings}.*` |
| `us.hebi.protocol.api.*` | `us.hebi.graalvm.bindings.model.*` (proto copied in, `java_package` changed) |
| `us.hebi.ffi.util.*` | `us.hebi.graalvm.bindings.runtime.*` |
| `-Ahebi.nativegen.*` | `-Abindings.*` |

Dropped during extraction: the `parser/` package (shelled out to `clang`), `CArrayConverter`
(depended on `us.hebi.math.data.Transform`), `com.google.mug:dot-parse`, lombok, and the
antrun step that deleted `META-INF/services`.

There are two `MethodFlag` enums on purpose. The annotation API has a plain one in
`bindings-runtime`, the descriptor model has the protobuf one. `AnnotatedCLibMethod`
maps between them by name.

## The build has two stages

Stage 1 is `mvn verify -Pnative`. It runs native-image with
`--native-compiler-path=${native.compiler}`, which points at `compiler-<os>` in
`bindings-tests/target/image/static`. Those wrappers intercept the link step and
additionally emit a **static** library plus `libs.cmake` and `opts.cmake` into the same
directory.

The wrappers are **generated**, from resources in `bindings-generator` next to the other
cmake templates. Annotation processing runs at `compile` and native-image at `package`, so
the script exists by the time it is needed, and a consuming project does not vendor
anything. They are loaded raw rather than as templates, because `compiler-windows.bat` is
full of `%%` variables that a template pass would eat. The processor sets the executable
bit on the `.sh` variants, which the previously checked in copies never had.

Stage 2 runs CMake over `target/image/static`, where the generated `CMakeLists.txt` lives.
It links the static library into the final shared library and, when `MATLABROOT` is set,
builds the MEX gateway. Output lands in
`target/native-build/release/bindings_tests/bindings_tests/lib/<platform>/`.

**Stage 1 alone is not enough.** Its library only exports the `__`-prefixed `@CEntryPoint`
symbols. The `bindings_tests_*` wrappers the generated header declares are compiled from
`bindings_tests_c.cpp` during stage 2. Anything linking against the C or C++ headers has
to use the stage 2 output.

`scripts/build-native.{bat,sh}` runs both stages and stages the results. Use it rather
than calling Maven directly.

## Spans

A span is a `(pointer, length)` pair crossing the ABI as one by-value struct, so bulk
array methods need no separate count argument and wrappers never reconcile lengths. A
method that wants equal lengths checks once in Java (`TestArrays.dotProduct`).

- Parameters are `DoubleSpan`, a `@CStruct` interface in `bindings-runtime` and therefore
  a zero-allocation word type. Convenience accessors live in the static `Spans` class,
  not on the interface (see the trap below). JVM-mode tests construct one via
  `CTypes.toDoubleSpan(...)`.
- Returns are plain `double[]`. The entry point copies them into a per-thread scratch
  buffer via `CTypes.toTempDoublePointer` and fills a trailing `_spanOut` struct. The
  buffer is valid until the thread's next call, the same contract as string returns, and
  every wrapper copies out immediately.
- There are two struct names on purpose. The probe-only header `hebi_structs.h` (a
  runtime resource that `SpanDirectives` extracts to a temp dir at image build) declares
  `DoubleSpan`. The generated user headers declare a per-library `<lib>_double_span`.
  Only layout and symbols cross the linker, so the names never meet.
- The C shim passes addresses of its by-value parameters into the struct-pointer entry
  points. Wrappers map spans to `std::vector<double>`, numpy arrays and MATLAB vectors.
- Double only for now. Other element types are the same mechanical addition.
- The processor rejects `DoubleSpan` returns (declare `double[]`) and `double[]`
  parameters (declare `DoubleSpan`), covered in `ProcessorValidationTest`.

## Verification

```
mvn clean verify                                     assert against reference files
mvn clean verify -Dbindings.updateReference=true     rewrite them, then review git diff
scripts\build-native.bat                             both stages
scripts\run-tests.bat [c] [cpp] [python] [matlab]
```

The `clean` is not optional, see the bitten list for the two ways its absence produces
wrong results.

`mvn verify` compares every generated file against
`bindings-tests/src/test/resources/reference/`. **Any change to a generator changes those
files.** A refactor that is meant to preserve behaviour should produce an empty diff. Do
not hand-edit the reference files, regenerate them.

`ProcessorValidationTest` uses `compile-testing` to drive the processor over snippets that
must be rejected. These are the only tests that can cover the failure paths, since
`src/main/java` has to compile.

## Environment

The assistant has a working PowerShell and can run Maven, CMake, the build scripts and
all four test suites directly. Verify changes by running the scripts rather than only
reading generated output. Do not claim something builds without evidence.

Under PowerShell, `-D` arguments have to be quoted (`"-Dbindings.updateReference=true"`)
or Maven parses the part after the dot as a lifecycle phase.

The machine is Windows with MSVC already on `PATH` (do not add vcvars handling), CMake,
Python and MATLAB R2022a at `C:\Program Files\MATLAB\R2022a`. The build scripts derive
`MATLABROOT` from `matlab` on `PATH`.

## Things that have already bitten

- **`annotationProcessorPaths` is an isolated classloader** and does not inherit
  `provided`-scope dependencies. `native-image-base` has to be listed explicitly there or
  the processor dies with `NoClassDefFoundError: org/graalvm/nativeimage/ObjectHandles`.
- **`String.formatted` eats `%%`.** It collapses `%%TOKEN%%` to `%TOKEN%` before
  `TemplateString` ever sees it. Never call `.formatted()` on a string containing template
  markers.
- **Use the existing template tokens.** Both `%%SUPER_INIT%%`/`%%CAST%%`/`%%LIB%%` and the
  resource templates already resolve names and paths. Do not rebuild symbol names inline.
  Unresolved tokens pass through inner renders and get substituted by the outer one.
- **`IndentingTextRenderer` is the only templating helper.** All four generators use it for
  `%%KEY%%` substitution, and ST4 is only for whole-file resource templates. The call order
  decides when values land, and that is observable: `set(...)` before `add(...)` substitutes
  during the append, which strips trailing spaces, while `IndentingTextRenderer.of(text)`
  appends first so `render()` substitutes afterwards and keeps them. MATLAB and C build
  line by line and set first. Python and C++ splice pre-indented blocks and use `of`.
- **A `@CDefault` on a `@CLibIndex` parameter shifts into the target language, it is not
  converted.** Defaults are stored zero based like the rest of the model, so
  `getMatlabDefaultValue` emits `default + 1` and `getIndexInputConversion` then subtracts
  unconditionally. Emitting the raw default and letting the conversion run over it
  decrements it twice, which is invisible for a `@CDefault("0")` and wrong for every other
  value. `TestIndices.fromRow` pins it. A `matlabOverload` that sets `parameters` or `call`
  owns its own conversion and gets none generated, but one that sets only `postBody` gets
  both, in that order. Do not combine them.
- **Descriptor flags are only set when they differ from the declared proto default**, which
  keeps `api.json` free of rows that say nothing. The rule is "omit what matches the
  default", not "omit false": `instantiable` defaults to `true`, so it is the `false` case
  that gets recorded. Setting it unconditionally is what a naive cleanup gets wrong, and it
  silently turns every non-instantiable class into a handle type. Nothing reads these
  through `hasX()`, so an absent field resolves to the declared default on read.
- **The renderer only indents after a newline it has already written.** A fresh renderer at
  indent level 1 does not indent its first line. Seed it with the text that ends the previous
  line, the way `SignatureBuilder` passes `") {\n"` into `of`.
- **`ImageInfo.inImageRuntimeCode()` must not be cached** in a `static final`. It would be
  folded at image build time, where the builder JVM is not a native image.
- **cmd `goto` inside a called subroutine is unreliable.** `run-tests.bat` uses `if`
  blocks for dispatch on purpose.
- The processor sets `hasProcessedElements` only after a class fully succeeds, so a
  rejected class skips `postRound` and its diagnostic is not buried by a second exception.
- **`updateReference` needs a clean build.** A generator-only edit does not dirty
  bindings-tests, so the processor is not re-run and
  `mvn verify -Dbindings.updateReference=true` silently copies the previous run's output
  as the new references. Always `mvn clean verify`.
- **The reference check needs a clean build after a native build.** Stage 1 drops its
  artifacts (`.lib` files, `libs.cmake`, the compiler wrappers) into the compared
  `target/image/static` directory, so a plain `mvn verify` false-fails on "the set of
  generated files changed".
- **Word types must not be compared to `null`.** `pointer == null` is a native-image
  build error ("Should not compare Word to Object"). Use `isNull()`.
- **`@CStruct` interfaces may only contain annotated accessors.** Default or static
  methods fail the image build with "Unexpected method without annotation", which is why
  the span helpers live in the separate `Spans` class.

## Generator gaps the test API exposed

native-charts never exercises these combinations, so they were untested before this repo.
Expect more of the same shape: anything the charts API does not do is probably broken.

Fixed: `CHAR16` as a return type, `@CLibMethod(name = ...)` renaming the Java call as well
as the symbol, `char**` in the MEX gateway, subclass constructors in all three wrappers,
Python static overloads needing an explicit `cls`, MATLAB echoing non-const pointer params
as extra outputs (dropped, buffers are caller-owned and mutated in place like the C ABI),
`@CDefault` string literals reaching MATLAB double-quoted (converted to single-quoted char
arrays, the counterpart of Python's `convertCDefaultParamString`), the C++ constructor
factory colliding with wrapped methods (now a per-class free function in the internal
namespace).

Open:

1. `cppOverloads` ignores `postBody`, and only the MATLAB generator emits `postCall`.
   For C++ put the whole prologue in `body`. `TestOverloads.echoIndex` pins the
   behaviour down in all four languages.
2. A class may declare at most one constructor. The MATLAB generator asserts this.
3. Only leaf classes may declare constructors.
4. Structs are unsupported, except the span struct as the one special case.
   `@CExternalEnums` is declared but never read.
5. The download URL is hardcoded to `files.hebi.us` in the Python loader, the C++ cmake
   and `locate_binary.m`. A configurable version was written and reverted for being too
   large to review. If revisited, keep it to one `%%DOWNLOAD_URL%%` token in the existing
   templates rather than adding a proto field and splitting the cmake template.

## Working style

- Small, reviewable changes. One issue at a time. A nine-file change to make one string
  configurable was rejected, correctly.
- Do not refactor while fixing. The extracted code is tested and working.
- Prefer disabling a test with a `TODO:` naming the exact cause over a speculative fix.
- Generated output is read by humans. Keep it clean.
- Delegate only wide independent work. Subagents have produced hand-edited reference
  files and `.formatted()` bugs that needed correcting.
- Prose in docs and comments: no em dashes, no semicolons as sentence connectors.
