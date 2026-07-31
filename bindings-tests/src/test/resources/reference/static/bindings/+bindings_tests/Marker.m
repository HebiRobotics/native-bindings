classdef Marker < int32
% auto-generated class for holding Marker constants

    enumeration
        Dot(0)
        Cross(1)
    end

    methods (Static, Access = public)
        function out = toNativeValue(input)

            if isstring(input)
                input = char(input);
            elseif ~ischar(input)
                input = int32(input);
            end

            switch input
                case {'Dot', 'bindings_tests_Marker_Dot', 0}
                    out = 0;
                case {'Cross', 'bindings_tests_Marker_Cross', 1}
                    out = 1;
                otherwise
                    error('input is not a valid Marker value. Use char or int constants.');
            end

            out = int32(out);
        end

    end

end
