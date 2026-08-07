#include "<libName>_c.h"

// Aliases for the built-in GraalVM isolate API to avoid naming conflicts
extern "C" {
<isolateApi>
}

namespace {
namespace isolate {

// Initial static creation. We attach the thread in the
// constructor, so we detach immediately to get an even
// number of operations.
<cPrefix>_Isolate* createIsolate() {
    <cPrefix>_IsolateThread* thread = <cPrefix>_createIsolate();
    if (!thread) {
        return (<cPrefix>_Isolate*) nullptr;
    }
    <cPrefix>_Isolate* iso = <cPrefix>_getIsolate(thread);
    <cPrefix>_detachThread(thread);
    return iso;
}

// Thread-local attachment that lives for the lifetime of the thread
struct ThreadHandle {
    <cPrefix>_IsolateThread* ptr = nullptr;

    explicit ThreadHandle(<cPrefix>_Isolate* iso) {
        if (iso) ptr = <cPrefix>_attachThread(iso);
    }

    ~ThreadHandle() {
        if (ptr) <cPrefix>_detachThread(ptr);
    }
};

// Default global isolate
<cPrefix>_IsolateThread* global() {
    static <cPrefix>_Isolate* isolate = createIsolate();
    static thread_local ThreadHandle thread(isolate);
    return thread.ptr;
}

<isolates>

}
}

#ifdef __cplusplus
extern "C" {
#endif

<content>

#ifdef __cplusplus
}
#endif
