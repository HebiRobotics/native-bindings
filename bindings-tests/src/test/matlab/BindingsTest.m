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
%   Callbacks are deliberately not covered: MATLAB cannot pass a C function
%   pointer, so the typedef degrades to void* and NativeCallbacks is not
%   callable from here. That feature area is covered by C, C++ and Python.
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
            testCase.verifyEqual(bindings_tests.Math.addInt64(8589934592, 1), 8589934593);

            testCase.verifyEqual(bindings_tests.Math.addFloat32(0.5, 0.25), 0.75);
            testCase.verifyEqual(bindings_tests.Math.addFloat64(0.5, 0.25), 0.75);

            testCase.verifyEqual(bindings_tests.Math.addSize(4, 5), 9);
            testCase.verifyEqual(bindings_tests.Math.sizeToInt32(7), 7);

            testCase.verifyEqual(bindings_tests.Math.maxInt32(), 2147483647);
            % Both sides round to the same double, so this compares the rounded
            % value rather than the exact 63 bit cap.
            testCase.verifyEqual(bindings_tests.Math.maxSize(), 9223372036854775807);
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

        function testStrings(testCase)
            % String marshalling in both directions, plus char** arrays
            testCase.verifyEqual(bindings_tests.Strings.echo('hello'), 'hello');

            % This file is UTF-8. The literal must survive the round trip unchanged.
            nonAscii = 'naïve café';
            testCase.verifyEqual(bindings_tests.Strings.echo(nonAscii), nonAscii);

            testCase.verifyEqual(bindings_tests.Strings.concat('ab', 'cd'), 'abcd');

            % 6 UTF-8 bytes but only 5 UTF-16 code units
            testCase.verifyEqual(bindings_tests.Strings.utf8Length('naïve'), 6);
            testCase.verifyEqual(bindings_tests.Strings.utf16Length('naïve'), 5);

            testCase.verifyEqual(bindings_tests.Strings.empty(), '');

            % The MEX layer turns a null char* into an empty array, so null and
            % '' are indistinguishable here. The C suite checks the pointer.
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

            % The length check lives once in the Java implementation. A mismatch
            % returns the type default and sets the last error.
            bindings_tests.Errors.clearLastError();
            testCase.verifyEqual(bindings_tests.Arrays.dotProduct([1 2 3], [4 5]), 0);
            testCase.verifyTrue(contains(bindings_tests.Errors.getLastError(), 'elements'));
            bindings_tests.Errors.clearLastError();

            % Span in, row vector out. The MEX gateway copies out of the
            % library-owned buffer before returning.
            testCase.verifyEqual(bindings_tests.Arrays.addOffset([1 2 3], 10), [11 12 13]);

            testCase.verifyEqual(bindings_tests.Arrays.getSequence(5), [0 1 2 3 4]);

            % A count of zero returns an empty array
            testCase.verifyTrue(isempty(bindings_tests.Arrays.getSequence(0)));

            % 200 elements, which forces the 128 element scratch buffer to grow
            long = bindings_tests.Arrays.getSequence(200);
            testCase.verifyEqual(numel(long), 200);
            testCase.verifyEqual(long(200), 199);
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

            testCase.verifyEqual(bindings_tests.Palette.fromOrdinal(1), green);
            testCase.verifyEqual(bindings_tests.Palette.fromOrdinal(99), red);
        end

        function testErrors(testCase)
            % Error propagation across the ABI
            bindings_tests.Errors.clearLastError();

            bindings_tests.Errors.requireNonNegative(5);

            % A throwing void method is promoted to an int status code, and the
            % wrapper turns a non-zero status into a MATLAB error whose message
            % already carries the last error text.
            message = testCase.captureError(@() bindings_tests.Errors.requireNonNegative(-1));
            testCase.verifyTrue(contains(message, 'must not be negative'));
            testCase.verifyTrue(contains(bindings_tests.Errors.getLastError(), 'must not be negative'));

            % Methods with a real return type get a type specific default on
            % failure. The generated wrapper does not check those, so nothing is
            % raised here and the MATLAB result matches the raw C ABI.
            testCase.verifyEqual(bindings_tests.Errors.divideInt32(10, 2), 5);
            testCase.verifyEqual(bindings_tests.Errors.divideInt32(1, 0), 0);

            testCase.verifyEqual(bindings_tests.Errors.failingFloat64(false), 1.5);
            testCase.verifyEqual(bindings_tests.Errors.failingFloat64(true), 0.0);

            testCase.verifyEqual(bindings_tests.Errors.failingBool(false), 1);
            testCase.verifyEqual(bindings_tests.Errors.failingBool(true), 0);

            testCase.verifyEqual(bindings_tests.Errors.failingString(false), 'ok');
            testCase.verifyTrue(isempty(bindings_tests.Errors.failingString(true)));

            % Marked Noexcept, so there is no status code to check
            bindings_tests.Errors.neverThrows();

            bindings_tests.Errors.clearLastError();
            testCase.verifyTrue(isempty(bindings_tests.Errors.getLastError()));
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

            % 42 * multiplier everywhere except C++, where the hand-written
            % cppBody returns 21 * multiplier
            testCase.verifyEqual(bindings_tests.Overloads.answer(2), 84);
        end

        function testIsolated(testCase)
            % State confined to a second GraalVM isolate. It does not share
            % static fields, the handle registry or the last error slot with the
            % default isolate, which is why the class has its own last error
            % provider.
            bindings_tests.Isolated.reset();
            bindings_tests.Isolated.increment();
            bindings_tests.Isolated.increment();
            bindings_tests.Isolated.increment();
            testCase.verifyEqual(bindings_tests.Isolated.getCounter(), 3);

            message = testCase.captureError(@() bindings_tests.Isolated.alwaysFails());
            testCase.verifyTrue(contains(message, 'secondary isolate'));
            testCase.verifyTrue(contains(bindings_tests.Isolated.getLastError(), 'secondary isolate'));
        end

    end

    methods (Access = private)

        function message = captureError(testCase, fn)
            % Runs fn and returns the error message. The generated wrappers call
            % error() with a plain message and no identifier, so verifyError
            % cannot be used here.
            message = '';
            try
                fn();
                testCase.verifyFail('Expected the call to raise an error');
            catch err
                message = err.message;
            end
        end

    end

end
