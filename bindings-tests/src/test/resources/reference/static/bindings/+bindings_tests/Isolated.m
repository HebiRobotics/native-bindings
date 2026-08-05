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
            bindings_tests_native('bindings_tests_Isolated_alwaysFails');
        end

    end

end
