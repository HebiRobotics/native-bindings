#ifndef HEBI_BINDINGS_STRUCTS_H
#define HEBI_BINDINGS_STRUCTS_H

/*
 * Struct definitions for the GraalVM @CStruct layout probe. This header is only
 * used while building the native image and is never included by users. Generated
 * user-facing headers define layout-compatible structs under library-prefixed
 * names, which is safe because only the layout crosses the linker.
 */

#include <stddef.h>
#include <stdint.h>

typedef struct {
    const double* data;
    size_t length;
} DoubleSpan;

/*
 * Filled by every entry point that can throw. The message and id point into the
 * calling thread's scratch buffer and stay valid only until its next call. There
 * is room for one more int32 in the tail padding, anything beyond that changes
 * the size and breaks callers that allocated the old struct.
 */
typedef struct {
    const char* message;
    const char* id;
    int32_t code;
} Error;

#endif
