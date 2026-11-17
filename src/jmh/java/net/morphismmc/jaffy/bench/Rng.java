// JAFFY: upstream=none
// JAFFY-NOTE: bench support: deterministic xorshift64 RNG

package net.morphismmc.jaffy.bench;

/// Shared deterministic RNG (xorshift64) — the same seed produces the same
/// style sequence for both libraries, the Java counterpart of upstream
/// benches' `ChaCha8Rng::seed_from_u64` (bit-exact ChaCha parity is not
/// required; identical sequences across the two libraries are).
public final class Rng {
    private long state;

    public Rng(long seed) {
        this.state = seed == 0 ? 1 : seed;
    }

    public long next() {
        long x = state;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        state = x;
        return x;
    }

    /// `rng.random_range(0.0..=1.0)`
    public float nextFloat() {
        return (next() >>> 40) / 16777216.0f;
    }

    /// `rng.random_range(0.0..=500.0)`
    public float nextFloat500() {
        return nextFloat() * 500.0f;
    }

    /// `rng.random_bool(0.5)`
    public boolean nextBool() {
        return nextFloat() < 0.5f;
    }

    /// `rng.random_range(range)` — inclusive [lo, hi]
    public int nextUsize(int lo, int hi) {
        return lo + (int) Math.floorMod(next() >>> 33, hi - lo + 1L);
    }
}
