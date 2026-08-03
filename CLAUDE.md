# CLAUDE.md

Guidance for working in this repository. The README covers usage. This file covers the
things that are easy to get wrong. The longer form notes live in `.claude/`:
`architecture.md` for how the pieces fit together, `plans.md` for prospective work items
and release blockers, and `style.md` for how code here is written.

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

Reference **code** is read-only. Never modify it. The exception is notes: work items
and findings that belong to the source repo go into
`../HEBI-Java/.claude/plans.md` rather than into this repo's notes, and each
repo's notes describe only that repo. The checkout differs per machine, so check what
exists before citing paths:

- On the machine with the full source repo, `../HEBI-Java` holds everything:
  `native-util` (still a live dependency there), `native-charts` (the real consumer,
  now migrated onto this repo's artifacts, generated output in `target/image`, demo
  apps in `src/test/java`), `protocol` (former origin of `LibraryDescription.proto`),
  the original `native-generator` only in git history (removed 2026-08-02, clang
  parser now in native-sdk test sources),
  `.github/workflows/publish-native-charts.yml` (the release CI and the reference for
  the two-stage build), and its `CLAUDE.md` plus `.claude/` (source repo docs, whose
  extraction plan in `plans.md` item B.1 is superseded by this repo).
- **Never look above the `claude/` parent directory.** Everything this repo may read
  lives under `C:\code\HEBI\claude\`, and the only valid source checkout is
  `../HEBI-Java` (a pristine copy kept in sync by the author). Directories above or
  beside `claude/`, such as `C:\code\HEBI\HEBI-Java`, are stale working copies that
  must not be read, cited or evaluated.
- On the other machine only a small subset of the `native-charts` project is available.
  Everything not in that subset has to come from this repo's own reference files and
  the notes in this file. Do not invent paths, and record here anything from the full
  repo that turns out to be needed.

## Origin mapping

| Original | Here |
|---|---|
| `us.hebi.ffi.generator.annotations.*` (annotation types) | `us.hebi.graalvm.bindings.annotations.*` in bindings-runtime |
| `us.hebi.ffi.generator.annotations.*` (Annotated* model classes) | `us.hebi.graalvm.bindings.generator.annotations.*` |
| `us.hebi.ffi.generator.{generators,bindings}.*` | `us.hebi.graalvm.bindings.generator.{generators,bindings}.*` |
| `us.hebi.protocol.api.*` | `us.hebi.graalvm.bindings.model.*` (proto copied in, `java_package` changed) |
| `us.hebi.ffi.util.*` | `us.hebi.graalvm.bindings.runtime.*` |
| `-Ahebi.nativegen.*` | `-Abindings.*` |

Dropped during extraction: the `parser/` package (shelled out to `clang`, now living in
native-sdk's test sources), `com.google.mug:dot-parse`, lombok, and the antrun step that
deleted `META-INF/services`. `CArrayConverter` was dropped at first and later ported in
trimmed form (no `Transform` overload, that one call site keeps a private helper in
native-charts). Its `Unsafe` fast path is also the reason `bindings-runtime` depends on
`us.hebi:glue`, and its element loops double as the HotSpot mock path because the mocks
throw from `rawValue()`.

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

## Allocation policy

The generated bindings are used in low latency contexts, so **prefer zero allocation in
steady state**. A call through a wrapper should allocate nothing on the Java heap, nothing
in the C++ wrapper and nothing in the shim. Setup and failure paths are exempt, because
neither happens in a hot loop: image startup, isolate creation, the first call on a thread
that has to grow a scratch buffer, and everything on a throw may allocate freely.

How hard this binds, per language:

- **C** is allocation free today and stays that way. That is the contract the rest is
  measured against.
- **Java entry points** allocate nothing per call. The per-thread scratch buffers exist for
  this reason.
- **C++** raw pointer and span overloads must be allocation free. The convenience overloads
  that return `std::vector` or `std::string` allocate by construction, which is fine as long
  as an allocation free path sits next to each one rather than instead of it.
- **Python and MATLAB** are best effort. CPython boxes return values and MATLAB allocates
  mxArrays on every call, so neither can be made allocation free. Avoid gratuitous per call
  allocation, do not contort a design for it.

A new generated shape is judged against this. If a feature can only be expressed with a per
call allocation, it needs an allocation free alternative alongside it.

Where the current output does allocate per call, all of it in the string and span return
paths, and none of it yet fixed:

- `CTypes.toTempDoublePointer` calls `ByteBuffer.asDoubleBuffer()` on every call, which
  builds a fresh view buffer. The view does not escape, so partial escape analysis may
  remove it, but that is not a property to rely on. Cache the view next to the `ByteBuffer`.
- A `double[]` return allocates that array in Java on every call and discards it right after
  copying it into the scratch buffer. A caller provided output span removes the allocation
  and the copy together.
- `CTypes.toTempCString(String)` calls `String.getBytes`, one byte array per call.
- The C++ span return copies into a fresh `std::vector`, and a string return into a fresh
  `std::string`.
- `<lib>_dynamic.hpp` resolves each symbol into a function local `static`, so every call
  pays a thread safe init guard check. Not an allocation, but the same kind of steady state
  cost, and linking the static library avoids it.

Nothing in the build checks any of this. See item 11 in `.claude/plans.md`.

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
Python and MATLAB R2022a. The build scripts derive `MATLABROOT` from `matlab` on
`PATH`, and the MEX SDK (headers and import libs) can be found in `${MATLABROOT}`.

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
- **javac maps `-Akey=` to a null value, not to an empty string.** An unset property in a
  consuming pom produces exactly that, so an option whose empty value is meaningful has to
  read the map directly instead of relying on `getOrDefault`, which only fires when the key
  is absent. `getDownloadUrl` is the one that bit.
- **Only the generated Java is nesting aware, and it has to be built from
  `ClassName.get(element)`.** `ClassName.get(package, simpleName)` silently produces a
  top-level `pkg.Inner` for a nested `pkg.Outer.Inner`, so the entry points import a type
  that does not exist and the compile fails with a bare "cannot find symbol". This is what
  the old "inner classes hang the processor" TODO really described, the hang itself came
  from the commented-out handling that processed nested classes a second time. The wrapper
  class is always top level, so `getWrapperType` joins the whole nesting path
  (`Outer_Inner$CEntries`). Join the path rather than taking the innermost name, or
  `Outer.Inner` and a top-level `Inner` write to the same file and the second one dies in
  `AbstractGenerator` with a swallowed `FilerException`. `AnnotatedClibType.getApiTypeWrapperClass`
  derives the same name independently for cross-class handle references, so the two must
  change together, and `CEnumGenerator.getGeneratedClassName` joins the path for the same
  reason. That last one collided without any nesting at all, since two top-level classes
  each declaring a `Color` enum both wanted `Color$$CEnum`. Nothing outside generated Java is affected: every C, C++, Python and
  MATLAB symbol comes from the `@CLibClass` value, which is why nesting `TestCircle` inside
  `TestShape` left all four languages' reference files byte identical.
- **Word types must not be compared to `null`.** `pointer == null` is a native-image
  build error ("Should not compare Word to Object"). Use `isNull()`.
- **`@CStruct` interfaces may only contain annotated accessors.** Default or static
  methods fail the image build with "Unexpected method without annotation", which is why
  the span helpers live in the separate `Spans` class.
- **The static library defaults to the release CRT on MSVC**, in `<lib>_static.cmake` and
  again on the consumer's target in `target_add_<lib>_static`, both only when the consumer
  has not chosen already. CMake honours `MSVC_RUNTIME_LIBRARY` only under `CMP0091 NEW`,
  so a consumer declaring `cmake_minimum_required(VERSION 3.14)` or older gets it silently
  ignored and still fails with `LNK2038`. Only the C++ compiled libs carry the
  `/FAILIFMISMATCH` record that forces this (`dumpbin /directives` over `libs.cmake`
  reports `glass`, `javafx_font`, `prism_d3d` and `sunmscapi` for native-charts, and none
  at all for native-sdk, which does link and run against the debug CRT). Which ones appear
  follows what the image reaches rather than whether it has a GUI, `sunmscapi` being the
  `java.base` crypto provider. The local suites only link the shared library, so nothing
  here covers this path.

## Generator gaps the test API exposed

native-charts never exercises these combinations, so they were untested before this repo.
Expect more of the same shape: anything the charts API does not do is probably broken.

Fixed: `CHAR16` as a return type, `@CLibMethod(name = ...)` renaming the Java call as well
as the symbol, `char**` in the MEX gateway, subclass constructors in all three wrappers,
Python static overloads needing an explicit `cls`, MATLAB echoing non-const pointer params
as extra outputs (dropped, buffers are caller-owned and mutated in place like the C ABI),
`@CDefault` string literals reaching MATLAB double-quoted (converted to single-quoted char
arrays, the counterpart of Python's `convertCDefaultParamString`), the `%LOG_FILE%` literal
in the generated `compiler-{linux,osx}.sh` log header, the C++ constructor
factory colliding with wrapped methods (now a per-class free function in the internal
namespace), the MEX target failing to build in a `Debug` configuration on MSVC (cl
rejects the unconditional `/O2` against the configuration's `/RTC1` with "D8016",
which broke IDE profiles defaulting to Debug, so `/O2` and `/DNDEBUG` now apply only
outside Debug while `/MT` stays unconditional, its override being only a warning),
annotated static nested classes (the old TODO claimed an infinite loop, see the bitten
list for what actually happened).

Open:

1. `cppOverloads` ignores `postBody`, and only the MATLAB generator emits `postCall`.
   For C++ put the whole prologue in `body`. `TestOverloads.echoIndex` pins the
   behaviour down in all four languages.
2. A class may declare at most one constructor. The MATLAB generator asserts this.
3. Only leaf classes may declare constructors.
4. Structs are unsupported, except the span struct as the one special case.
   `@CExternalEnums` is declared but never read.
5. `LibraryDescription.proto` still says `package hebi.api.proto` and
   `default = "hebi"` for the library name. The package rename only regenerates the
   model classes. The name default interacts with the omit-what-matches-default
   encoding: a library actually named `hebi` gets its name omitted from `api.json` and
   resolved from the default on read, so changing the default changes what older
   `api.json` files mean. Change it before the format has external consumers, with a
   reference update.
## Working style

- Small, reviewable changes. One issue at a time. A nine-file change to make one string
  configurable was rejected, correctly.
- Do not refactor while fixing. The extracted code is tested and working.
- Prefer disabling a test with a `TODO:` naming the exact cause over a speculative fix.
- Generated output is read by humans. Keep it clean.
- Delegate only wide independent work. Subagents have produced hand-edited reference
  files and `.formatted()` bugs that needed correcting.
- Prose in docs and comments: no em dashes, no semicolons as sentence connectors.
- Generated code must be indistinguishable in care from the hand-written source.
  What makes the author's style distinct is written down in `.claude/style.md`, read it
  before writing new code.
