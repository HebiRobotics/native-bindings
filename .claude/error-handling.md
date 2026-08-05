# Error handling across the generated bindings

The design round for how a failing call reports itself in C, C++, Python and MATLAB.
Split out of `matlab-migration.md` open questions 2 and 6 because it is a generator
concern that outlives the MATLAB migration: the MATLAB SDK is the consumer that made
it urgent, SaxonC is a second consumer with the same need, and B.20 (hebi.h) will be a
third. The migration tracker keeps the status line and the phase gating, this file
keeps the mechanism.

Nothing here is decided yet. The interim ruling stands (author, 2026-08-05): until this
round concludes, silently swallowed errors on value returns are acceptable, tests and
benchmarks avoid erroneous inputs, and nothing before the round adds per-call error
plumbing.

## What exists today (verified 2026-08-05 against the reference output)

The mechanism has two halves that do not meet.

**The Java half always runs.** Every generated entry point wraps its invocation in a
try/catch that calls `ExceptionHandlers.setLastError`
(`bindings-runtime/.../exceptions/ExceptionHandlers.java:33`), which stores the
`Throwable` in a `ThreadLocal`, the errno convention. A void method additionally
returns `STATUS_CODE_GENERIC_ERROR` (1). There is exactly one status code besides
success, and the source carries a `TODO` asking whether more are wanted. The library
exports `<lib>_getLastError` per isolate, returning `ex.getMessage()` or the empty
string when the slot is empty.

**The wrapper half only listens on void methods.** From
`reference/static/bindings/`, for the same `TestErrors` class:

| Language | Void method | Value-returning method |
|---|---|---|
| C | returns `int` status, caller checks | returns the value, no status anywhere |
| C++ | `throw Exception("Encountered error in Errors::requireNonNegative")` | returns the value |
| Python | `raise RuntimeError(f'... ({getLastError()})')` | returns the value |
| MATLAB | `error(['Encountered error in ...' ': ' getLastError()])` | returns the value |

