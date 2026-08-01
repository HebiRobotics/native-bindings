# fable5-plans.md

Prospective features for the bindings generator. Each section is a standalone work item
with a recommendation, a design, the files it touches, and the risks. Read `CLAUDE.md`
first: the reference-file check in `bindings-tests` is the safety net for every one of
these, and `mvn clean verify` must be run with `clean`.

Three design questions were settled before writing this:

- Spans keep the `DoubleSpan` / `IntSpan` naming.
- Callbacks are synchronous only, and the processor enforces it.
- The Python C extension auto-selects with a silent ctypes fallback.

Sections are ordered by topic, not by priority. See **Sequencing** at the end.

**Item 3 is implemented.** Item 7 was attempted and reverted, see its notes. Everything
else is still a proposal. **Read *Reading the items together* before picking anything up**,
it records the cross-item constraints and the order they imply.

---

## 1. Callbacks in all four languages

**Status today.** `TestCallbacks` declares a `@CLibTypeDef` with three hand-written
definitions (`cDefinition`, `cppDefinition`, `pythonDefinition`) and a Java
`CFunctionPointer` interface with `@InvokeCFunctionPointer`. C and C++ get a raw function
pointer, Python gets a `CFUNCTYPE`, and MATLAB degrades the typedef to `void*` so the
methods are not callable there. The user carries a `VoidPointer userData` through by hand.

**Contract.** Callbacks fire on the calling thread and complete before the library method
returns. That is what makes MATLAB possible at all, and it is what `TestCallbacks` already
does. The processor rejects anything that cannot honour it, see *Enforcement* below.

### Design

Add `@CLibCallback` in `bindings-runtime` alongside the existing annotations. It marks the
`CFunctionPointer` interface and replaces the three hand-written definition strings, which
the generators can derive from the interface's `@InvokeCFunctionPointer` method:

```java
@CLibCallback("IndexCallback")
public interface IndexCallbackFunction extends CFunctionPointer {
    @InvokeCFunctionPointer(transition = CFunction.Transition.TO_NATIVE)
    void invoke(VoidPointer userData, int index);
}
```

The C ABI does not change. It stays `void (*)(void* userData, ...)` plus a `void* userData`
parameter. Everything below is wrapper ergonomics on top of that unchanged transport.

The generator needs the callback's parameter list in the descriptor model, so
`LibraryDescription.proto` gains a `CallbackDescription` message (name, parameters, return
type) and `LibraryDescription` gains a `repeated CallbackDescription callbacks`.
`AnnotatedCLibClass` already collects `@CLibTypeDef`, so collect `@CLibCallback` the same
way.

**C.** Unchanged output. The typedef is now generated from the interface rather than copied
from an annotation string.

**C++.** Emit a `std::function` overload next to the raw pointer one, exactly the way
`@CConst CDoublePointer` already produces a `std::vector<double>` overload in
`CppBindingsGenerator.writeFunction`. The trampoline is a free function in the `internal`
namespace, following the constructor-factory precedent:

```cpp
void invokeTimes(IndexCallback func, void* userData, size_t times) noexcept;   // raw
void invokeTimes(const std::function<void(int)>& fn, size_t times);            // added
```

The added overload puts the `std::function` on the stack, passes its address as `userData`,
and the trampoline casts back. That is safe precisely because the call is synchronous.

**Python.** The wrapper accepts any callable, wraps it in the `CFUNCTYPE`, and holds the
reference in a local for the duration of the call. That also fixes a real bug in today's
API: a user who passes `CFUNCTYPE(...)(fn)` inline can have it collected mid-call.

**MATLAB.** This is the new part. The MEX gateway gains a `MatlabCallback` RAII helper next
to `MatlabString` and `MatlabStringArray` in `libName_native.cpp`. It stores the `mxArray*`
function handle in a file-scope slot, passes a trampoline function pointer plus the slot
address as `userData`, and the trampoline calls `mexCallMATLAB` with the marshalled
arguments. `getMexToC` gains a callback case and `getMexCType` maps it to the typedef.
Because the callback runs inside `mexFunction`, `mexCallMATLAB` is legal and re-entrant
MATLAB execution works.

Nesting matters: the slot must be a stack of handles rather than a single pointer, so a
callback that itself calls back into the library does not clobber the outer handle.

### Enforcement

`ProcessorValidationTest` gains cases for a callback interface without
`@InvokeCFunctionPointer`, a callback parameter not immediately followed by a
`VoidPointer userData` parameter, and a callback whose parameters use a type the MEX
gateway cannot marshal (object handles, `char**`). Each must be a compile error with a
message naming the offending method, matching how the span validations already read.

### Files

- `bindings-runtime/.../annotations/CLibCallback.java` (new)
- `bindings-generator/src/main/protobuf/LibraryDescription.proto`
- `.../generator/annotations/AnnotatedCLibClass.java`, `AnnotatedCLibMethod.java`
- `.../bindings/CppBindingsGenerator.java`, the new overload in `writeFunction`
- `.../bindings/PythonBindingsGenerator.java`, the keepalive in the wrapper body
- `.../bindings/MatlabBindingsGenerator.java`, `getMexToC` and `getMexCType`
- `.../resources/.../matlab/libName_native.cpp`, `MatlabCallback` and the trampoline
- `bindings-tests`: extend `TestCallbacks`, enable it in `BindingsTest.m`, and drop the
  "not covered in MATLAB" note in the class header

### Risks

- `mexCallMATLAB` longjmps out of the MEX function on error. The trampoline must not hold
  C++ objects with destructors across it, or it must use `mexCallMATLABWithTrap`.
- The typedef derived from `@CLibCallback` has to reproduce today's `#ifndef MATLAB`
  fallback byte for byte, or every reference file churns.

**Effort:** medium to large. The MATLAB trampoline is the bulk of it. Split into two
commits: first derive the typedefs from the annotation and confirm an empty reference diff,
then add the per-language ergonomics.

---

## 2. Python C extension with ctypes fallback

**Is the speedup real?** Yes, but it only matters for some call shapes, and the size below
should be measured before committing to the work.

