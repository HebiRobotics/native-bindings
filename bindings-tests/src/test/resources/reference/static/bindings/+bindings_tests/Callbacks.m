classdef (Sealed) Callbacks
% Synchronous callbacks into caller-provided function pointers.

    methods(Static)

        function result = invokeDoubleCallback(func, a, b)
            result = bindings_tests_native('bindings_tests_Callbacks_invokeDoubleCallback', func, a, b);
        end

        function result = invokeDoubleSpanToScalarCallback(func, a, b)
            result = bindings_tests_native('bindings_tests_Callbacks_invokeDoubleSpanToScalarCallback', func, a, b);
        end

        function result = invokeProgressCallback(func, progress)
            % Invokes the callback once with the given record and returns its result.
            %
            %   Inputs:
            %       func
            %       progress
            result = bindings_tests_native('bindings_tests_Callbacks_invokeProgressCallback', func, progress);
        end

        function varargout = invokeTimes(varargin)
            % Invokes the callback exactly 'times' times with indices 0 to times-1, then
            %   returns. A null function pointer is ignored, and a non-positive count does
            %   nothing.
            %
            %   Not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.
            error('bindings_tests:NotSupported', "'invokeTimes' is not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.");
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
            bindings_tests_native('bindings_tests_Callbacks_invokePlainOnWorkerThread', func, index);
        end

        function notifyPlainOnce(func)
            % Invokes the callback once. Returns after it completes.
            %
            %   Inputs:
            %       func
            bindings_tests_native('bindings_tests_Callbacks_notifyPlainOnce', func);
        end

    end

end
