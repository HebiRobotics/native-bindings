#!/usr/bin/env bash
# Runs the per-language runtime suites against the built shared library.
#
#   scripts/run-tests.sh              all four suites
#   scripts/run-tests.sh c cpp        only the ones named
#
# Suites: c, cpp, python, matlab

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_built

targets=("$@")
if [ ${#targets[@]} -eq 0 ]; then
    targets=(c cpp python matlab)
fi

run_cmake_suite() {
    local lang="$1"
    local build_dir="$MODULE_DIR/target/ctest-$lang"
    step "Building the $lang suite"
    cmake -S "$MODULE_DIR/src/test/$lang" -B "$build_dir"
    cmake --build "$build_dir" --config Release
    step "Running the $lang suite"
    ctest --test-dir "$build_dir" --build-config Release --output-on-failure
}

failed=()

for target in "${targets[@]}"; do
    case "$target" in
        c|cpp)
            run_cmake_suite "$target" || failed+=("$target")
            ;;
        python)
            step "Running the Python suite"
            ( cd "$MODULE_DIR" && python -m unittest discover -s src/test/python -v ) || failed+=(python)
            ;;
        matlab)
            step "Running the MATLAB suite"
            if command -v matlab >/dev/null 2>&1; then
                ( cd "$MODULE_DIR" && matlab -batch \
                    "results = runtests('src/test/matlab'); disp(results); assertSuccess(results);" ) \
                    || failed+=(matlab)
            else
                echo "matlab is not on PATH, skipping."
            fi
            ;;
        *)
            echo "Unknown suite: $target" >&2
            exit 1
            ;;
    esac
done

echo
if [ ${#failed[@]} -eq 0 ]; then
    echo "All requested suites passed."
else
    echo "Failed: ${failed[*]}" >&2
    exit 1
fi