A `ctypes` call does argument conversion in the interpreter on every invocation. It walks
`argtypes`, boxes each argument, builds a libffi call frame, and unboxes the result. That is
roughly **1 to 3 microseconds per call**. A compiled extension using `METH_FASTCALL` parses
a `PyObject*` array directly into C values, roughly **50 to 150 nanoseconds**. So the
ceiling is about a **10x to 20x reduction in per-call overhead**, and none of it applies to
time spent inside the library itself. Treat those numbers as order-of-magnitude estimates
until benchmarked on this API.

Where it matters:

| Call shape | ctypes overhead | Verdict |
|---|---|---|
| `chart.setTitle("x")` at UI rates | irrelevant | no gain worth having |
| `series.addPoint(x, y)` in a 1 kHz loop | dominates | large gain |
| `arrays.sumFloat64(np_array, n)` | amortised over the buffer | small gain |
| numpy conversion in the generated wrapper | often exceeds the call itself | **fix this first** |

That last row is the important one. The generated Python already calls
`np.ascontiguousarray(...).ctypes.data_as(...)` per call for pointer parameters. For an
array that is already contiguous float64 this is pure overhead, and it is fixable in the
existing ctypes backend with no extension at all. Measure that before building anything.

### Design

Model the extension on the MEX gateway, which is the right precedent: one generated,
compiled translation unit that loads the shared library and dispatches.

Three deliberate differences from MEX:

- **No hash dispatch.** MEX needs `hash32_fnv1a` because everything funnels through one
  `mexFunction`. CPython gives one C function per method via `PyMethodDef`, so dispatch is
  free. Drop the hashing and the collision-free seed search from this path entirely.
- **Limited API / abi3.** Compile with `Py_LIMITED_API` so one binary per platform serves
  CPython 3.8 and up. Without it the build matrix multiplies by every minor version, which
  would sink the release pipeline. `METH_FASTCALL` is only in the limited API from 3.10, so
  fall back to `METH_VARARGS` below that and accept a smaller win.
- **No numpy link dependency.** Accept any object supporting the buffer protocol via
  `PyObject_GetBuffer`. That is zero-copy for numpy arrays, `array.array`, `bytes` and
  memoryviews, and it avoids compiling against numpy headers.

Backend selection lives in the generated module:

```python
try:
    from . import _%%LIB_NAME%%_ext as _impl
except ImportError:
    _impl = _CTypesBackend()
```

The class wrappers stay exactly as they are and call `_impl.<c_name>(...)`. GraalPy, PyPy
and any platform without a prebuilt wheel keep working on ctypes with no user-visible
change. The existing wrapper generation is untouched, so only the dispatch layer is new.

Build: extend `mex.cmake` into a sibling `python_ext.cmake` driven by the same stage-2
CMake run, guarded on a `Python3_Development` find the way `MATLABROOT` guards the MEX
build.

### Files

- `.../bindings/PythonBindingsGenerator.java`, split the current
  `appendCFunctionSignatures` output into a `_CTypesBackend` class and add the import shim
- `.../resources/.../python/libName_ext.c` (new template, mirrors `libName_native.cpp`)
- `.../resources/.../c/python_ext.cmake` (new), referenced from `c/CMakeLists.txt`
- `scripts/build-native.*`, stage the built extension next to the `.py`

### Risks

- The GIL. A synchronous callback from item 1 that re-enters Python must hold it, via
  `PyGILState_Ensure` and `PyGILState_Release` in the trampoline. **This couples items 1
  and 2.** Do callbacks first, or the extension's callback path will need reworking.
- Reference counting in generated dispatch code is the classic source of leaks. Shape the
  generated code so every `PyObject*` is either borrowed or immediately owned, and route
  all error paths through a single cleanup label.
- Wheel building and `manylinux` are a distribution problem rather than a code problem, but
  they are why this is a bigger commitment than it looks.

**Effort:** large, and the least certain payoff of the six. Land a micro-benchmark and the
`ascontiguousarray` fast path in the existing ctypes backend first. If that closes the gap
for the real workloads, stop there.

---

## 3. `@CLibIndex` for language-specific index conversion (IMPLEMENTED)

This one has landed. The section is kept because item 5 and item 6 depend on it. See
*What shipped* below.

**Motivating case.** `NativeGridWindow` in `native-charts` has four methods that each carry
a hand-copied `MATLAB_FIX_INDEX` block in a `matlabOverload`:

```matlab
% Switch to zero-based indexing. We can't distinguish between the default and
% a user-set zero, but that likely results in the user-intended behavior anyways.
if row > 0, row = row - 1; else, row = 0; end
if col > 0, col = col - 1; else, col = 0; end
```

That comment is load-bearing. Because `row` has `@CDefault("0")`, a MATLAB caller who omits
the argument and one who passes `0` are indistinguishable, so the conversion clamps instead
of subtracting blindly. Any generated version has to reproduce that clamp.

### Design

A parameter and return annotation in `bindings-runtime`:

```java
public NativeLineChart addLineChart(@CLibIndex @CDefault("0") int row,
                                    @CLibIndex @CDefault("0") int col) { ... }
```

Java stays zero based. `TypeDescription` gains `optional bool is_index`. Each generator then
decides its own base, which is the whole point of putting this in the model rather than in
per-language overload strings:

| Language | Base | Emitted |
|---|---|---|
| C, C++, Python | 0 | nothing |
| MATLAB | 1 | `if x > 0, x = x - 1; else, x = 0; end` on input, `x = x + 1` on an index return |
| Julia (see item 5) | 1 | same as MATLAB |

The MATLAB generator already has the hooks this needs. `preCallActions` and
`postCallActions` in `MatlabBindingsGenerator.ParameterLists` are exactly where `postBody`
and `postCall` land today, so this is an insertion into an existing mechanism rather than
new machinery.

The clamp form is only correct when the parameter has a `@CDefault` of `0`. With no default,
emit the plain `x = x - 1;`. Encode that decision in one place and comment why.

### What shipped

- `bindings-runtime/.../annotations/CLibIndex.java`, a `TYPE_USE` annotation so it applies
  to parameters and return values alike, the same way `@CSizeType` does.
