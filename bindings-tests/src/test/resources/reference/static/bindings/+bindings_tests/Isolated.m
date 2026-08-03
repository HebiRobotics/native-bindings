classdef (Sealed) Isolated
% State confined to a second isolate.

    methods(Static)

        function increment()
            bindings_tests_native('bindings_tests_Isolated_increment');
        end

        function result = getCounter()
            result = bindings_tests_native('bindings_tests_Isolated_getCounter');
        end

        function reset()
            bindings_tests_native('bindings_tests_Isolated_reset');
        end

        function alwaysFails()
            % Always throws, so the secondary isolate's error path is exercised too.
            %
            %   Throws:
            %       Error always
            status_ = bindings_tests_native('bindings_tests_Isolated_alwaysFails');
            if status_ ~= 0
                error(['Encountered error in Isolated.alwaysFails' ': ' bindings_tests_native('bindings_tests_secondary_getLastError')]);
            end
        end

    end

end
