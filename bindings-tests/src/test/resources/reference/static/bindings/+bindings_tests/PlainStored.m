classdef (Sealed) PlainStored < handle & matlab.mixin.SetGet
% A handle that invokes a callback during construction and stores it for later calls.

    methods(Static)

        function result = getClosedCount()
            % Total number of handles of this type that have been released so far in this
            %   isolate. Increments exactly once per released handle.
            result = bindings_tests_native('bindings_tests_PlainStored_getClosedCount');
        end

    end

    methods

        function fire(this, index)
            % Invokes the stored callback once with the given index, on the calling thread.
            %
            %   Inputs:
            %       index
            bindings_tests_native('bindings_tests_PlainStored_fire', this.ptr, index);
        end

    end

    methods(Access = public)
        function this = PlainStored(func, times)
            % Invokes the callback with indices 0 to times-1 before returning the handle, and
            %   keeps it for every later call to fire(), so it has to stay alive for as long as
            %   the handle does.
            %
            %   Inputs:
            %       func
            %       times
            %
            %   Outputs:
            %       A handle that invokes a callback during construction and stores it for later calls.
            %
            %   Throws:
            %       Error on internal errors
            validateattributes(func, {'function_handle'}, {'scalar'}, 'PlainStored.PlainStored', 'func');
            ptr_ = bindings_tests_native('bindings_tests_PlainStored_create', func, times);
            if isempty(ptr_)
                error('Failed to create bindings_tests.PlainStored in PlainStored.PlainStored');
            end
            this.ptr = ptr_;
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
                bindings_tests_native('bindings_tests_PlainStored_release', this.ptr);
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
