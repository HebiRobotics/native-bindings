#ifndef HEBI_BINDINGS_STRUCTS_H
#define HEBI_BINDINGS_STRUCTS_H

/*
 * Struct definitions for the GraalVM @CStruct layout probe. This header is only
 * used while building the native image and is never included by users. Generated
 * user-facing headers define layout-compatible structs under library-prefixed
 * names, which is safe because only the layout crosses the linker.
 */

#include <stddef.h>

typedef struct {
    const double* data;
    size_t length;
} DoubleSpan;

#endif
