// JAFFY: upstream=src/style/grid.rs
// JAFFY-NOTE: `RepetitionCount` enum
// `TryFrom<&str>`. The `parse` impl is deferred with the feature.
package net.morphismmc.jaffy.style.grid;

import net.morphismmc.just.result.Result;

/// The first argument to a repeated track definition. This type represents the type of automatic repetition to perform.
///
/// See <https://www.w3.org/TR/css-grid-1/#auto-repeat> for an explanation of how auto-repeated track definitions work
/// and the difference between AutoFit and AutoFill.
public sealed interface RepetitionCount {

    /// Auto-repeating tracks should be generated to fit the container
    /// See: <https://developer.mozilla.org/en-US/docs/Web/CSS/repeat#auto-fill>
    record AutoFill() implements RepetitionCount {}

    /// Auto-repeating tracks should be generated to fit the container
    /// See: <https://developer.mozilla.org/en-US/docs/Web/CSS/repeat#auto-fit>
    record AutoFit() implements RepetitionCount {}

    /// The specified tracks should be repeated exacts N times
    /// (Rust `u16` -> `int`, GAP-014)
    record Count(int count) implements RepetitionCount {}

    RepetitionCount AUTO_FILL = new AutoFill();
    RepetitionCount AUTO_FIT = new AutoFit();

    /// `From<u16>`
    static RepetitionCount from(int value) {
        return new Count(value);
    }

    /// `TryFrom<&str>` — "auto-fit"/"auto-fill" only.
    static Result<RepetitionCount, InvalidStringRepetitionValue> tryFrom(String value) {
        return switch (value) {
            case "auto-fit" -> Result.ok(AUTO_FIT);
            case "auto-fill" -> Result.ok(AUTO_FILL);
            default -> Result.err(new InvalidStringRepetitionValue());
        };
    }
}
