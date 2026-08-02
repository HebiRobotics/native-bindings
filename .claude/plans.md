# Plans

Prospective features for the bindings generator. Each section is a standalone work item
with a recommendation, a design, the files it touches, and the risks. Read `CLAUDE.md`
first: the reference-file check in `bindings-tests` is the safety net for every one of
these, and `mvn clean verify` must be run with `clean`.

Three design questions were settled before writing this:

- Spans keep the `DoubleSpan` / `IntSpan` naming.
- Callbacks are synchronous only, and the processor enforces it.
- The Python C extension auto-selects with a silent ctypes fallback.

A fourth constraint arrived after the first draft and cuts across most of the items:
**the generated bindings prefer zero allocation in steady state.** Setup and failure paths
are exempt. The policy itself lives in `CLAUDE.md` under *Allocation policy*. Items that
change shape because of it carry an **Allocation impact** note below, item 11 is the gate
that would catch a regression, and the one item it reorders is item 4.

Sections are ordered by topic, not by priority. See **Sequencing** at the end.

**Items 3 and 6 are done.** Item 7 was attempted and reverted, see its notes. Everything
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

### Allocation impact: prefer a template over `std::function`

The C++ ergonomics above are the part that changes. `const std::function<void(int)>&`
still requires the caller to build a `std::function`, and that heap allocates whenever the
captures exceed the small object buffer, which is typically about 16 bytes. A callback
invoked in a hot loop is exactly where that is least acceptable.

Emit a template overload instead, which is what makes the synchronous contract pay off:

```cpp
template <typename F>
void invokeTimes(F&& fn, size_t times);   // passes &fn as userData, trampoline casts back
```

No type erasure, no allocation, and it collapses into the raw function pointer path after
inlining. The `std::function` overload is still worth having for call sites that need a
non-template signature, but the template is the documented hot path and the one the C++
suite should assert against in item 11.

Python is best effort, but the obvious implementation is the expensive one. Constructing a
`CFUNCTYPE` per call allocates and is slow. Either cache the wrapper keyed on the callable
or document build-once-call-many, and keep the keepalive fix either way since that one is
a correctness bug rather than a performance one.

The MATLAB trampoline allocates mxArrays per invocation through `mexCallMATLAB`. That is
inherent to MATLAB and out of scope.

### Risks

- `mexCallMATLAB` longjmps out of the MEX function on error. The trampoline must not hold
  C++ objects with destructors across it, or it must use `mexCallMATLABWithTrap`. Note that
  the existing RAII helpers in the gateway survive this only because MATLAB's MEX memory
  manager reclaims `mxCalloc` and `mxCreate*` memory at exit. A trampoline holding anything
  that is not mx-allocated does not get that safety net. See item 12.
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

### Allocation impact: raises the ceiling, does not change the priority

The buffer protocol path is strictly better on allocation than ctypes. `PyObject_GetBuffer`
avoids both the `np.ascontiguousarray` copy and the fresh ctypes pointer object that
`.ctypes.data_as(...)` builds on every call, and the latter is paid even when the input is
already contiguous float64.

That does not promote this item. CPython boxes return values and builds argument tuples
regardless of backend, so Python can never be allocation free and is explicitly best effort
under the policy. The extension is a latency item, not an allocation policy item, and it
should not jump the queue on allocation grounds.

What does change is the pre-work. The `ascontiguousarray` fast path was already the first
thing to do and is now also an allocation fix rather than only a latency fix, so measure
allocations alongside time. If the fast path plus item 4's output spans close the gap for
the real workloads, that is a stronger reason to stop there than the original benchmark
argument was.

**Effort:** large, and the least certain payoff of the six. Land a micro-benchmark and the
`ascontiguousarray` fast path in the existing ctypes backend first. If that closes the gap
for the real workloads, stop there.

---

## 3. `@CLibIndex` for language-specific index conversion (IMPLEMENTED)

