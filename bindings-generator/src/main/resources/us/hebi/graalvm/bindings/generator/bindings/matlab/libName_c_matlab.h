// Remove macros that can't be parsed by the MATLAB parser
#define DLL_PUBLIC /* ignore */
#define DLL_LOCAL /* ignore */

// Remove enum size checks -> error C2059: syntax error: 'sizeof'
#define static_assert(condition, message)
#define _Static_assert(condition, message)

// Set a variable that removes otherwise incompatible lines
#define MATLAB 1

// Parse the remaining header
#include "%%LIB_NAME%%_c.h"
