# Coding style guide

For future Claude instances and other tools generating code in this repo. The code
was extracted from a codebase written and re-read by one senior author since 2015,
and this repo is headed for open source, so the bar is higher, not lower. Generated
code must be indistinguishable in care from the hand-written source: a reviewer
should not be able to tell it apart. When in doubt, read three neighboring files and
imitate them, that always wins over this document.

## The core principle

Code is written to be re-read a decade later by the same person, who will have
forgotten everything except how to read good code. Self-documenting code first,
comments only where the code cannot speak: constraints, incidents, units, and
rationale. Documentation lives where the reader is, not in external wikis.

## Naming carries the physics

The parent codebase is robotics software and the habit applies here to every ABI
boundary. Names and signatures carry units, layouts, and conventions, because a
wrong frame, unit, or memory ordering is the classic silent failure:

- Parameters spell out semantics when ambiguous: `setScale(double
  scaleUnitsToMillimeters)` rather than `setScale(double s)` plus a comment.
- Conventions are cited by name, not paraphrased: "follows the ROS/REP-103
  convention where the scalar component 'w' is last", row-major vs column-major
  stated at every matrix crossing.
- API names are chosen for discoverability, including autocomplete sort order and
  what a user would type first. Backwards compatibility of public names is a feature.

Never emit a bare `double x` where the unit or frame is not obvious from context.

## Comments record why and history, never what

The codebase reads like a lab notebook:

- Workarounds name their symptom and their source: a JDK or GraalVM bug ID, a
  StackOverflow link, or a version bound like "fixed in jdk8_u202+, can probably
  get deleted". A workaround without provenance is a style violation.
- Decisions carry rationale in place, especially version pins in poms. A pinned
  version without a reason comment is a style violation.
- TODOs are specific and actionable: "TODO: remove when fixed upstream". A disabled
  test carries a TODO naming the exact cause. Open design questions may live as
  comments instead of becoming speculative code.
- Never write narration ("// increment the counter"), and never write comments
  addressed to a reviewer about the change itself.
- Keep it short. One or two sentences carry a constraint or an incident, and the
  supporting argument belongs in `.claude/plans.md` rather than in a javadoc block.
  A comment that reads like a commit message has outgrown its place.

## Class anatomy

- Class javadoc is one to three sentences on purpose, sometimes with an incident
  note, followed by trailing `@author` and `@since 10 Aug 2021` style tags (day
  month year).
- Some files keep fields grouped at the bottom of the class, after the methods.
  Match whatever the file you are editing does.
- Not every method gets javadoc. A method whose signature explains itself stays
  bare. Javadoc appears where behavior is subtle or the method is a public surface.

## Generated output is a first-class API surface

The emitters produce C headers, C++ wrappers, Python packages, and MATLAB classdefs
that end users read, step through, and learn from. Emitted code follows the idiom of
its target language, carries the `@CLibComment` documentation with units and
warnings ("The input is not verified!"), and contains nothing a hand-author would be
embarrassed by: no dead branches, no placeholder names, no formatting noise. When
changing an emitter, read the diff of the generated output as carefully as the
emitter itself.

Effort follows the number of future readers, and users outnumber maintainers. The
`@CLibComment` blocks and the MATLAB help text get documented exhaustively, with
units, layouts, and explicit warnings. Internal generator classes get a fraction of
that.

## Modern Java, bounded by where the code ends up

`bindings-generator` runs at build time on a known JDK and uses `var`, switch arrows,
and text blocks freely. `bindings-runtime` is the artifact every annotated library
depends on and is written conservatively, down to spelling out type arguments
(`new ThreadLocal<Throwable>()`). Emitted Java is compiled by the consuming project
under its own release level, so it stays plainer still. Match the module you are in
rather than the newest thing the compiler accepts.

## Concurrency is written defensively and explains itself

The per-thread scratch buffers and the isolate lifecycle are the concurrency surface,
and both have a contract stated in prose next to the code: who owns the memory, how
long it stays valid, and what invalidates it. Locks take timeouts with a comment on
why. New concurrency should look this paranoid, and anything reachable from a
generated entry point states its thread affinity.

## Small classes, explicit mechanics

- Prefer a small class with one job over a configurable framework.
- Prefer builders, method references, and code generation over reflection when both
  work. If native-image cannot see it at build time, it is the wrong mechanism.
  This whole repo exists because annotations processed at compile time beat runtime
  magic under closed-world compilation.
- Explicit registration over classpath scanning, always.

## Allocation discipline where it matters, pragmatism where it does not

The runtime and the generated call paths are hot: no allocation per call, reuse
buffers, and treat the allocation policy in CLAUDE.md as binding. Generator-side
code runs once at build time and may use streams, `Optional`, and readable
conveniences freely. Know which side of that line you are on before you either
optimize or allocate.

## Errors carry context, leniency is opt-in

Failures tell the user what and where, with the offending name or value in the
message. Lenient behavior is an explicit flag with debug logging behind it, never a
silent default. Logging is terse and measured, debug output gated by a check.

## No speculative generality

Solve the problem in front of you and leave a comment where the extension would go.
A nine-file change to make one string configurable was rejected, correctly. Small,
reviewable changes, one issue at a time, and no refactoring while fixing.

If the surrounding code looks odd, assume an incident you have not read about yet.
Check the "things that have already bitten" list in CLAUDE.md before improving it.

## Formatting

Four-space indent, K&R braces, blank lines to group logical stanzas. Do not reformat
lines you were not asked to touch, and never let a formatter churn a file as a side
effect. The same holds for emitted output in every target language.

## Prose rules for docs, comments, and commit messages

No em dashes. No semicolons as sentence connectors. No emoji. Mermaid diagrams are
fine in markdown docs. Write plainly and specifically.

## The open source bar

This repo will be published. The author's polished extractions (MFL, QuickBuffers,
reachability-annotations) share a deliberate polish layer beyond the base style, and
new public-facing work here should match it:

- Evidence replaces assertion. Design decisions get standalone "why" documents with
  primary sources (QuickBuffers ships an ACM citation and a JOL memory dump).
  Performance claims carry hardware, library versions, and raw output, pasted where
  the claim is made.
- Anti-overclaiming is a feature. Limitations appear by the third paragraph of the
  README, and benchmarks state their own bias ("an unfair comparison", "deliberately
  setup to favor FlatBuffers"). Protect this trait, it is why maintainers trust the
  work.
- Attribution is source code. Upstream `@author` tags stay on adapted code, external
  links pin to commit SHAs so they cannot rot, and machine-generated code is labeled
  as such until verified.
- The public surface is curated hard. Implementation classes package-private, one
  static entry point, zero-dependency runtime artifacts, `Automatic-Module-Name`,
  deprecation with delegation instead of removal.
- Documentation is progressive disclosure. The one-liner first, escalation after,
  every snippet compilable, and a sample module that doubles as executable docs.
- The back-of-house must match the front. CI mirrors the compatibility claim exactly,
  required jobs never use `continue-on-error`, a changelog exists from the first tag,
  license headers name the right module, and README links get checked. These are the
  gaps the survey found even in the best repos, and they are agent work.