- `optional bool is_index = 22` on `TypeDescription`. It is a flag rather than a `DataType`
  because index-ness changes no type in any language. The generated C header for an index
  is byte identical to one without the annotation, and index-ness is orthogonal to width,
  so a `DataType` would need an `INDEX_INT32` / `INDEX_INT64` / `INDEX_SIZE_T` explosion
  and a new case in each of the 24 `DataType` switches. `is_const` is the precedent.
- `AnnotatedClibType.isIndex()`, detected from the type mirror like `isSizeType` and
  `isConst` already are, and set in `toDescription()`.
- Type validation in the `AnnotatedCLibMethod` constructor, next to the span checks, with
  two rejection cases in `ProcessorValidationTest` covering the parameter and the return.
- `MatlabBindingsGenerator.getIndexInputConversion`, appended to `preCallActions`, plus the
  return conversion in the `default ->` branch of the return-type switch.
- `TestIndices` in the test API, with assertions in all four language suites.

Defaults shift into the target language rather than being converted. An index `@CDefault`
is written zero based like the rest of the model, and `getMatlabDefaultValue` emits it as
`default + 1` so the subtraction below can be unconditional:

```matlab
if nargin < 1
    row = 1;
end
row = row - 1;
```

This replaces the `MATLAB_FIX_INDEX` clamp rather than reproducing it. The clamp existed
only because the default was previously emitted in Java's space, which made an omitted
argument indistinguishable from an explicit zero. Shifting the default removes the
ambiguity at the source, and it fixes non-zero defaults, which the clamp silently got
wrong by decrementing them twice. The cost is that an explicit `0` from MATLAB, which is
not a valid one-based index, now reaches Java as `-1` instead of being absorbed.

Overload precedence, as implemented: a `matlabOverload` that sets `parameters` or `call`
owns the signature and gets no generated conversion. One that sets only `postBody` gets
both, generated conversion first. That is recorded in the `CLAUDE.md` bitten list because
it is a live double-conversion hazard during the item 6 migration.

### Decided: the index width is the author's choice, including `size_t`

`isIndexType` accepts `INT8`, `INT16`, `INT32`, `INT64` and `SIZE_T`, and
`TestIndices.echoSize` covers `@CLibIndex @CSizeType long`. That is deliberate. Do not
restrict it to signed types. An API author who picks `size_t` is stating that the index is
never negative, which is a legitimate contract, and forcing a signed type on them would be
this project deciding something that belongs to the library being wrapped.

Guidance for choosing, worth repeating to anyone writing a new annotated method:

- **Signed `int` or `long`** when the index can be absent or out of range. `-1` is the
  conventional "none", which is what `std::string::npos` works around and what the C++
  Core Guidelines ES.107 gets at by recommending the signed `gsl::index`. The one-based
  conversion carries the sentinel for free: with no default, MATLAB `0` becomes C `-1` and
  a C `-1` comes back as MATLAB `0`, so "none" round-trips on its own. Every index in
  `native-charts` today is a plain `int`.
- **`size_t` via `@CSizeType`** when the index genuinely cannot be negative and matching a
  buffer length matters more than having a sentinel.

The one caveat to document if it ever bites someone: with `size_t`, an out-of-range input
from a one-based language is not detectable. A MATLAB caller passing `0`, which is not a
valid one-based index in the first place, produces `-1.0` in the MEX gateway, and
`static_cast<size_t>(-1.0)` narrows a negative double to an unsigned type. That is garbage
in, garbage out on input that was already invalid, so it is a documentation matter rather
than a reason to remove the option.

**Effort:** done.

---

## 4. Spans for every element type

**Naming.** Keep `DoubleSpan` and add `IntSpan`, `FloatSpan`, `LongSpan`, `ShortSpan`,
`ByteSpan`. `CDoubleArray` is already taken in `bindings-runtime` by the RAII
unmanaged-memory wrapper, so that name was never available. `Span` matches `std::span`,
`Span<T>` and Rust slices, which is the vocabulary users of a native binding already have.

### Design

Span support is currently hard-wired to one element type in five places. All five need to
become a lookup keyed on the element type:

1. `LibraryDescription.proto` has `FLOAT64_SPAN = 50`. Add `INT32_SPAN`, `FLOAT32_SPAN`,
   `INT64_SPAN`, `INT16_SPAN` and `INT8_SPAN` in the same block.
2. `TypeMap` has `SPAN_STRUCT_NAME = "double_span"`, which becomes a per-type name. The
   `FLOAT64_SPAN` cases in `toExternalC`, `toInternalC`, `getCPythonType` and
   `toPythonTypeHint` become element-type switches, and the
   `addMapping(DoubleSpan.class, ...)` registration grows one row per type.
3. `bindings-runtime/types` needs one `@CStruct` interface per element type, plus matching
   rows in `hebi_structs.h`, which `SpanDirectives` extracts at image build.
4. `Spans` needs overloads per type, as do `CTypes.toDoubleSpan` and
   `CTypes.toTempDoublePointer`.
5. `GeneratorUtil.findSpanType` already returns a `TypeDescription`, so it generalises for
   free, but `usesSpans(lib)` has to become "which span types does this library use" so the
   generators emit only the structs actually needed.

Each generator then emits one struct per used element type instead of one global struct.
`CBindingsGenerator.getSpanTypedef` is already parameterised on the type after the recent
hoist, so it needs a loop rather than a rewrite. The C++ `internal` namespace and the
Python `_DoubleSpan` follow the same shape.

**Scope control.** Do not add every type at once. Add `IntSpan` first as the second type,
because that is what forces each hard-coded `FLOAT64_SPAN` into a general form. Once the
tests pass with exactly two types, the rest are additive rows in a table.

### Files

`LibraryDescription.proto`, `TypeMap.java`, `GeneratorUtil.java`, all four generators,
`bindings-runtime/types/*`, `hebi_structs.h`, and `TestArrays` for coverage.

### Risks

- A `CharSpan` would be ambiguous, because Java `char` is UTF-16 while `char*` in C is a
  byte string. Skip it, or name it `Char16Span` to keep it distinct from `CCharPointer`.
  Recommendation is to skip it until a caller actually needs it.
- The MEX `MatlabArray<T, mxClassID>` template already covers the numeric types, so MATLAB
  is mostly a table extension.
- Reference-file churn is large but mechanical, and fully covered by the diff check.

### How a span return actually crosses, since the header looks like it should not work