This one has landed, and the motivating `NativeGridWindow` swap below is done too.
The section is kept because item 5 depends on it. See *What shipped* below.

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
it is a live double-conversion hazard for any future swap of a hand-written conversion.

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

### Allocation impact: this item gains an output span, and it cannot be deferred

Span **input** is already the zero allocation shape and is the model for everything else.
`DoubleSpan` is a `@CStruct` word type, so the parameter costs nothing in Java, C++ passes
`.data()` and `.size()` from a vector it already owns, and Python hands over the numpy
buffer address. `dotProduct` allocates nothing anywhere. Widening the element type keeps
that property for free.

Span **output** is where every allocation in the feature lives. The processor rejects a
`DoubleSpan` return and requires `double[]`, and that rule is what forces the cost. One
call to `addOffset` allocates a Java array, copies it into the per-thread scratch buffer,
discards the array, then allocates a `std::vector` in C++ or a numpy copy in Python. Three
allocations for one logical return, and this item as originally written reproduces all
three across six element types.

So add a **caller provided output span** alongside the `double[]` return:

```java
@CLibMethod(flags = Noexcept)
public static int addOffset(DoubleSpan values, double offset, DoubleSpan out) { ... }
```

Java writes through the pointer and returns the element count written. Nothing is
allocated on either side of the ABI: C++ writes into a vector the caller already sized,
Python into a preallocated array, C into whatever it owns. The `double[]` return stays as
the convenience form, because it is the right default for a caller who does not care. The
point is that an allocation free path exists next to it, not instead of it.

### Considered and rejected: `double*` return plus an out-parameter length

The alternative shape is to drop the struct from the public header and return the pointer
with the length travelling separately:

```c
const double* addOffset(bindings_tests_double_span values, double offset, size_t* length);
```

Four reasons it loses, the first being the one that motivated asking:

- **It is the shape that breaks the symmetry, not the span.** A span is the same type in
  both directions. Pointer plus out-length only works for output, because an input length
  is passed by value while an output length has to be passed by reference. There is a
  fully classic C form, `(const double*, size_t)` in and `double*` plus `size_t*` out, and
  the library already has its input half in `sumFloat64`. But that form is not symmetric
  either, it is just conventionally asymmetric. The span is the only shape where input and
  output read identically.
- **It does not change the allocation story at all.** Both forms hand back a pointer into
  the per-thread scratch buffer that the wrapper has to copy before the next call. The
  wrapper allocation is caused by the library owning the memory, not by how the length
  travels. Switching shapes buys nothing on the problem this section exists to solve.
- **No ABI advantage, and a small disadvantage.** A 16 byte struct return goes in
  `RAX:RDX` under SysV, so the span is returned in registers. Under the Windows x64
  convention a 16 byte struct is already returned through a hidden pointer, which is
  exactly what the explicit `size_t*` would be. So the two are identical on Windows and
  the struct is better on Linux and macOS.
- **The struct is atomic.** A caller cannot forget the length or pass null for it.

### The cached buffer idea, and where it actually applies

Returning a reusable buffer rather than a fresh array is the right instinct, and it solves
a different half of the problem than the output span does:

| Who owns the buffer | Java array per call | Wrapper copy per call |
|---|---|---|
| Today, `double[]` return | yes | yes |
| Library, cached and reused | no | yes, memory dies at the next call |
| Caller, via an output span | no | no |

So a library-cached buffer fixes the Java side and leaves the wrapper copy, because the
memory is still only valid until the thread's next call. Only a caller-owned buffer removes
both. Both are worth having, and the useful part is that **they are the same C shape and the
same entry point**, differing only in who allocates. The convenience overload can point the
mutable span at the library's cached buffer and the explicit one at the caller's, so this
costs one entry point, not two.

