classdef (Sealed) Circle < bindings_tests.Shape
% A circle. Inherits name and area from Shape.

    properties (Dependent)
        radius
    end

    methods

        function result = get.radius(this)
            result = bindings_tests_native('bindings_tests_Circle_getRadius', this.ref_Circle);
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
            ptr_ = bindings_tests_native('bindings_tests_Circle_create', radius);
            if isempty(ptr_)
                error(['Failed to create bindings_tests.Circle in Circle.Circle' ': ' bindings_tests_native('bindings_tests_Errors_getLastError')]);
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
