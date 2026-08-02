# Architecture

The architecture of the charts system, extracted from the blog notes so that
`../../HEBI-Java/docs/charts-blog-notes.md` can stay about writing rather than about
structure.

**Provenance, and how to read it.** Sections are marked:

- **[verified]** Read directly from this repository. Accurate as of the current `develop`.
- **[secondhand]** Reconstructed from an editorial review of the original notes. The notes
  are not in this repo and were never read directly, so the shape is probably right and the
  detail needs the author's correction. Treat every number as unconfirmed.

Anything the review did not mention is simply absent here rather than judged unimportant.

---

## The constraint everything else follows from

**[secondhand]**

A chart that perturbs the system it is plotting is not a measurement device. The design
constraint is a jitter budget, not a frame rate: the library sits inside a control loop
running at kilohertz rates, and any pause it introduces shows up in the data as if it were
a property of the system under observation.

That single constraint explains choices that otherwise look eccentric:

- Serial GC, and a deliberately small eden.
- Separate isolates for latency critical state.
- Allocation free label rendering.
- A layout pass that must terminate in one iteration.
- A wrapper API that never requires the caller to be on a particular thread.

Read the rest of this document as consequences of that one requirement.

---

## Latency domains, and isolates as GC boundaries

**[secondhand]** *The most novel idea in the material, and the least written down.*

GraalVM isolates each own a separate heap. The system uses that as a blast radius boundary
rather than only as an embedding mechanism: latency critical state, the `loopTimer` being
the named example, lives in its own isolate, so the chart's allocation churn cannot stall
it. A collection in the UI isolate does not touch the timer's heap.

This inverts the usual approach to GC in a latency sensitive JVM application. Instead of
tuning one heap until the worst case is acceptable everywhere, it partitions the program so
that the components with a hard latency requirement do not share a heap with the components
that allocate.

Consequences worth confirming with the author:

- Which state lives in which isolate, and what the rule is for deciding.
- How state crosses between isolates, given that an object reference cannot.
- Whether the isolate boundary is visible in the public API or entirely internal.

The bindings layer already supports named isolates as a first class concept, so this is
likely where that support comes from. **[verified]** `@CLibMethod(isolateName = ...)` puts a
class in a named isolate, each isolate has its own state and its own error provider, and the
generated C shim selects the isolate per call site (`isolate::global()` versus
`isolate::secondary()`). `TestIsolated` in this repo covers it.

---

## Memory and the collector

**[secondhand]**

Serial GC, chosen because its pause time scales with eden size rather than with live set
size. That is the load bearing detail: it means the tuning direction is to *shrink* eden,
not to grow it, which is the opposite of the usual instinct. Eden and survivor sizes are
fixed via substitution rather than left adaptive.

Reported figures, all unconfirmed: 5 to 20 MB steady state, 12 MB eden, roughly a 1 ms
pause every 3 seconds at over 10 M operations per second, around 80 ns average.

Two allocation sources that had to be removed underneath the application:

- `Math::pow` allocating three `double[]` internally.
- Marlin's foreign memory allocations, which appeared after the Graal 25 update and cost two
  days to find.

Both are examples of the same class of problem: at this level, the JDK and the renderer are
part of your allocation profile, and neither is under your control until you substitute it.

---

## Ingest to JavaFX handoff

**[secondhand]** *A reusable concurrency pattern, and probably the most portable idea here.*

The data producer must never block, and the UI must never see a torn read. The mechanism:

- An uncontended per object spinlock. Per object rather than global, so producers do not
  contend with each other.
- A dirty bit, so a given object triggers at most one FX update per frame regardless of how
  many times it was written.
- The FX application thread synchronises at frame start, not on demand.
- Double or triple buffered structures with a pointer swap, so ingest never waits for the
  UI to finish reading.

The property that matters is asymmetric: the producer is on the latency critical path and
the consumer is not, so every tradeoff is resolved in favour of the producer.

---

## Rendering

**[secondhand]**

**Allocation free axis labels, via Schubfach.** The interesting part is not that formatting
avoids allocation. It is that decomposing a value to mantissa and exponent answers "how many
distinct digits does this range need" without ever materializing a string, so the decision
about how many labels to draw is free. Allocation freedom falls out of that rather than
being the goal.

**Glyph cache blitting.** Rendered glyphs are cached and blitted rather than re-rasterised.

**DPI handling.** Explicit logical to physical coordinate conversion.

**Deterministic single pass layout.** The failure mode being designed against is oscillation:
more space produces more labels, which consumes space, which produces fewer labels, which
frees space. The fix is a fixed measurement order, horizontal then vertical, driven by pre
and post layout listeners, so the pass terminates in one iteration by construction rather
than by converging.

---

## Native image configuration

**[secondhand]**

The recurring problem is that native-image needs to know statically what reflection will
occur, and the usual libraries answer that question at runtime.

- Annotation processors generate `reflect-config` and `jni-config` rather than the config
  being maintained by hand or traced from a run.
- FXML and CSS annotations generate their own config.
- XML handling went JAXB, then Jackson, then a custom layer. JAXB was incompatible. Jackson
  worked on Graal 21 and broke on Graal 25. The replacement emits the same schema
  information as compile time constants and does no reflection at all.

The pattern across all three: replace runtime discovery with compile time generation. That
is the same pattern the bindings layer applies to the C ABI, which is probably not a
coincidence.

---

## The language bindings