For the Java-side type, use the mutable span rather than a `DoubleBuffer`. A `DoubleBuffer`
works for the library-cached case, but it cannot wrap a caller-supplied raw pointer without
allocating a view object per call, which is the same trap as `asDoubleBuffer` above. The
mutable span is a word type and works for both owners, with `Spans.write(out, i, v)`
mirroring the existing `Spans.read`.

One convention has to be decided rather than discovered: what a caller-provided buffer that
is too small does. Returning the required count without writing, so a zero-length span is
also the size query, is the smallest rule that covers both cases. Write it down once.

**This has to land with the element widening, not after it.** Today's struct is
`const double* data`. An output span needs `double* data`, so it is a second struct per
element type. The ABI is identical, so it costs nothing at the linker and the layout
question does not arise, but it is a header change and a `TypeMap` change in the same
places item 4 already touches. Doing it as a follow-up means churning every reference file
a second time over the same lines.

Consequences for the plan above:

- The struct count doubles, six element types by two constnesses. That makes the
  `usesSpans(lib)` change from point 5 mandatory rather than a nicety: emit only the
  structs a library actually uses, or every header carries twelve definitions.
- Naming. `double_span` keeps its name and its `const` for compatibility, and the mutable
  one needs a distinct name because C cannot express pointee constness through a by-value
  struct otherwise. `double_span_mut` reads badly next to `double_span`. Prefer
  `mutable_double_span`, or revisit the pair as `const_double_span` / `double_span` if the
  compatibility constraint on the existing name is worth spending.
- The processor validation grows a case: an output span parameter on a method that also
  declares an array return is almost certainly a mistake, so reject it.

Two fixes in the existing scratch buffer path belong with this item, since it is the only
item that touches them:

- `CTypes.toTempDoublePointer` calls `ByteBuffer.asDoubleBuffer()` on every call, which
  builds a fresh view object. The view does not escape so partial escape analysis may
  remove it, but that is not a property to design around. Cache the view next to the
  `ByteBuffer`.
- One `ThreadLocal` per element type is six thread local lookups and six independently
  grown buffers. Use one holder object per thread carrying the buffer and its typed views,
  so a thread that only ever touches doubles still allocates once.

### Files

`LibraryDescription.proto`, `TypeMap.java`, `GeneratorUtil.java`, all four generators,
`bindings-runtime/types/*`, `hebi_structs.h`, and `TestArrays` for coverage.

Plus, for the output span: a mutable `@CStruct` interface per element type next to the
const one, its row in `hebi_structs.h`, the `usesSpans` change from point 5 promoted to
mandatory, the `CTypes` scratch buffer holder, and the `AnnotatedCLibMethod` validation
rejecting an output span on a method that also returns an array.

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

### Allocation impact: Rust gains, Node loses

The ranking shifts, though not enough to change the recommendation.

**Rust** moves up. `&[f64]` maps onto a span with no copy and no wrapper type, `Drop` is
deterministic, and its audience cares about exactly this property. The "low leverage"
argument against it was that `bindgen` already produces working bindings, but an
allocation free safe layer is more than `bindgen` gives you, so the generator earns more
than it did before.

**Julia** stays first on audience grounds, but with a caveat the original entry missed. Its
GC means an idiomatic Julia wrapper allocates a fresh `Vector` per array return, the same
shape as the C++ and Python wrappers. A Julia generator needs item 4's output span form to
be first class rather than an afterthought, so the dependency on item 4 is stronger than
"spans work unchanged there".

**TypeScript / Node** drops further. N-API allocates per call on top of an addon build per
platform, and the async idiom already fought the synchronous callback contract.

**The difficulty common to all of them.** Every generator added multiplies the cost of
every future change to the descriptor model. Before adding a fifth language, the four
existing generators should share more than they do now. The `ParameterLists` shape in the
Python and MATLAB generators is the obvious seam, and it was deliberately left unmerged
because the bodies diverge for real reasons. Adding a language is a good reason to revisit
that decision.

---

