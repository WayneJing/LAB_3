package ece.course.lab3.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.hardware.Camera;
import java.util.List;
import android.hardware.Camera.Size;

/**
 * Created by chenjingwen on 2017/10/8.
 */

public class CameraPreviewView extends SurfaceView implements SurfaceHolder.Callback {
    private Size mPreviewSize;
    private List<Size> mSupportedPreviewSizes;
    private Camera mCamera;
    public CameraPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);
        if (mSupportedPreviewSizes != null)
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
    }

    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
        if (sizes == null) return null;
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        double minDiff = Double.MAX_VALUE;
        int targetHeight = h;
        Size optimalSize = null;
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    public void setCamera(Camera camera) {
        mCamera = camera;
        if (mCamera != null) {
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            requestLayout();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        mCamera.setParameters(parameters);
        mCamera.startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        try {
            if (mCamera != null) mCamera.setPreviewDisplay(surfaceHolder);
        } catch (Exception exception) { }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        if (mCamera != null) mCamera.stopPreview();
    }
}
