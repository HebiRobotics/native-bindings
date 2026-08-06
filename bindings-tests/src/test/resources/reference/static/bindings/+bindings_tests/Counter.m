classdef (Sealed) Counter < handle & matlab.mixin.SetGet
% A mutable counter, used to check handle creation and release.

    methods(Static)

        function result = getClosedCount()
            % Total number of counters that have been released so far in this isolate.
            %   Increments exactly once per released handle, so tests can verify that the
            %   generated destructor ran.
            result = bindings_tests_native('bindings_tests_Counter_getClosedCount');
        end

        function resetClosedCount()
            % Resets the released counter, so tests do not depend on execution order.
            bindings_tests_native('bindings_tests_Counter_resetClosedCount');
        end

    end

    properties (Dependent)
        value
    end

    methods

        function result = get.value(this)
            result = bindings_tests_native('bindings_tests_Counter_getValue', this.ptr);
        end

        function set.value(this, value)
            bindings_tests_native('bindings_tests_Counter_setValue', this.ptr, value);
        end

        function increment(this)
            bindings_tests_native('bindings_tests_Counter_increment', this.ptr);
        end

        function add(this, amount)
            bindings_tests_native('bindings_tests_Counter_add', this.ptr, amount);
        end

        function result = isClosed(this)
            % Returns true once close() has run. Only observable before the handle is released.
            result = bindings_tests_native('bindings_tests_Counter_isClosed', this.ptr);
        end

    end

    methods(Access = public)
        function this = Counter(varargin)
            % Creates a counter starting at the given value, or at zero by default.
            %
            %   Inputs:
            %       start
            %
            %   Outputs:
            %       A mutable counter, used to check handle creation and release.
            %
            %   Throws:
            %       Error on internal errors
            start = 0;
            if ~isempty(varargin) && ~ischar(varargin{1}) && ~isstring(varargin{1})
                start = varargin{1};
                varargin(1) = [];
            end
            ptr_ = bindings_tests_native('bindings_tests_Counter_create', start);
            if isempty(ptr_)
                error('Failed to create bindings_tests.Counter in Counter.Counter');
            end
            this.ptr = ptr_;
            if numel(varargin) > 0
                this.set(varargin{:});
            end
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
                bindings_tests_native('bindings_tests_Counter_release', this.ptr);
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
