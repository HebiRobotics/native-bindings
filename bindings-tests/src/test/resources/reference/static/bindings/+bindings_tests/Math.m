classdef (Sealed) Math
% Stateless arithmetic used to check primitive type mapping.

    methods(Static)

        function result = negate(value)
            result = bindings_tests_native('bindings_tests_Math_negate', value);
        end

        function result = nextChar(value)
            % Returns the next UTF-16 code unit. 'A' becomes 'B'.
            %
            %   Inputs:
            %       value
            result = bindings_tests_native('bindings_tests_Math_nextChar', value);
        end

        function result = addInt8(a, b)
            result = bindings_tests_native('bindings_tests_Math_addInt8', a, b);
        end

        function result = addInt16(a, b)
            result = bindings_tests_native('bindings_tests_Math_addInt16', a, b);
        end

        function result = addInt32(a, b)
            result = bindings_tests_native('bindings_tests_Math_addInt32', a, b);
        end

        function result = addInt64(a, b)
            % Uses values above 2^32 to catch platforms that truncate long to 32 bit.
            %
            %   Inputs:
            %       a
            %       b
            result = bindings_tests_native('bindings_tests_Math_addInt64', a, b);
        end

        function result = addFloat32(a, b)
            result = bindings_tests_native('bindings_tests_Math_addFloat32', a, b);
        end

        function result = addFloat64(a, b)
            result = bindings_tests_native('bindings_tests_Math_addFloat64', a, b);
        end

        function result = addSize(a, b)
            % size_t in and size_t out. Bridged through int64 on the Java side.
            %
            %   Inputs:
            %       a
            %       b
            result = bindings_tests_native('bindings_tests_Math_addSize', a, b);
        end

        function result = sizeToInt32(value)
            % Takes a size_t that is capped to a 32 bit Java int on the way in.
            %
            %   Inputs:
            %       value
            result = bindings_tests_native('bindings_tests_Math_sizeToInt32', value);
        end

        function result = maxInt32()
            % Returns Integer.MAX_VALUE, so callers can check the 31 bit cap.
            result = bindings_tests_native('bindings_tests_Math_maxInt32');
        end

        function result = maxSize()
            % Returns Long.MAX_VALUE as a size_t, so callers can check the 63 bit cap.
            result = bindings_tests_native('bindings_tests_Math_maxSize');
        end

    end

end
