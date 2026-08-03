/*
 * Consumer test for the staged static library package, C side.
 *
 * This exists to prove a pure C translation unit links against
 * bindings_tests::static with no shared library present, not to repeat the
 * full value table the C suite in src/test/c already covers.
 *
 * Exit code 0 means success.
 */

#include <stdio.h>
#include <string.h>

#include "bindings_tests_c.h"

static int failures = 0;

#define CHECK(cond)                                                  \
  do {                                                                \
    if (!(cond)) {                                                    \
      printf("FAILED %s:%d: %s\n", __FILE__, __LINE__, #cond);        \
      ++failures;                                                     \
    }                                                                  \
  } while (0)

int main(void) {
    bindings_tests_CounterPtr counter;
    double x[3] = {1.0, 2.0, 3.0};
    double y[3] = {4.0, 5.0, 6.0};
    bindings_tests_double_span xSpan;
    bindings_tests_double_span ySpan;
    const char* lastError;

    CHECK(bindings_tests_Math_addInt32(2, 3) == 5);
    CHECK(bindings_tests_Math_negate(0) == 1); /* boolean not */

    counter = bindings_tests_Counter_create(5);
    CHECK(counter != NULL);
    CHECK(bindings_tests_Counter_getValue(counter) == 5);
    bindings_tests_Counter_increment(counter);
    CHECK(bindings_tests_Counter_getValue(counter) == 6);
    bindings_tests_Counter_release(counter);

    xSpan.data = x;
    xSpan.length = 3;
    ySpan.data = y;
    ySpan.length = 3;
    CHECK(bindings_tests_Arrays_dotProduct(xSpan, ySpan) == 32.0);

    bindings_tests_Errors_clearLastError();
    bindings_tests_Errors_requireNonNegative(-1);
    lastError = bindings_tests_Errors_getLastError();
    CHECK(lastError != NULL && strlen(lastError) > 0);

    if (failures == 0) {
        printf("static C consumer OK\n");
        return 0;
    }
    printf("%d static C consumer check(s) failed\n", failures);
    return 1;
}
