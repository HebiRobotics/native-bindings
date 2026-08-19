classdef (Sealed) Square < bindings_tests.Shape
% A square. Inherits name and area from Shape.

    properties (Dependent)
        side
    end

    methods

        function result = get.side(this)
            result = bindings_tests_native('bindings_tests_Square_getSide', this.ref_Square);
        end

        function result = area(this)
            result = bindings_tests_native('bindings_tests_Square_area', this.ref_Square);
        end

        function result = sideCount(this)
            result = bindings_tests_native('bindings_tests_Square_sideCount', this.ref_Square);
        end

    end

    methods(Access = public)
        function this = Square(side, varargin)
            % A uint64 scalar is a handle the library created
            if nargin >= 1 && isa(side, 'uint64') && isscalar(side)
                ptr_ = side;
            else
                ptr_ = bindings_tests_native('bindings_tests_Square_create', side);
                if isempty(ptr_)
                    error('Failed to create bindings_tests.Square in Square.Square');
                end
            end
            this@bindings_tests.Shape(bindings_tests_native('bindings_tests_Square_to_Shape', ptr_));
            this.ref_Square = ptr_;
            if numel(varargin) > 0
                this.set(varargin{:});
            end
        end
    end
    properties (SetAccess = private, GetAccess = private, Hidden = true)
        ref_Square = [];
    end

    methods(Access = public, Hidden = true)

        function ptr = c_ptr(this)
            ptr = this.ref_Square;
        end

    end

end
