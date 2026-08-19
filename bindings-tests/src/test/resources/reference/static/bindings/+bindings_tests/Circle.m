classdef (Sealed) Circle < bindings_tests.Shape
% A circle. Inherits name and area from Shape.

    properties (Dependent)
        radius
    end

    methods

        function result = get.radius(this)
            result = bindings_tests_native('bindings_tests_Circle_getRadius', this.ref_Circle);
        end

        function result = getColor(this)
            % Returns the fill color, which is always Blue until setters exist.
            %
            %   Outputs:
            %       Fill color of a circle.
            result = bindings_tests_native('bindings_tests_Circle_getColor', this.ref_Circle);
        end

        function result = area(this)
            result = bindings_tests_native('bindings_tests_Circle_area', this.ref_Circle);
        end

        function result = sideCount(this)
            result = bindings_tests_native('bindings_tests_Circle_sideCount', this.ref_Circle);
        end

    end

    methods(Access = public)
        function this = Circle(radius, varargin)
            % A uint64 scalar is a handle the library created
            if nargin >= 1 && isa(radius, 'uint64') && isscalar(radius)
                ptr_ = radius;
            else
                ptr_ = bindings_tests_native('bindings_tests_Circle_create', radius);
                if isempty(ptr_)
                    error('Failed to create bindings_tests.Circle in Circle.Circle');
                end
            end
            this@bindings_tests.Shape(bindings_tests_native('bindings_tests_Circle_to_Shape', ptr_));
            this.ref_Circle = ptr_;
            if numel(varargin) > 0
                this.set(varargin{:});
            end
        end
    end
    properties (SetAccess = private, GetAccess = private, Hidden = true)
        ref_Circle = [];
    end

    methods(Access = public, Hidden = true)

        function ptr = c_ptr(this)
            ptr = this.ref_Circle;
        end

    end

end
