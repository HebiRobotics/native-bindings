classdef (Sealed) Arrays
% Bulk array operations over caller-owned buffers.

    methods(Static)

        function result = sumFloat64(values, count)
            result = bindings_tests_native('bindings_tests_Arrays_sumFloat64', values, count);
        end

        function result = sumInt32(values, count)
            result = bindings_tests_native('bindings_tests_Arrays_sumInt32', values, count);
        end

        function fillFloat64(target, count, value)
            % Writes value into every element of target. Checks output buffer handling.
            %
            %   Inputs:
            %       target
            %       count
            %       value
            bindings_tests_native('bindings_tests_Arrays_fillFloat64', target, count, value);
        end

        function cumulativeSum(source, target, count)
            % Writes the running total of source into target. Both buffers must hold at
            %   least count elements. Passing the same buffer for both is supported.
            %
            %   Inputs:
            %       source
            %       target
            %       count
            bindings_tests_native('bindings_tests_Arrays_cumulativeSum', source, target, count);
        end

        function reverseFloat64(values, count)
            % Reverses the buffer in place.
            %
            %   Inputs:
            %       values
            %       count
            bindings_tests_native('bindings_tests_Arrays_reverseFloat64', values, count);
        end

        function result = dotProduct(x, y)
            % Dot product of two spans. The lengths ride along in the spans, so a mismatch is checked once here rather than in every language wrapper.
            %
            %   Inputs:
            %       x
            %       y
            %
            %   Throws:
            %       Error mismatched lengths
            result = bindings_tests_native('bindings_tests_Arrays_dotProduct', x, y);
        end

        function result = addOffset(values, offset)
            % Returns a new array with the offset added to every element. Checks a span in and an array out.
            %
            %   Inputs:
            %       values
            %       offset
            result = bindings_tests_native('bindings_tests_Arrays_addOffset', values, offset);
        end

        function result = getSequence(count)
            % Returns 0, 1, .. count-1. A count of zero returns an empty array, and a count
            %   over 128 forces the per-thread scratch buffer to grow, mirroring Strings.repeat.
            %
            %   Inputs:
            %       count
            result = bindings_tests_native('bindings_tests_Arrays_getSequence', count);
        end

    end

end
