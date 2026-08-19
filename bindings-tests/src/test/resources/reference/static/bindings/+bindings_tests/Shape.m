classdef Shape < handle & matlab.mixin.SetGet
% Base class for the shape hierarchy.

    properties (Dependent)
        name
    end

    methods

        function result = get.name(this)
            result = bindings_tests_native('bindings_tests_Shape_getName', this.ptr);
        end

        function set.name(this, name)
            bindings_tests_native('bindings_tests_Shape_setName', this.ptr, name);
        end

        function obj = plainCopy(this, varargin)
            % Creates a plain shape with the same name, without area or sides.
            %
            %   Outputs:
            %       Base class for the shape hierarchy.
            ptr_ = bindings_tests_native('bindings_tests_Shape_plainCopy', this.ptr);
            if isempty(ptr_)
                error('Failed to create bindings_tests.Shape in Shape.plainCopy');
            end
            obj = bindings_tests.Shape(ptr_, varargin{:});
        end

        function result = area(this)
            % Area of the shape. Dispatches to the concrete subclass, zero for a plain shape.
            result = bindings_tests_native('bindings_tests_Shape_area', this.ptr);
        end

        function result = sideCount(this)
            % Number of sides. Zero for shapes without straight edges.
            result = bindings_tests_native('bindings_tests_Shape_sideCount', this.ptr);
        end

    end

    methods(Access = public)
        function this = Shape(name, varargin)
            % Creates a plain named shape without area or sides.
            %
            %   Inputs:
            %       name
            %
            %   Outputs:
            %       Base class for the shape hierarchy.
            %
            %   Throws:
            %       Error on internal errors

            % A uint64 scalar is a handle the library created
            if nargin >= 1 && isa(name, 'uint64') && isscalar(name)
                ptr_ = name;
            else
                ptr_ = bindings_tests_native('bindings_tests_Shape_create', name);
                if isempty(ptr_)
                    error('Failed to create bindings_tests.Shape in Shape.Shape');
                end
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
                bindings_tests_native('bindings_tests_Shape_release', this.ptr);
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