So `Errors.divideInt32(1, 0)` returns 0, `failingFloat64` returns 0.0, `failingBool`
returns false and `failingString` returns a null pointer, in all four languages, with
no error raised. The test API documents this in its own doc comments ("Returns 0
through the ABI when it throws"), which is honest but is a description of the gap
rather than a design.

Three consequences worth naming before choosing anything:

- **The error is not lost, only unread.** `setLastError` ran. A wrapper that called
  `getLastError` after every value return would see it. That makes a cheap intermediate
  fix look available, and the next point is why it is not.
- **`getLastError` cannot answer "did it fail".** It returns the empty string for "no
  error" and also for an exception whose message is null or empty, and it is never
  cleared except by an explicit `clearLastError`. A non-empty string may be from three
  calls ago. Poll-style checking on a value return therefore needs an occurred flag or
  a sequence number, not just the message accessor.
- **State is per thread, not per object.** Interleaved calls on two objects from one
  thread clobber each other. Interleaved calls from two threads do not. Whether that is
  the right axis is one of the open decisions below.

This is what bit S2c in HEBI-Java: the reflective `StructAccess.getMaxNumModules` walk
silently returned zero modules until `@Reachable` registered it, and it was found only
by calling `Diagnostics.getLastError` by hand, because the value return swallowed the
error.

## What the design has to satisfy

1. **Non-void calls must be checkable.** This is the whole gap.
2. **Interleaving must not clobber.** Two objects, or two calls, in flight in one
   thread must not read each other's errors.
3. **Two tiers.** C gets something it can check without exceptions. C++, Python and
   MATLAB turn the same thing into a thrown exception or an `error()`.
4. **Codes separate from messages.** MATLAB `try`/`catch` users want an identifier, and
   the SaxonC surface wants W3C error codes. Today there is one message string and one
   generic status code.
5. **Message quality survives.** The `@InputParser` error text is part of the MATLAB UX
   and must reach the user, not be replaced by "Encountered error in X".
6. **Allocation policy.** Per the repo's policy, the success path must stay allocation
   free in C, in the Java entry points and in the C++ raw overloads. Failure paths are
   explicitly exempt, so a throw may allocate freely.
7. **Backwards compatibility.** `getLastError` is exported and used. Whatever lands
   should be additive, or the change has to be sequenced deliberately.

## SaxonC as the reference point (researched 2026-08-05, Saxonica 12.x docs and headers)

SaxonC is a live candidate consumer, so its shape is evidence about what a second
serious user needs.

- It pivoted in 12.1 from poll-style accessors to genuinely thrown C++
  `SaxonApiException`s.
- The exception carries `getMessage()`, `getErrorCode()` as a **separate accessor**
  (W3C and Saxon codes like `XPST0017`), `getMessageWithErrorCode()`, and
  `getLineNumber()`/`getSystemId()`.
- Accumulated compile errors are exposed as a count plus one combined string. There is
  no indexed access, which is an accepted simplification and a useful precedent.
- The old poll surface (`exceptionOccurred`, `exceptionClear`, `getErrorMessage`,
  `getErrorCode`) still exists, deprecated. Note that `exceptionOccurred` is exactly
  the occurred-query this repo lacks.
- Error state is per processor **object**, not global and not per thread.
- Python throws `PySaxonApiError` with the code embedded in the text.
- At the GraalVM boundary they catch at the wrapper and rebuild a heap exception object
  owned by the failing instance.

## Candidates

### 1. Status quo, documented

Leave the mechanism, write the gap into the generated doc comments (already done in the
test API) and the release notes.

- **Pro.** Zero work, zero risk, no reference file churn. Honest about what the ABI does.
- **Con.** Fails requirement 1 outright. The S2c bug is the failure mode, and it cost
  real debugging time on a surface whose author knew the mechanism. A user who does not
  will read a zero as data. Not viable as an end state, only as the interim ruling it
  already is.

### 2. Error out-parameter (author candidate, 2026-08-05)

`value = getDouble(&error, x)`, an out-parameter carrying a code and a message, with
`if (error.code != 0) throw ...` at the wrapper. This is the leading candidate, worked
out concretely in its own section below.

- **Pro.** Fits the machinery that already exists. Span, matrix and string-list returns
  are all out-structs filled from per-thread scratch, so this is the fourth member of a
  family rather than a new concept. It is **per call**, so requirement 2 is satisfied
  structurally rather than by convention: two objects cannot clobber each other because
  neither owns the slot. It works for non-void returns, which is exactly the gap.
  Callers who do not care pass NULL, so the C surface stays usable without ceremony. It
  layers cleanly into the two tiers: C checks the struct, C++ and Python wrappers turn
  it into a throw, and the MEX gateway always passes one and raises an identified
  MATLAB error. It removes the status-code return on void, so one generated shape
  covers void, value and handle returns alike.
- **Con.** Every non-noexcept signature in the C header changes, void ones included, so
  this is a one-time ABI break across the whole library rather than an additive change.
  It puts a small cost on the success path, one pointer argument and one branch. It is
  a parameter, so nothing forces a caller to pass it, and the NULL-means-do-not-care
  convenience is also what lets the S2c failure mode recur silently in raw C. Doxygen,
  matlabdoc and `functionSignatures.json` all need the parameter hidden from the
  wrapper-facing signatures, or every language gets an argument its users never pass.

### 3. SaxonC-style per-object error slot

Error state lives on the failing instance, queried with `exceptionOccurred` /
`getErrorMessage` / `getErrorCode` / `exceptionClear`.

- **Pro.** Proven by a shipping product with the same GraalVM boundary. Satisfies
  requirement 2 on the object axis, which is the axis a user reasons about. Additive,
  no existing signature changes, so the reference diff is new methods only. Cheap on
  the success path, nothing is passed or returned. If SaxonC does consume this
  generator, matching its shape is worth something on its own.
- **Con.** Static methods and free functions have no object to hang the slot on, and
  this repo generates plenty of them, so it needs a per-thread or per-library fallback
  and then there are two mechanisms. Checking is opt-in and out of band, so the same
  silent-zero failure mode survives for anyone who does not call `exceptionOccurred`.
  Requires per-instance state in the image, which is a new allocation and lifetime
  concern the handle table does not currently carry. Deprecated by its own author in
  favour of throwing, which is a signal.

### 4. Poll surface completed in place

Keep the current per-thread slot, add `<lib>_errorOccurred` and `<lib>_clearLastError`
plus a `<lib>_getLastErrorCode`, and have the generated wrappers check after every
call.

- **Pro.** The smallest change that closes requirement 1. The slot already exists and
  is already set on every failure, so this is accessors plus a generated check, no ABI
  change to any existing method. Fixes the "empty string is ambiguous" defect directly.
  Message quality survives unchanged, which is requirement 5 for free.
- **Con.** Two native calls per invocation on every wrapper method, and the second one
  is on the success path, which roughly doubles the dispatch cost the P0 spike measured.
  Per thread is the wrong axis for requirement 2: interleaved calls on two objects in
  one thread still clobber. The clear-before-call discipline is easy to get wrong and
  impossible to enforce from the generator's side once a user calls the C ABI directly.

### 5. Status code on every method, value through an out-pointer

Invert the convention: every method returns `int` status, the value moves to a trailing
out-parameter.

- **Pro.** One uniform convention, no exceptions, no per-object state, and it is what a
  C API conventionally looks like. Checking is not optional, the status is the return
  value. Maps to every language cleanly.
- **Con.** It changes every signature in the library, including the ones that work, and
  it makes the C++ and Python wrappers pure translation layers over an unpleasant C
  face. It is the most invasive option by a wide margin, for a benefit option 2 gets
  with an optional argument. Rejected unless something else forces it.

## The out-parameter worked out (author sketch plus review, 2026-08-05)

The shape, in Java:

```java
@CEntryPoint
static double getDouble(IsolateThread thread, Error error, ObjectHandle instance, int param1) {
    try {
        return resolve(instance).get(param1);
    } catch (Throwable t) {
        Errors.fill(error, t);   // sets code, message, id from the per-thread scratch
        return DEFAULT_DOUBLE;
    }
}
```

and in the generated C++ wrapper:

```cpp
double getDouble(int param1) {
  bindings_tests_error error = {};
  double value = bindings_tests_Class_getDouble(&error, handle_, param1);
  if (error.code != 0) {
    throw Exception(error);
  }
  return value;
}
```

### Position: first, ahead of the instance handle

The tracker's earlier wording said *trailing*, matching the span, matrix and
string-list outs. That is wrong here, for a hard reason: `ProcessorValidationTest`
enforces "A VarArgList must be the trailing parameter", and every one of the 92 MATLAB
SDK methods is a varargs method. A trailing error slot collides with all of them.

Leading also beats putting it after the instance handle, because the public C signatures
strip the isolate (`bindings_tests_Counter_getValue(bindings_tests_CounterPtr obj)`) and
have no instance at all for statics. With the error first it is argument zero in every
public C signature, static and instance alike. After the instance it would be argument
zero for statics and argument one for instance methods, and "the error slot is the first
argument" would stop being true.

So the entry point is `(IsolateThread, Error, ObjectHandle, params...)` and the public C
face is `(<lib>_error*, obj, params...)`, which are the same thing with the isolate
stripped by the shim. Noexcept methods get no slot at all, exactly as they get no status
code today.

### The struct

```c
typedef struct {
    const char* message;    // 8, the exception message, NULL on success
    const char* id;         // 8, identifier for catch dispatch, may be NULL
    int32_t     code;       //  4, 0 on success
} bindings_tests_error;      // 24 bytes, 4 bytes of tail padding
```

Named `<lib>_error` after `<lib>_double_span`, `<lib>_double_matrix` and
`<lib>_string_list`, with a generic `Error` in the probe-only `hebi_structs.h`, the same
split those three already use.

**Why not `char**`.** The sketch's "can be changed to a struct later" does not hold.
Widening `char**` to a struct pointer is an ABI break on every method, which is the cost
the whole design exists to pay once. Note that a struct is not freely growable either:
the caller allocates it, so growing past 24 bytes breaks old callers. The 4 bytes of
tail padding are one free `int32` for later, and anything beyond that is a break. Decide
the fields now, while the library is unreleased.

**What `code` is for.** The generator's own taxonomy, which today is
`ExceptionHandlers.STATUS_CODE_SUCCESS` and `STATUS_CODE_GENERIC_ERROR` plus a `TODO`
asking whether more are wanted. It is the cheap check on the success path, and it is
where marshalling-level failures (bad handle, wrong argument count from the gateway)
become distinguishable from "the library threw". C callers switch on it without
`strcmp`.

**What `id` is for.** The user-facing identifier, and it has to be a string because both
consumers that asked for it need strings. MATLAB `error(id, msg)` takes a
`component:mnemonic` identifier, and that is exactly what question 2 asks for and what
forced two `MATLAB:Java:GenericException` pins to be relaxed in the api-compatibility
suite. SaxonC's `getErrorCode()` returns W3C codes like `XPST0017`, also strings, and
Saxonica deliberately keeps it separate from the message. A numeric `code` cannot
express either, because the generator does not know the annotated library's exception
taxonomy.

### Where the id comes from (author sketch plus review, 2026-08-05)

The runtime declares the opt-in, the library's exceptions carry it, and a runtime
utility fills the struct so the generator emits one line per entry point.

**An interface, not a base class.** A `ExceptionWithId` base class would force every
exception that wants an id to extend it, and the exceptions that want one already
extend something: `IllegalArgumentException`, `IllegalStateException`, HEBI's
`MatlabError`, Saxon's own hierarchy. An interface composes with all of them.

```java
public interface HasErrorId {
    String getErrorId();
}
```

A convenience base class implementing it is fine to add for new code, but the interface
is the contract.

**The default cannot be a bare simple name.** MATLAB identifiers are
`component:mnemonic` and need at least one colon. `error('IllegalArgumentException',
msg)` does not raise an identified error, MATLAB decides the first argument is not an
identifier and treats it as a format string with `msg` as its argument, so the user gets
mangled text instead of a catchable id. The default therefore has to be prefixed,
`<lib>:IllegalArgumentException`, and the prefix has to be applied **in Java** so that C,
C++, Python and MATLAB all see the same string. The library name is already available
without a per-call argument through `Configuration`.

The default stays a convenience rather than a contract, if only because
`getSimpleName()` is the empty string for anonymous classes. Obfuscation is not a
concern: the image is built from unobfuscated types, ProGuard runs on the JVM-bridge
release path only.

**The fill utility.** `Exceptions.fillErrorStruct(throwable, error)` keeps the generator
to one line in the catch. It has to be a **separate class**, not a static on the `Error`
`@CStruct` interface, for the reason `Spans` exists: a `@CStruct` interface may only
contain annotated accessors, and a default or static method fails the image build with
"Unexpected method without annotation".

Put both awkward cases inside the utility so the generator never sees them:

- the caller-passed-NULL check, as `error.isNull()`, since word types may not be
  compared to `null`
- the success-path clear, so a caller reusing one `error` across a loop cannot read a
  stale one

That makes the generated catch block a single `Exceptions.fillErrorStruct(t, error)` and
the generated prologue a single `Exceptions.clearErrorStruct(error)`.

**Decided (author, 2026-08-05): the utility goes on `ExceptionHandlers`.** It is already
the routing point every generated entry point calls, and it already carries static
methods. It gains its first dependency on a `@CStruct` type, which is the accepted cost.

**Work it implies.** An `Error` `@CStruct` interface plus an `ErrorMock` for JVM-mode
tests, following `DoubleMatrixMock` and `StringListMock`, and the
`ImageInfo.inImageRuntimeCode()` branch that the string helpers already take, since
`WordFactory` pointers cannot be cast outside the image. That branch is what makes the
path assertable from a JVM test at all.

### What else an error might want to carry, and why none of it is a field

The struct is caller-allocated, so it can grow into its 4 bytes of tail padding by
exactly one `int32` and no further without breaking old callers. That makes "what goes
in" worth settling now. The candidates, from the consumers on the table:

- **Source location** (line, column, system id). SaxonC exposes `getLineNumber()` and
  `getSystemId()`, and it is not XML-specific: HEBI parses HRDF, gains XML, safety
  params and YAML robot configs, and a line number on a malformed file is real value.
- **Stack trace.** Already reachable through `getLastError(true)` and
  `printLastStackTrace`, and gated on `Configuration.isDebugEnabled()` because walking
  the stack is expensive.
- **Cause chain.** Java messages routinely lose the root cause, which is the usual
  reason an error text is useless.
- **Accumulated errors.** SaxonC's count plus one combined string, for compile-style
  surfaces that produce many.
- **Exception class name**, qualified, as a discriminator if C++ ever wants to map onto
  typed exceptions rather than one `Exception`.

**None of these should be a field.** Every one of them is rare, needed only on the
failure path, and already reachable: the `Throwable` is still in the `ThreadLocal` that
`setLastError` filled, so any of them can be a separate accessor
(`<lib>_getErrorStackTrace`, `<lib>_getErrorLine`, and so on) fetched after the failing
call. Adding an accessor is additive and never breaks the ABI, whereas every field is a
one-shot budget against 4 bytes. The allocation policy already exempts failure paths, so
the second call costs nothing that matters.

The one constraint that buys: those accessors read per-thread state, so they are valid
only immediately after the failing call, on the same thread, before any further call.
The generated wrappers fetch on the throw path, which satisfies that by construction.
It has to be written into the C documentation for hand-written callers.

So the struct keeps three fields, and the free `int32` stays free. **Severity** is the
one thing that would genuinely have to be in the struct, because whether a wrapper
throws depends on it, and it needs no field either: negative `code` values can mean
warning, positive mean error, zero means success.

### Details that are easy to get wrong

- **The entry point must write the struct on success too**, clearing it. If it only
  writes on failure, a caller reusing one `error` variable across a loop reads a stale
  error, which is the same ambiguity `getLastError` has today. Three stores on the
  success path.
- **`message` and `id` point into the per-thread scratch**, valid until that thread's
  next call, the same contract as string and span returns. Every wrapper copies out
  immediately. The catch-block code already exists: `CEntryPointGenerator:138` is
  literally `CTypes.toTempCharPointer(ExceptionHandlers.getLastError(false))`, so this
  relocates it into the per-method catch rather than building machinery.
- **The cleanup contract is a generator obligation, not a comment.** "If the error is
  set, no handle was registered and none needs releasing" is free for exceptions out of
  the method body. The one path where it is not is the `Object[]` variant-list return,
  which registers handles into the shared table during the write pass, so anything
  throwing mid-pass has to unregister what it already registered. Pin it with a test.
- **The MEX gateway must copy the message before raising.** `mexErrMsgIdAndTxt` longjmps
  past C++ destructors, which is already why the varargs packing takes owned memory from
  the mx allocator.
- **Hide the parameter from every wrapper-facing signature**, including
  `functionSignatures.json` and the generated matlabdoc. Only the C header shows it.
- **Two fixes come along for free.** The C++ void path today throws
  `Exception("Encountered error in Errors::requireNonNegative")` with no message at all,
  while Python and MATLAB pay a second `getLastError()` call to get one. Under this
  design every language gets the real message in one call, so the error path goes from
  two calls to one and the success path costs one pointer push and one branch.
- **Keep `getLastError`.** It stays as the diagnostic aid with the stack trace, which is
  how the S2c `getMaxNumModules` bug was actually found. It stops being the mechanism.

## Where this points

Options 2 and 3 are the real candidates and they are not exclusive: 2 is a per-call
channel, 3 is a per-object one, and SaxonC ships both a thrown exception and a
deprecated poll surface for the same reason. On the requirements, 2 wins: 3 has no slot
to hang static methods on and this generator emits plenty of them, and 4 costs a second
native call on the success path of every method, which roughly doubles the 1.81 us
dispatch the P0 spike measured. The only thing 3 does better is leave existing
signatures alone, and that is worth little before 1.0.

Decisions this round still has to make:

1. **Confirm the axis is per call.** Everything above assumes it.
2. **Whether NULL stays legal.** It preserves the silent-zero failure mode for raw C
   callers. The generated wrappers always pass a slot, so the exposure is C only, but
   it should be a written decision rather than a side effect.
3. ~~**Where `id` comes from.**~~ Answered above: a `HasErrorId` interface the library's
   exceptions may implement, defaulting to a library-prefixed class simple name, filled
   by `ExceptionHandlers`. What is left is the prefix's exact form.
4. ~~**Whether multi-error accumulation is in scope.**~~ Not a struct field either way,
   see above. If it is wanted it is an accessor over the per-thread state.
5. **Whether a noexcept-style second overload is wanted**, letting a caller take the
   status instead of an exception. Nearly free in C++, where the raw `internal::`
   declarations already exist, and needs a name suffix in C. Not urgent.

## Open items elsewhere that fold into this

- `matlab-migration.md` open questions 2 and 6 resolve here. Question 6's "value returns
  may lose parser error text" was accepted as temporary on 2026-08-04 and the S2b
  finding elevated it from "loses text" to "no error at all".
- `ExceptionHandlers.returnErrorStatusCode` carries the `TODO` about richer status
  codes. That TODO is decision 3.
- The MATLAB cleanup debt entry "value returns lose parser error text" clears when this
  lands.
