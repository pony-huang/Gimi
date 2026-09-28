package github.ponyhuang.gimi.data.mobileuse;

interface IMobileUseService {
    int uid() = 1;
    int createDisplay() = 2;
    void launch(String component, int displayId) = 3;
    void tap(int displayId, int x, int y) = 4;
    void swipe(int displayId, int x1, int y1, int x2, int y2, int durationMs) = 5;
    void back(int displayId) = 6;
    byte[] capture() = 7;
    void stop() = 8;
    void destroy() = 16777114;
}
