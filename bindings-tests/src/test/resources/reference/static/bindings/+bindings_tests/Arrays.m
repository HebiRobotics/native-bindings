classdef (Sealed) Arrays
% Bulk array operations over caller-owned buffers.

    methods(Static)

        function [result, values] = sumFloat64(values, count)
            [result, values] = bindings_tests_native('bindings_tests_Arrays_sumFloat64', values, count);
        end

        function [result, values] = sumInt32(values, count)
            [result, values] = bindings_tests_native('bindings_tests_Arrays_sumInt32', values, count);
        end

        function target = fillFloat64(target, count, value)
            % Writes value into every element of target. Checks output buffer handling.
            %
            %   Inputs:
            %       target
            %       count
            %       value
            target = bindings_tests_native('bindings_tests_Arrays_fillFloat64', target, count, value);
        end

        function [source, target] = cumulativeSum(source, target, count)
            % Writes the running total of source into target. Both buffers must hold at
            %   least count elements. Passing the same buffer for both is supported.
            %
            %   Inputs:
            %       source
            %       target
            %       count
            [source, target] = bindings_tests_native('bindings_tests_Arrays_cumulativeSum', source, target, count);
        end

        function values = reverseFloat64(values, count)
            % Reverses the buffer in place.
            %
            %   Inputs:
            %       values
            %       count
            values = bindings_tests_native('bindings_tests_Arrays_reverseFloat64', values, count);
        end

    end

end
