# MATLAB SDK on the bindings stack

The working tracker for migrating the hebi-matlab SDK from MATLAB's embedded JRE onto
the generated MEX bindings. The source assessment is item **B.8** in
`../HEBI-Java/.claude/plans.md` (2026-08-02, reassessed 2026-08-03), which stays the
record of the reasoning. This file is the execution plan and the running status, kept
here at the author's request because the gating work is generator features in this
repo. It deliberately spans both repos, each work item names where its code lands.

**Status: planning. No code written. Next action: Phase P-1 (test harness prep), then
the P0 spike.** Update the status line and the Log at the bottom as work proceeds.

## Fixed design direction (author decisions, do not relitigate)

- The full `send()` surface stays. Every parameter is deliberate. The MATLAB style
  (polymorphic `send`, key-value args, struct-like objects) is the usability standard.
- Parsing stays in Java. MATLAB's `inputParser` is too slow for the real-time loop,
  and parsing in the MEX layer would leak API semantics into the open-source
  generator. Keep MATLAB dumb.
- The user-visible API is unchanged. The `api-compatibility` golden suite is the
  acceptance gate. The B.11 "API 2.0" review is decoupled from this migration
  (author, 2026-08-04): it is the same amount of work before or after the switch, so
  it does not gate any phase here.
- The generator ships only generic machinery (variant marshalling, handles). Key
  names, synonyms, types and validation stay in the closed Java core.
- Arrays cross as a **flat buffer plus dims** (author, 2026-08-04). MFL used the
  same shape and it worked well. Concretely a matrix struct
  `{ double* data; size_t* dims; int numDims; layout }`, one shape for 1D/2D/3D in
  both directions. The `layout` field means the transpose question lives **in the
  struct that has the data**, not in a convention: MATLAB sends column-major and
  says so, consumers index accordingly. No eager transposing, and for now no
  auto-conversion to `double[][]`/`double[][][]` on the Java side, code consumes
  the flat buffer (a lazy converter in the native `MatlabParameter` is fine where
  the parsers require nested arrays).
- Feedback stays an **object with property syntax** (author, 2026-08-04). Per-field
  getters on a handle classdef, which also preserves the current field display with
  units. No mxArray-struct assembly path is planned.
- Performance is not a gate (author, 2026-08-04). The current bridge goes through
  reflection and is guaranteed worse than any MEX wrapper this builds. Measure to
  know the numbers, not to decide go/no-go.

## Why this works (verified 2026-08-04 by survey of both repos)

The three facts that make the migration small relative to the API size:

1. **One calling convention covers the whole SDK.** All 92 `@MatlabMethod`s across
   the 8 `*Api` classes have the signature `Object method(MatlabArgs args)`. Getting
   `MatlabArgs` across the MEX boundary once gets the entire SDK across. The 607
   generated fixed-arity Java facade overloads exist only to give the JVM bridge
   something to bind to and disappear entirely.
2. **`MatlabParameter` is the seam** (`matlab-common/.../util/MatlabParameter.java`,
   365 lines). It enumerates the complete variant set crossing the boundary: empty,
   boolean, boolean[], string/char, string[], double scalar, double[], double[][],
   double[][][], and object handles. No function handles, no callbacks, no cell
   arrays going in (except cell-of-char as string[]).
3. **The Java side runs unchanged inside the image.** The sdk is already
   native-image compliant (author, 2026-08-03). The 48 generated `@InputParser`
   parsers, the Api classes and the core all compile into the image as is. The
   `*Struct` types are Java objects that only look like structs from MATLAB, so no
   generator struct support is needed, they map to handle classdefs with property
   accessors, the proven hebi-charts pattern.

Performance is expected to improve, not regress: ~0.4 us per MEX property write vs
~35 us for a JVM field write (2015 measurement), and the hebi-charts gateway measures
~2.5 MHz dispatch. The current bridge additionally pays reflection on every call, so
any MEX wrapper is a floor improvement. The spike still measures per-field feedback
reads at 1 kHz for the record, but it is informational, not a gate.

The API is polling-only. The examples corpus (133 files) has zero callbacks into
MATLAB, zero user threads, zero timers on HEBI objects. Callbacks, the one thing MEX
cannot do well, are not needed. Bindings item 1 (callbacks) is NOT a prerequisite.

