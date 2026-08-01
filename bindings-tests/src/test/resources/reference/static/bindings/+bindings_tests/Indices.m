classdef (Sealed) Indices
% Index conversion for languages that count from one.

    methods(Static)

        function result = echo(index)
            % Returns the index unchanged. Both directions are marked, so a one-based caller
            %   passes a value and gets the same value back.
            %
            %   Inputs:
            %       index
            index = index - 1;
            result = bindings_tests_native('bindings_tests_Indices_echo', index);
            result = result + 1;
        end

        function result = countUpTo(index)
            % Index in, plain number out, so only the input converts.
            %
            %   Inputs:
            %       index
            index = index - 1;
            result = bindings_tests_native('bindings_tests_Indices_countUpTo', index);
        end

        function result = lastIndexOf(count)
            % Plain number in, index out, so only the output converts.
            %
            %   Inputs:
            %       count
            result = bindings_tests_native('bindings_tests_Indices_lastIndexOf', count);
            result = result + 1;
        end

        function result = offsetFrom(row, col)
            % Two indices that default to the first element. The default is written zero based
            %   here and each one-based wrapper shifts it, so an omitted argument converts the
            %   same way a supplied one does.
            %
            %   Inputs:
            %       row
            %       col
            if nargin < 1
                row = 1;
            end
            if nargin < 2
                col = 1;
            end
            row = row - 1;
            col = col - 1;
            result = bindings_tests_native('bindings_tests_Indices_offsetFrom', row, col);
        end

        function result = fromRow(row)
            % An index whose default is not zero, which is what catches a default that gets
            %   converted twice. Omitting the argument has to reach Java as 5 in every language.
            %
            %   Inputs:
            %       row
            if nargin < 1
                row = 6;
            end
            row = row - 1;
            result = bindings_tests_native('bindings_tests_Indices_fromRow', row);
        end

        function result = echoSize(index)
            % A size_t index, so the size bridge and the index conversion have to compose.
            %
            %   Inputs:
            %       index
            index = index - 1;
            result = bindings_tests_native('bindings_tests_Indices_echoSize', index);
            result = result + 1;
        end

    end

end
