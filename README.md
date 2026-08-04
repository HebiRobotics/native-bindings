# GraalVM Native Bindings

An annotation processor that turns annotated Java classes into a C ABI for GraalVM
native-image, plus idiomatic wrappers for C, C++, Python and MATLAB.

You write plain Java and add a few annotations. The processor generates the
`@CEntryPoint` methods, the handle management, the exception plumbing, and a wrapper
library for each target language. Callers never see isolates or handles.

```java
@CLibClass("Counter")
public class TestCounter implements AutoCloseable {

    @CLibMethod(constructor = true)
    public static TestCounter create(@CDefault("0") int start) {
        return new TestCounter(start);
    }

    @CLibMethod(flags = Noexcept)
    public void increment() {
        value++;
    }

    @CLibMethod(flags = {Noexcept, Const}, property = "value")
    public int getValue() {
        return value;
    }

    // ...
}
```

becomes, in each language:

```cpp
bindings::tests::Counter counter(10);
counter.increment();
int value = counter.getValue();
```

```python
counter = bindings_tests.Counter(10)
counter.increment()
value = counter.value
```

```matlab
counter = bindings_tests.Counter(10);
counter.increment();
value = counter.value;
```

## Modules

| Module | What it is | Who depends on it |
|---|---|---|
| `bindings-runtime` | The annotations and the runtime support classes (handles, last-error storage, string and size_t marshalling) | Your annotated library, at compile time and at run time |
| `bindings-generator` | The annotation processor and the four language generators | Your build, as an `annotationProcessorPath` only |
| `bindings-tests` | A small annotated API that exercises every feature, the reference file comparison, and the per-language test suites | Nothing. It is the test bed |

`bindings-runtime` has no dependencies beyond the GraalVM native-image API, which is
`provided`. Nothing from the generator ends up on your runtime classpath.

## Using it

Add the runtime to your dependencies and the generator to your processor path:

```xml
<dependency>
    <groupId>us.hebi.graalvm</groupId>
    <artifactId>bindings-runtime</artifactId>
    <version>0.1.0</version>
</dependency>
```

