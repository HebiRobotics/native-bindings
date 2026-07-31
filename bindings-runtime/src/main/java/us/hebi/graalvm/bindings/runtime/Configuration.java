package us.hebi.graalvm.bindings.runtime;

/**
 * Runtime switches for the binding support classes.
 * <p>
 * The debug flag is deliberately mutable rather than a {@code static final} read at
 * class initialization. A constant would be folded into the image heap when the class
 * is initialized at build time, which would bake in whatever the value happened to be
 * on the machine that built the image. Native image builds that want the flag to follow
 * the host environment should still add
 * {@code --initialize-at-run-time=us.hebi.graalvm.bindings.runtime.Configuration}.
 *
 * @author Florian Enner
 */
public final class Configuration {

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public static void printDebug(String message) {
        if (debugEnabled) {
            System.err.println("[DEBUG] " + message);
        }
    }

    private Configuration() {
    }

    private static volatile boolean debugEnabled = Boolean.getBoolean("bindings.debug");

}