The public header returns the struct by value, which reads as if it contradicts the GraalVM
rule that a struct cannot cross an `@CEntryPoint`. Both are true, because the by-value
return happens one layer out.

The entry point takes the input as a pointer and gets a trailing out-pointer:

```c
void __bindings_tests_Arrays_addOffset(graal_isolatethread_t* isolate,
        const bindings_tests_double_span* values, double offset,
        bindings_tests_double_span* spanOut);
```

The C shim is compiled by an ordinary C or C++ compiler, where by-value struct returns are
routine, so it declares a local, passes its address, and returns it:

```c
bindings_tests_double_span bindings_tests_Arrays_addOffset(bindings_tests_double_span values, double offset) {
    bindings_tests_double_span result_;
    __bindings_tests_Arrays_addOffset(isolate::global(), &values, offset, &result_);
    return result_;
}
```

Two properties are easy to break later and worth stating:

- **`result_` is a stack local, not an allocation.** Nothing is malloc'd. The struct is a
  `(pointer, length)` pair and the buffer it points at is the per-thread scratch buffer the
  entry point already filled. Turning this into a heap allocation would hand the caller an
  ownership question that the current contract does not have.
- **The wrapper languages never hold the struct.** C++ copies into `std::vector<double>` and
  Python into a numpy array on the line after the call, because the scratch buffer only
  lives until the calling thread's next call. Allocating in the wrapper layers instead is
  not needed and would be harder for no gain.

This is why the item generalises without new design. Each element type gets the same
in-pointer, out-pointer entry point and the same by-value shim, so the added surface is a
struct definition and a row in each type switch, not a new calling convention.

**Effort:** medium, and mostly repetition once the second type has forced the
generalisation.

---

## 5. Other languages: what it would take, and whether it is worth it

The C ABI is the product. Any language with a decent FFI can call it today without this
project generating anything, so the question for each is whether a generated idiomatic
wrapper earns its maintenance cost. Every new language is a permanent tax: another
generator, another reference-file set, another test suite, another entry in the known-gaps
list.

Cost is dominated by three things. How the language declares foreign functions, whether it
can express deterministic destruction for the handles, and whether it needs a compiled shim
like the MEX gateway or can bind at runtime like ctypes.

| Language | FFI mechanism | Handle lifetime | Compiled shim | Verdict |
|---|---|---|---|---|
| **Julia** | `ccall`, excellent, no shim | finalizers, GC-driven | no | **Best candidate.** The scientific audience overlaps MATLAB's almost exactly, `ccall` maps one-to-one onto the existing C header, and structs pass by value natively so spans work unchanged. 1-based indexing makes item 3 a prerequisite, which is already in place. |
| **Rust** | `extern "C"`, `bindgen` | `Drop`, deterministic and idiomatic | no | **Good, but low leverage.** Running `bindgen` on the existing C header already produces working bindings, so a generator only adds the safe RAII layer. `Drop` maps onto the generated destructor better than any other language here. Worth it only if Rust becomes a first-class consumer. |
| **TypeScript / Node** | N-API or `node-ffi-napi` | `FinalizationRegistry`, non-deterministic | **yes**, an N-API addon | **Viable, real work.** Needs a compiled addon per platform, the same commitment as item 2. Async is the natural idiom there, which fights the synchronous-callback contract from item 1. |
| **JavaScript, browser** | none, no FFI | not applicable | not applicable | **Not feasible as-is.** Would need a WASM build of the library, and GraalVM native-image does not target WASM for a JVM guest. That is a different project. |
| **PHP** | FFI extension, 7.4 and up | `__destruct` | no | **Poor fit.** PHP's FFI is per-request, the extension is often disabled in shared hosting, and the audience for a charting or robotics library is thin. Skip unless a customer asks. |
| **C# / .NET** | P/Invoke, `LibraryImport` | `IDisposable` and `SafeHandle` | no | **Not on the list, but stronger than PHP or browser JS.** Source-generated `LibraryImport` maps cleanly and `SafeHandle` fits the handle model well. Worth considering ahead of TypeScript. |

**Recommendation.** If any language is added, add **Julia**. It is the cheapest generator
because `ccall` is declarative and needs no shim, its audience is closest to the existing
MATLAB users, and it reuses the index work from item 3, which has landed, and the span work
from item 4. Rust
second, and only as a thin safe layer over `bindgen`. Treat PHP and browser JavaScript as
out of scope.

**The difficulty common to all of them.** Every generator added multiplies the cost of
every future change to the descriptor model. Before adding a fifth language, the four
existing generators should share more than they do now. The `ParameterLists` shape in the
Python and MATLAB generators is the obvious seam, and it was deliberately left unmerged
because the bodies diverge for real reasons. Adding a language is a good reason to revisit
that decision.

---

## 6. Migrating `native-charts` onto these annotations

`native-charts` is the real consumer and the reason the extraction happened. It is
read-only from this repo, so the migration happens in that repo against a published
`bindings-runtime` and `bindings-generator`.

### What already works

An annotation census over `native-charts` confirms it uses only features this project now
supports and tests: `@CLibClass` (34 uses), `@CLibMethod` (343), `@CLibComment` (433),
`@CDefault` (36), `@CSizeType` (28), `@CConst` (18), `@CLibEnum` (12), `@CLibTypeDef` (1),
`@CLibLastErrorProvider` (1), the `Const` and `Noexcept` flags, and the `property`,
`constructor`, `abstractClass`, `isolateName`, `enableInstancing`, `ignore`, `cppBody`,
`cppOverloads`, `pythonOverload`, `matlabOverload`, `postBody` and `postCall` attributes.
`@CExternalEnums` is declared but never used, in either repo.

### The four actual blockers

1. **`CArrayConverter`, 9 uses.** Dropped during extraction because it pulled in
   `us.hebi.math.data.Transform`, `us.hebi.core.util.ArrayUtil` and
   `us.hebi.glue.UnsafeAccess`. The bulk-copy methods are the part `native-charts` actually
   uses. Port a trimmed version into `bindings-runtime` with the loop implementations and
   without the `Transform` overloads. The `Unsafe.copyMemory` fast path is optional, so
   measure before porting it. Better still, several call sites are `(pointer, length)` pairs
   that item 4 replaces outright, so do item 4 first and port only what remains.