## 6. Migrating `native-charts` onto these annotations (DONE)

Landed 2026-08-02 in the source repo: imports and poms switched to the published
artifacts, the generated-output diff against the pre-migration baseline reviewed and
pinned by a reference harness there (`GeneratedBindingsTest`), `native-generator` and
`native-util` deleted, the clang parser moved into native-sdk's test sources, and the
`CArrayConverter` bulk copies ported into `bindings-runtime` with the `Unsafe` fast
path on `us.hebi:glue` (the `Transform` overload stayed behind as a private helper in
native-charts). Source-repo follow-ups live in `../HEBI-Java/.claude/plans.md`
under the B.1 status note.

The `@CLibIndex` swap landed 2026-08-03: `NativeGridWindow` annotates row/col, the
`MATLAB_FIX_INDEX` overloads are deleted, and the source repo's reference harness
pins the regenerated wrapper.

Still open, in this repo's court:

1. **`cPrefix` and `useCamelCase` have zero test coverage here**, and native-sdk uses
   them in 16 places to match the hand-written `hebi.h` ABI. Add a `Test*` class that
   pins both.
2. **Port `checkEquals` to the runtime mocks.** The old mock helpers were not
   extracted, so the native-sdk tests carry a local copy per test class.

Release notes owed when the migrated library ships:

- `Runtime.m` `runOnUiThread` no longer echoes `userData` as a MATLAB output. Callers
  that captured the return break.
- The `@CLibIndex` swap: the hand-written clamp absorbed an explicit MATLAB `0`, the
  generated conversion does not, so `addLineChart(0, 0)` now reaches Java as `-1, -1`.
  Zero was never a valid one-based index, so silent absorption becomes a detectable
  error.

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

### Allocation impact: the finding above puts `getLastError` in the hot loop

The error path itself is exempt from the policy, so nothing about `setLastError` or the
exception plumbing changes. What does change is a consequence of the finding that stopped
this item.

Because a throwing method with a value return does not throw in any wrapper, a caller who
wants to detect failure has to poll `getLastError` after every such call. That moves an
error accessor into steady state. The C++ wrapper returns `std::string`, so polling costs
an allocation per call, and the Python one costs a `.decode("utf-8")`.

Whichever of the two ways forward is taken, the accessor needs an allocation free form:

- Under option 1, keeping it exposed in every language, add a cheap predicate next to the
  string accessor. `bool hasLastError()` returning a flag, or a `const char*` overload that
  the caller only converts when it is non-null, makes the common "no error" case free.
- Under option 2, making every throwing method throw, the polling disappears and the
  question goes away. That is the better answer on allocation grounds as well as on
  ergonomics, which is worth recording since the original text treats the two as evenly
  matched.

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
  `hebi_charts_getLastError`. The item 6 migration landed first, so this now goes into
  the same breaking release as item 9 rather than riding along with the migration.

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
4. **Intel Mac static link fails on zlib symbols (reported 2026-08-02).** Linking the
   hand-packaged static bundle into the Core repo fails on osx_amd64 with undefined
   `_crc32`, `_deflate`, ... referenced from
   `Core/third_party/hebi/ndk/lib/osx_amd64/libzip.a` (`CRC32.o`, `zip_util.o`).
   GraalVM's static `libzip.a` expects the system zlib, so `-lz` is missing from that
   link line. This is exactly what `syslibs.txt` and `libs.cmake` exist to record, which
   is why the issue lives here rather than in HEBI-Java: verify the osx wrapper captures
   `-lz` and the other system `-l` flags the same way the Linux wrapper does, add a
   linux/osx x86_64 leg to whatever CI consumes the static package, and until the
   package format ships, document `-lz` as a required system lib for macOS consumers.
   Alternative worth one look: if the image build can prove `java.util.zip` unreachable
   for the algorithms library, `libzip.a` drops out entirely.

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

### Allocation impact: this is now a latency item too