## Generator gaps, mapped to work items

What the pipeline supports today and what is missing, from the 2026-08-04 survey of
`MatlabBindingsGenerator`, `TypeMap` and the reference output:

Supported already: scalars in/out (with int64/size_t precision handling), strings
in/out (UTF-8 correct back to R2018b), string arrays IN (cell of char), double
buffers in (zero-copy on class match), `double[]` returns as 1xN, object handles
with hierarchies and upcasts, enums, `@CLibIndex`, `@CDefault`, properties,
per-class `matlabOverload` escape hatches, `functionSignatures.json`, the whole
packaging and loader chain, and a clean-room MEX SDK so CI needs no MATLAB.

Missing, in priority order:

### G1. Varargs/variant transport (the gate, everything waits on it)

The number one generator feature (B.8's words). Estimated 1-2 weeks.

- [ ] Args-builder C API in `bindings-runtime`: an opaque builder handle with
      `addEmpty`, `addBool`, `addLogicalArray`, `addString`, `addStringArray`,
      `addDoubleArray(matrixStruct)` taking the `{ data, dims, numDims, layout }`
      struct (one entry for scalar/1D/2D/3D, the MFL shape), `addBytes`,
      `addHandle`.
- [ ] A varargs flag on `@CLibMethod` so a method declares "trailing args become a
      variant list". The MEX gateway then loops over `prhs`, packing each mxArray by
      its MATLAB class. One MATLAB-to-MEX transition per call regardless of arg count.
- [ ] Java-side receipt: the entry point materializes the builder buffer as the
      argument list object the annotated method declares.
- [ ] Pinning: a `TestVarArgs` class in `bindings-tests` test API plus assertions in
      all four language suites (other languages get the raw builder, MATLAB wrapper
      first). Reference files, `ProcessorValidationTest` rejection cases for invalid
      combinations (varargs not last, varargs plus `matlabOverload` owning the call).
- Relationship to item 13 (Saxon): explicitly complementary, Saxon does not need
  this. Recorded in plans.md item 13c.

### G2. Return-path gaps

The SDK returns shapes hebi-charts never did. About a week, per the reassessment.

- [ ] **Matrix returns**: `double[][]` and `double[][][]` (kinematics FK/Jacobian/IK,
      trajectory `getState`, 4x4xN frame stacks). Today only `double[]` crosses, as a
      1xN row vector. Decided shape: the matrix struct
      `{ data, dims, numDims, layout }` from the design direction, with `data`
      pointing at the per-thread scratch buffer (same lifetime contract as string
      and span returns, valid until the thread's next call) and the gateway doing
      one `mxCreateNumericArray` plus memcpy. Pin the layout semantics with a
      golden test early.
- [ ] **String-array returns**: `CHAR_POINTER_POINTER` crosses inbound only today,
      `getCToMex` throws on it. Needed for name lists (`getInfo` names, log field
      lists). Return as cell of char.
- [ ] **Cell returns** for `Object[]` (`HebiUtils.loadHRDF` does `cell(...)` today).
      Check whether the string-array return plus handle returns cover the real call
      sites before building anything generic.
- Not needed: echoing non-const buffers (already dropped deliberately), struct
  codegen (structs are Java objects, see above).

### Considered and rejected: allocator callbacks from Java into the shim

Explored 2026-08-04 at the author's request: could the shim or gateway expose
functions that Java calls mid-entry-point to allocate structs or MATLAB matrices
directly, so returns write once into MATLAB-owned memory with no scratch-buffer
copy?

Mechanically yes. The ingredient is proven in this repo: `TestCallbacks` invokes C
function pointers from image code via `@InvokeCFunctionPointer` with a `TO_NATIVE`
transition, so the gateway could register an allocator function pointer per session
and the entry point could call it. Rejected for now, reasons ranked:

1. **Allocation failure longjmps through Java frames.** `mxCreate*` inside a MEX
   file does not return null on OOM, it terminates the MEX function through
   MATLAB's error mechanism. Today that longjmp is confined to gateway C++ frames
   and survivable only because MATLAB reclaims mx memory at MEX exit (item 12
   finding). An allocator callback moves the longjmp origin inside a Graal entry
   point, unwinding through Java frames and the isolate's transition bookkeeping.
   That is undefined behavior, not a recoverable error, and no trap variant exists
   for plain `mxCreate*`.
2. **Thread affinity is uncheckable.** mx functions are legal only on the
   interpreter thread during `mexFunction`. The SDK's image runs real background
   threads (feedback scheduler, lookup, log writers), and nothing can enforce that
   none of them ever reaches the allocator. It would compile fine and corrupt
   MATLAB's memory manager at runtime. The current design has no such trap because
   background threads cannot reach MATLAB at all.
3. **It makes the library MATLAB-shaped.** One core serves MATLAB, C, C++ (B.20)
   and possibly Python. Either `mxArray` semantics leak into the generic ABI, or a
   language-neutral allocator-callback protocol is designed (dims-aware allocate,
   ownership slots, a nesting stack for multiple allocations per call) and
   implemented per wrapper. That is a full cross-language feature, roughly items 1
   plus 4 combined, for a benefit only MATLAB sees.
4. **The payoff is one memcpy.** The saving over scratch-buffer-plus-copy is a
   single bulk copy at memory bandwidth, low microseconds even for a full feedback
   view, against a MEX transition that already costs microseconds. The
   performance-is-not-a-gate ruling applies squarely.
5. **It weakens the test story.** JVM-mode mocks cannot invoke real function
   pointers, so the path is exercisable only in the full MATLAB suite, and the
   failure modes above are the nondeterministic kind.

The door stays open: the matrix struct says nothing about who owns `data`, so an
allocator-backed variant could be added later as a new entry-point shape without
disturbing the copy-based one, if measurement ever justifies it. It would build on
the item 1 callback machinery.

### G3. Feedback path (decided: property objects)

- Feedback is a handle classdef with per-field property getters, each one MEX call
  returning a row vector. Decided 2026-08-04, this also preserves the current field
  display with units. Carrying the unit-annotated display over is an S2 work item.
- [ ] P0 spike records per-field read rates at 1 kHz with many modules, for the
      record only. The old fallback (a hand-written gateway extension assembling an
      mxArray struct in one call) stays noted in B.8 but is not planned.

## SDK-side work items (all in ../HEBI-Java)

### S1. Native-backed MatlabParameter (days)

- [ ] Implement the `MatlabParameter` accessor surface over the args-builder buffer.
      First version may re-materialize boxed objects, optimize later. Everything
      above the seam (`MatlabArgs`, the 48 parsers, the Api classes) is untouched.

### S2. Annotated API layer and dispatch (1-2 weeks, mechanical)

- [ ] `@CLibClass` layer over the existing `*Api` classes (or directly over
      DeviceGroup/DeviceLookup/kinematics/trajectories, decide against native-sdk's
      B.20 scope). Name-to-method dispatch: each `@MatlabMethod` name becomes one
      varargs entry point, `hebi_Group_send(group, cmd, args)`.
- [ ] Struct handle classdefs: `CommandStruct`, `GainStruct`, `IoCommandStruct`,
      `FocCommandStruct`, `SafetyParamsStruct` as annotated classes with property
      accessors (IO pins as fixed properties a1..a8, b1..b8). `FeedbackStruct` as a
      handle over a native snapshot buffer, refilled by `getNextFeedback(fbk)`,
      preserving today's reuse semantics exactly.
- [ ] Display: carry over the unit-annotated field display (today from the
      `matlab-common` display classes and `getFeedbackUnits`). Likely a generated or
      hand-kept `disp` override on the feedback and command classdefs.
- [ ] The thin `.m` layer: generated classdefs replace the hand-written wrappers.
      `HebiArm`, `+HebiArmPlugins`, `HebiMobileIO`, `HebiGripper`, `plotLogs` and the
      other pure-.m code migrates untouched. The ~35 lines per classdef of
      handle-hiding boilerplate and the `methods`/`fields` shims are Java-bridge
      artifacts and disappear (the generator already shadows handle methods).
- [ ] Blocking convenience loops (`executeTrajectory`, `moveJoint`, `moveLinear`,
      `sendWithRetry`) stay in `.m` over `trajectory.getState` so MATLAB keeps loop
      control and Ctrl+C behavior (author direction).

### S3. Logging (P4)

- [ ] `.hebilog` writing and MAT conversion via MFL compile into the image unchanged.
      `stopLog`: converter writes the MAT file, the `.m` wrapper `load()`s it, the log
      becomes a real MATLAB struct. The 7 `struct(log)` sites and `plotLogs` (76 call
      sites) work unchanged. The in-memory `LogFormat.Memory` path goes through the
      same route (it was the OOM-prone one anyway).
- [ ] Drop the `System.gc()` calls (`triggerStopLogGC`), meaningless under Serial GC
      in an image.

### S4. Lifecycle (the known unknowns, 2-3 weeks bundled with S5)

- [ ] `clear all` / `clear classes` / `clear mex` teardown and mexLock policy.
      Background threads (feedback scheduler, lookup, log writers) live in the image,
      decide what unload means.
- [ ] Ctrl+C during blocking calls (`getNextFeedback` blocks up to 2 s in the image).
      The spike measures behavior, the mitigation is short native timeouts with the
      wait loop in `.m` if needed.
- [ ] Workspace `save`/`load` of API objects stops working (handles are session
      pointers). Census says nobody does this. Release-note it, delete the
      `Serializable` shim.

### S5. Packaging, docs, input devices (P5)

- [ ] Reuse the hebi-charts chain: per-platform zip, `locate_binary.m` three-step
      resolution, shared cache, `downloadUrl`. The `.mltbx` toolbox per bindings
      item 15b: generator emits the `.prj`, `packageToolbox` runs in HEBI-Java's
      release CI where a MATLAB license exists. A toolbox bundles all platforms.
- [ ] Help: today 267 HTML files come from undocumented `help2html` via
      `hebi_help_deploy.m`. The generator emits `functionSignatures.json` and help
      text from `@Parameter` annotations. Decide what replaces the HTML docs.
- [ ] `HebiKeyboard`/`HebiJoystick` (Java plus jinput) die with the JRE independently
      and the flagship arm demos use them. Needs its own small plan (figure
      callbacks, a tiny input MEX, or third-party). `HebiCam` is orphaned, out of
      scope.
- [ ] Delete on completion: the dual script dirs plus `hebi_deploy.m` (existed only
      to p-code `hebi_load.m`), `hebi_load`/`hebi_config` library-path logic, the
      Zulu 8 download, the ProGuard-against-rt.jar chain, the 607-overload facade
      half of `MatlabAnnotationProcessor`, the Java 8 pins once nothing else needs
      them.

## Phases

Matches B.8's phasing, with current status.

| Phase | What | Where | Status |
|---|---|---|---|
| P-1 | Test harness: runner in-repo (done via B.19), parameterize the API-under-test path in `ExecuteTests.m`, add imitation-device group tests (lookup, send round trip, getNextFeedback incl. reuse, start/stopLog), extend `MultiArgsWrapperBenchmark.m` with a MEX variant | HEBI-Java | partially done (runner merged, rest open) |
| P0 | Spike, 1-2 weeks: imitation-device lookup + group + send with varargin forwarding + feedback loop through a prototype gateway. Records send latency and per-field reads at 1 kHz (informational), verifies Ctrl+C behavior. Exit: api-compatibility green on the prototype for covered classes | both | not started |
| P1 | Generator work: G1 varargs, G2 returns | native-bindings | not started |
| P2 | Annotated API layer (S2 first half) | HEBI-Java | not started |
| P3 | Thin `.m` layer, hebi-matlab-examples as acceptance (`basic/` on imitation devices, `ex3e_command_pos_vel_effort` as cross-language conformance) | HEBI-Java | not started |
| P4 | Logging (S3), HebiUtils, plotLogs untouched | HEBI-Java | not started |
| P5 | Input devices, packaging, MATLAB release matrix from R2018b up (S5) | both | not started |
| P6 | Kept open, not planned: Python/C++ typed faces over the same varargs parser | both | not started |

Effort, per the 2026-08-03 reassessment: roughly 6-9 focused weeks to an
api-compatibility-green imitation-device milestone, P4/P5 breadth on top.

## Open design questions

1. **Annotate `*Api` or the core directly?** The Api classes carry the `@InputParser`
   machinery and MATLAB semantics, so annotating them is the low-risk path. B.20
   (hebi.h) wants annotations over the core. They converge on the same core, decide
   deliberately rather than drift.
2. **`getLastError` and error identifiers.** Wrapper errors today throw
   `MatlabError` with clean MATLAB messages. Map parser errors through
   `getLastError` so message quality survives (the parser's error text is part of
   the UX). Check whether error identifiers are needed for `try`/`catch` users.
3. **MATLAB `string` objects.** The current gateway rejects double-quoted strings
   (char arrays only). The Java bridge accepts them today via auto-conversion.
   Either accept `string` in the gateway/`.m` layer or release-note the restriction.

Resolved 2026-08-04 (author): array shape is the flat matrix struct
`{ data, dims, numDims, layout }`, the layout field puts the column-major question
inside the struct that has the data (were questions on shape and transpose),
B.11/API 2.0 is decoupled entirely, and allocator callbacks from Java into the shim
are considered and rejected (see the G2 subsection). All moved into the design
direction and G2 sections.

## Risks

- **Ctrl+C on blocking native calls.** Mitigation is keeping waits short and loops in
  `.m`. Spike verifies.
- **Lifecycle edge cases** (`clear` variants, unload with live background threads).
  Known unknown, budgeted in S4.
- **Surface breadth beyond the examples census.** The census covered 133 example
  files and the golden tests. Long-tail users may touch uncensused corners
  (workspace save, `struct()` on command objects, `methods`/`fields` shims). The
  release notes list what changed, and the compatibility suite is the contract.
- **Scope creep toward B.20.** hebi.h compatibility is a separate deliverable. This
  item only decides annotation placement (question 3) with B.20 in view.

## Verification

- Generator changes: `mvn clean verify` here against the reference files, then
  `mvn install` and `mvn clean verify -pl native-charts -am` in HEBI-Java, per the
  workspace cross-repo loop. `scripts\build-native.bat` plus
  `scripts\run-tests.bat matlab` for the MEX suite.
- SDK acceptance: the `api-compatibility` suite (28 tests,
  `sdk/src/test/matlab/`, runs the assembled release zip via
  `mvn verify -Pproguard -DenableMatlabTests -DskipTests -pl sdk`) green on both
  backends, plus `matlab-compatibility/` (26 tests, `matlab-common`) as the
  checklist of bridge behaviors to re-verify or consciously drop.
- Benchmarks: `MultiArgsWrapperBenchmark.m` extended with the MEX-gateway variant
  for apples-to-apples dispatch numbers.

## Log

- **2026-08-04** Author decisions on the matrix shape: matrices cross as
  `{ double* data; size_t* dims; int numDims; layout }`, the layout field carries
  the column-major fact so transposes live in the struct that has the data, no
  eager transposing and no auto-conversion to nested Java arrays for now. Explored
  allocator callbacks from Java into the shim (Java allocating mxArrays or structs
  through registered function pointers) and rejected them, see the G2 subsection:
  mx OOM longjmps through Java frames, uncheckable thread affinity against the
  image's background threads, MATLAB-shaped ABI or a full cross-language allocator
  protocol, one memcpy of payoff, and a path only testable in the full MATLAB
  suite. The copy-based path stays, the struct leaves the door open.
- **2026-08-04** Author decisions: B.11/API 2.0 decoupled (same work before or after
  the switch, no phase gates on it). Arrays cross as flat buffer plus dims, the MFL
  shape, in both directions. Feedback stays an object with property syntax,
  preserving the unit-annotated field display, no mxArray-struct assembly path.
  Performance is informational, not a gate, since the reflection-based bridge is a
  guaranteed-worse baseline. Updated G1, G2, G3, phases, questions and risks.
- **2026-08-04** File created. Surveyed both repos: confirmed the single
  `Object method(MatlabArgs)` calling convention (92 methods, 8 Api classes, 607
  facade overloads to delete), the `MatlabParameter` variant set as the transport
  contract, and the generator gap list (G1 varargs is the gate, G2 matrix and
  string-array returns, G3 decided by spike, callbacks and struct codegen not
  needed). Plan matches B.8 plus its 2026-08-03 reassessment. No code yet.