**[verified]** *This is the part this repository is. See `CLAUDE.md` and `README.md` for the
detail, this is only the shape.*

**The inversion.** The C ABI is the product. The annotated Java API is the single source of
truth. The C, C++, Python and MATLAB wrappers are derived artifacts, and none of them may be
hand edited.

**The pipeline.** An annotation processor reads the annotated Java, builds a descriptor
model, and drives one generator per target language:

```
annotated Java  ->  AnnotatedCLib* model  ->  LibraryDescription (proto)
                                                |
                        +-----------+-----------+-----------+
                        |           |           |           |
                    C header    C++ header   Python      MATLAB
                    + shim      + wrappers   ctypes      classdef
                                                         + MEX gateway
                        |
                    @CEntryPoint methods, generated as Java source
```

**Why the entry points are generated rather than hand written**, which is the good argument
and not the obvious one: hand maintaining them puts the code at the wrong level for testing.
The JRE has no `ObjectHandle` lookup and no type information, so that layer cannot be unit
tested at all. Generating it moves the testable surface somewhere a test can reach.

**Handles.** Objects cross the ABI as opaque `ObjectHandle` values, one registry per class
hierarchy. Callers never see isolates or handles. Everything instantiable gets a generated
destructor.

**Buffers.** Caller owned in, library owned out. A returned pointer is valid only until the
calling thread's next call, so every wrapper copies immediately. Spans carry
`(pointer, length)` as one by-value struct so a bulk method needs no separate count
argument.

**Errors.** A method not marked `Noexcept` is assumed to throw. Void methods are promoted to
an int status code, and every other return type gets a type specific default on failure.
There is a known consequence here worth naming, because it surprises people: a throwing
method with a value return does not throw in any wrapper, so failure is detected out of
band via the last error accessor. See item 7 in `plans.md`.

**Verification.** Every generated file is checked into `bindings-tests` as a reference file
and compared on every build. A refactor meant to preserve behaviour must produce an empty
diff. This is the primary safety net for the whole generator.

**Allocation policy.** Zero allocation in steady state, with setup and failure paths exempt.
C and the Java entry points are held to it strictly, C++ raw and span overloads strictly with
allocating convenience overloads allowed alongside, Python and MATLAB best effort because
neither can be made allocation free. See `CLAUDE.md`.

**Two stage build.** Stage one runs native-image with a generated compiler wrapper that
intercepts the link step and additionally emits a static library. Stage two runs CMake over
that output to produce the final shared library and, optionally, the MEX gateway. Stage one
alone is not sufficient, because it only exports the prefixed entry point symbols.

---

## MATLAB distribution and the MEX gateway

**[verified]** for the gateway as it exists in this repo. **[secondhand]** for the history.

**The history.** `calllib` worked and reached roughly 0.5 MHz, but required a C compiler
that beta testers could not install. Building the MEX inside MATLAB broke on customer
licenses, hit version specific bugs (2022a on macOS pulling in the MATLAB engine), had no
aarch64 at first release, and depended on CI that never answered the request that mattered.
`matlab_add_mex` was too magical and needed a local install.

**The resolution.** A MEX file is a shared library with one required symbol and an unusual
extension. Three headers, two `.lib` files, and plain CMake against R2018b replace the whole
MATLAB side toolchain. Reported result: 800 kb to 100 kb, and 0.5 MHz to 2.5 MHz.

**The gateway, as it exists.** **[verified]** Everything funnels through one `mexFunction`,
so dispatch is the design problem:

- The function name is hashed directly over the raw `mxChar` data with FNV-1a, using a build
  time seed chosen until the known symbol set has no collisions. Dispatch is then a single
  `switch` compare. The naive alternative calls `mxArrayToString` and allocates on every call
  just to pick a branch.
- Symbols resolve into a function local `static` per case, so it is one `dlsym` per symbol
  per session rather than per call. `LibLoader` and the library path lookup are likewise
  function local statics, so the library loads once per MATLAB session.
- `MatlabArray<T, mxClassID>` zero-copies when the class ID matches and only allocates a
  converted copy on mismatch, which mirrors `calllib` semantics. Correct for a careless
  caller, free for a careful one.
- RAII wrappers handle temporary UTF-8 strings and string arrays.

**Three properties that are easy to get wrong later**, recorded because they are not obvious
from the code:

1. The RAII destructors do **not** run if `mexErrMsgIdAndTxt` fires, because it longjmps.
   Nothing leaks only because MATLAB's MEX memory manager reclaims `mxCalloc` and
   `mxCreate*` memory at exit. Anything not mx-allocated has no such safety net.
2. `private/` is enforced by MATLAB, not conventional. Only functions in `+<lib>/` can call
   the gateway, so it is a genuine boundary.
3. Every numeric return is cast to double, so an `int64` or `size_t` above 2^53 loses
   precision silently. Handles are unaffected, they cross as `uint64`.

Known gaps in the gateway are recorded as item 12 in `plans.md`.

---

## Open questions

Things the review surfaced that this document cannot answer without the original notes.

- The layer decomposition. The original notes had a Layers section that the review
  recommended stripping for parts. Those parts are distributed above, but the overall
  decomposition is not reconstructed here.
- Pointer lifecycle beyond "caller owned in, library owned out, everything has a
  destructor".
- Where the isolate boundary sits relative to the public API.
- Whether the ingest handoff and the isolate partitioning interact, since both are about
  keeping the producer off the UI's critical path.