2. **`us.hebi.ffi.util.*` imports, 8 files.** A mechanical rename to
   `us.hebi.graalvm.bindings.runtime.*`. `CTypes` and `ExceptionHandlers` exist here with
   the same shape.
3. **Processor options.** `-Ahebi.nativegen.*` becomes `-Abindings.*` in
   `native-charts/pom.xml`. Five arguments, including `enableGraphics=true`, which this
   project already reads in `EnvProcessor` and `CocoaFeature`.
4. **`annotationProcessorPaths` must list `native-image-base` explicitly.** The isolated
   classloader does not inherit `provided` scope. This has bitten before and shows up as
   `NoClassDefFoundError: org/graalvm/nativeimage/ObjectHandles`.

### Ordering

Do the rename and the pom changes first and get a build. Then diff the generated output
against `native-charts/target/image` from before the migration. That diff is the real test,
the same technique as the reference-file check applied to the production library, and it
will surface anything the extraction changed. Expect two known non-empty entries:

- The feature-ordering change from the recent generator cleanup. The C++ and Python
  wrappers now emit the `LibraryInfo` block before the `Cocoa` block. The declarations are
  independent so this is cosmetic, but it will show in the diff.
- The `Indices` conversions from item 3, once the hand-written `MATLAB_FIX_INDEX` blocks
  are replaced by `@CLibIndex`.
- Whatever the span work changes, if item 4 lands first.

Item 3 has landed, so delete the four `MATLAB_FIX_INDEX` blocks in `NativeGridWindow` while
adding `@CLibIndex`, or the conversion will happen twice. A `matlabOverload` that sets only
`postBody` does not suppress the generated conversion, which is exactly the shape those four
methods have today.

One behaviour change to expect for MATLAB users of those methods: the hand-written clamp
absorbed an explicit `0`, and the generated conversion does not. `addLineChart(0, 0)` used
to mean the first cell and now reaches Java as `-1, -1`. Zero was never a valid one-based
index, so this turns silent absorption into a detectable error, but it is worth a release
note.

### Also worth doing during the migration

- `native-charts` classes are named `Native*` while this repo renamed its test API to
  `Test*`. Not required, because `@CLibClass(value = ...)` decouples the Java name from the
  C symbol, but if it is wanted then do it as its own commit and confirm an empty
  generated-output diff.
- The hardcoded `files.hebi.us` download URL, known gap 5, affects `native-charts`
  directly. A previous attempt at making it configurable was reverted for being too large
  to review. If revisited, keep it to one `%%DOWNLOAD_URL%%` token in the existing
  templates.

**Effort:** small to medium, and mostly mechanical. The risk is concentrated in
`CArrayConverter` and in whatever the output diff turns up.

---

## 7. Replacing `@CLibLastErrorProvider` with a generated per-isolate accessor

Attempted and reverted. The work is sound but the commit was too large, so it needs
splitting. Everything below was verified against the reference files before the revert.

**Goal.** Delete the `@CLibLastErrorProvider` annotation. Instead of the user marking one
method per isolate, the generator emits the accessor itself, calling
`ExceptionHandlers.getLastError(false)`.

### The design that worked

**One `@CEntryPoint` for the whole library, not one per isolate.** The last error slot
lives in the isolate's own heap, so the isolate thread the C shim passes in already selects
which heap gets read. The Java body is identical for every isolate, so duplicating it just
puts N copies of the same bytecode in the image:

```java
@CEntryPoint(name = "__bindings_tests_getLastError")
public static CCharPointer bindings_tests_getLastError(IsolateThread _isolate) { ... }
```
```c
const char* bindings_tests_getLastError()           { return __bindings_tests_getLastError(isolate::global()); }
const char* bindings_tests_secondary_getLastError() { return __bindings_tests_getLastError(isolate::secondary()); }
```

The trap: `CBindingsGenerator.SignatureBuilder` derives the internal symbol from the
external one, so routing these through it forces N internal symbols. Emit the two lines
directly instead.

**Naming.** Default isolate gets the library-level name next to `getLibraryVersion`, a
named isolate carries its name. Falls out of `AnnotatedCLibClass.getCEntryName(true,
prefix, isolateName, "getLastError")` with no special case.

### What was built and verified

- `CEntryPointGenerator.finish()` emits a `_LastError$CEntries` class into the
  lexicographically first generated package. `AbstractGenerator` already had the `finish()`
  hook.
- `CBindingsGenerator.processIsolates()` emits the header declarations and the per-isolate
  wrappers.
- `GeneratorUtil.getIsolateNames(lib)` and `getLastErrorCName(lib, isolate)`. No proto
  change is needed, because the symbol is derivable from the isolate name.
- `BindingGenerator.getLastErrorMethod` computes the symbol instead of scanning for a
  marked method, so the MATLAB and Python error messages already resolve correctly,
  including for the secondary isolate.
- `GeneratorUtil.getAllCMethodSymbols` and the MEX dispatch gained cases for the new
  symbols. The MATLAB error path calls them, so the dispatch has to know them.
- Annotation, `is_last_error_provider`, `AnnotatedCLibMethod.isLastErrorProvider()`, the
  three validation tests and the two hand-written methods all removed.

### The finding that stopped it

The assumption that C++, Python and MATLAB users never need `getLastError`, because the
wrappers turn failures into native exceptions, **only holds for constructors and `void`
methods**. Those return a handle or a status code, so failure is detectable in band.

A throwing method that returns a value does not throw in any wrapper:

```cpp
inline double arrays::dotProduct(internal::double_span x, internal::double_span y) {
  return bindings_tests_Arrays_dotProduct(x, y);
}
```

On failure the ABI hands back `0.0`, indistinguishable from a real result.
`CppBindingsGenerator.writeFunction` only emits a throw for `OBJECT_HANDLE` and
`VOID_WITH_ERROR_CODE`, and Python and MATLAB match. So `getLastError` is part of the
public contract in every language, not an internal helper, and the language suites assert
exactly that:

```cpp
bt::errors::clearLastError();
CHECK(bt::arrays::dotProduct(x, shorter) == 0.0);
CHECK(bt::errors::getLastError().find("elements") != std::string::npos);
```