`<lib>_dynamic.hpp` resolves each symbol into a function local `static`, so every wrapper
call pays a thread safe initialisation guard check and an indirect call through a variable
the compiler cannot see through. That is not an allocation, but it is the same class of
steady state cost the policy is aimed at, and it is unavoidable as long as the consumer
binds at runtime.

Linking the static package removes it entirely. The symbol is a direct call, and the
toolchain can inline through the shim into the entry point. So this item is no longer only
about packaging convenience for a second consumer. It is the fastest available calling
path, which matters to the same users the allocation policy exists for, and item 11's C++
allocation harness should link the static package for that reason rather than the shared
library.

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

### Allocation impact: the shared shape has to carry it

Whether a parameter is a caller owned buffer, and whether a return allocates in the
wrapper, are currently decided independently inside each generator. Item 4 adds an output
span and item 1 adds a callback, and both are exactly the kind of thing where one generator
quietly emits the allocating form because nobody wrote the rule down.

Put both properties on the shared shape: "the caller owns this buffer" and "this return
allocates in the wrapper". Then each generator reads a decision rather than making one, and
item 11 has something to assert against per method instead of per hand-picked test case.

**Effort:** medium, and entirely reference-file verifiable. A pure refactor must produce an
empty diff.

---

## 11. An allocation gate

The allocation policy has no enforcement. The reference file check compares text, so it
cannot see that a generated wrapper allocates, and every language suite asserts values
rather than behaviour. Without a gate the policy decays into a comment.

### C++, which is the cheap and strong half

Override global `operator new` and `operator delete` in the C++ test binary, count calls,
and assert the counter does not move across a hot loop:

```cpp
size_t before = alloc_count;
for (int i = 0; i < 1000; i++) {
    total += bt::arrays::dotProduct({x.data(), x.size()}, {y.data(), y.size()});
}
CHECK(alloc_count == before);
```

This is a real gate rather than a proxy. It catches a `std::vector` return sneaking into a
path that is supposed to be allocation free, it catches `std::function` in the item 1
callback, and it needs no tooling beyond the suite that already exists. Warm up first, so
the dynamic loader's one time symbol resolution does not land inside the measured loop, or
link the static package per item 8 and avoid the question.

Cover one method per allocation free shape rather than every method: a primitive call, a
handle call, a span in, an output span, a raw pointer buffer, and a callback. The
convenience overloads that return `std::vector` or `std::string` are asserted to allocate,
which documents the split and catches an accidental reversal.

### Java, which needs a decision first

`bindings-tests` has no way to measure this today. `GeneratedBindingsTest` only compares
files, and nothing drives the generated `$CEntries` from the JVM. The mock word types
(`CDoublePointerMock`, `DoubleSpanMock`) allocate by construction, so a JVM mode assertion
would measure the mock rather than the real path.

Three options, in order of preference:

1. **Measure in the image.** Most faithful, since it is the code that ships. Needs a
   native-image allocation counter and a test entry point that runs the loop inside the
   image, which is new machinery in a repo that currently has no in-image test harness.
2. **Measure in JVM mode with real word types.** Would need the mocks replaced by something
   allocation free, which is a change to `bindings-runtime` for the benefit of a test.
3. **Assert on the generated source instead.** Cheap and immediate: check that no entry
   point for a method without a String or array return contains a `new` expression, or more
   simply that the set of `CTypes` helpers it calls is on an allowlist. Catches the
   generator regressing, not the runtime helpers.

Option 3 is worth doing first regardless, because it costs almost nothing and covers the
generator, which is what actually changes. Options 1 and 2 both want a decision from the
author before anyone writes code.

C needs nothing. It never allocates and the header has no place to hide one.

**Effort:** small for the C++ half, and it should land with item 4 so the output span has
something asserting its whole point. The Java half is a separate decision.

---

## 12. The MEX gateway, as reviewed

