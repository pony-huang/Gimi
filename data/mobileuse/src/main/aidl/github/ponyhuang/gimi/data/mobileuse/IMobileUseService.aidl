package github.ponyhuang.gimi.data.mobileuse;

import android.os.Bundle;
import android.view.Surface;

interface IMobileUseService {
    int uid() = 1;
    int createDisplay() = 2;
    void launch(String component, int displayId) = 3;
    void tap(int displayId, int x, int y) = 4;
    void swipe(int displayId, int x1, int y1, int x2, int y2, int durationMs) = 5;
    void back(int displayId) = 6;
    Bundle capture() = 7;
    void stop() = 8;
    int[] geometry(int displayId) = 9;
    void setPreview(int displayId, String bindingId, in Surface surface, int width, int height) = 10;
    void clearPreview(int displayId, String bindingId) = 11;
    boolean touch(int displayId, int action, float x, float y, long gestureId, long eventTimeMs) = 12;
    void destroy() = 16777114;
}
