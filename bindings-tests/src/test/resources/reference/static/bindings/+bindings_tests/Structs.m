classdef (Sealed) Structs
% Reads fields out of a caller-owned struct.

    methods(Static)

        function result = totalPower(status, currentAmps)
            % Power draw in watts for the given current. Returns zero unless the actuator is
            %   enabled and in the running state (2), so all three fields decide the result.
            %
            %   Inputs:
            %       status
            %       currentAmps
            result = bindings_tests_native('bindings_tests_Structs_totalPower', status, currentAmps);
        end

        function result = checkedVoltage(status)
            % Bus voltage in volts. Returns 0.0 through the ABI when it throws.
            %
            %   Inputs:
            %       status
            %
            %   Throws:
            %       Error the actuator is disabled
            result = bindings_tests_native('bindings_tests_Structs_checkedVoltage', status);
        end

        function result = getStatus(index)
            % Fills the out parameter with a canned reading for the given index: index 0
            %   reads (48.0, running, enabled), every other index reads (12.5, 1, disabled).
            %
            %   Inputs:
            %       index
            %
            %   Outputs:
            %       Status reading for the given index.
            result = bindings_tests_native('bindings_tests_Structs_getStatus', index);
        end

        function result = getCheckedStatus(index)
            % Same readings as getStatus. Every field of the out parameter reads as zero through the ABI when it throws.
            %
            %   Inputs:
            %       index
            %
            %   Throws:
            %       Error the index is negative
            result = bindings_tests_native('bindings_tests_Structs_getCheckedStatus', index);
        end

    end

end