Not a feature. A record of what the gateway does well and the four things worth changing,
so the callback work in item 1 does not have to rediscover them.

### What is right, and should not be touched

- **The perfect hash dispatch.** Hashing the raw `mxChar` data with a build time chosen
  seed avoids `mxArrayToString`, which would allocate on every call just to pick a branch.
  The seed search guaranteeing no collisions in the known set is what turns the `switch`
  into a single compare. This is the right design and the alternative, a string comparison
  chain, is both slower and allocating.
- **`MatlabArray` zero-copies on a class ID match** and only allocates a converted copy on
  mismatch, mirroring `calllib`. Correct for a careless caller, free for a careful one,
  which is the right default.
- **`static` function pointers per case** mean one `dlsym` per symbol per session rather
  than per call, and `LibLoader` and `getLibraryPath` as function local statics give
  load-once-per-session for free.
- The RAII helpers are the right shape even though MATLAB's MEX memory manager would
  reclaim the memory anyway.

### Worth changing

1. **`getScalar` does not validate, and nothing upstream does either.** `getPtr` checks for
   uint64, `MatlabArray` checks for complex, `charsToUtf8String` checks for char, but a
   scalar parameter goes straight through `mxGetScalar`, which is undefined on an empty
   array and returns an unpredictable value on a struct or a cell.

   The reflex is that this is redundant because the `.m` wrappers already check. They do
   not. `Math.negate(value)` passes its argument straight to the gateway, and a grep over
   the generated package finds exactly one type check in total, `isnumeric` on a pointer in
   `Shape.m`. So `bindings_tests.Math.negate(struct('a', 1))` is an ordinary user typo that
   reaches `mxGetScalar` on a struct and comes back with garbage. **This is the only input
   path reachable from the public API where a wrong type produces a wrong answer instead of
   an error**, which makes it the one worth fixing.

   Fix it in the gateway rather than in the `.m` wrappers. One place instead of one per
   method, it cannot be bypassed, it does not raise the R2018b version floor the way an
   `arguments` block would, and the cost is a `mxGetClassID` compare against an interpreter
   dispatch that already costs microseconds. The generator knows the parameter name, so the
   error message can name it and read as well as a `validateattributes` message would.
2. **`nrhs` is never checked against the arity of the selected case.** Every case indexes
   `prhs[1]` and up directly, so too few arguments reads out of bounds. Unlike the above
   this is **not** reachable from user code: a `private` subfolder is enforced by MATLAB,
   not conventional, so only functions in `+%%LIB_NAME%%/` can call the gateway at all, and
   the generated wrappers always pass the right count. It guards against a generator bug,
   not a user error. Worth adding because the generator knows the arity and it is one line
   per case, but it is hygiene rather than the fix that matters.
3. **The `uint32` raw hash entry path is dead code.** The generated `.m` files pass the name
   string, so every MATLAB call builds a char array and runs FNV-1a over it. Wiring the
   literal hash into the `.m` files would skip both. The tradeoff is worth stating before
   anyone does it: embedded hashes mean the seed search rewrites every `.m` file whenever a
   method is added, and worse, a stale `.m` against a new MEX would fail by hashing to a
   different function rather than by raising "not found". Names fail loudly, hashes can fail
   silently. **Recommendation: keep the names and delete the raw hash path**, unless a
   MATLAB profile shows the hashing actually matters, which for a dozen ASCII characters is
   unlikely.
4. **`createMexScalar` casts every return to double**, so an `int64` or `size_t` return
   above 2^53 loses precision silently. Pointers already avoid this via the separate uint64
   path. The comment acknowledges the limit, so this is a known tradeoff rather than an
   oversight, but it deserves a line in the known gaps list because it is invisible at the
   call site.

### On allocation

The gateway is about as lean as MATLAB permits. Dispatch is allocation free by design, input
is zero-copy on a class match, and output is one `mxCreate*` per return, which is
unavoidable because MATLAB owns its arrays. Nothing here needs to change for the policy,
and MATLAB stays best effort.