Two ways forward:

1. **Keep it exposed in every language.** Then the remaining work is the Python
   `restype = c_char_p` registration (without it ctypes defaults to `c_int` and the
   `.decode()` breaks), the user-facing accessor in the three wrappers, and updating the
   four suites. C++ needs no `extern "C"` declaration today because it never calls the
   symbol, only Python and MATLAB do.
2. **Make every throwing method throw**, which makes the original assumption true. Value
   returns then need an out of band failure signal: clear the error before each call and
   check after, or add a status out-param to the C ABI. Both are real ABI and performance
   changes and belong in their own pass.

### Suggested split

- **Commit 1**: generated accessor plus C emission, with `@CLibLastErrorProvider` still in
  place and unused. Reference diff is purely additive, nothing breaks.
- **Commit 2**: point `getLastErrorMethod` at the generated symbol, add the MEX dispatch
  case and the Python registration. Wrappers switch over, suites still pass.
- **Commit 3**: delete the annotation, its validation and the hand-written methods, and
  update the four suites.

### Also worth knowing

- `clearLastError` and `printLastStackTrace` stay hand-written, they are debug helpers. That
  does split the pair across two places, `Errors.clearLastError()` alongside a library-level
  `getLastError()`. Decide whether that is acceptable before committing.
- This breaks the C ABI. `hebi_charts_Runtime_getLastError` becomes
  `hebi_charts_getLastError`, so it belongs with the item 6 migration rather than before it.

---

## 8. Packaging the static build for C and C++ consumers

**Why.** `hebi-charts` links the shared library, but the original goal was replacing parts
of the HEBI Python and C++ C ABI. Those consumers link the wrappers into their own binary.
The static flavour is already produced, it just is not packaged.

### What exists today

Verified against `bindings-tests/target/image/static` and the real
`native-charts/target/image/static`:

- The compiler wrappers (`c/compiler-windows.bat`, `compiler-linux.sh`, `compiler-osx.sh`)
  intercept native-image's link step. They archive the image object, copy every dependency
  archive next to it, and write `libs.cmake` (`GRAALVM_LIBS`) and `opts.cmake`
  (`GRAALVM_OPTS`).
- `<lib>_static.cmake` defines a `<lib>_static` STATIC target that compiles `<lib>_c.cpp`
  and links `GRAALVM_LIBS`.
- The stage 2 `CMakeLists.txt` already exposes `target_add_<lib>_static(target)`.
- `publish-native-charts.yml` already has the shape this needs: a per-platform `compile`
  job, then a `package` job that assembles a headers-only zip and an `-all` zip from the
  platform zips.

**The gap:** the release zip only contains the shared library. Everything static stays in
the build tree.

### Size

The static consumer is not `native-charts`. It is a separate algorithms library with no
charting or JavaFX, around **15 MB per platform unzipped**, so a four or five platform
`-all` lands well under 100 MB before compression. That needs no special handling.

For contrast, `native-charts` measures 157 MB of archives on Windows alone, 117.6 MB of
which is the image and 36 MB is JavaFX's `glass.lib`, across 14 GraalVM archives and about
20 system libs. That is the heavy shared-library consumer, not anything that would ship
statically, and it is only worth remembering as the upper bound if a charting library ever
does want a static package.

### Problems to fix along the way

1. **`libs.cmake` lists every GraalVM archive twice.** The linker command line repeats them
   and the wrapper appends each `.lib` it sees. Harmless with MSVC, and on Linux repetition
   is how circular archive dependencies get resolved, but right now it is accidental rather
   than deliberate. Deduplicate, and on Linux wrap the group in
   `-Wl,--start-group ... -Wl,--end-group` instead of relying on the repeat.
2. **`libs.cmake` is CMake-only.** The consuming C and C++ libraries may not use CMake.
3. **The consumer compiles the shim.** `<lib>_c.cpp` needs C++ only for the
   `static thread_local ThreadHandle` that detaches isolate threads on exit. It uses no STL,
   so the C++ ABI surface is `__cxa_thread_atexit` and static init guards, nothing more.

### Package layout

```
<lib>-<version>-static-<platform>/
  include/
    <lib>_c.h            <lib>.hpp
                         # graal_isolate.h only until item 9 lands, then our own typedefs
  lib/<platform>/
    lib<lib>_static.a    # merged: compiled shim + image + every GraalVM archive
    syslibs.txt          # one system lib per line, link order preserved
    manifest.json        # platform, version, build, archives merged, system libs,
                         # toolchain and C++ runtime flavour used
  src/
    <lib>_c.cpp          <lib>_objc.m               <lib>_static.cmake
  cmake/
    <lib>-config.cmake   <lib>-targets.cmake
  THIRD-PARTY-NOTICES.txt
```

The `-all` bundle is the same tree with one `lib/<platform>/` per platform, sharing a single
`include/` and `src/`.

### Merging the archives

Per platform, after the shim is compiled:

| Platform | Command |
|---|---|
| Linux | `ar -M` with an MRI script (`create`, `addlib` per input, `save`, `end`) |
| macOS | `libtool -static -o out.a *.a` |
| Windows | `lib.exe /OUT:out.lib *.lib` |

Do this in **stage 2 CMake**, not in the compiler wrapper. The wrapper runs inside
native-image's link step and should stay minimal, and the shim has to be compiled first,
which is something only stage 2 does.

### Integration

CMake consumers get the idiomatic path:

```cmake
find_package(hebi_charts REQUIRED PATHS <pkg>/cmake)
target_link_libraries(app PRIVATE hebi_charts::static)
```

`<lib>-config.cmake` picks `lib/<platform>/` using the same platform detection already in
the stage 2 `CMakeLists.txt`, then defines an IMPORTED STATIC target carrying
`INTERFACE_INCLUDE_DIRECTORIES` and the system libs as `INTERFACE_LINK_LIBRARIES`.

Everyone else reads `syslibs.txt` and `manifest.json`, one system lib per line in link
order.

### Where the code goes

- New resource template `c/libName-config.cmake` next to the existing cmake templates,
  emitted from `CBindingsGenerator.writeTo` alongside `<lib>_static.cmake`.
