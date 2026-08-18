package us.hebi.graalvm.bindings.generator.bindings.features;

import us.hebi.graalvm.bindings.generator.annotations.AnnotatedCLibClass;
import us.hebi.graalvm.bindings.generator.annotations.EnvProcessor;
import us.hebi.graalvm.bindings.generator.generators.DocumentationUtil;
import us.hebi.graalvm.bindings.generator.generators.IndentingTextRenderer;
import us.hebi.graalvm.bindings.model.LibraryDescription;

import java.util.Collections;
import java.util.List;

/**
 * macOS requires the Cocoa event loop to run on the main thread,
 * and all GUI interactions have to run on corresponding background
 * threads. This requires a hand-off on macOS that is not required
 * on Windows or Linux. Unfortunately the required callbacks need
 * to be implemented in C rather than Java, and they do not play
 * well with the protobuf description.
 * <p>
 * Until we have a general way to implement C sources w/ wrappers,
 * we keep all relevant code to Cocoa in this class.
 *
 * @author Florian Enner
 * @since 22 Mai 2025
 */
public class CocoaFeature implements FeatureGenerator {

    @Override
    public String getCHeader(boolean isCpp) {
        if (isCpp) {
            return render("""
                    typedef int (*%%MAIN_FUNC_T%%)(int argc, char** argv);
                    int %%RUN_APPLICATION%%(%%MAIN_FUNC_T%% callback, int argc, char** argv);
                    """);
        } else {
            return render("""

                    // ==== Cocoa utilities for supporting macOS ====
                    typedef int (*%%MAIN_FUNC_T%%)(int argc, char** argv);
                    %%DLL_PUBLIC%%int %%RUN_APPLICATION%%(%%MAIN_FUNC_T%% callback, int argc, char** argv);
                    """);
        }
    }

    @Override
    public String getCppWrapper(boolean useDynamicLookup) {
        if (!useDynamicLookup) {
            return render("""
                    // ==== Cocoa utilities for supporting macOS ====
                    %%DOXYGEN%%
                    inline int runApplication(%%MAIN_FUNC_T%% callback, int argc, char** argv) {
                      return %%RUN_APPLICATION%%(callback, argc, argv);
                    }
                    """);
        } else {
            // Call directly in case the library is not available
            return render("""
                    // ==== Cocoa utilities for supporting macOS ====
                    typedef int (*%%MAIN_FUNC_T%%)(int argc, char** argv);
                    %%DOXYGEN%%
                    inline int runApplication(%%MAIN_FUNC_T%% callback, int argc, char** argv) {
                      if (!DynamicLookup::instance().isLoaded()) {
                        return callback(argc, argv);
                      }
                      static auto %%RUN_APPLICATION%% = DynamicLookup::instance().getFunc<int(*)(%%MAIN_FUNC_T%%, int, char**)>("%%RUN_APPLICATION%%");
                      return %%RUN_APPLICATION%%(callback, argc, argv);
                    }
                    """);
        }
    }

    @Override
    public List<String> getPythonExports() {
        return prefix == null ? Collections.emptyList() : List.of("run_application");
    }

    @Override
    public String getPythonWrapper() {
        if (prefix == null) return "";
        var out = renderer().add("""

                # ==== Cocoa utilities for supporting macOS ====
                # C function signature
                %%MAIN_FUNC_T%% = CFUNCTYPE(c_int, c_int, POINTER(c_char_p))
                %%LIB_FIELD%%.%%RUN_APPLICATION%%.argtypes = [%%MAIN_FUNC_T%%, c_int, POINTER(c_char_p)]
                %%LIB_FIELD%%.%%RUN_APPLICATION%%.restype = c_int

                # Python wrapper. We ignore the C args as Python can just use a global namespace
                """);
        out.beginControlFlow("def run_application(callback_fn, *args, **kwargs):");
        out.addLine(DocumentationUtil.wrapInPydoc(documentation));
        out.add("""
                def c_callback_wrapper(argc, argv):
                    callback_fn(*args, **kwargs)
                    return 0
                c_callback = %%MAIN_FUNC_T%%(c_callback_wrapper)
                return %%LIB_FIELD%%.%%RUN_APPLICATION%%(c_callback, 0, (c_char_p * 0)())
                """);
        return out.render();
    }

    private static final String documentation = """
            Sets up required system libraries and executes the callback on an appropriate thread

            This is technically only needed on macOS as the Cocoa framework for displaying
            windows needs to be run on the main thread. On Windows and Linux this method
            executes the callback directly and otherwise does nothing. However, all platforms
            are supported to enable platform-independent code with the same behavior.
            """;

    @Override
    public String getCSource() {
        return render("""
                #if !defined(__APPLE__)
                int %%RUN_APPLICATION%%(%%MAIN_FUNC_T%% callback, int argc, char** argv) {
                  return callback(argc, argv); // Run immediately on same thread
                }
                #endif
                """);
    }

