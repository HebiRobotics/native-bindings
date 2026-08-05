classdef (Sealed) Errors
% Deliberate failures, used to check error propagation.

    methods(Static)

        function requireNonNegative(value)
            % Throws when value is negative, otherwise does nothing.
            %
            %   Inputs:
            %       value
            %
            %   Throws:
            %       Error value is negative
            status_ = bindings_tests_native('bindings_tests_Errors_requireNonNegative', value);
            if status_ ~= 0
                error(['Encountered error in Errors.requireNonNegative' ': ' bindings_tests_native('bindings_tests_getLastError')]);
            end
        end

        function result = divideInt32(a, b)
            % Integer division. Returns 0 through the ABI when it throws.
            %
            %   Inputs:
            %       a
            %       b
            %
            %   Throws:
            %       Error divisor is zero
            result = bindings_tests_native('bindings_tests_Errors_divideInt32', a, b);
        end

        function result = failingFloat64(shouldFail)
            % Returns 0.0 through the ABI when it throws.
            %
            %   Inputs:
            %       shouldFail
            %
            %   Throws:
            %       Error shouldFail is true
            result = bindings_tests_native('bindings_tests_Errors_failingFloat64', shouldFail);
        end

        function result = failingBool(shouldFail)
            % Returns false through the ABI when it throws.
            %
            %   Inputs:
            %       shouldFail
            %
            %   Throws:
            %       Error shouldFail is true
            result = bindings_tests_native('bindings_tests_Errors_failingBool', shouldFail);
        end

        function result = failingString(shouldFail)
            % Returns a null pointer through the ABI when it throws.
            %
            %   Inputs:
            %       shouldFail
            %
            %   Throws:
            %       Error shouldFail is true
            result = bindings_tests_native('bindings_tests_Errors_failingString', shouldFail);
        end

        function result = failWithoutMessage()
            % Throws an exception that carries no message.
            %
            %   Throws:
            %       Error always
            result = bindings_tests_native('bindings_tests_Errors_failWithoutMessage');
        end

        function neverThrows()
            % Marked noexcept, so no status code is generated for it.
            bindings_tests_native('bindings_tests_Errors_neverThrows');
        end

        function clearLastError()
            % Clears the last error for the calling thread.
            bindings_tests_native('bindings_tests_Errors_clearLastError');
        end

        function result = getLastError()
            % Message of the last exception thrown on the calling thread, or an empty
            %   string if there has not been one. Same content as the generated library
            %   level accessor, exported as an ordinary method.
            result = bindings_tests_native('bindings_tests_Errors_getLastError');
        end

    end

end