The one thing to carry into item 1: the RAII helpers survive a `mexErrMsgIdAndTxt` longjmp
only because MATLAB reclaims `mxCalloc` and `mxCreate*` memory at MEX exit, not because the
destructors run. They do not run. A callback trampoline holding anything that is not
mx-allocated leaks, which is why item 1's risk list names `mexCallMATLABWithTrap`.

**Effort:** small. Items 1 and 2 are the only ones worth doing on their own, and both are
one generated line per case.

---

## Before open sourcing

Release blockers, separate from the feature items above. None of these is large, and
none should wait until a release is imminent. Assessed 2026-08-02 against the source
repo (`../HEBI-Java`, see its `.claude/plans.md` Part A for the full evaluation).

1. **License.** The README says `TODO`. Nothing else can ship first.
2. **Download URL option.** CLAUDE.md gap 5. The generated loaders point every consumer
   at `files.hebi.us`. The reverted attempt showed the wrong shape, the right one is a
   single `%%DOWNLOAD_URL%%` token.
3. **Nested class rejection.** CLAUDE.md gap 6. First external users will annotate an
   inner class within a week, and today that hangs the compile with no message.
4. **`compiler-linux.sh` log header.** Done. The `%LOG_FILE%` literal is fixed in the
   generated `compiler-linux.sh` and `compiler-osx.sh`, with the reference files updated.
5. **Proto remnants.** CLAUDE.md gap 7. `package hebi.api.proto` and the `"hebi"` name
   default should change before `api.json` has external consumers, not after.
6. **Decide which documents ship.** Mostly handled: the root now carries only `README.md`
   and `CLAUDE.md`, the working notes moved to `.claude/`, and the blog plan moved out to
   the charts blog notes kept outside the repo. What remains is the call on `.claude/` itself,
   which is internal working material that a public repo would be judged by rather than by
   the code. Either keep it out of the published tree or move an edited
   `.claude/architecture.md` to `docs/` as the one piece worth publishing.
7. **Origin note in the README.** One sentence on the extraction from a robotics
   codebase in production since 2015, once the source history reference is public
   somewhere. Provenance is the strongest answer to the AI-generated dismissal, and it
   is currently invisible.
8. **CI that verifies exactly what the README claims.** This is the one recurring gap
   found in a 2026-08-02 survey of the author's three published extractions: MFL's
   workflow triggers on branches that no longer exist so the badge lies,
   QuickBuffers claims Java 6 to 20 but tests only JDK 8 with `continue-on-error`,
   and reachability-annotations has no CI at all. Each repo now has a
   `fable5-findings.md` with agent-executable fixes. For this repo the rule is set
   before release: the CI matrix mirrors the compatibility claim one to one, required
   jobs never use `continue-on-error`, and action versions stay current. The author
   has said CI is outside their expertise, so this is standing work for an agent, not
   for the author.
9. **Changelog from the first tag.** QuickBuffers reached 19 tags with no changelog
   and reconstructing one later is expensive. MFL's hand-written per-fix changelog
   with issue links and contributor credits is the house model, start it at 0.1.
10. **Hygiene a machine can hold.** The survey found license headers naming the wrong
    project in two of three repos, a README link to a file that does not exist, and
    build mechanics invisible to IDEs. Before release: verify the license-plugin
    project names per module, run a link check over the README, and keep all code
    generation visible to IDE and grep.

## Reading the items together

### Two things that do not line up

**The ABI-breaking items now come after the migration.** Items 7 and 9 both rename
public C symbols, and item 6 landed first, so the original plan of folding every break
into the migration release is off the table. What remains of the constraint: **items 9
and 7 land together as the one remaining breaking release**, 9 first so 7's accessor is
only named once.