- Extend the `c/CMakeLists.txt` template with a `BUILD_STATIC_PACKAGE` option that merges
  the archives, writes `syslibs.txt` and `manifest.json`, stages the tree and zips it.
  Mirror the existing `BUILD_RELEASE_PACKAGE` block rather than inventing a new shape.
- Compiler wrappers: deduplicate, and record system libs into their own list so the package
  step does not have to re-parse `libs.cmake`.
- CI: add the static zip to the per-platform upload, and assemble a static `-all` in the
  existing `package` job.

### Risks, worst first

- **A second static image in the same process.** Two Graal images in one binary both export
  the generic `graal_*` API, which is a hard duplicate-symbol error. **Item 9 is the fix**
  and should land first. Even with prefixes, each image still initialises its own isolate,
  so test two libraries in one process before promising the pattern.
- **Symbol visibility.** The JDK's static `zip` archive carries zlib, so the merged archive
  defines `deflate`, `inflate` and friends. HEBI links libz dynamically rather than
  statically, so this is not a duplicate-definition error. It is the quieter version: the
  linker resolves the consumer's own zlib calls against the archive's bundled copy instead
  of the system library. Almost always harmless, since it is the same library, but it should
  be a deliberate outcome rather than a surprise. Localizing everything except the generated
  entry points, with `objcopy --localize-hidden` or a symbol allowlist, removes the question
  and is worth doing as hygiene.
- **MSVC runtime flavour.** The prebuilt archive bakes one of the runtime options. Document
  which, and point at the `src/` escape hatch for the other.
- **macOS specifics.** `libtool -static` warns on duplicate members, and the ObjC shim has
  to be compiled and merged too, which means `enable_language(OBJC)` in the package step.

### Verification

`bindings-tests` gains a consumer alongside the existing C and C++ suites: a small project
that does `find_package` against the staged static package and links
`bindings_tests::static`, built by `scripts/build-native.*` after stage 2. It has to link
and run with no shared library present at all, which is the property the whole package
exists to provide.

**Effort:** medium, and low risk now that size and zlib are both settled. The packaging is
mechanical. Its only hard gate is item 9, which removes the `graal_*` collision. It does not
depend on item 6, because the static consumer is a separate library, see *Reading the items
together*.

---

## 9. Prefixing the GraalVM isolate API

**Why.** A static image exports the generic GraalVM C API, so two images in one binary
collide. This is not specific to one deployment. Once this project is open sourced, anyone
generating two libraries with it and linking both hits the same wall, as an unexplained
duplicate-symbol error in their own build with nothing pointing back at the generator. A
code generator should not emit a generic symbol name it does not have to.

All seven are in the archive today, confirmed with `dumpbin` on `hebi_charts.lib`:

```
graal_create_isolate    graal_attach_thread    graal_get_current_thread   graal_get_isolate
graal_detach_thread     graal_tear_down_isolate    graal_detach_all_threads_and_tear_down_isolate
```

Because the Graal image is a single large object file, the archive member gets pulled in
whether or not anything references those names, so this is a hard duplicate-symbol error
rather than something the linker quietly drops. Prefixing is the fix:
`hebi_charts_create_isolate()`.

### The mechanism exists and is supported

`CEntryPoint.Builtin` in `graal-sdk` provides aliases for six of the seven. Verified present
in the version this project builds against:

```java
@CEntryPoint(name = "hebi_charts_create_isolate", builtin = CEntryPoint.Builtin.CREATE_ISOLATE)
public static native IsolateThread createIsolate();

@CEntryPoint(name = "hebi_charts_get_isolate", builtin = CEntryPoint.Builtin.GET_ISOLATE)
public static native Isolate getIsolate(IsolateThread thread);

@CEntryPoint(name = "hebi_charts_attach_thread", builtin = CEntryPoint.Builtin.ATTACH_THREAD)
public static native IsolateThread attachThread(Isolate isolate);

@CEntryPoint(name = "hebi_charts_detach_thread", builtin = CEntryPoint.Builtin.DETACH_THREAD)
public static native int detachThread(IsolateThread thread);
```

Plus `GET_CURRENT_THREAD` and `TEAR_DOWN_ISOLATE`. The methods are `static native` with no
body, and the signatures are fixed by the builtin. Note `CREATE_ISOLATE` takes **no
arguments** and returns the `IsolateThread` directly, which is simpler than the three
argument `graal_create_isolate`. There is no builtin for
`graal_detach_all_threads_and_tear_down_isolate`, so that one cannot be aliased.

Generate these from `CEntryPointGenerator.finish()`, next to the last error accessor from
item 7, in the same generated class.

### The shim gets simpler, and `graal_isolate.h` goes away

`<lib>_c.cpp` currently opens with a three argument `graal_create_isolate` followed by a
detach. With the aliases:

```cpp
graal_isolate_t* createIsolate() {
    auto* thread = hebi_charts_create_isolate();
    if (!thread) return nullptr;
    auto* iso = hebi_charts_get_isolate(thread);
    hebi_charts_detach_thread(thread);
    return iso;
}
```

Once no generated code references a `graal_*` name, the shipped `graal_isolate.h` can be
replaced by our own two opaque typedefs. That drops a third-party header from the static
package's `include/` and removes the last place the public surface leaks GraalVM naming.

### Naming

Library-level functions use snake_case, matching the `graal_*` convention they replace.
Class methods keep camelCase. `AnnotatedCLibClass.getCEntryName` already takes a
`camelCase` flag, so this is choosing `false` at the library-level call sites rather than
new machinery.

```
hebi_charts_create_isolate()          hebi_charts_get_last_error()
hebi_charts_attach_thread(iso)        hebi_charts_get_library_version(...)
hebi_charts_Counter_getValue(obj)     // unchanged
```

This changes the existing ABI for `getLibraryVersion` and `getHeaderVersion` in
`LibraryInfoFeature`, and for item 7's `getLastError`. Land it together with item 7 so the
last error accessor is only named once.

### The open question, and the fallback

**Do the default `graal_*` symbols disappear once aliases are declared, or are both
emitted?** This decides whether prefixing alone is sufficient. Verify first, it is a ten
minute experiment: declare the six aliases, build, then run `dumpbin /symbols` or `nm -g`
over the archive and look for the old names.

