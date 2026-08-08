classdef (Sealed) Counted < handle & matlab.mixin.SetGet
% A handle whose construction invokes a callback once per index.

    methods(Static)

        function result = getClosedCount()
            % Total number of handles of this type that have been released so far in this
            %   isolate. Increments exactly once per released handle.
            result = bindings_tests_native('bindings_tests_Counted_getClosedCount');
        end

    end

    methods(Access = public)
        function this = Counted(varargin)
            % Creates the handle, invoking the callback with indices 0 to times-1 before returning it.
            %
            %   Not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.
            error('bindings_tests:NotSupported', "'Counted' is not supported from MATLAB: its callback takes an argument ('userData') that the gateway cannot convert to an mxArray.");
        end
    end
    properties (SetAccess = private, GetAccess = private, Hidden = true)
        ptr = [];
    end

    methods(Access = public, Hidden = true)

        function ptr = c_ptr(this)
            ptr = this.ptr;
        end

        function delete(this) % destructor
             if ~isempty(this.ptr)
                bindings_tests_native('bindings_tests_Counted_release', this.ptr);
                this.ptr = [];
            end
        end

        % Inherited handle methods that should be omitted from auto - complete
        function varargout = addlistener(varargin)
            varargout{:} = addlistener@handle(varargin{:});
        end
        function varargout = eq(varargin)
            varargout{:} = eq@handle(varargin{:});
        end
        function varargout = findobj(varargin)
            varargout{:} = findobj@handle(varargin{:});
        end
        function varargout = findprop(varargin)
            varargout{:} = findprop@handle(varargin{:});
        end
        function varargout = ge(varargin)
            varargout{:} = ge@handle(varargin{:});
        end
        function varargout = gt(varargin)
            varargout{:} = gt@handle(varargin{:});
        end
        function varargout = le(varargin)
            varargout{:} = le@handle(varargin{:});
        end
        function varargout = listener(varargin)
            varargout{:} = listener@handle(varargin{:});
        end
        function varargout = lt(varargin)
            varargout{:} = lt@handle(varargin{:});
        end
        function varargout = ne(varargin)
            varargout{:} = ne@handle(varargin{:});
        end
        function varargout = notify(varargin)
            varargout{:} = notify@handle(varargin{:});
        end

    end

end
