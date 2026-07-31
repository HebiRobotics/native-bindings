classdef (Sealed) Palette
% Enum round trips.

    methods(Static)

        function result = next(color)
            % Cycles Red to Green to Blue and back to Red.
            %
            %   Inputs:
            %       color
            result = bindings_tests_native('bindings_tests_Palette_next', bindings_tests.Color.toNativeValue(color));
        end

        function result = nameOf(color)
            result = bindings_tests_native('bindings_tests_Palette_nameOf', bindings_tests.Color.toNativeValue(color));
        end

        function result = ordinalOf(color)
            result = bindings_tests_native('bindings_tests_Palette_ordinalOf', bindings_tests.Color.toNativeValue(color));
        end

        function result = describe(color, marker)
            % Takes both enums at once, so the two generated types have to coexist in one signature.
            %
            %   Inputs:
            %       color
            %       marker
            result = bindings_tests_native('bindings_tests_Palette_describe', bindings_tests.Color.toNativeValue(color), bindings_tests.Marker.toNativeValue(marker));
        end

        function result = fromOrdinal(ordinal)
            % Returns the enum with the given ordinal, or Red if it is out of range.
            %
            %   Inputs:
            %       ordinal
            result = bindings_tests_native('bindings_tests_Palette_fromOrdinal', ordinal);
        end

    end

end