If both are emitted, prefixing still buys the clean public API but not collision freedom,
and the packaging step has to localize or rename the leftovers with
`objcopy --redefine-syms`, which works on both ELF and PE-COFF. That folds into the symbol
visibility hygiene already proposed in item 8, so the work is not wasted either way.

### Risks

- The `native-image` version pins the available builtins. `CEntryPoint.Builtin` has been
  stable since 19.0, so this is low risk, and a signature mismatch fails the image build
  rather than degrading silently.
- Anyone currently calling `graal_create_isolate` against the shared library breaks if the
  defaults are suppressed. `native-charts` does not, since the shim is the only caller, but
  check the examples repo before releasing.

**Effort:** small, once the open question is answered. Do it before item 8, and make the
prefixed names the default rather than an opt-in flag. An option defaulting to the colliding
behaviour would just relocate the trap.

---

## 10. Converging the four generators

Item 5 names this as a prerequisite for adding a fifth language, but it is not really about
item 5. Every generator added multiplies the cost of every future change to the descriptor
model, and items 1, 2 and 4 each add a per-language branch, so the refactor gets more
expensive the longer it waits.

The seam is the `ParameterLists` shape in the Python and MATLAB generators, deliberately
left unmerged because the bodies diverge for real reasons. The realistic goal is a shared
*shape*, not shared code: same field names, same construction order, a documented contract
for what each stage may assume. `CppBindingsGenerator.ClassScope` and `MethodParams` are
the pattern to follow, since they were introduced for exactly this reason.

Do not attempt this before items 1 and 4 land. Both add per-language branches, and
converging on a shape that predates them means converging twice.

**Effort:** medium, and entirely reference-file verifiable. A pure refactor must produce an
empty diff.

---

## Reading the items together

### Two things that do not line up

**The ABI-breaking items sit after the migration.** Items 7 and 9 both rename public C
symbols. Item 6 migrates `native-charts` onto the annotations. If the migration lands first,
its consumers get one breaking release for the migration and then two more for the renames.
Item 7's own text already says it "belongs with the item 6 migration rather than before
it", which the ordering below now reflects: **all ABI breaks land before or with item 6, as
one release.**

**Item 8 does not actually depend on item 6.** Item 8 says to sequence it after the
migration, but the static consumer is a separate algorithms library, not `native-charts`.
Item 8's real gates are item 9, for the symbol collision, and that library adopting the
annotations, which is its own migration. `bindings-tests` is enough to build and verify the
packaging. Treat the item 6 dependency as removed.

### Interactions worth knowing

| Items | Interaction |
|---|---|
| 9 into 7 | Both rename library-level symbols. Land as one release or `getLastError` is renamed twice. |
| 9 into 1 | The MEX dispatch hash table covers every symbol. Renaming after adding callback cases means redoing that work. |
| 9 into 2 | The Python extension binds C symbols by name, so it should be written against the final names. |
| 9 into 8 | Removes the duplicate `graal_*` symbols that otherwise make two static images unlinkable. |
| 4 into 6 | Spans replace several `CArrayConverter` call sites, so less of it needs porting. |
| 4 into 2 | The extension's buffer-protocol path is the span path. Spans first avoids writing it twice. |
| 4 into 1 | A callback taking a span cannot be marshalled by the MEX gateway. Item 1's rejection list needs a span case once item 4 lands. |
| 4 into 5 | Julia passes structs by value natively, so spans work unchanged there. |
| 1 into 2 | A synchronous callback re-entering Python must hold the GIL. Callbacks first, or the extension's trampoline is reworked. |
| 1, 4 into 10 | Both add per-language branches. Converging before them means converging twice. |
| 2 with 8 | A statically linked extension gives a self-contained wheel with no separate shared library. Both also extend the same stage 2 `CMakeLists.txt`, so expect a merge point. |
| 3 into 5 | Julia is one-based, so `@CLibIndex` is a prerequisite. Already landed. |

Two items are **ABI-neutral** and can therefore slot anywhere: item 1, because the callback
transport is unchanged and only the typedef's origin moves, and item 4, provided the
`FLOAT64` struct keeps the name `double_span` so existing consumers see no rename.

## Sequencing

Grouped, because the ordering constraint is mostly between groups rather than within them.

```
A. Foundation            4 (spans)
                         additive, unblocks 6, 2 and 5, and shrinks the item 6 port

B. One breaking release  9 (isolate API)      then   7 (last error)
                         every public rename in a single release, 9 first so 7 is
                         named once. Retry 7 with its three commit split.

C. Consumer migration    6 (native-charts)
                         now lands on the final ABI and migrates exactly once

D. Parallelisable        1 (callbacks)   and   8 (static packaging)
                         independent of each other. 1 is ABI-neutral, 8 needs only 9.

E. Gated on the above    10 (generator convergence)
                         after 1 and 4, so it converges on the final shape

F. Larger, optional      2 (Python extension)   then   5 (other languages)
                         2 needs 1, 4 and 9. Benchmark the ctypes fast path first,
                         it may remove the need. 5 needs 10 and 4.

    3 (@CLibIndex)       DONE
```

The one genuine judgement call is **C against D**. If the static consumer is more urgent
than `native-charts`, item 8 moves ahead of item 6, since it no longer depends on it.
Nothing else in the order changes.

## Verification, for every item

The reference-file check is the primary test. A change meant to preserve behaviour must
produce an empty diff under `bindings-tests/src/test/resources/reference/`.

```
mvn clean verify                                     assert against reference files
mvn clean verify -Dbindings.updateReference=true     rewrite them, then review git diff
scripts\build-native.bat                             both stages
scripts\run-tests.bat [c] [cpp] [python] [matlab]    all four suites
mvn clean verify                                     again: stage 1 dirties target/image
```

Under PowerShell, quote `-D` arguments: `"-Dbindings.updateReference=true"`.

Every item above must add to `bindings-tests`:

- annotated API coverage in `bindings-tests/src/main/java/.../api/`
- assertions in all four language suites, asserting the same value table
- rejection cases in `ProcessorValidationTest` for anything the processor must refuse

New annotations also need a row in the README feature table and, if they carry a trap, an
entry in the `CLAUDE.md` bitten list.
