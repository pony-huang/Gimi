package github.ponyhuang.gimi.data.mobileuse;

/** 当前主屏对应的副屏像素边界和密度。 */
public final class MobileDisplayGeometry {
    public final int width;
    public final int height;
    public final int densityDpi;

    public MobileDisplayGeometry(int width, int height, int densityDpi) {
        if (width <= 0 || height <= 0 || densityDpi <= 0) {
            throw new IllegalArgumentException("Invalid display geometry");
        }
        this.width = width;
        this.height = height;
        this.densityDpi = densityDpi;
    }

    public boolean contains(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public boolean sameAs(MobileDisplayGeometry other) {
        return other != null && width == other.width && height == other.height
                && densityDpi == other.densityDpi;
    }
}
