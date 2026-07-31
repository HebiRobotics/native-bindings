classdef (Sealed) Overloads
% Default arguments and language specific convenience overloads.

    methods(Static)

        function result = scale(value, factor)
            % Both parameters have defaults, so wrappers may call this with no arguments.
            %
            %   Inputs:
            %       value
            %       factor
            if nargin < 1
                value = 1;
            end
            if nargin < 2
                factor = 10;
            end
            result = bindings_tests_native('bindings_tests_Overloads_scale', value, factor);
        end

        function result = greet(name, greeting)
            % A trailing default only, which is the common case.
            %
            %   Inputs:
            %       name
            %       greeting
            if nargin < 2
                greeting = 'Hello';
            end
            result = bindings_tests_native('bindings_tests_Overloads_greet', name, greeting);
        end

        function result = total(values, factor)
            % Takes a pre-summed total. Each language gets a wrapper that sums a collection for you.
            %
            %   Inputs:
            %       sum
            %       factor
            if nargin < 2, factor = 1; end
            result = bindings_tests_native('bindings_tests_Overloads_total', sum(values), factor);
        end

        function result = gain(invert, scale)
            % A boolean default and a double default in scientific notation. Each wrapper has
            %   to render both in its own syntax, which is where they tend to go wrong.
            %
            %   Inputs:
            %       invert
            %       scale
            if nargin < 1
                invert = false;
            end
            if nargin < 2
                scale = 1e-3;
            end
            result = bindings_tests_native('bindings_tests_Overloads_gain', invert, scale);
        end

        function result = echoIndex(index)
            % Returns the index unchanged. MATLAB converts to zero based indexing on the way
            %   in and back to one based on the way out, so it sees the value it passed in.
            %   Python clamps negatives instead, which matches C for every valid index.
            %
            %   Inputs:
            %       index
            index = index - 1;
            result = bindings_tests_native('bindings_tests_Overloads_echoIndex', index);
            result = result + 1;
        end

        function result = answer(multiplier)
            % The C++ wrapper body is hand written and never calls into the library, so
            %   this is the one method where the C++ result may legitimately differ from
            %   the others. Kept deliberately obvious.
            %
            %   Inputs:
            %       multiplier
            result = bindings_tests_native('bindings_tests_Overloads_answer', multiplier);
        end

        function result = renamed()
            % Declared as originalName in Java but exported as renamed.
            result = bindings_tests_native('bindings_tests_Overloads_renamed');
        end

    end

end
