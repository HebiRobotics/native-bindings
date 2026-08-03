classdef CircleColor < int32
% auto-generated class for holding CircleColor constants

    enumeration
        Red(0)
        Green(1)
        Blue(2)
    end

    methods (Static, Access = public)
        function out = toNativeValue(input)

            if isstring(input)
                input = char(input);
            elseif ~ischar(input)
                input = int32(input);
            end

            switch input
                case {'Red', 'bindings_tests_CircleColor_Red', 0}
                    out = 0;
                case {'Green', 'bindings_tests_CircleColor_Green', 1}
                    out = 1;
                case {'Blue', 'bindings_tests_CircleColor_Blue', 2}
                    out = 2;
                otherwise
                    error('input is not a valid CircleColor value. Use char or int constants.');
            end

            out = int32(out);
        end

    end

end
