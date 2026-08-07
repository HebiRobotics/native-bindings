classdef (Sealed) Callbacks
% Synchronous callbacks into caller-provided function pointers.

    methods(Static)

        function invokeTimes(func, times)
            % Invokes the callback exactly 'times' times with indices 0 to times-1, then
            %   returns. A null function pointer is ignored, and a non-positive count does
            %   nothing.
            %
            %   Inputs:
            %       func
            %       times
            validateattributes(func, {'function_handle'}, {'scalar'}, 'Callbacks.invokeTimes', 'func');
            bindings_tests_native('bindings_tests_Callbacks_invokeTimes', func, times);
        end

        function invokeOnce(func, index)
            % Invokes the callback once with the given index. Returns after it completes.
            %
            %   Inputs:
            %       func
            %       index
            validateattributes(func, {'function_handle'}, {'scalar'}, 'Callbacks.invokeOnce', 'func');
            bindings_tests_native('bindings_tests_Callbacks_invokeOnce', func, index);
        end

    end

end
