classdef (Sealed) Strings
% String conversion, including non-ASCII round trips.

    methods(Static)

        function result = echo(value)
            % Returns the input unchanged. Round trips UTF-8 through Java's UTF-16.
            %
            %   Inputs:
            %       value
            result = bindings_tests_native('bindings_tests_Strings_echo', value);
        end

        function result = concat(a, b)
            result = bindings_tests_native('bindings_tests_Strings_concat', a, b);
        end

        function result = utf8Length(value)
            % Length in UTF-8 bytes, which differs from the Java String length for non-ASCII input.
            %
            %   Inputs:
            %       value
            result = bindings_tests_native('bindings_tests_Strings_utf8Length', value);
        end

        function result = utf16Length(value)
            % Number of UTF-16 code units, i.e. the Java String length.
            %
            %   Inputs:
            %       value
            result = bindings_tests_native('bindings_tests_Strings_utf16Length', value);
        end

        function result = empty()
            % Returns an empty string, which must not be confused with a null pointer.
            result = bindings_tests_native('bindings_tests_Strings_empty');
        end

        function result = nullString()
            % Returns a null pointer rather than an empty string.
            result = bindings_tests_native('bindings_tests_Strings_nullString');
        end

        function result = join(values, count, separator)
            % Joins a char* array with a separator. Checks char** parameter handling.
            %
            %   Inputs:
            %       values
            %       count - number of entries in values
            %       separator
            result = bindings_tests_native('bindings_tests_Strings_join', values, count, separator);
        end

        function result = repeat(value, times)
            % Returns a string longer than the initial scratch buffer, forcing it to grow.
            %
            %   Inputs:
            %       value
            %       times
            result = bindings_tests_native('bindings_tests_Strings_repeat', value, times);
        end

    end

end
