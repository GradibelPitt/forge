package forge;

import com.mortennobel.imagescaling.ResampleOp;
import com.mortennobel.imagescaling.DimensionConstrain;

public final class ImageScalingPolicy {
    private ImageScalingPolicy() {
    }

    public static ResampleOp createResampler(final int width, final int height) {
        final ResampleOp resampler = new ResampleOp(width, height);
        resampler.setNumberOfThreads(1);
        return resampler;
    }

    public static ResampleOp createResampler(final DimensionConstrain constrain) {
        final ResampleOp resampler = new ResampleOp(constrain);
        resampler.setNumberOfThreads(1);
        return resampler;
    }

    static boolean shouldCacheScaledImage(final boolean isPlaceholder, final boolean isDefaultImage) {
        return !isPlaceholder || isDefaultImage;
    }
}
