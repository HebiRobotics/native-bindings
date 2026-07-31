package us.hebi.graalvm.bindings.annotations;

/**
 * Semantic markers for methods exposed via {@link CLibMethod#flags()}.
 * <p>
 * The generator mirrors these into the descriptor model by name, so the
 * constant names here must stay in sync with the {@code MethodFlag} enum
 * in {@code LibraryDescription.proto}.
 *
 * @author Florian Enner
 */
public enum MethodFlag {

    /**
     * The method does not change any internal state.
     */
    Const,

    /**
     * The method is guaranteed to never throw exceptions.
     */
    Noexcept

}
