# Testing the MATLAB port against the known API surface

An evaluation of the options, written 2026-08-05 while the port sat at 32/33 on the
native backend. Kept out of `matlab-migration.md` so the tracker stays the execution
plan and the status. Nothing here is decided.

## The gap this is about

The acceptance gate today is behavioral: 33 golden tests in `sdk/src/test/matlab/`,
parameterized over both backends through `-Dmatlab.api.path`. That measures whether the
ported surface behaves, not whether the surface is complete. The two numbers that frame
it: the shipping `.m` layer is 6324 lines across 23 files, the staged compat layer is
1362 lines across 12, and 33 tests touch a small fraction of either. A method that was
never ported fails no test. It simply is not there.

So the options split into "does it behave" and "is it all present", and today only the
first is covered.

## A. Behavioral golden suite on both backends (status quo)

`api-compatibility` under `-Dmatlab.api.path`, the same tests against the shipping
toolbox and the staged native one.

- **Pro.** Tests semantics, which is the thing that actually breaks. The backend
  parameterization makes every test a differential test, and the bridge run is the
  control that says a red test is a port bug rather than a bad assertion. Runs in CI on
  imitation devices with no hardware. It is the contract the release notes point at.
- **Con.** Coverage is whatever someone wrote. It cannot detect a missing method, a
  dropped key-value option or a silently narrowed accepted type, and those are exactly
  the failure modes of a hand-staged compat layer. Growing it to surface completeness
  means hand-writing hundreds of tests. Several pins had to be relaxed on 2026-08-05
  because they asserted class names and exception identifiers, which is the sign that
  goldens written against an implementation drift into testing it.

## B. Structural surface dump from the Java annotations, diffed as a reference file

An annotation processor output listing every `@MatlabMethod` with its `@InputParser`
required, optional and parameter names, types and defaults. The old facade and the new
annotated layer each emit one, and the diff is the port's completeness report.

- **Pro.** The annotations are the actual definition of the key-value surface, so this
  measures the real thing rather than a proxy. It is the one option that answers "what
  did the port drop" mechanically, over all 92 methods and 48 parsers at once, instead
  of per test. No MATLAB, no image, no hardware, runs in every `mvn verify` in seconds.
  It is the reference-file pattern both repos already live by, so a deliberate removal
  is an approved diff hunk and an accidental one is a red build. Cheap: one processor
  that writes a sorted text file, and the comparison machinery exists.
- **Con.** `@InputParser` is `RetentionPolicy.SOURCE`
  (`matlab-generator/.../parser/InputParser.java`), so this has to be a processor and
  cannot be a reflective test at runtime. It says nothing about behavior, a method can
  be present and wrong. It covers only the Java-declared surface, so the hand-written
  `.m` layer (`HebiArm`, the blocking convenience loops, the display overrides) is
  invisible to it. It needs a decision on what counts as an intended difference, or the
  diff becomes noise that gets rubber-stamped.

## C. MATLAB-side introspection diff over the two toolboxes

Walk `meta.class` for every class on both paths and compare method names, arities,
property names and visibility.

- **Pro.** Covers the whole `.m` surface including the hand-written parts, which is
  precisely B's blind spot. Catches the boilerplate-shaped regressions: a hidden method
  that should not be, a property that lost its setter, a classdef that is missing.
  Small, one `.m` file plus one golden.
- **Con.** Needs MATLAB and a built image, so it runs where the expensive suite already
  runs and saves nothing. Arity is uninformative once everything is `varargin`, which is
  the whole point of the port, so it degrades to a name list. It reports differences the
  contract ruling already declared irrelevant (class names, the `methods`/`fields`
  shims, the constructor rule that forces `Utils.newGains`), so it needs a maintained
  exception list from day one. Overlaps B on the Java-backed classes.

## D. Differential dual-backend execution

Run each call on both backends in one MATLAB session and compare the results, the way
the `VarArgParameter` differential suite compared against the boxed backend.

- **Pro.** The strongest per-call assertion available, and it is proven in this project
  at the `MatlabParameter` layer, where it caught transpose and permutation bugs a
  golden would have blessed. No expected values to author, the shipping backend is the
  oracle.
- **Con.** Both backends must be loadable in one session, which means a JVM and an image
  side by side, and the two do not share module state, so anything stateful (a group, a
  log, a trajectory handle) has no comparable identity. That leaves pure functions
  (kinematics, trajectory generation, file IO), which is where the golden tests are
  already strongest and cheapest. The handle-heavy surfaces are the risky ones and are
  exactly the ones it cannot reach. High harness cost for a narrow slice.

## E. Examples corpus as executable acceptance

The 174 `.m` files under `hebi-matlab-examples`, run on imitation devices. Already the
stated P3 acceptance.

- **Pro.** Real user code written against the shipping API with no knowledge of the
  port, so it exercises combinations no test author would think to write, which is the
  long tail the surface-breadth risk names. The cost is scheduling rather than design.
  Failures come with a concrete user-visible story.
- **Con.** Not a suite. Most files want hardware, many want a human watching a plot, and
  the pass criterion for most is "did not error", which is weak. Assertion free, so a
  wrong number passes. Needs per-file triage into runnable, adaptable and out of scope
  before it reports anything, and that triage is the real work. Slow, and flaky against
  timing on the ones that do run.

## F. Signature and help surface goldens

Pin `functionSignatures.json` and the generated help text as reference files.

- **Pro.** The generator emits both already, so a golden is nearly free, and it protects
  the tab completion and `help` experience, which is user visible and otherwise
  untested. It is B's data in the form MATLAB consumes, so it catches the same drops on
  the way out.
- **Con.** Documentation coverage is not API coverage, and the 267 HTML help files have
  no replacement decided yet (S5), so pinning now pins something that is about to be
  replaced.

## Where this points

Layer B, A and E. Treat C, D and F as later or narrow.

1. **B first, it is the missing one.** Cheap, needs no MATLAB, and it is the only option
   that turns "is the port complete" from a judgment call into a build result.
   Everything else measures depth on a surface whose width nobody has measured.
2. **A stays the gate,** unchanged. It answers what B cannot, and the both-backends
   parameterization is what makes it trustworthy. Keep relaxing pins that assert
   implementation details rather than contract.
3. **E at P3 acceptance,** with the triage done once and recorded, so the runnable
   subset becomes a standing suite and the rest becomes a documented list of what was
   not checked.
4. **C when the `.m` layer stops moving.** Its exception list would be churn today.
5. **D only if a specific pure-function surface turns out to be under-tested.** The
   cost-to-reach ratio does not justify it as a general harness.
6. **F folded into P5** with the help and packaging decision, not before.
