classdef BindingsTest < matlab.unittest.TestCase
% Runtime test for the generated MATLAB wrapper of the bindings_tests library.
%
%   Asserts the same value table as the C, C++ and Python suites, so a
%   divergence between languages shows up as a failure here.
%
%   Run after `mvn verify -Pnative`, from the bindings-tests module root:
%
%       runtests('src/test/matlab')
%
%   The generated wrapper locates the shared library under
%   <bindings>/lib/<platform>/, so the built library has to be copied there
%   first (see .github/workflows/native.yml).
%
%   Note on types: the MEX dispatch returns every scalar as a MATLAB double,
%   including booleans and enums, so the expected values below are plain
%   numbers rather than logicals or enum instances.

    methods (TestClassSetup)

        function addBindingsToPath(testCase)
            thisDir = fileparts(mfilename('fullpath'));
            bindingsDir = fullfile(thisDir, '..', '..', '..', 'target', 'image', 'static', 'bindings');
            testCase.assertTrue(isfolder(bindingsDir), ...
                sprintf('Generated bindings not found in %s. Run `mvn verify -Pnative` first.', bindingsDir));
            testCase.applyFixture(matlab.unittest.fixtures.PathFixture(bindingsDir));
        end

    end

    methods (Test)

        function testMath(testCase)
            % Every primitive width that can cross the ABI, plus size_t
            testCase.verifyEqual(bindings_tests.Math.negate(true), 0);
            testCase.verifyEqual(bindings_tests.Math.negate(false), 1);

            % A Java char crosses as a uint16 code unit, so compare numerically
            testCase.verifyEqual(double(bindings_tests.Math.nextChar(double('A'))), double('B'));

            testCase.verifyEqual(bindings_tests.Math.addInt8(100, 20), 120);
            testCase.verifyEqual(bindings_tests.Math.addInt16(30000, 700), 30700);
            testCase.verifyEqual(bindings_tests.Math.addInt32(2, 3), 5);

            % 2^33 + 1. Catches platforms that truncate a Java long to 32 bit.
            % Still a double, the value is well below the 2^53 mantissa limit.
            testCase.verifyEqual(bindings_tests.Math.addInt64(8589934592, 1), 8589934593);

            testCase.verifyEqual(bindings_tests.Math.addFloat32(0.5, 0.25), 0.75);
            testCase.verifyEqual(bindings_tests.Math.addFloat64(0.5, 0.25), 0.75);

            testCase.verifyEqual(bindings_tests.Math.addSize(4, 5), 9);
            testCase.verifyEqual(bindings_tests.Math.sizeToInt32(7), 7);

            testCase.verifyEqual(bindings_tests.Math.maxInt32(), 2147483647);
            % Past the mantissa, so this one comes back as uint64 and compares the
            % exact 63 bit cap. The literal cannot be written directly, MATLAB parses
            % it as a double and rounds it up to 2^63 before the conversion sees it.
            testCase.verifyEqual(bindings_tests.Math.maxSize(), uint64(intmax('int64')));

            % A wrong type on a scalar parameter raises in the MEX gateway
            % instead of producing garbage through mxGetScalar. Unlike the
            % wrapper errors, this one carries an identifier.
            testCase.verifyError(@() bindings_tests.Math.negate(struct('a', 1)), 'bindings_tests:TypeError');
            testCase.verifyError(@() bindings_tests.Math.negate([]), 'bindings_tests:TypeError');
            testCase.verifyError(@() bindings_tests.Math.negate([1 2]), 'bindings_tests:TypeError');
        end

        function testCounter(testCase)
            % Object handle lifecycle: constructor, methods, properties, destructor
            bindings_tests.Counter.resetClosedCount();
            testCase.verifyEqual(bindings_tests.Counter.getClosedCount(), 0);

            counter = bindings_tests.Counter(10);
            counter.increment();
            counter.increment();
            testCase.verifyEqual(counter.value, 12);

            counter.add(5);
            testCase.verifyEqual(counter.value, 17);

            counter.value = 3;
            testCase.verifyEqual(counter.value, 3);

            testCase.verifyEqual(counter.isClosed(), 0);

            % The single constructor defaults 'start' to 0
            defaultCounter = bindings_tests.Counter();
            testCase.verifyEqual(defaultCounter.value, 0);

            % delete runs the generated destructor, which calls close() in Java
            delete(counter);
            delete(defaultCounter);
            testCase.verifyEqual(bindings_tests.Counter.getClosedCount(), 2);
        end

        function testNaming(testCase)
            % cPrefix and useCamelCase rename C symbols only. The classdef is
            % generated from the library name and the Java method names either way,
            % so it looks like every other class while dispatching on
            % custom_prefix_Naming_*. The C suite is where the symbols are asserted.
            naming = bindings_tests.Naming(7);
            testCase.verifyEqual(naming.getStartValue(), 7);

            defaultNaming = bindings_tests.Naming();
            testCase.verifyEqual(defaultNaming.getStartValue(), 0);

            testCase.verifyEqual(bindings_tests.Naming.addTwoNumbers(2, 3), 5);

            delete(naming);
            delete(defaultNaming);
        end

        function testStrings(testCase)
            % String marshalling in both directions, plus char** arrays
            testCase.verifyEqual(bindings_tests.Strings.echo('hello'), 'hello');

            % Built from code points rather than from a literal in this file. MATLAB
            % only reads source as UTF-8 from R2020a, and before that it decodes this
            % file in the platform character set, which would change what the byte
            % length assertions below are even asking about.
            naive = char([110 97 239 118 101]);              % naïve
            nonAscii = [naive ' ' char([99 97 102 233])];    % naïve café
            testCase.verifyEqual(bindings_tests.Strings.echo(nonAscii), nonAscii);

            % A code point above the BMP crosses as a surrogate pair, so the round trip
            % also has to survive being two MATLAB chars for one character.
            emoji = char([55357 56842]);                     % U+1F60A
            testCase.verifyEqual(bindings_tests.Strings.echo(emoji), emoji);

            testCase.verifyEqual(bindings_tests.Strings.concat('ab', 'cd'), 'abcd');

            % 6 UTF-8 bytes but only 5 UTF-16 code units
            testCase.verifyEqual(bindings_tests.Strings.utf8Length(naive), 6);
            testCase.verifyEqual(bindings_tests.Strings.utf16Length(naive), 5);

            % Only emptiness is part of the contract, not the shape or the class of the
            % empty result. A null char* also comes back as an empty array, so null and
            % '' are indistinguishable here. The C suite checks the pointer.
            testCase.verifyTrue(isempty(bindings_tests.Strings.empty()));
            testCase.verifyTrue(isempty(bindings_tests.Strings.nullString()));

            testCase.verifyEqual(bindings_tests.Strings.join({'a', 'b', 'c'}, 3, '-'), 'a-b-c');

            % 200 characters, which forces the 128 byte scratch buffer to grow
            testCase.verifyEqual(numel(bindings_tests.Strings.repeat('0123456789', 20)), 200);
        end

        function testArrays(testCase)
            % The (pointer, length) parameter pattern for input and output buffers.
            %
            % Pointer parameters are caller-owned buffers that the native side
            % writes in place, exactly as in C, so the wrapper returns only the C
            % return value. An output buffer therefore has to be a named double
            % variable: a temporary would be written and then discarded, and a
            % class other than double is converted to a copy by the MEX layer.
            sum1 = bindings_tests.Arrays.sumFloat64([1.0 2.0 3.5], 3);
            testCase.verifyEqual(sum1, 6.5);

            sum2 = bindings_tests.Arrays.sumInt32([1 2 3], 3);
            testCase.verifyEqual(sum2, 6);

            filled = zeros(1, 4);
            bindings_tests.Arrays.fillFloat64(filled, 4, 2.5);
            testCase.verifyEqual(filled, [2.5 2.5 2.5 2.5]);

            target = zeros(1, 4);
            bindings_tests.Arrays.cumulativeSum([1 2 3 4], target, 4);
            testCase.verifyEqual(target, [1 3 6 10]);

            reversed = [1 2 3 4];
            bindings_tests.Arrays.reverseFloat64(reversed, 4);
            testCase.verifyEqual(reversed, [4 3 2 1]);
        end

        function testSpans(testCase)
            % Pointer and length cross as one by-value struct, so span methods
            % take plain arrays without a count argument.
            testCase.verifyEqual(bindings_tests.Arrays.dotProduct([1 2 3], [4 5 6]), 32);

            % The length check lives once in the Java implementation, and the
            % gateway raises with its message rather than returning a silent zero.
            message = testCase.captureError(@() bindings_tests.Arrays.dotProduct([1 2 3], [4 5]));
            testCase.verifyTrue(contains(message, 'elements'));

            % Span in, row vector out. The MEX gateway copies out of the
            % library-owned buffer before returning.
            testCase.verifyEqual(bindings_tests.Arrays.addOffset([1 2 3], 10), [11 12 13]);

            % Typed spans repeat the same shape per element type, coming back
            % as the matching MATLAB class
            testCase.verifyEqual(bindings_tests.Arrays.addOffsetInt32([1 2 3], 10), int32([11 12 13]));
            testCase.verifyEqual(bindings_tests.Arrays.addOffsetFloat32([1.5 2.5 3.5], 10), single([11.5 12.5 13.5]));

            % Raw uint8 buffer with an explicit count, the shape an image row
            % uses. The sum is unsigned, 250 counts as 250 and not -6.
            testCase.verifyEqual(bindings_tests.Arrays.sumUint8(uint8([250 10 5]), 3), 265);

            % Byte spans carry binary data, so an embedded zero must survive
            % the roundtrip. 246 + 10 wraps to 0 in uint8.
            testCase.verifyEqual(bindings_tests.Arrays.addOffsetUint8([1 246 3], 10), uint8([11 0 13]));

            testCase.verifyEqual(bindings_tests.Arrays.getSequence(5), [0 1 2 3 4]);

            % A count of zero returns an empty array
            testCase.verifyTrue(isempty(bindings_tests.Arrays.getSequence(0)));

            % 200 elements, which forces the 128 element scratch buffer to grow
            long = bindings_tests.Arrays.getSequence(200);
            testCase.verifyEqual(numel(long), 200);
            testCase.verifyEqual(long(200), 199);
        end

        function testStructs(testCase)
            % A user defined struct crosses by value. The gateway reads the fields
            % by name, so a MATLAB struct only has to carry the right names, and
            % both a logical and an int32 convert through the same scalar path.
            s = struct('voltage', 48.0, 'state', int32(2), 'enabled', true);
            testCase.verifyEqual(bindings_tests.Structs.totalPower(s, 2.5), 120);
            testCase.verifyEqual(bindings_tests.Structs.checkedVoltage(s), 48);

            % State 1 is not the running state, so the power is zero while the
            % voltage still reads.
            idle = struct('voltage', 48.0, 'state', int32(1), 'enabled', true);
            testCase.verifyEqual(bindings_tests.Structs.totalPower(idle, 2.5), 0);
            testCase.verifyEqual(bindings_tests.Structs.checkedVoltage(idle), 48);

            disabled = struct('voltage', 48.0, 'state', int32(2), 'enabled', false);
            testCase.verifyEqual(bindings_tests.Structs.totalPower(disabled, 2.5), 0);
            [message, identifier] = testCase.captureError(@() bindings_tests.Structs.checkedVoltage(disabled));
            testCase.verifyTrue(contains(message, 'disabled'));
            testCase.verifyEqual(identifier, 'bindings_tests:IllegalArgumentException');

            % Anything that is not a struct is rejected in the gateway rather than
            % read as garbage.
            testCase.verifyError(@() bindings_tests.Structs.checkedVoltage(48.0), 'bindings_tests:TypeError');
            testCase.verifyError(@() bindings_tests.Structs.checkedVoltage(rmfield(s, 'enabled')), ...
                'bindings_tests:InvalidInput');

            % The remaining field types. The pointer fields carry no lifetime story
            % here, so only the code unit comes back.
            ft = bindings_tests.Structs.fillFieldTypes(65, int16([1 2]), int64([3 4]), []);
            testCase.verifyEqual(ft.unit, 65);

            % getStatus/getCheckedStatus present the same struct the other way round, as
            % a 1x1 struct return keyed by index rather than an input the caller builds.
            got = bindings_tests.Structs.getStatus(0);
            testCase.verifyTrue(isstruct(got));
            testCase.verifyEqual(fieldnames(got), {'voltage'; 'state'; 'enabled'});
            testCase.verifyEqual(got.voltage, 48.0);
            testCase.verifyEqual(got.state, 2); % scalars come back as double, like every other return
            testCase.verifyTrue(islogical(got.enabled));
            testCase.verifyTrue(got.enabled);

            got1 = bindings_tests.Structs.getStatus(1);
            testCase.verifyEqual(got1.voltage, 12.5);
            testCase.verifyEqual(got1.state, 1);
            testCase.verifyTrue(islogical(got1.enabled));
            testCase.verifyFalse(got1.enabled);

            % The returned struct round-trips back in as an input, the same shape totalPower
            % and checkedVoltage already accept.
            testCase.verifyEqual(bindings_tests.Structs.totalPower(got, 2.5), 120);
            testCase.verifyEqual(bindings_tests.Structs.checkedVoltage(got), 48);

            checked = bindings_tests.Structs.getCheckedStatus(0);
            testCase.verifyEqual(checked.voltage, 48.0);

            % A negative index throws, same identifier style as the other struct errors.
            [message, identifier] = testCase.captureError(@() bindings_tests.Structs.getCheckedStatus(-1));
            testCase.verifyTrue(contains(message, 'negative'));
            testCase.verifyEqual(identifier, 'bindings_tests:IllegalArgumentException');
        end

        function testShapes(testCase)
            % Inheritance. MATLAB models the hierarchy with real classdef
            % inheritance, and the generated cast function runs when the
            % subclass hands its pointer to the parent constructor.
            circle = bindings_tests.Circle(2.0);
            testCase.verifyEqual(circle.area(), pi * 4.0, 'AbsTol', 1e-9);
            testCase.verifyEqual(circle.sideCount(), 0);
            testCase.verifyEqual(circle.name, 'circle');

            square = bindings_tests.Square(3.0);
            testCase.verifyEqual(square.area(), 9.0);
            testCase.verifyEqual(square.sideCount(), 4);
            testCase.verifyEqual(square.name, 'square');

            % Both are usable as the base type. MATLAB has no way to invoke a
            % superclass method from outside a class method, so the explicit
            % upcast that C, C++ and Python perform is checked here as an isa
            % check plus the inherited 'name' property, which is declared on
            % Shape and therefore already goes through the Shape pointer.
            testCase.verifyTrue(isa(circle, 'bindings_tests.Shape'));
            testCase.verifyTrue(isa(square, 'bindings_tests.Shape'));

            delete(circle);
            delete(square);
        end

        function testPalette(testCase)
            % Enums. The MEX dispatch returns the ordinal as a double rather
            % than as an instance of the generated enum class.
            red = double(bindings_tests.Color.Red);
            green = double(bindings_tests.Color.Green);
            blue = double(bindings_tests.Color.Blue);

            testCase.verifyEqual(bindings_tests.Palette.next(bindings_tests.Color.Red), green);
            testCase.verifyEqual(bindings_tests.Palette.next(bindings_tests.Color.Green), blue);
            testCase.verifyEqual(bindings_tests.Palette.next(bindings_tests.Color.Blue), red);

            testCase.verifyEqual(bindings_tests.Palette.nameOf(bindings_tests.Color.Green), 'Green');
            testCase.verifyEqual(bindings_tests.Palette.ordinalOf(bindings_tests.Color.Blue), 2);

            % Two enums declared in the same class, used in the same signature.
            testCase.verifyEqual(bindings_tests.Palette.describe( ...
                bindings_tests.Color.Red, bindings_tests.Marker.Cross), 'Red Cross');

            testCase.verifyEqual(bindings_tests.Palette.fromOrdinal(1), green);
            testCase.verifyEqual(bindings_tests.Palette.fromOrdinal(99), red);
        end

        function testErrors(testCase)
            % Error propagation across the ABI
            bindings_tests.Errors.requireNonNegative(5);

            % A throwing void method is promoted to an int status code, and the
            % wrapper turns a non-zero status into a MATLAB error carrying the
            % message and id from the error struct.
            [message, identifier] = testCase.captureError(@() bindings_tests.Errors.requireNonNegative(-1));
            testCase.verifyTrue(contains(message, 'must not be negative'));
            testCase.verifyEqual(identifier, 'bindings_tests:IllegalArgumentException');

            % Methods with a real return type report through the same error
            % struct, so a failure raises here too and the id reaches MATLAB as
            % the error identifier that try/catch matches on.
            testCase.verifyEqual(bindings_tests.Errors.divideInt32(10, 2), 5);
            [message, identifier] = testCase.captureError(@() bindings_tests.Errors.divideInt32(1, 0));
            testCase.verifyTrue(contains(message, 'division by zero'));
            testCase.verifyEqual(identifier, 'bindings_tests:ArithmeticException');

            testCase.verifyEqual(bindings_tests.Errors.failingFloat64(false), 1.5);
            testCase.captureError(@() bindings_tests.Errors.failingFloat64(true));

            testCase.verifyEqual(bindings_tests.Errors.failingBool(false), 1);
            testCase.captureError(@() bindings_tests.Errors.failingBool(true));

            testCase.verifyEqual(bindings_tests.Errors.failingString(false), 'ok');
            testCase.captureError(@() bindings_tests.Errors.failingString(true));

            % An exception without a message falls back to a placeholder
            [message, identifier] = testCase.captureError(@() bindings_tests.Errors.failWithoutMessage());
            testCase.verifyTrue(contains(message, 'unknown error'));
            testCase.verifyEqual(identifier, 'bindings_tests:IllegalStateException');

            % Marked Noexcept, so there is no status code to check
            bindings_tests.Errors.neverThrows();

            % A successful call after a failure must not report the earlier one
            testCase.verifyEqual(bindings_tests.Errors.divideInt32(9, 3), 3);
        end

        function testOverloads(testCase)
            % Default arguments and the MATLAB specific convenience overload
            testCase.verifyEqual(bindings_tests.Overloads.scale(3, 4), 12);
            testCase.verifyEqual(bindings_tests.Overloads.scale(), 10);
            testCase.verifyEqual(bindings_tests.Overloads.scale(3), 30);

            testCase.verifyEqual(bindings_tests.Overloads.greet('Bob'), 'Hello, Bob');
            testCase.verifyEqual(bindings_tests.Overloads.greet('Bob', 'Hi'), 'Hi, Bob');

            testCase.verifyEqual(bindings_tests.Overloads.renamed(), 7);

            % Declared as originalName in Java but exported as renamed, and the
            % ignored hidden() must not appear anywhere.
            names = methods('bindings_tests.Overloads');
            testCase.verifyFalse(ismember('originalName', names));
            testCase.verifyFalse(ismember('hidden', names));

            % The MATLAB overload replaces the signature entirely, so there is a
            % single form that sums whatever it is given. C takes (sum, factor).
            testCase.verifyEqual(bindings_tests.Overloads.total([1 2 3], 2), 12);
            testCase.verifyEqual(bindings_tests.Overloads.total(6, 2), 12);

            % Defaults are emitted as nargin guards, including the boolean
            % and the scientific notation double.
            testCase.verifyEqual(bindings_tests.Overloads.gain(), 1e-3);
            testCase.verifyEqual(bindings_tests.Overloads.gain(true), -1e-3);
            testCase.verifyEqual(bindings_tests.Overloads.gain(false, 2.0), 2.0);

            % The matlabOverload subtracts one in the postBody and adds it back
            % in the postCall, so MATLAB sees its own one based indexing while
            % the C ABI stays zero based.
            testCase.verifyEqual(bindings_tests.Overloads.echoIndex(3), 3);

            % 42 * multiplier everywhere except C++, where the hand-written
            % cppBody returns 21 * multiplier
            testCase.verifyEqual(bindings_tests.Overloads.answer(2), 84);
        end

        function testIndices(testCase)
            % MATLAB is the only language where @CLibIndex converts. Every value
            % here is one based, and the C suite asserts the zero based twin.
            testCase.verifyEqual(bindings_tests.Indices.echo(4), 4);

            % Index 4 (one based) is index 3 in C, so it counts up to 4.
            testCase.verifyEqual(bindings_tests.Indices.countUpTo(4), 4);

            % The last index of a 5 element array is 5 when counting from one.
            testCase.verifyEqual(bindings_tests.Indices.lastIndexOf(5), 5);

            % Row 2, column 3 one based is row 1, column 2 zero based.
            testCase.verifyEqual(bindings_tests.Indices.offsetFrom(2, 3), 12);

            % The default is shifted into MATLAB's indexing, so omitting an
            % argument and passing the first index are the same thing.
            testCase.verifyEqual(bindings_tests.Indices.offsetFrom(), 0);
            testCase.verifyEqual(bindings_tests.Indices.offsetFrom(1, 1), 0);

            % A non-zero default has to survive the shift. Java defaults to row 5,
            % which is row 6 counting from one.
            testCase.verifyEqual(bindings_tests.Indices.fromRow(), 5);
            testCase.verifyEqual(bindings_tests.Indices.fromRow(6), 5);

            testCase.verifyEqual(bindings_tests.Indices.echoSize(4), 4);
        end

        function testContextCarryingCallbacksAreNotSupported(testCase)
            % Nothing may cross the ABI as context, because the gateway would have to hand
            % the void* to the function handle as an mxArray and an address is not one. A
            % method whose callback carries one therefore gets a stub, and the raw pointer
            % shape below is rejected for its own reason on top of that.
            noop = @(varargin) [];

            testCase.verifyError(@() bindings_tests.Callbacks.invokeTimes(noop, 4), 'bindings_tests:NotSupported');
            testCase.verifyError(@() bindings_tests.Callbacks.transformOnce(noop), 'bindings_tests:NotSupported');
        end

        function testValueReturningCallback(testCase)
            % The trampoline asks feval for one output and hands it back as the C return
            % value, so a binary operator round-trips through the library.
            testCase.verifyEqual(bindings_tests.Callbacks.invokeDoubleCallback(@(a, b) a + b, 1.5, 2.25), 3.75);
            testCase.verifyEqual(bindings_tests.Callbacks.invokeDoubleCallback(@max, 1.5, 2.25), 2.25);

            % A result that is not a numeric scalar is trapped like a failing feval and
            % raised once the library has returned, rather than from inside its frames.
            testCase.verifyError(@() bindings_tests.Callbacks.invokeDoubleCallback(@(a, b) [a b], 1, 2), ...
                'bindings_tests:TypeError');
            testCase.verifyError(@() bindings_tests.Callbacks.invokeDoubleCallback(@(a, b) [], 1, 2), ...
                'bindings_tests:TypeError');
            testCase.verifyError(@() bindings_tests.Callbacks.invokeDoubleCallback(@fail, 1, 2), ...
                'bindings_tests:CallbackFailed');

            function result = fail(~, ~)
                result = 0; %#ok<NASGU>
                error('bindings_tests:CallbackFailed', 'raised from the MATLAB callback');
            end
        end

        function testSpanArgumentCallback(testCase)
            % A span argument carries its length, so the trampoline can size the mxArray and
            % the handle sees a numeric row vector rather than an address.
            testCase.verifyEqual(bindings_tests.Callbacks.invokeDoubleSpanToScalarCallback( ...
                @(a, b) sum(a) + sum(b), [1 2 3], [4 5]), 15);
            testCase.verifyEqual(bindings_tests.Callbacks.invokeDoubleSpanToScalarCallback( ...
                @(a, b) numel(a) * 10 + numel(b), [1 2 3], [4 5]), 32);
        end

        function testPlainCallbacks(testCase)
            % The supported shape: no void* anywhere, so the thunk the library was handed is
            % the whole channel and its identity is what names the record.
            collected = [];
            outer = [];
            inner = [];

            bindings_tests.Callbacks.invokePlainTimes(@collect, 4);
            testCase.verifyEqual(collected, [0 1 2 3]);

            % A non-positive count never reaches the callback
            collected = [];
            bindings_tests.Callbacks.invokePlainTimes(@collect, 0);
            testCase.verifyTrue(isempty(collected));

            % The null function pointer the other suites pass is not representable here,
            % the wrapper only accepts a function handle.
            testCase.verifyError(@() bindings_tests.Callbacks.invokePlainTimes([], 1), ...
                'MATLAB:Callbacks:invokePlainTimes:invalidType');
            testCase.verifyError(@() bindings_tests.Callbacks.invokePlainTimes(42, 1), ...
                'MATLAB:Callbacks:invokePlainTimes:invalidType');

            % The callback calls back into a method that takes a callback, which is what the
            % per-slot thunks exist for: each registration gets an id of its own and therefore
            % a distinct function pointer. Nesting level n collects 0..n-1.
            bindings_tests.Callbacks.invokePlainTimes(@nest, 3);
            testCase.verifyEqual(outer, [0 1 2]);
            testCase.verifyEqual(inner, [0 0 1 0 1 2]);

            % An error raised inside the callback is trapped by the thunk and rethrown at the
            % call site once the library call has returned.
            testCase.verifyError(@() bindings_tests.Callbacks.invokePlainTimes(@fail, 1), ...
                'bindings_tests:CallbackFailed');

            % The registry recovers from that error, so the next call still works
            collected = [];
            bindings_tests.Callbacks.invokePlainTimes(@collect, 2);
            testCase.verifyEqual(collected, [0 1]);

            function collect(index)
                collected(end + 1) = index;
            end

            function nest(index)
                outer(end + 1) = index;
                bindings_tests.Callbacks.invokePlainTimes(@collectInner, index + 1);
            end

            function collectInner(index)
                inner(end + 1) = index;
            end

            function fail(~)
                error('bindings_tests:CallbackFailed', 'raised from the MATLAB callback');
            end
        end

        function testPlainCallbackErrorOrdering(testCase)
            % A throwing method invokes the callback before it validates, so the call below
            % records the index and then raises the library error.
            collected = [];
            bindings_tests.Callbacks.invokePlainChecked(@collect, 3);
            testCase.verifyEqual(collected, 3);

            collected = [];
            testCase.verifyError(@() bindings_tests.Callbacks.invokePlainChecked(@collect, -1), ...
                'bindings_tests:IllegalArgumentException');
            testCase.verifyEqual(collected, -1);

            % Both fail in the same call. The callback error is the root cause of the library
            % one, so the gateway has to raise it first.
            testCase.verifyError(@() bindings_tests.Callbacks.invokePlainChecked(@fail, -1), ...
                'bindings_tests:CallbackFailed');

            function collect(index)
                collected(end + 1) = index;
            end

            function fail(~)
                error('bindings_tests:CallbackFailed', 'raised from the MATLAB callback');
            end
        end

        function testPlainCallbackOnWorkerThread(testCase)
            % Only the MATLAB thread may enter the Matrix API, so a thunk that finds itself
            % anywhere else skips the invocation and the call reports the count.
            collected = [];

            [message, identifier] = testCase.captureError( ...
                @() bindings_tests.Callbacks.invokePlainOnWorkerThread(@collect, 5));
            testCase.verifyEqual(identifier, 'bindings_tests:NotSupported');
            testCase.verifyTrue(contains(message, '1 callback invocation'));
            testCase.verifyTrue(isempty(collected));

            % The registry recovers from that raise, so the next call still works
            bindings_tests.Callbacks.invokePlainTimes(@collect, 2);
            testCase.verifyEqual(collected, [0 1]);

            function collect(index)
                collected(end + 1) = index;
            end
        end

        function testArgumentlessPlainCallback(testCase)
            % Nothing crosses the ABI at all, so the function handle takes no arguments and
            % feval gets the handle alone. This is the pair the old rule rejected outright.
            called = false;
            bindings_tests.Callbacks.notifyPlainOnce(@() note());
            testCase.verifyTrue(called);

            function note()
                called = true;
            end
        end

        function testCallbackRegistryExhaustion(testCase)
            % One slot per callback of a type live at once, out of a fixed table. A callback
            % is live for exactly the call that passed it, so the cap is nesting depth: the
            % seventeenth call to hold one has nowhere to go. The slot is acquired before the
            % library is called, so the raise leaves nothing behind.
            depth = 0;
            testCase.verifyError(@() nest(0), 'bindings_tests:TooManyCallbacks');
            testCase.verifyEqual(depth, 17);

            % Every slot was released on the way out, so the next call works again
            bindings_tests.Callbacks.invokePlainTimes(@(index) [], 1);

            function nest(~)
                depth = depth + 1;
                bindings_tests.Callbacks.invokePlainTimes(@nest, 1);
            end
        end

        function testIsolated(testCase)
            % State confined to a second GraalVM isolate. It does not share
            % static fields or the handle registry with the default isolate.
            bindings_tests.Isolated.reset();
            bindings_tests.Isolated.increment();
            bindings_tests.Isolated.increment();
            bindings_tests.Isolated.increment();
            testCase.verifyEqual(bindings_tests.Isolated.getCounter(), 3);

            % The error struct is caller-owned, so the secondary isolate reports the same way
            [message, identifier] = testCase.captureError(@() bindings_tests.Isolated.alwaysFails());
            testCase.verifyTrue(contains(message, 'secondary isolate'));
            testCase.verifyEqual(identifier, 'bindings_tests:IllegalStateException');
        end

    end

    methods (Access = private)

        function [message, identifier] = captureError(testCase, fn)
            % Runs fn and returns the error message and identifier. Some wrappers
            % still call error() with a plain message and no identifier, so
            % verifyError cannot be used here.
            message = '';
            identifier = '';
            try
                fn();
                testCase.verifyFail('Expected the call to raise an error');
            catch err
                message = err.message;
                identifier = err.identifier;
            end
        end

    end

end