```xml
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>us.hebi.graalvm</groupId>
                <artifactId>bindings-generator</artifactId>
                <version>0.1.0</version>
            </path>
            <!-- Required. The processor path is an isolated classloader and does not
                 inherit provided-scope dependencies from the generator. -->
            <path>
                <groupId>org.graalvm.nativeimage</groupId>
                <artifactId>native-image-base</artifactId>
                <version>24.0.2</version>
            </path>
        </annotationProcessorPaths>
        <compilerArgs>
            <arg>-Abindings.outputDir=${project.build.directory}/image/static</arg>
            <arg>-Abindings.libName=mylib</arg>
            <arg>-Abindings.namespace=com.example</arg>
            <arg>-Abindings.libVersion=1.0.0</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

### Options

| Option | Default | Meaning |
|---|---|---|
| `bindings.outputDir` | none, required | Where to write the generated bindings. Also readable as a system property |
| `bindings.libName` | `lib` | Library name, used for file names and symbol prefixes |
| `bindings.namespace` | `lib` | Dotted namespace, becomes the C++ namespace and the MATLAB package |
| `bindings.cPrefix` | derived from namespace | Prefix for every exported C symbol |
| `bindings.libVersion` | `0.0.0` | Semantic version reported by the generated version accessors |
| `bindings.libBuild` | `0` | Build number reported alongside the version |
| `bindings.downloadUrl` | none | Base url of the directory holding the release archives. Empty means the generated loaders never download |
| `bindings.enableGraphics` | `false` | Emits the macOS main thread handover needed by UI libraries |

### Finding the binary at runtime

The Python loader, the C++ `<libName>.cmake` and the MATLAB `locate_binary.m` search the
same two places in the same order: `lib/<platform>/` next to the generated files, and then
a shared per-user cache in `<os cache dir>/<libName>/<version>-build<libBuild>/`.

With `bindings.downloadUrl` set they also fetch
`<downloadUrl>/<libName>-<libVersion>-<platform>.zip` into that cache and extract it. The
option is the directory holding the archives rather than a pattern, so anything that varies
per release belongs in the url the build assembles:

```xml
<arg>-Abindings.downloadUrl=https://example.com/download/${lib.name}/snapshot/${lib.build}</arg>
```

Without it no download code is generated at all, and a missing binary is an error naming
the path that was searched. That is the default because a library that does not publish
release archives should not ship a downloader aimed at somebody else's server.

### Linking the static package

The static package ships one merged archive per platform next to a `find_package` config
that carries the whole link contract, including the system libraries listed in
`lib/<platform>/syslibs.txt`:

```cmake
find_package(<libName> REQUIRED PATHS path/to/package/cmake)
target_link_libraries(app PRIVATE <libName>::static)
```

The package ships no sources. The C shim that creates the isolate and attaches the
calling thread is already compiled into the archive, and its symbols are hidden there,
so an application links the documented API and nothing else. Isolate lifetime is not
part of the contract.

ELF only allows position independent objects inside a shared library, so everything in
the archive is built that way and linking it into your own `.so` works out of the box.

- **A static zlib has to be position independent too.** Debian and Ubuntu ship an
  x86_64 `libz.a` that is not, which is why the build falls back to listing `z` in
  `syslibs.txt` rather than merging it, and why consumers on minimal images then need
  the system zlib installed. Building zlib with `CFLAGS=-fPIC` and pointing
  `BINDINGS_ZLIB_STATIC` at the result makes the archive self-contained instead.
  Exporting it is not enough: native-image runs the compiler with an environment of
  its own, so the variable also has to be named in the build arguments as
  `-EBINDINGS_ZLIB_STATIC`. Without that the wrapper reads it as unset and falls back
  to the system archive, and the only sign is the `syslibs.txt` line it leaves behind.

## Annotations

| Annotation | On | What it does |
|---|---|---|
| `@CLibClass` | class | Marks a class for export and names it in the generated API |
| `@CLibMethod` | method | Marks a method for export. Controls naming, constructors, properties, flags, and per-language overloads |
| `@CLibEnum` | enum | Exports an enum as a 4 byte C int keyed on ordinal |
| `@CLibTypeDef` | interface | Declares a C type alias, used for function pointer callbacks |
| `@CLibComment` | type, method, parameter | Documentation, propagated to doxygen, Python docstrings, and MATLAB help |
| `@CDefault` | parameter | Default value, surfaced by the C++, Python and MATLAB wrappers |
| `@CSizeType` | int or long type use | Maps to `size_t` in C while staying an int or long in Java |
| `@CLibIndex` | integral type use | Marks an index, so wrappers for one-based languages convert it. Java and C stay zero based |

`@CLibMethod(flags = Noexcept)` means the method cannot throw. Without it, a method is
assumed to throw: void methods are promoted to an int status code, and every other
return type gets a type specific default on failure (`false`, `0`, or a null pointer).
`flags = Const` marks an instance method that does not mutate state and becomes a
`const` member function in C++.

## Building and testing

The build has two tiers.

```bash
mvn verify
```

Compiles everything, runs the processor over the test API, and compares every generated
file against the copies checked into `bindings-tests/src/test/resources/reference/`. No
GraalVM, no native toolchain, about a minute. This is the gate that runs on every push.

When a change to the generator is meant to change its output:

```bash
mvn verify -Dbindings.updateReference=true
git diff
```

The diff is the review. That is the point of the reference files: a refactor that is
supposed to be behaviour preserving should produce an empty diff, and one that is not
shows you exactly what moved.

### Running the language suites

The second tier builds the test API into a shared library with GraalVM and runs the C,
C++, Python and MATLAB suites against it. There is a script per platform so you do not
have to remember the staging step.

Windows:

```
scripts\build-native.bat
scripts\run-tests.bat
```

Linux and macOS:

```bash
./scripts/build-native.sh
./scripts/run-tests.sh
```

Pass suite names to run a subset, for example `scripts\run-tests.bat c cpp` or
`./scripts/run-tests.sh python`. Valid names are `c`, `cpp`, `python` and `matlab`.
A suite whose tool is not installed is skipped rather than failed, so MATLAB being
absent does not break the run.

You will need:

| Tool | Notes |
|---|---|
| GraalVM JDK 21 | On `JAVA_HOME`. `mvn -Pnative` calls `native-image` from it |
| A C toolchain | MSVC on Windows, gcc or clang elsewhere. Also used by the C and C++ suites |
| CMake 3.17+ | Links the release library and builds the C and C++ suites through CTest |
| Python 3.8+ | `numpy` is only needed for the array bindings |
| MATLAB R2018b+ | Optional. Set `MATLABROOT`, or put `matlab` on `PATH` for the scripts to derive it |

On Windows, `native-image` and CMake both need `cl.exe` on `PATH`. Run the scripts from
a shell that already has the MSVC environment loaded, such as the `x64 Native Tools
Command Prompt for VS`. The scripts check for it and stop early with a clear message
rather than failing halfway through the native build.

### What the scripts do

`build-native.bat` / `build-native.sh` run the native build in two stages.

Stage one is `mvn clean verify -Pnative`. It points native-image at
`compiler-<os>`, a wrapper around the platform C compiler that builds the usual shared
library and additionally writes a static library plus `libs.cmake` and `opts.cmake` into
`bindings-tests/target/image/static/`. The processor generates that wrapper into the same
directory during the compile phase, so there is nothing to vendor or keep in sync. Extra
arguments are passed through to Maven, so `./scripts/build-native.sh -o` builds offline.

Stage two is CMake over that directory, using the `CMakeLists.txt` the processor
generated next to the static library:

```bash
cmake -S bindings-tests/target/image/static -B bindings-tests/target/native-build
cmake --build bindings-tests/target/native-build --config Release
```

This links the static library into the final shared library and, when `MATLABROOT` points
at a MATLAB installation or a MEX SDK, also builds the MATLAB MEX gateway. The scripts use
`MATLABROOT` if it is already set and otherwise derive it from `matlab` on `PATH`. If
MATLAB cannot be found they say so and carry on without the gateway, because only the
MATLAB suite needs it.

The scripts then copy the built library into
`bindings-tests/target/image/static/bindings/lib/<platform>/` and, if it was built, the
MEX gateway into `bindings-tests/target/image/static/bindings/+bindings_tests/private/`.
Those copies matter: the generated Python and MATLAB loaders look there first, and without
them they fall back to the shared cache and, if `bindings.downloadUrl` is set, to
downloading a release binary.

`run-tests.bat` / `run-tests.sh` configure and run each suite in place and report which
ones failed. The equivalent manual commands, from the `bindings-tests` module root:

```bash
cmake -S src/test/c -B target/ctest-c && cmake --build target/ctest-c --config Release
ctest --test-dir target/ctest-c --build-config Release --output-on-failure

