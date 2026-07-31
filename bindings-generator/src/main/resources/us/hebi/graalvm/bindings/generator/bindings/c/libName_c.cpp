#include "<libName>_c.h"
#include "graal_isolate.h"

namespace {
namespace isolate {

// Initial static creation. We attach the thread in the
// constructor, so we detach immediately to get an even
// number of operations.
graal_isolate_t* createIsolate() {
    graal_isolate_t* iso = nullptr;
    graal_isolatethread_t* thread = nullptr;
    if (graal_create_isolate(nullptr, &iso, &thread) != 0) {
        return (graal_isolate_t*) nullptr;
    }
    graal_detach_thread(thread);
    return iso;
}

// Thread-local attachment that lives for the lifetime of the thread
struct ThreadHandle {
    graal_isolatethread_t* ptr = nullptr;

    explicit ThreadHandle(graal_isolate_t* iso) {
        graal_attach_thread(iso, &ptr);
    }

    ~ThreadHandle() {
        if (ptr) graal_detach_thread(ptr);
    }
};

// Default global isolate
graal_isolatethread_t* global() {
    static graal_isolate_t* isolate = createIsolate();
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
