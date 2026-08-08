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

        function invokeChecked(func, index)
            % Invokes the callback once with the given index, then throws if the index is
            %   negative. The invocation happens before the validation on purpose, so that a
            %   caller can observe both failures in one call.
            %
            %   Inputs:
            %       func
            %       index
            %
            %   Throws:
            %       Error index is negative
            validateattributes(func, {'function_handle'}, {'scalar'}, 'Callbacks.invokeChecked', 'func');
            bindings_tests_native('bindings_tests_Callbacks_invokeChecked', func, index);
        end

        function invokeOnWorkerThread(func, index)
            % Invokes the callback once with the given index on a worker thread, and joins that
            %   thread before returning. The invocation therefore completes within the call but not
            %   on the calling thread, which is the shape a solver has when it fans an objective
            %   out to workers. The callback must be thread safe.
            %
            %   Inputs:
            %       func
            %       index
            validateattributes(func, {'function_handle'}, {'scalar'}, 'Callbacks.invokeOnWorkerThread', 'func');
            bindings_tests_native('bindings_tests_Callbacks_invokeOnWorkerThread', func, index);
        end

        function varargout = notifyOnce(varargin)
            % Invokes the callback once. Returns after it completes.
            %
            %   Not supported from MATLAB: its callback may fire after the call returns.
            error('bindings_tests:NotSupported', "'notifyOnce' is not supported from MATLAB: its callback may fire after the call returns.");
        end

        function varargout = applyScaled(varargin)
            % Passes the value to the callback and returns what the callback produced. A null
            %   function pointer leaves the value unchanged.
            %
            %   Not supported from MATLAB: its callback returns a value, which the gateway cannot hand back yet.
            error('bindings_tests:NotSupported', "'applyScaled' is not supported from MATLAB: its callback returns a value, which the gateway cannot hand back yet.");
        end

    end

end
