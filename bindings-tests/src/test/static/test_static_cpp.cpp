// Consumer test for the staged static library package, C++ side.
//
// This exists to prove that bindings_tests.hpp links against
// bindings_tests::static with no shared library present, not to repeat the
// full value table the C++ suite in src/test/cpp already covers.
//
// Exit code 0 means success.

#include <iostream>
#include <vector>

#include "bindings_tests.hpp"

namespace bt = bindings::tests;

static int failures = 0;

#define CHECK(cond)                                                           \
  do {                                                                        \
    if (!(cond)) {                                                            \
      std::cout << "FAILED " << __FILE__ << ":" << __LINE__ << ": " << #cond  \
                << std::endl;                                                 \
      ++failures;                                                             \
    }                                                                         \
  } while (0)

int main() {
  bt::Counter counter(5);
  CHECK(counter.getValue() == 5);
  counter.increment();
  CHECK(counter.getValue() == 6);

  CHECK(bt::math::addInt32(2, 3) == 5);

  std::vector<double> x = {1.0, 2.0, 3.0};
  std::vector<double> y = {4.0, 5.0, 6.0};
  CHECK(bt::arrays::dotProduct(x, y) == 32.0);

  bool threw = false;
  try {
    bt::errors::requireNonNegative(-1);
  } catch (const bt::Exception&) {
    threw = true;
  }
  CHECK(threw);

  if (failures == 0) {
    std::cout << "static C++ consumer OK" << std::endl;
    return 0;
  }
  std::cout << failures << " static C++ consumer check(s) failed" << std::endl;
  return 1;
}