    @Override
    public String getObjCSource() {
        return render("""
                #import <Cocoa/Cocoa.h>
                #include <signal.h>

                typedef int (*%%MAIN_FUNC_T%%)(int argc, char** argv);
                typedef void (*user_func_t)(void* user_data);

                @interface %%APP_DELEGATE%% : NSObject <NSApplicationDelegate>
                @property (nonatomic, assign) int argc;
                @property (nonatomic, assign) char **argv;
                @property (nonatomic, assign) %%MAIN_FUNC_T%% callback;
                @end

                @implementation %%APP_DELEGATE%%

                - (void)applicationDidFinishLaunching:(NSNotification *)aNotification  {
                    // NSLog(@"Cocoa finished launching");

                    // Start the actual main in a background thread as the main thread is busy
                    // running the Cocoa event loop
                    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
                        // NSLog(@"Handing over application logic to background thread");
                        self.callback(self.argc, self.argv);

                        // Close the application once the callback is executed
                        dispatch_async(dispatch_get_main_queue(), ^{
                            // NSLog(@"closing");
                            [NSApp terminate:nil];
                        });
                    });

                }

                - (BOOL) applicationShouldTerminateAfterLastWindowClosed:(NSApplication *)app {
                    return NO;
                }

                - (BOOL) applicationSupportsSecureRestorableState:(NSApplication *)app {
                    return YES;
                }

                @end

                /*
                 * Overrides signal handlers to ensure Ctrl+C works even when Cocoa owns the main thread.
                 * It looks like normal signals are blocked, so we need to set special flags. We store
                 * the previous actions, so other handlers (e.g. Python) are still getting called.
                 */
                static void closeCocoaOnSignal(int sig, siginfo_t *info, void *context);
                static struct sigaction prevSigintAction;
                static struct sigaction prevSigtermAction;
                static void registerSignalHandlers() {
                    struct sigaction sa;
                    memset(&sa, 0, sizeof(sa));
                    sa.sa_sigaction = closeCocoaOnSignal;
                    sigemptyset(&sa.sa_mask);

                    // SA_NODEFER: Ensures the signal hits even if the thread is "blocked" in Cocoa
                    // SA_RESETHAND: Makes the 2nd Ctrl+C a guaranteed OS-level kill if the 1st hangs
                    sa.sa_flags = SA_NODEFER | SA_RESETHAND | SA_SIGINFO;

                    // React to terminal close and ctrl+c
                    sigaction(SIGINT, &sa, &prevSigintAction);
                    sigaction(SIGTERM, &sa, &prevSigtermAction);
                }

                static void closeCocoaOnSignal(int sig, siginfo_t *info, void *context) {
                    dispatch_async(dispatch_get_main_queue(), ^{
                        [NSApp terminate:nil];

                        // Post a dummy event to ensure the loop wakes up to process the termination
                        // This is not needed in the normal quit since the app is guaranteed to not
                        // be idle.
                        NSEvent* event = [NSEvent otherEventWithType:NSEventTypeApplicationDefined
                                          location:NSMakePoint(0,0) modifierFlags:0 timestamp:0.0
                                          windowNumber:0 context:nil subtype:0 data1:0 data2:0];
                        [NSApp postEvent:event atStart:YES];
                    });

                    struct sigaction *action = (sig == SIGINT) ? &prevSigintAction : &prevSigtermAction;
                    if (action->sa_flags & SA_SIGINFO) {
                        if (action->sa_sigaction) action->sa_sigaction(sig, info, context);
                    } else if (action->sa_handler != SIG_DFL && action->sa_handler != SIG_IGN && action->sa_handler != NULL) {
                        action->sa_handler(sig); // Call the original handler (e.g., Python's internal signal bookkeeping)
                    }

                }

                int %%RUN_APPLICATION%%(%%MAIN_FUNC_T%% callback, int argc, char** argv) {
                    // NSLog(@"Launching Cocoa framework");
                    @autoreleasepool {

                        // The main method gets called once the framework finished launching
                        %%APP_DELEGATE%%* delegate = [[%%APP_DELEGATE%% alloc] init];
                        delegate.argc = argc;
                        delegate.argv = argv;
                        delegate.callback = callback;

                        // Standard app setup
                        NSApplication *app = [NSApplication sharedApplication];
                        app.delegate = delegate;

                        // Regular means it's a normal app that shows up in the dock and can be tabbed
                        [NSApp setActivationPolicy:NSApplicationActivationPolicyRegular];

                        // Bring the app to the foreground
                        [NSApp activateIgnoringOtherApps:YES];

                        // Start the Cocoa event loop (must be on the main thread)
                        registerSignalHandlers();
                        [NSApp run];
                    }
                    return 0;

                }
                """);
    }

    @Override
    public List<String> getAppleLibraries() {
        if (prefix == null) return Collections.singletonList("\"\"");
        return List.of("\"-framework Cocoa\"");
    }

    /**
     * Empty when the feature is disabled
     */
    private String render(String template) {
        return prefix == null ? "" : renderer().add(template).render();
    }

    /**
     * The prefixed names. Tokens of the surrounding file pass through.
     */
    private IndentingTextRenderer renderer() {
        String noClass = "";
        return new IndentingTextRenderer()
                .set("MAIN_FUNC_T", AnnotatedCLibClass.getCEntryName(false, prefix, "MainCallbackFunction", ""))
                .set("RUN_APPLICATION", AnnotatedCLibClass.getCEntryName(true, prefix, noClass, "runApplication"))
                .set("APP_DELEGATE", AnnotatedCLibClass.getCEntryName(true, prefix, noClass, "AppDelegate"))
                .set("DOXYGEN", DocumentationUtil.wrapInDoxygen(documentation).trim());
    }

    public CocoaFeature(EnvProcessor.GeneratorOptions options) {
        prefix = options.enableGraphics() ? options.getCPrefix() : null;
    }

    public CocoaFeature(LibraryDescription desc) {
        prefix = desc.getEnableGraphics() ? desc.getCPrefix() : null;
    }

    private final String prefix;

}
