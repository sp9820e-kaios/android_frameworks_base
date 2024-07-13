package com.sprd.quickcamera;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.sprd.quickcamera.exif.ExifInterface;
import com.sprd.quickcamera.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.os.Vibrator;
import android.opengl.GLES20;
import android.graphics.SurfaceTexture;

/**
 * TODO: - Performance when over gl surfaces? Ie. Gallery - what do we say in
 * the Toast? Which icon do we get if the user uses another type of gallery?
 */
public class QuickCamera implements SurfaceHolder.Callback, SensorEventListener {
    private static final String TAG = "QuickCamera";

    private static final int QUICKCAMERA_REMOVE_LAYOUT = 0;
    //private static final int QUICKCAMERA_DROP_OUT_DELAY = 4000;
    private static final int QUICKCAMERA_DO_CAPTURE = 1;
    private static final int QUICKCAMERA_TAKE_PICTURE_DELAY_BACK = 300;
    private static final int QUICKCAMERA_TAKE_PICTURE_DELAY_FRONT = 500;

    private Context mContext;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mWindowLayoutParams;
    private Display mDisplay;
    private DisplayMetrics mDisplayMetrics;
    private Matrix mDisplayMatrix;

    // private Bitmap mShotBitmap;
    private View mQuickshotLayout;
    private ImageView mBackgroundView;
    /*
     * private ImageView mQuickshotView; private ImageView mQuickshotFlash;
     */
    private SurfaceHolder holder = null;
    private SurfaceView mSurfaceView = null;
    private RotateableTextView mRotateTextView = null;

    // The value for android.hardware.Camera.Parameters.setRotation.
    private int mJpegRotation;
    private int mDeviceRotation;
    private byte[] mJpegData;
    private int mImageRotation;
    private int mImageWidth;
    private int mImageHeight;
    private Size mBestPictureSize;

    private Long mStartTime;
    private Runnable mFinisher = null;
    private String mStoragePath = null;

    private Camera mCamera = null;
    private Camera.Parameters mParameters = null;
    private boolean mCaptureDone = false;
    private int mCameraId = 0;
    //private MediaActionSound mCameraSound;

    private SensorManager mSensorManager;
    private AsyncTask<SaveImageInBackgroundData, Void, SaveImageInBackgroundData> mSaveInBgTask;