**Item 8 never depended on item 6.** The static consumer is a separate algorithms
library, not `native-charts`. Item 8's real gates are item 9, for the symbol collision,
and that library adopting the annotations, which is its own migration. `bindings-tests`
is enough to build and verify the packaging.

### Interactions worth knowing

| Items | Interaction |
|---|---|
| 9 into 7 | Both rename library-level symbols. Land as one release or `getLastError` is renamed twice. |
| 9 into 1 | The MEX dispatch hash table covers every symbol. Renaming after adding callback cases means redoing that work. |
| 9 into 2 | The Python extension binds C symbols by name, so it should be written against the final names. |
| 9 into 8 | Removes the duplicate `graal_*` symbols that otherwise make two static images unlinkable. |
| 4 into 2 | The extension's buffer-protocol path is the span path. Spans first avoids writing it twice. |
| 4 into 1 | A callback taking a span cannot be marshalled by the MEX gateway. Item 1's rejection list needs a span case once item 4 lands. |
| 4 into 5 | Julia passes structs by value natively, so spans work unchanged there. |
| 1 into 2 | A synchronous callback re-entering Python must hold the GIL. Callbacks first, or the extension's trampoline is reworked. |
| 1, 4 into 10 | Both add per-language branches. Converging before them means converging twice. |
| 2 with 8 | A statically linked extension gives a self-contained wheel with no separate shared library. Both also extend the same stage 2 `CMakeLists.txt`, so expect a merge point. |
| 3 into 5 | Julia is one-based, so `@CLibIndex` is a prerequisite. Already landed. |
| 4 into 11 | The output span is the reason the gate exists. Land the C++ half with item 4 or nothing asserts it. |
| 8 into 11 | The gate should link the static package, so loader symbol resolution stays out of the measured loop. |
| 12 into 1 | The gateway review records the longjmp and validation constraints the callback trampoline runs into. |
| 10 into 11 | Once the shared shape carries "allocates", the gate can assert per method instead of per hand-picked case. |

Two items are **ABI-neutral** and can therefore slot anywhere: item 1, because the callback
transport is unchanged and only the typedef's origin moves, and item 4, provided the
`FLOAT64` struct keeps the name `double_span` so existing consumers see no rename. The
output span added under *Allocation impact* preserves that, because it is a new struct
rather than a change to the existing one.

## Sequencing

Grouped, because the ordering constraint is mostly between groups rather than within them.

```
A. Foundation            4 (spans, now with the output span)   with   11 (C++ gate)
                         additive, unblocks 2 and 5.
                         The gate lands with it or nothing asserts the output span.

B. One breaking release  9 (isolate API)      then   7 (last error)
                         every public rename in a single release, 9 first so 7 is
                         named once. Retry 7 with its three commit split.

C. Parallelisable        1 (callbacks)   and   8 (static packaging)
                         independent of each other. 1 is ABI-neutral, 8 needs only 9.

D. Gated on the above    10 (generator convergence)
                         after 1 and 4, so it converges on the final shape

E. Larger, optional      2 (Python extension)   then   5 (other languages)
                         2 needs 1, 4 and 9. Benchmark the ctypes fast path first,
                         it may remove the need. 5 needs 10 and 4.

    3 (@CLibIndex)       DONE
    6 (migration)        DONE, its two leftovers are small and slot anywhere
    12 (MEX review)      unblocked, slots anywhere. Its two worthwhile fixes are
                         one generated line per case and depend on nothing.
```

The one genuine judgement call is the order within **C**: whether the static packaging
or the callbacks matter first. Nothing else in the order changes.

The allocation policy did not reorder the groups. It grew item 4, which was already first,
and added item 11 next to it.

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
- once item 11 exists, a case in the C++ allocation harness for any new call shape that
  claims to be allocation free, and a deliberate one for any that is not

New annotations also need a row in the README feature table and, if they carry a trap, an
entry in the `CLAUDE.md` bitten list.