python -m unittest discover -s src/test/python -v

matlab -batch "results = runtests('src/test/matlab'); assertSuccess(results);"
```

Every suite asserts the same value table, so a failure in one language and not the
others points at that language's generator rather than at the API.

### What the test API covers

`bindings-tests/src/main/java/.../api/` holds thirteen small classes, each aimed at one
part of the feature matrix. Everything is deterministic and synchronous: no UI, no
threads, no sleeps. Every language suite asserts the same value table, so a divergence
between languages is immediately visible.

| Class | Covers |
|---|---|
| `TestMath` | Every primitive width, `size_t`, and the 31 and 63 bit caps |
| `TestCounter` | Constructor, instance methods, properties, and the generated destructor |
| `TestStrings` | String marshalling both ways, `const char**` arrays, UTF-8 versus UTF-16 lengths |
| `TestArrays` | The (pointer, length) pattern for input and output buffers, spans, and `@CConst` |
| `TestShape`, `TestCircle`, `TestSquare` | Inheritance, a shared handle registry, and the generated upcast |
| `TestPalette` | Enum round trips |
| `TestErrors` | Status codes, per-type error defaults, and the last error provider |
| `TestOverloads` | Default arguments, per-language overloads, `postBody`, `postCall`, renaming, and `ignore` |
| `TestIsolated` | A second isolate with its own state and its own error provider |
| `TestCallbacks` | Synchronous function pointer callbacks |
| `TestIndices` | `@CLibIndex` conversion, including defaults that shift into the target language |
| `TestIgnored` | A class level `ignore`, which must produce no output at all |

`ProcessorValidationTest` covers the other half: the cases that are rejected at compile
time and therefore cannot live in a source tree that has to compile.

## Known gaps

These are pre-existing behaviours carried over from the original implementation. They
are documented rather than fixed so that the extraction stayed behaviour preserving.

- **A class may declare at most one constructor.** The MATLAB generator asserts this,
  because it cannot overload on the first argument of a classdef constructor. Use
  `@CDefault` for the no-argument case.
- **`cppOverloads` ignores `postBody`.** The C++ generator never emits that field, so
  anything placed there is silently dropped. Put the whole prologue in `body`. Python
  emits `postBody`, and MATLAB emits both `postBody` and `postCall`.
- **Python static overloads need an explicit `cls`.** Static methods are emitted as
  `@classmethod` but the overload parameter list is copied verbatim, so the leading
  `cls` has to be written into the annotation.
- **MATLAB echoes non-const pointer parameters as extra outputs**, while the MEX
  dispatch only ever fills the first one.
- **MATLAB returns every number as a double**, so an `int64` or `size_t` return above
  2^53 loses precision at the call site with no warning. Object handles are unaffected,
  they cross as `uint64`.
- **Structs are not supported.** There is no by-value struct mapping, and `@CExternalEnums`
  is declared but never read by the processor.
- **Only leaf classes may declare constructors**, and an abstract parent shares its
  handle registry with the whole hierarchy.

## License

TODO
