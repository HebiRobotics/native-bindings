#include "<libName>_c.h"

namespace {
namespace isolate {

// Initial static creation. We attach the thread in the
// constructor, so we detach immediately to get an even
// number of operations.
<cPrefix>_isolate_t* createIsolate() {
    <cPrefix>_isolatethread_t* thread = <cPrefix>_createIsolate();
    if (!thread) {
        return (<cPrefix>_isolate_t*) nullptr;
    }
    <cPrefix>_isolate_t* iso = <cPrefix>_getIsolate(thread);
    <cPrefix>_detachThread(thread);
    return iso;
}

// Thread-local attachment that lives for the lifetime of the thread
struct ThreadHandle {
    <cPrefix>_isolatethread_t* ptr = nullptr;

    explicit ThreadHandle(<cPrefix>_isolate_t* iso) {
        if (iso) ptr = <cPrefix>_attachThread(iso);
    }

    ~ThreadHandle() {
        if (ptr) <cPrefix>_detachThread(ptr);
    }
};

// Default global isolate
<cPrefix>_isolatethread_t* global() {
    static <cPrefix>_isolate_t* isolate = createIsolate();
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
