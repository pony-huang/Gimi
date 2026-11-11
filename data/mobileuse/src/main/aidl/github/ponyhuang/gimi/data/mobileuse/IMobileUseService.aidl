package github.ponyhuang.gimi.data.mobileuse;

import android.os.Bundle;
import android.view.Surface;

interface IMobileUseService {
    int uid() = 1;
    int createDisplay() = 2;
    int launch(String component, int displayId) = 3;
    boolean tap(int displayId, int x, int y) = 4;
    boolean back(int displayId) = 6;
    Bundle capture() = 7;
    void stop() = 8;
    int[] geometry(int displayId) = 9;
    void setPreview(int displayId, String bindingId, in Surface surface, int width, int height, boolean interactive) = 10;
    void clearPreview(int displayId, String bindingId) = 11;
    boolean touch(int displayId, int action, float x, float y, long gestureId, long eventTimeMs) = 12;
    int[] rotateDisplay(int displayId) = 13;
    boolean gesture(int displayId, int x1, int y1, int x2, int y2, int holdMs, int durationMs) = 14;
    boolean pressKey(int displayId, String key) = 15;
    Bundle runShell(int displayId, String command) = 16;
    boolean preparePaste(int displayId, String text) = 17;
    void restoreClipboard() = 18;
    void destroy() = 16777114;
}
