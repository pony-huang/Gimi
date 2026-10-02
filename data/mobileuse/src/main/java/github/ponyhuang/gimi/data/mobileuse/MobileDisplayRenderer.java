package github.ponyhuang.gimi.data.mobileuse;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** 专用 GL 线程将最新副屏帧分发给截图及可替换的预览输出；切窗不改变输入 Surface。 */
final class MobileDisplayRenderer implements AutoCloseable {
    private final HandlerThread thread = new HandlerThread("gimi-display-render");
    private final Handler handler;
    private EGLDisplay egl;
    private EGLContext context;
    private EGLConfig config;
    private EGLSurface parking;
    private EGLSurface capture = EGL14.EGL_NO_SURFACE;
    private EGLSurface preview = EGL14.EGL_NO_SURFACE;
    private Surface previewSurface;
    private SurfaceTexture texture;
    private Surface input;
    private int width, height, previewWidth, previewHeight, textureId, program;
    private long lastCapture;
    private boolean closed;
    private boolean renderPending;
    private final Runnable captureLatest = () -> {
        if (!closed) {
            draw(capture, width, height);
            lastCapture = SystemClock.elapsedRealtime();
        }
    };
    private final Runnable renderLatest = () -> {
        renderPending = false;
        try { render(); }
        catch (RuntimeException failure) { Log.w("MobileDisplayRenderer", "Frame unavailable", failure); }
    };
    private final float[] matrix = new float[16];
    private final FloatBuffer vertices = buffer(new float[]{-1,-1, 1,-1, -1,1, 1,1});
    private final FloatBuffer coordinates = buffer(new float[]{0,0, 1,0, 0,1, 1,1});

