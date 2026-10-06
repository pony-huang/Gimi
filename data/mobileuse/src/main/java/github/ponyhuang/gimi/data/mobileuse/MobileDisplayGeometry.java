package github.ponyhuang.gimi.data.mobileuse;

/** 副屏当前像素边界和密度；手动方向可独立于主屏。 */
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

    /** 保留主屏像素长度和密度，只按用户选择排列长短边。 */
    public MobileDisplayGeometry oriented(boolean landscape) {
        int shortSide = Math.min(width, height);
        int longSide = Math.max(width, height);
        return new MobileDisplayGeometry(landscape ? longSide : shortSide,
                landscape ? shortSide : longSide, densityDpi);
    }

    public boolean contains(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** 将画面中的千分比位置映射到当前副屏像素，1000 对应最末像素。 */
    public int xAtPermille(int value) {
        return atPermille(value, width);
    }

    public int yAtPermille(int value) {
        return atPermille(value, height);
    }

    private static int atPermille(int value, int length) {
        if (value < 0 || value > 1000) throw new IllegalArgumentException("Invalid relative coordinate");
        return (int) ((long) value * (length - 1) / 1000);
    }

    public boolean sameAs(MobileDisplayGeometry other) {
        return other != null && width == other.width && height == other.height
                && densityDpi == other.densityDpi;
    }
}
