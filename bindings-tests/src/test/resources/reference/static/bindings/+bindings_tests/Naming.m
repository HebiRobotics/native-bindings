classdef (Sealed) Naming < handle & matlab.mixin.SetGet
% A counter exported under a custom prefix in snake case.

    methods(Static)

        function result = addTwoNumbers(first, second)
            % A static method, so the conversion is pinned for both kinds of call.
            %
            %   Inputs:
            %       first
            %       second
            result = bindings_tests_native('custom_prefix_Naming_add_two_numbers', first, second);
        end

    end

    methods

        function result = getStartValue(this)
            % An instance method whose name needs more than one word to show the conversion.
            result = bindings_tests_native('custom_prefix_Naming_get_start_value', this.ptr);
        end

    end

    methods(Access = public)
        function this = Naming(startValue)
            % Creates an instance holding the given value, or zero by default.
            %
            %   Inputs:
            %       startValue
            %
            %   Outputs:
            %       A counter exported under a custom prefix in snake case.
            %
            %   Throws:
            %       Error on internal errors

            % A uint64 scalar is a handle the library created
            if nargin >= 1 && isa(startValue, 'uint64') && isscalar(startValue)
                ptr_ = startValue;
            else
                if nargin < 1
                    startValue = 0;
                end
                ptr_ = bindings_tests_native('custom_prefix_Naming_create', startValue);
                if isempty(ptr_)
                    error('Failed to create bindings_tests.Naming in Naming.Naming');
                end
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
                bindings_tests_native('custom_prefix_Naming_release', this.ptr);
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
