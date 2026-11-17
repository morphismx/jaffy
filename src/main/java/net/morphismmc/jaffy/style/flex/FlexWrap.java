// JAFFY: upstream=src/style/flex.rs
// JAFFY-NOTE: `FlexWrap` enum
//
// `#[cfg(feature = "parse")]` FromCss impls are feature-gated: deferred.
package net.morphismmc.jaffy.style.flex;

public enum FlexWrap {
    /// Items will not wrap and stay on a single line (default)
    NO_WRAP,
    /// Items will wrap according to this item's `FlexDirection`
    WRAP,
    /// Items will wrap in the opposite direction to this item's `FlexDirection`
    WRAP_REVERSE,
    /// Items will wrap, balanced across lines such that the largest line is
    /// as small as possible (`#[cfg(feature = "flexbox_balance")]`)
    BALANCE,
    /// Like `BALANCE` but in the opposite direction
    /// (`#[cfg(feature = "flexbox_balance")]`)
    BALANCE_REVERSE;

    /// `Default` impl — `#[default] NoWrap`
    public static final FlexWrap DEFAULT = NO_WRAP;

    /// Is this a wrapping mode (any value other than `NoWrap`)?
    public boolean isMultiLine() {
        return this != NO_WRAP;
    }

    /// Do lines stack in the opposite direction to the cross axis?
    public boolean isReverse() {
        return this == WRAP_REVERSE || this == BALANCE_REVERSE;
    }

    /// Are items balanced across lines? (`#[cfg(feature = "flexbox_balance")]`)
    public boolean isBalance() {
        return this == BALANCE || this == BALANCE_REVERSE;
    }
}