    MobileDisplayRenderer(Surface captureSurface, int width, int height) {
        thread.start();
        handler = new Handler(thread.getLooper());
        try { call(() -> {
            egl = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (!EGL14.eglInitialize(egl, new int[2], 0, new int[2], 0)) throw new IllegalStateException("EGL initialize failed");
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            EGL14.eglChooseConfig(egl, new int[]{EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
                    EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE}, 0, configs, 0, 1, count, 0);
            if (count[0] == 0) throw new IllegalStateException("No EGL config");
            config = configs[0];
            context = EGL14.eglCreateContext(egl, config, EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE}, 0);
            parking = EGL14.eglCreatePbufferSurface(egl, config,
                    new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
            current(parking);
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            textureId = textures[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER,
                    "attribute vec2 pos; attribute vec2 uv; uniform mat4 transform; varying vec2 tex; void main(){gl_Position=vec4(pos,0.,1.);tex=(transform*vec4(uv,0.,1.)).xy;}"));
            GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER,
                    "#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES image; varying vec2 tex; void main(){gl_FragColor=texture2D(image,tex);}"));
            GLES20.glLinkProgram(program);
            int[] linked = new int[1];
            GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);
            if (linked[0] == 0) throw new IllegalStateException("GL link failed");
            texture = new SurfaceTexture(textureId);
            texture.setOnFrameAvailableListener(ignored -> {
                if (!closed && !renderPending) {
                    renderPending = true;
                    handler.post(renderLatest);
                }
            }, handler);
            input = new Surface(texture);
            resizeInternal(captureSurface, width, height);
            return null;
        }); } catch (RuntimeException failure) {
            try { close(); } catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    Surface inputSurface() { return input; }

    void resize(Surface surface, int width, int height) {
        call(() -> { resizeInternal(surface,width,height); return null; });
    }

    private void resizeInternal(Surface surface, int width, int height) {
        current(parking);
        if (capture != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(egl,capture);
        this.width = width;
        this.height = height;
        texture.setDefaultBufferSize(width,height);
        capture = window(surface);
        lastCapture = 0;
    }

    void preview(Surface surface, int width, int height) {
        call(() -> {
            current(parking);
            if (preview != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(egl,preview);
            if (previewSurface != null) previewSurface.release();
            previewSurface = surface;
            preview = surface == null ? EGL14.EGL_NO_SURFACE : window(surface);
            previewWidth = width;
            previewHeight = height;
            if (surface != null) render();
            return null;
        });
    }

    private void render() {
        if (closed) return;
        current(parking);
        // SurfaceTexture 消费最新缓冲，窗口不会积压历史帧。截图限频，预览按绘制帧刷新。
        texture.updateTexImage();
        texture.getTransformMatrix(matrix);
        long now = SystemClock.elapsedRealtime();
        if (now - lastCapture >= 150) {
            handler.removeCallbacks(captureLatest);
            draw(capture,width,height);
            lastCapture = now;
        } else if (!handler.hasCallbacks(captureLatest)) {
            // 即使页面随后静止，也必须截图到最后一帧，避免 AI 观察到输入前的画面。
            handler.postDelayed(captureLatest, 150 - (now - lastCapture));
        }
        if (preview != EGL14.EGL_NO_SURFACE && !draw(preview,previewWidth,previewHeight)) {
            EGL14.eglDestroySurface(egl,preview);
            preview = EGL14.EGL_NO_SURFACE;
        }
    }

    private boolean draw(EGLSurface surface, int targetWidth, int targetHeight) {
        if (surface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(egl,surface,surface,context)) return false;
        GLES20.glViewport(0,0,targetWidth,targetHeight);
        GLES20.glClearColor(0,0,0,1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        float scale = Math.min((float)targetWidth/width,(float)targetHeight/height);
        int w = Math.round(width*scale), h = Math.round(height*scale);
        GLES20.glViewport((targetWidth-w)/2,(targetHeight-h)/2,w,h);
        GLES20.glUseProgram(program);
        int pos = GLES20.glGetAttribLocation(program,"pos"), uv = GLES20.glGetAttribLocation(program,"uv");
        vertices.position(0); coordinates.position(0);
        GLES20.glEnableVertexAttribArray(pos); GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,vertices);
        GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,0,coordinates);
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"transform"),1,false,matrix,0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"image"),0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        return EGL14.eglSwapBuffers(egl,surface);
    }

    private EGLSurface window(Surface surface) {
        EGLSurface result = EGL14.eglCreateWindowSurface(egl,config,surface,new int[]{EGL14.EGL_NONE},0);
        if (result == EGL14.EGL_NO_SURFACE) throw new IllegalStateException("EGL output unavailable");
        return result;
    }
    private void current(EGLSurface surface) {
        if (!EGL14.eglMakeCurrent(egl,surface,surface,context)) throw new IllegalStateException("EGL context unavailable");
    }
    private static int shader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader,source); GLES20.glCompileShader(shader);
        int[] compiled = new int[1]; GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,compiled,0);
        if (compiled[0] == 0) throw new IllegalStateException("GL shader failed");
        return shader;
    }
    private static FloatBuffer buffer(float[] data) {
        FloatBuffer result = ByteBuffer.allocateDirect(data.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        result.put(data).position(0); return result;
    }
    private <T> T call(java.util.concurrent.Callable<T> action) {
        FutureTask<T> task = new FutureTask<>(action);
        handler.post(task);
        try { return task.get(3,TimeUnit.SECONDS); }
        catch (Exception failure) { throw new IllegalStateException("Display renderer failed",failure); }
    }
    @Override public void close() {
        try { call(() -> {
            closed = true;
            handler.removeCallbacks(renderLatest);
            handler.removeCallbacks(captureLatest);
            if (texture != null) texture.setOnFrameAvailableListener(null);
            if (egl != null && parking != null && context != null) EGL14.eglMakeCurrent(egl,parking,parking,context);
            if (input != null) input.release();
            if (texture != null) texture.release();
            if (previewSurface != null) previewSurface.release();
            if (egl != null) {
                if (capture != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(egl,capture);
                if (preview != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(egl,preview);
                if (parking != null) EGL14.eglDestroySurface(egl,parking);
                EGL14.eglMakeCurrent(egl,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                if (context != null) EGL14.eglDestroyContext(egl,context);
                EGL14.eglTerminate(egl);
            }
            return null;
        }); } finally { thread.quitSafely(); }
    }
}
