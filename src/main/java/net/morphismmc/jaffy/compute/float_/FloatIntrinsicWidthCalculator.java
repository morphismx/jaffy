// JAFFY: upstream=src/compute/float.rs
// JAFFY-NOTE: `FloatIntrinsicWidthCalculator`
package net.morphismmc.jaffy.compute.float_;

import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.float_.Clear;
import net.morphismmc.jaffy.style.float_.FloatDirection;
import net.morphismmc.just.math.F32;

/// Context for computing the intrinsic width contribution of a set of floats
public final class FloatIntrinsicWidthCalculator {

    /// The available width of the container
    private final AvailableSpace availableWidth;
    /// The running sums of the widths of adjacent uncleared floats on each side (left, right)
    private final float[] sideSums = new float[2];
    /// The running total intrinsic width contribution
    private float contribution;
    /// The widest single float (the floats' min-content contribution)
    private float widest;

    /// Create a new `FloatIntrinsicWidthCalculator`
    public FloatIntrinsicWidthCalculator(AvailableSpace availableWidth) {
        this.availableWidth = availableWidth;
        this.sideSums[0] = 0.0f;
        this.sideSums[1] = 0.0f;
        this.contribution = 0.0f;
        this.widest = 0.0f;
    }

    /// Add a float to the computation
    public void addFloat(float width, FloatDirection direction, Clear clear) {
        if (availableWidth instanceof AvailableSpace.Definite || availableWidth instanceof AvailableSpace.MaxContent) {
            // Definite available width means the container is being fit-content sized
            // (e.g. it is itself a float being shrink-to-fit sized). The floats' max-content
            // contribution (widths summed) is clamped by the available width in `result`.

            // A float that clears a side is placed below the floats on that side, so its
            // width does not accumulate with theirs.
            if (clear == Clear.LEFT || clear == Clear.BOTH) {
                sideSums[0] = 0.0f;
            }
            if (clear == Clear.RIGHT || clear == Clear.BOTH) {
                sideSums[1] = 0.0f;
            }
            sideSums[direction.ordinal()] += width;
            contribution = F32.max(contribution, sideSums[0] + sideSums[1]);
        } else {
            // MinContent
            contribution = F32.max(contribution, width);
        }
        widest = F32.max(widest, width);
    }

    /// Get the computed float contribution to intrinsic width
    public float result() {
        if (availableWidth instanceof AvailableSpace.Definite d) {
            // Fit-content sizing: clamp the max-content contribution between the available
            // width and the min-content contribution (floats narrow by wrapping onto new
            // bands, but never below the widest single float).
            return F32.max(F32.min(contribution, d.value()), widest);
        }
        return contribution;
    }
}
