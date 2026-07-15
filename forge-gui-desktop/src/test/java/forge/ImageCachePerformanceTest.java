package forge;

import com.mortennobel.imagescaling.ResampleOp;
import com.mortennobel.imagescaling.DimensionConstrain;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ImageCachePerformanceTest {
    @Test
    public void cardImageResamplerUsesOneWorker() {
        final ResampleOp resampler = ImageScalingPolicy.createResampler(200, 280);

        Assert.assertEquals(resampler.getNumberOfThreads(), 1);
    }

    @Test
    public void panelResamplerUsesOneWorker() {
        final ResampleOp resampler = ImageScalingPolicy.createResampler(
                DimensionConstrain.createRelativeDimension(0.5f));

        Assert.assertEquals(resampler.getNumberOfThreads(), 1);
    }

    @Test
    public void sharedDefaultPlaceholderIsCached() {
        Assert.assertTrue(ImageScalingPolicy.shouldCacheScaledImage(true, true));
    }

    @Test
    public void cardSpecificPlaceholderIsNotCached() {
        Assert.assertFalse(ImageScalingPolicy.shouldCacheScaledImage(true, false));
    }

    @Test
    public void resolvedCardImageIsCached() {
        Assert.assertTrue(ImageScalingPolicy.shouldCacheScaledImage(false, false));
    }
}
