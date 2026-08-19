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

        function obj = copy(this, varargin)
            % Creates an independent counter with the same value.
            %
            %   Outputs:
            %       A mutable counter, used to check handle creation and release.
            ptr_ = bindings_tests_native('bindings_tests_Counter_copy', this.ptr);
            if isempty(ptr_)
                error('Failed to create bindings_tests.Counter in Counter.copy');
            end
            obj = bindings_tests.Counter(ptr_, varargin{:});
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
            %
            % Creates a counter starting at the value written in text.
            %
            %   Inputs:
            %       text
            %
            %   Outputs:
            %       A mutable counter, used to check handle creation and release.
            %
            %   Throws:
            %       Error on internal errors

            % A uint64 scalar is a handle the library created
            if nargin >= 1 && isa(varargin{1}, 'uint64') && isscalar(varargin{1})
                ptr_ = varargin{1};
                nargs_ = 1;
            else
                % Positional arguments end at the first property name
                nargs_ = nargin;
                for i = 1:nargin
                    if (ischar(varargin{i}) || isstring(varargin{i})) && any(strcmpi(varargin{i}, {'value'}))
                        nargs_ = i - 1;
                        break
                    end
                end
                if nargs_ <= 1 && (nargs_ < 1 || (isnumeric(varargin{1}) || islogical(varargin{1})))
                    start = 0;
                    if nargs_ >= 1
                        start = varargin{1};
                    end
                    ptr_ = bindings_tests_native('bindings_tests_Counter_create', start);
                    if isempty(ptr_)
                        error('Failed to create bindings_tests.Counter in Counter.Counter');
                    end
                elseif nargs_ == 1 && (ischar(varargin{1}) || isstring(varargin{1}))
                    text = varargin{1};
                    ptr_ = bindings_tests_native('bindings_tests_Counter_parse', text);
                    if isempty(ptr_)
                        error('Failed to create bindings_tests.Counter in Counter.Counter');
                    end
                else
                    error('bindings_tests:TypeError', 'Counter has no constructor for these arguments');
                end
            end
            varargin(1:nargs_) = [];
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