    private SurfaceTexture gSurfaceTexture;
    private int gSurfaceTextureId = 0;
    private boolean mSurfaceVisible = false;
    private SurfaceHolder mSurfaceHolder = null;
    private boolean mFirstSensorEvent = true;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case QUICKCAMERA_REMOVE_LAYOUT:
                if (mBackgroundView != null) {
                    mBackgroundView.setVisibility(View.GONE);
                }
                if (mWindowManager != null) {
                    mWindowManager.removeView(mQuickshotLayout);
                }
                if (mCamera != null) {
                    mCamera.release();
                    mCamera = null;
                }
                break;
            case QUICKCAMERA_DO_CAPTURE:
                doCapture(mSurfaceVisible);
                break;
            }
        }
    };

    /**
     * @param context everything needs a context
     */
    public void initializeViews(Context context) {
        LayoutInflater layoutInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Inflate the quickshot layout
        mDisplayMatrix = new Matrix();
        mQuickshotLayout = layoutInflater.inflate(R.layout.quickcamera_snap,
                null);
        mQuickshotLayout.setRotation(0);
        mBackgroundView = (ImageView) mQuickshotLayout
                .findViewById(R.id.quickshot_background);
        /*
         * mQuickshotView = (ImageView)
         * mQuickshotLayout.findViewById(R.id.quickshot); mQuickshotFlash =
         * (ImageView) mQuickshotLayout.findViewById(R.id.quickshot_flash);
         */
        mSurfaceView = (SurfaceView) mQuickshotLayout
                .findViewById(R.id.surface_view);
        mRotateTextView = (RotateableTextView) mQuickshotLayout
                .findViewById(R.id.time_cost);

        // Setup the window that we are going to use
        mWindowLayoutParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0,
                WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT);

        mWindowLayoutParams.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        mWindowManager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        mDisplay = mWindowManager.getDefaultDisplay();
        mDisplayMetrics = new DisplayMetrics();
        mDisplay.getRealMetrics(mDisplayMetrics);

        // Setup the Camera shutter sound
        //mCameraSound = new MediaActionSound();
        //mCameraSound.load(MediaActionSound.SHUTTER_CLICK);
    }

    /**
     * @param context everything needs a context.
     * @param visible whether the preview interface is visible.
     */
    public QuickCamera(Context context, boolean surfaceVisible) {
        mContext = context;
        mSurfaceVisible = surfaceVisible;

        mSensorManager = (SensorManager) context
                .getSystemService(Context.SENSOR_SERVICE);

        if (QuickCameraService.getMode()
                == QuickCameraService.MODE_CAPTURE_WITH_BACK_CAMERA) {
            mCameraId = 0;
        } else if (QuickCameraService.getMode()
                == QuickCameraService.MODE_CAPTURE_WITH_FRONT_CAMERA) {
            mCameraId = 1;
        }

        if (surfaceVisible) {
            initializeViews(context);
        } else {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            gSurfaceTextureId = textures[0];
            gSurfaceTexture = new SurfaceTexture(gSurfaceTextureId);
        }
    }

    private boolean checkSpaceAvailable() {
        boolean result = true;
        StorageUtil storageUtil = StorageUtil.newInstance();
        /* SPRD: add fix the bug 521329 quick capture picture won't save with the path
           settings which defined by camera2 app @{ */
        String theDeafultPath = QuickCameraService.getStoragePath();
        mStoragePath = storageUtil.getDefaultSaveDirectory(theDeafultPath);
        /* @} */
        Log.v(TAG, "mStoragePath = " + mStoragePath);
        return (storageUtil.getAvailableSpace(mStoragePath) > StorageUtil.LOW_STORAGE_THRESHOLD_BYTES);

    }

    /**
     * Creates a new worker thread and saves the quickshot to the media store.
     */
    private void saveQuickshotInWorkerThread() {
        SaveImageInBackgroundData data = new SaveImageInBackgroundData();
        data.context = mContext;
        data.jpegData = mJpegData;
        data.width = mImageWidth;
        data.height = mImageHeight;
        data.orientation = mImageRotation;
        data.path = mStoragePath;

        if (mSaveInBgTask != null) {
            mSaveInBgTask.cancel(false);
        }

        //fix coverity issue 108933
        if (data.jpegData == null) {
            android.util.Log.d(TAG, "data.jpegData=null");
            return;
        }

        mSaveInBgTask = new SaveImageInBackgroundTask(mContext, data)
                .execute(data);
    }

    /**
     * Takes a quickshot of the current display and shows an animation.
     */

    void takeQuickshot(Runnable finisher) {
        // We need to orient the quickshot correctly (and the Surface api seems
        // to take quickshot
        // only in the natural orientation of the device :!)
        mStartTime = System.currentTimeMillis();
        Log.d(TAG, "call takeQuickshot");
        if (mDisplay != null) {
            mDisplay.getRealMetrics(mDisplayMetrics);
        }
        mFinisher = finisher;

        Sensor gsensor = mSensorManager
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (gsensor != null) {
            mSensorManager.registerListener(this, gsensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }

        // move addView here for SurfaceView create.
        if (mWindowManager != null && mQuickshotLayout != null
                && mWindowLayoutParams != null && mSurfaceView != null) {
            mWindowManager.addView(mQuickshotLayout, mWindowLayoutParams);
            holder = mSurfaceView.getHolder();
            holder.addCallback(this);
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        startCamera();

        // we design to do capture in onSensorChanged(SensorEvent event), but sometimes
        // sensor cann't be activated when screen off though we have acquire wakelock,
        // so we make the logic below as substitution.
        // note capture logic is different when mSurfaceVisible is true.
        if (!mSurfaceVisible) {
            mHandler.sendEmptyMessageDelayed(QUICKCAMERA_DO_CAPTURE, 1000);
        }
    }

    private synchronized void doCapture(boolean surfaceVisible) {

        Log.i(TAG, "doCapture start");
        if (mCamera == null || mCaptureDone) return;

        try {
            if (surfaceVisible) {
                mCamera.setPreviewDisplay(mSurfaceHolder);
            } else {
                mCamera.setPreviewTexture(gSurfaceTexture);
            }
        } catch (Exception e) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            return;
        }
        Log.v(TAG, "mDeviceRotation = " + mDeviceRotation);

        setRotation(mParameters, mDeviceRotation);

        mCamera.setParameters(mParameters);
        if (mRotateTextView != null) {
            mRotateTextView.setTextViewRotation(mDeviceRotation);
        }
        mHandler.postDelayed(new Runnable() {
            public void run() {
                Log.i(TAG, "takePicture");
                mCamera.takePicture(null, new RawPictureCallback(), new JpegPictureCallback());
            }
        }, mCameraId == 0 ? QUICKCAMERA_TAKE_PICTURE_DELAY_BACK : QUICKCAMERA_TAKE_PICTURE_DELAY_FRONT);

        mCaptureDone = true;
        Log.i(TAG, "doCapture end");
    }

    private void setRotation(Camera.Parameters parameters, int orientation) {
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;

        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(mCameraId, info);
        orientation = (orientation + 45) / 90 * 90;
        int rotation = 0;
        if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (info.orientation - orientation + 360) % 360;
        } else {  // back-facing camera
            rotation = (info.orientation + orientation) % 360;
        }

        mJpegRotation = rotation;

        Log.v(TAG, "rotation " + rotation + " info.orientation " + info.orientation);
        parameters.setRotation(rotation);
    }

    private void startCamera() {
        try {
            Log.d(TAG, "camera open");
            mCamera = Camera.open(mCameraId);
        } catch (RuntimeException e) {
            Log.e(TAG, "fail to open camera");
            e.printStackTrace();
            mFinisher.run();
            return;
        }
        if (mCamera != null) {
            Log.i(TAG, "startPreview");
            mCamera.startPreview();
            mParameters = mCamera.getParameters();
            mParameters.setPictureFormat(PixelFormat.JPEG);
            mBestPictureSize = mParameters.getSupportedPictureSizes().get(0);
            mParameters.setPictureSize(mBestPictureSize.width,
                    mBestPictureSize.height);
            mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            mParameters.set("capture-mode", "1");
            mParameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
            mParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            mParameters.setExposureCompensation(0);
            mParameters.setJpegQuality(100);
            /* SPRD: Fix bug 474860, Add for Dark photos of speedcapture. @{ */
            if (mParameters.isAutoExposureLockSupported()) {
                mParameters.setAutoExposureLock(true);
            }
            /* @} */
            //mParameters.set("zsl", 1);

            mCamera.setDisplayOrientation(90);

            // mCamera.setParameters(parameters);
        }
    }

    private final class RawPictureCallback implements PictureCallback {
        @Override
        public void onPictureTaken(byte[] rawData, Camera camera) {
            long costTime = System.currentTimeMillis() - mStartTime;
            Log.d(TAG, "RawPictureCallback cost : " + costTime + "ms.");
            if (mRotateTextView != null) {
                mRotateTextView.setText(mContext.getString(R.string.quick_snap_time_cost, firmatSnapCostTime(costTime)));
            }
            //mCameraSound.play(MediaActionSound.SHUTTER_CLICK);
            Vibrator vibrator = (Vibrator)mContext.getSystemService(Context.VIBRATOR_SERVICE);
            long [] pattern = {0, 50};
            vibrator.vibrate(pattern, -1);
            mFinisher.run(); // SPRD: Fix bug 565248
        }
    }

    private String firmatSnapCostTime(Long costTime) {
        DecimalFormat df = new DecimalFormat("0.0");
        return df.format((double)costTime / 1000);
    }

    private final class JpegPictureCallback implements PictureCallback {

        @Override
        public void onPictureTaken(byte[] jpegData, Camera camera) {

            long costTime = System.currentTimeMillis() - mStartTime;
            Log.d(TAG, "JpegPictureCallback cost : " + costTime + "ms.");

            mHandler.sendEmptyMessage(QUICKCAMERA_REMOVE_LAYOUT);

            mJpegData = jpegData;

            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }

            /*Sensor gsensor = mSensorManager
                    .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (gsensor != null) {
                mSensorManager.unregisterListener(QuickCamera.this, gsensor);
            }*/

            if (mJpegData == null) {
                Log.d(TAG, "quickcamera snap error! mJpegData == null");
                return;
            }

            if (!checkSpaceAvailable()) {
                //startCameraActivity(mContext);
                return;
            }

            ExifInterface exif = Exif.getExif(jpegData);
            mImageRotation = Exif.getOrientation(exif);
            // int width, height;
            if ((mJpegRotation + mImageRotation) % 180 == 0) {
                mImageWidth = mBestPictureSize.width;
                mImageHeight = mBestPictureSize.height;
            } else {
                mImageWidth = mBestPictureSize.height;
                mImageHeight = mBestPictureSize.width;
            }

            saveQuickshotInWorkerThread();

            // Start the post-quickshot animation
            // startAnimation(mFinisher, mDisplayMetrics.widthPixels,
            // mDisplayMetrics.heightPixels,
            // false, false);//statusBarVisible, navBarVisible

        }
    }

    public void surfaceChanged(SurfaceHolder sholder, int format, int width,
            int height) {
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "surfaceCreated");
        mSurfaceHolder = holder;
        doCapture(true /* surfaceVisible */);
    }

    public void surfaceDestroyed(SurfaceHolder sholder) {
        mSurfaceHolder = null;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values != null) {
            float x = event.values[0];
            float y = event.values[1];
            if (Math.abs(x) < 1) {
                x = 0;
            }
            if (Math.abs(y) < 1) {
                y = 0;
            }
            if (Math.abs(x) <= Math.abs(y)) {
                if (y < 0) {
                    // up is low
                    Log.v(TAG, "up now");
                    mDeviceRotation = 180;
                } else if (y > 0) {
                    // down is low
                    Log.v(TAG, "down now");
                    mDeviceRotation = 0;
                } else if (y == 0) {
                    // do nothing
                }
            } else {
                if (x < 0) {
                    // right is low
                    Log.v(TAG, "right now");
                    mDeviceRotation = 90;
                } else {
                    // left is low
                    Log.v(TAG, "left now");
                    mDeviceRotation = 270;
                }
            }
        }

        // first sensor event is not accurate
        if (!mFirstSensorEvent) {
            Sensor gsensor = mSensorManager
                    .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (gsensor != null) {
                mSensorManager.unregisterListener(QuickCamera.this, gsensor);
            }

            // generally, startCamera() completes before here.
            if (!mSurfaceVisible) {
                Log.d(TAG, "doCapture from onSensorChanged()");
                doCapture(false);
            }
        }

        mFirstSensorEvent = false;
    }

    /**
     * POD used in the AsyncTask which saves an image in the background.
     */
    class SaveImageInBackgroundData {
        Context context;
        Uri imageUri;
        int result;
        byte[] jpegData;
        int width;
        int height;
        int orientation;
        String path;

        void clearImage() {
            imageUri = null;
            jpegData = null;
        }

        void clearContext() {
            context = null;
        }
    }

    /**
     * An AsyncTask that saves an image to the media store in the background.
     */
    class SaveImageInBackgroundTask extends
            AsyncTask<SaveImageInBackgroundData, Void, SaveImageInBackgroundData> {
        private static final String TAG = "SaveImageInBackgroundTask";

        private static final String QUICKSHOT_FILE_NAME_TEMPLATE = "%s.jpg";

        private final File mQuickshotDir;
        private final String mImageFileName;
        private final String mImageFilePath;
        private final long mSnapTime;
        private final int mImageWidth;
        private final int mImageHeight;
        private final int mOrientation;

        SaveImageInBackgroundTask(Context context, SaveImageInBackgroundData data) {
            // Prepare all the output metadata
            mSnapTime = System.currentTimeMillis();
            String imageDate = new SimpleDateFormat("'IMG'_yyyyMMdd_HHmmss")
                    .format(new Date(mSnapTime));
            mImageFileName = String.format(QUICKSHOT_FILE_NAME_TEMPLATE, imageDate);

            mQuickshotDir = new File(data.path);
            mImageFilePath = new File(mQuickshotDir, mImageFileName)
                    .getAbsolutePath();

            mImageWidth = data.width;
            mImageHeight = data.height;
            mOrientation = data.orientation;

        }

        @Override
        protected SaveImageInBackgroundData doInBackground(
                SaveImageInBackgroundData... params) {
            if (params.length != 1)
                return null;
            if (isCancelled()) {
                params[0].clearImage();
                params[0].clearContext();
                return null;
            }

            // By default, AsyncTask sets the worker thread to have background
            // thread priority, so bump
            // it back up so that we save a little quicker.
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);

            Context context = params[0].context;
            byte[] jpegdata = params[0].jpegData;

            try {
                // Create quickshot directory if it doesn't exist
                mQuickshotDir.mkdirs();

                // media provider uses seconds for DATE_MODIFIED and DATE_ADDED, but
                // milliseconds
                // for DATE_TAKEN
                long dateSeconds = mSnapTime / 1000;

                // Save the quickshot to the MediaStore
                ContentValues values = new ContentValues();
                ContentResolver resolver = context.getContentResolver();
                values.put(MediaStore.Images.ImageColumns.DATA, mImageFilePath);
                values.put(MediaStore.Images.ImageColumns.TITLE, mImageFileName);
                values.put(MediaStore.Images.ImageColumns.DISPLAY_NAME,
                        mImageFileName);
                values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, mSnapTime);
                values.put(MediaStore.Images.ImageColumns.DATE_ADDED, dateSeconds);
                values.put(MediaStore.Images.ImageColumns.DATE_MODIFIED,
                        dateSeconds);
                values.put(MediaStore.Images.ImageColumns.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.ImageColumns.WIDTH, mImageWidth);
                values.put(MediaStore.Images.ImageColumns.HEIGHT, mImageHeight);
                values.put(MediaStore.Images.ImageColumns.ORIENTATION, mOrientation);
                Uri uri = resolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                //startCameraActivity(context);

                writeFile(mImageFilePath, jpegdata);

                // update file size in the database
                values.clear();
                values.put(MediaStore.Images.ImageColumns.SIZE, new File(
                        mImageFilePath).length());
                resolver.update(uri, values, null, null);

                params[0].imageUri = uri;
                params[0].result = 0;
            } catch (Exception e) {
                // IOException/UnsupportedOperationException may be thrown if
                // external storage is not
                // mounted
                Log.d(TAG, "save-Exception:" + e.toString());
                params[0].clearImage();
                params[0].result = 1;
            }

            return params[0];
        }

        @Override
        protected void onPostExecute(SaveImageInBackgroundData params) {
            if (isCancelled()) {
                params.clearImage();
                params.clearContext();
                return;
            }

            if (params.result > 0) {
                // Show a message that we've failed to save the image to disk
                Log.d(TAG, "quickcamera shot error : params.result ="
                        + params.result);
            } else {
                // Show the final notification to indicate quickshot saved

                // Create the intent to show the quickshot in gallery
            }
            params.clearContext();
        }

        public void writeFile(String path, byte[] data) {
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(path);
                out.write(data);
            } catch (Exception e) {
                Log.e(TAG, "Failed to write data", e);
            } finally {
                try {
                    out.close();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to close file after write", e);
                }
            }
        }

    }

    private ComponentName getTopActivity(Context context) {
        ActivityManager manager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningTaskInfo> runningTaskInfos = manager.getRunningTasks(1);

        if (runningTaskInfos != null) {
            return runningTaskInfos.get(0).topActivity;
        } else {
            return null;
        }
    }

    private void startCameraActivity(Context context) {
        ComponentName topActivity = getTopActivity(context);
        if ((null != topActivity
                && !topActivity.getPackageName().equalsIgnoreCase("com.android.camera2") && !topActivity
                .getPackageName().equalsIgnoreCase("com.huawei.camera"))
                || null == topActivity) {
            Intent intent = new Intent("android.media.action.STILL_IMAGE_CAMERA");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
}
