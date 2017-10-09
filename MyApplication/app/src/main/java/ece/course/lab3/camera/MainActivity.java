package ece.course.lab3.camera;

import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.*;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import ece.course.android.module.NetworkConnectionServer;


public class MainActivity extends AppCompatActivity {
    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private CameraPreviewView mCameraPreviewView;
    private Camera mCamera;
    private int mCameraId;
    private MediaRecorder mMediaRecorder;
    private Boolean isRecording=false;
    private String TAG_DEBUG="MainActivity";
    private static final int  APPS_PORT= 1234;
    private static final byte MSG_PICTURE_CAPTURE = (byte)'P';
    private static final byte MSG_VIDEO_RECORDING_START = (byte)'V';
    private static final byte MSG_VIDEO_RECORDING_STOP = (byte)'S';
    private WifiManager mWifiManager;
    private NetworkConnectionServer mServer;
    private TextView tvState;
    private TextView tvIp;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            Toast.makeText(this, "No Camara On The Phone Leaving...",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
        int numberOfCameras = Camera.getNumberOfCameras();
        boolean hvBackCamera = false;
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
                mCameraId = i;
                hvBackCamera = true;
                break;
            }
        }
        if (!hvBackCamera) {
            Toast.makeText(this, "The Apps only support the back Camara, Leaving...",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                getClass().getName());
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (mWifiManager == null) {
            Toast.makeText(this, "There is no wifi Module in the phone, leave...",
                    Toast.LENGTH_LONG).show();
            finish();
        }
        int ipInteger = mWifiManager.getConnectionInfo().getIpAddress();
        mCameraPreviewView = (CameraPreviewView) findViewById(R.id.mCameraPreviewView);
        tvState = (TextView) findViewById(R.id.tvState);
        tvState.setText("Not Connected");
        tvIp = (TextView) findViewById(R.id.tvIp);
        tvIp.setText(getIpString(ipInteger));
    }

    protected synchronized void onResume() {
        super.onResume();
        mWakeLock.acquire();
        mCamera = Camera.open(mCameraId);
        mCameraPreviewView.setCamera(mCamera);
        mServer = new NetworkConnectionServer(APPS_PORT, new Handler() {
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case NetworkConnectionServer.MSG_HV_DATA :
                        byte[] data = (byte[]) msg.obj;
                        int length = msg.arg1;
                        for (int i = 0; i < length; i++) {
                            byte[] msgData = new byte[1];
                            switch(data[i]) {
                                case MSG_PICTURE_CAPTURE :
                                    takePicture();
                                    Toast.makeText(MainActivity.this,
                                            "Picture Captured!!",
                                            Toast.LENGTH_SHORT).show();
                                    msgData[0] = MSG_PICTURE_CAPTURE;
                                    mServer.sendData(msgData);
                                    break;
                                case MSG_VIDEO_RECORDING_START :
                                    recordVideo();
                                    Toast.makeText(MainActivity.this,
                                            "Video Recording Started!!",
                                            Toast.LENGTH_SHORT).show();
                                    msgData[0] = MSG_VIDEO_RECORDING_START;
                                    mServer.sendData(msgData);
                                    break;
                                case MSG_VIDEO_RECORDING_STOP :
                                    stopRecord();
                                    Toast.makeText(MainActivity.this,
                                            "Video Recording Stopped!!",
                                            Toast.LENGTH_SHORT).show();
                                    msgData[0] = MSG_VIDEO_RECORDING_STOP;
                                    mServer.sendData(msgData);
                                    break;
                            }
                        }
                        break;
                    case NetworkConnectionServer.MSG_CONNECTED :
                        tvState.setText("Connected");
                        break;
                    case NetworkConnectionServer.MSG_CONNECTION_LOST :
                        tvState.setText("Connection Lost");
                        break;
                }
            }
        });
        mServer.start();
    }

    protected synchronized void onPause() {
        mWakeLock.release();
        if (mCamera != null) {
            mCameraPreviewView.setCamera(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        mServer.stop();
        tvState.setText("Not Connected");
        super.onPause();
    }
    private String getIpString(int ipInteger) {
        return (ipInteger & 0xFF) + "." + ((ipInteger >> 8) & 0xFF) + "."
                + ((ipInteger >> 16) & 0xFF) + "." + ((ipInteger >> 24) & 0xFF);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.menuTakePicture :
                takePicture();
                return true;
            case R.id.menuRecordVideo :
                recordVideo();
                return true;
            case R.id.menuStopRecord:
                stopRecord();
                return true;
        }
        return false;
    }
    private PictureCallback mPictureCallBack = new PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            try {
                File pictureFile = getOutputFile(true);
                FileOutputStream fileOutputStream = new FileOutputStream(pictureFile);
                fileOutputStream.write(data);
                fileOutputStream.close();
            } catch (Exception exception) { }
            mCamera.startPreview();
        }
    };
    private void takePicture() {
        mCamera.takePicture(null, null, mPictureCallBack);
    }
    private void recordVideo() {
        if (isRecording)
            return;
        if (prepareVideoRecorder()) {

            mMediaRecorder.start();
            isRecording = true;
        } else {
            releaseMediaRecorder();
        }
    }
    private void stopRecord() {
        if (!isRecording)
            return;
        try {
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.setOnInfoListener(null);
            mMediaRecorder.setPreviewDisplay(null);
            mMediaRecorder.stop();
        } catch (IllegalStateException e) {
            // TODO: handle exception
            Log.i("Exception", Log.getStackTraceString(e));
        }catch (RuntimeException e) {
            // TODO: handle exception
            Log.i("Exception", Log.getStackTraceString(e));
        }catch (Exception e) {
            // TODO: handle exception
            Log.i("Exception", Log.getStackTraceString(e));
        }
        releaseMediaRecorder();
        mCamera.lock();
        mCamera.stopPreview();
        mCamera.startPreview();
        isRecording = false;
    }
    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            mCamera.lock();
        }
    }
    private boolean prepareVideoRecorder() {
        mMediaRecorder = new MediaRecorder();

        mCamera.unlock();
        CamcorderProfile mProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setCamera(mCamera);

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
        File mediaFile = getOutputFile(false);
        mMediaRecorder.setOutputFile(mediaFile.toString());
        mMediaRecorder.setVideoSize(mProfile.videoFrameWidth, mProfile.videoFrameHeight);
        mMediaRecorder.setPreviewDisplay(mCameraPreviewView.getHolder().getSurface());
        try {
            mMediaRecorder.prepare();
            return true;
        } catch (Exception exception) { }
        releaseMediaRecorder();
        return false;
    }
    private File getOutputFile(boolean isPicture) {
        File storageDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "USTECE_Lab_3_2");
        if (!storageDir.exists()) {
            if (!storageDir.mkdirs()) {
                Log.d(TAG_DEBUG, "failed to create directory");
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        if (isPicture) {
            return new File(storageDir.getPath() + File.separator
                    + "IMG_"+ timeStamp + ".jpg");
        } else {
            return new File(storageDir.getPath() + File.separator
                    + "VID_"+ timeStamp + ".mp4");
        }
    }
}
