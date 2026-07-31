classdef (Sealed) Callbacks
% Synchronous callbacks into caller-provided function pointers.

    methods(Static)

        function invokeTimes(func, userData, times)
            % Invokes the callback exactly 'times' times with indices 0 to times-1, then
            %   returns. A null function pointer is ignored, and a non-positive count does
            %   nothing.
            %
            %   Inputs:
            %       func
            %       userData
            %       times
            bindings_tests_native('bindings_tests_Callbacks_invokeTimes', func, userData, times);
        end

        function invokeOnce(func, userData, index)
            % Invokes the callback once with the given index. Returns after it completes.
            %
            %   Inputs:
            %       func
            %       userData
            %       index
            bindings_tests_native('bindings_tests_Callbacks_invokeOnce', func, userData, index);
        end

    end

end
