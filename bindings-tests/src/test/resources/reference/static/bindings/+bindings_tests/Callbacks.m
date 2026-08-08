classdef (Sealed) Callbacks
% Synchronous callbacks into caller-provided function pointers.

    methods(Static)

        function varargout = invokeTimes(varargin)
            % Invokes the callback exactly 'times' times with indices 0 to times-1, then
            %   returns. A null function pointer is ignored, and a non-positive count does
            %   nothing.
            %
            %   Not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.
            error('bindings_tests:NotSupported', "'invokeTimes' is not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.");
        end

        function varargout = invokeOnce(varargin)
            % Invokes the callback once with the given index. Returns after it completes.
            %
            %   Not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.
            error('bindings_tests:NotSupported', "'invokeOnce' is not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.");
        end

        function varargout = invokeChecked(varargin)
            % Invokes the callback once with the given index, then throws if the index is
            %   negative. The invocation happens before the validation on purpose, so that a
            %   caller can observe both failures in one call.
            %
            %   Not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.
            error('bindings_tests:NotSupported', "'invokeChecked' is not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.");
        end

        function varargout = invokeOnWorkerThread(varargin)
            % Invokes the callback once with the given index on a worker thread, and joins that
            %   thread before returning. The invocation therefore completes within the call but not
            %   on the calling thread, which is the shape a solver has when it fans an objective
            %   out to workers. The callback must be thread safe.
            %
            %   Not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.
            error('bindings_tests:NotSupported', "'invokeOnWorkerThread' is not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.");
        end

        function varargout = notifyOnce(varargin)
            % Invokes the callback once. Returns after it completes.
            %
            %   Not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.
            error('bindings_tests:NotSupported', "'notifyOnce' is not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.");
        end

        function varargout = applyScaled(varargin)
            % Passes the value to the callback and returns what the callback produced. A null
            %   function pointer leaves the value unchanged.
            %
            %   Not supported from MATLAB: its callback returns a value, which the gateway cannot hand back yet.
            error('bindings_tests:NotSupported', "'applyScaled' is not supported from MATLAB: its callback returns a value, which the gateway cannot hand back yet.");
        end

        function varargout = transformOnce(varargin)
            % Invokes the callback once with the two buffers and returns after it completes.
            %   Both buffers stay owned by the caller, and how many elements they hold is
            %   between the caller and the callback, since a raw pointer carries no length.
            %   The input is not verified! A null function pointer leaves output untouched.
            %
            %   Not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.
            error('bindings_tests:NotSupported', "'transformOnce' is not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.");
        end

        function invokePlainTimes(func, times)
            % Invokes the callback exactly 'times' times with indices 0 to times-1, then
            %   returns. A null function pointer is ignored, and a non-positive count does
            %   nothing.
            %
            %   Inputs:
            %       func
            %       times
            validateattributes(func, {'function_handle'}, {'scalar'}, 'Callbacks.invokePlainTimes', 'func');
            bindings_tests_native('bindings_tests_Callbacks_invokePlainTimes', func, times);
        end

        function invokePlainChecked(func, index)
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
            validateattributes(func, {'function_handle'}, {'scalar'}, 'Callbacks.invokePlainChecked', 'func');
            bindings_tests_native('bindings_tests_Callbacks_invokePlainChecked', func, index);
        end

        function invokePlainOnWorkerThread(func, index)
            % Invokes the callback once with the given index on a worker thread, and joins that
            %   thread before returning. The invocation therefore completes within the call but not
            %   on the calling thread, which is the shape a solver has when it fans an objective
            %   out to workers. The callback must be thread safe.
            %
            %   Inputs:
            %       func
            %       index
            validateattributes(func, {'function_handle'}, {'scalar'}, 'Callbacks.invokePlainOnWorkerThread', 'func');
            bindings_tests_native('bindings_tests_Callbacks_invokePlainOnWorkerThread', func, index);
        end

        function notifyPlainOnce(func)
            % Invokes the callback once. Returns after it completes.
            %
            %   Inputs:
            %       func
            validateattributes(func, {'function_handle'}, {'scalar'}, 'Callbacks.notifyPlainOnce', 'func');
            bindings_tests_native('bindings_tests_Callbacks_notifyPlainOnce', func);
        end

    end

end
