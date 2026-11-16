// JAFFY: port of upstream src/style/mod.rs — `ContainingBlockClaims` struct.
package net.morphismmc.jaffy.style;

/// Which out-of-flow positions a node establishes a containing block for.
///
/// Returned by {@link CoreStyle#isContainingBlock()}.
///
/// @param absolute whether the node is the containing block for `position: absolute` boxes
/// @param fixed whether the node is the containing block for `position: fixed` boxes
public record ContainingBlockClaims(boolean absolute, boolean fixed) {

    /// Claims nothing
    public static final ContainingBlockClaims NONE = new ContainingBlockClaims(false, false);
    /// Claims every out-of-flow box (the initial containing block)
    public static final ContainingBlockClaims ALL = new ContainingBlockClaims(true, true);

    /// Whether the node is the containing block for a box with the given `position`
    public boolean forPosition(Position position) {
        return switch (position) {
            case ABSOLUTE -> absolute;
            case FIXED -> fixed;
            default -> false;
        };
    }
}
