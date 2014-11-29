package fi.hiit.complesense.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;

/**
 * Created by hxguo on 24.11.2014.
 */
public class TakePhotoActivity extends Activity implements SensorEventListener
{
    private final static String TAG = TakePhotoActivity.class.getSimpleName();
    public static final String IMAGE_NAMES = "image_names";
    private Camera mCamera;
    private int mCameraId = 0;
    private Button mPhotoButton, mFinishButton;
    private CameraPreview preview;
    private int imgCount;
    private File localDir;
    private ArrayList<String> imageNames = new ArrayList<String>();

    private SensorManager mSensorManager;
    private Sensor mRotation;
    private float[] sensorVals = new float[3];
    private File txtSensorValsFile;
    private ExecutorService threadPool = Executors.newFixedThreadPool(4);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_preview);
        preview = new CameraPreview(this);
        ((FrameLayout) findViewById(R.id.camera_preview)).addView(preview);

        mPhotoButton = (Button)findViewById(R.id.front_take_photos);
        mPhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                preview.mCamera.takePicture(shutterCallback, rawCallback,
                        jpegCallback);
                mPhotoButton.setEnabled(false);
            }
        });

        mFinishButton = (Button)findViewById(R.id.go_back);
        mFinishButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.putStringArrayListExtra(IMAGE_NAMES, imageNames);
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
        });

        localDir = new File(Constants.ROOT_DIR, Constants.LOCAL_SENSOR_DATA_DIR);
        txtSensorValsFile = new File(localDir, "orientations.txt");

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mRotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    /** Check if this device has a camera */
    private boolean checkCameraHardware() {
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            mCameraId = findBackFacingCamera();
            if (mCameraId < 0) {
                Toast.makeText(this, "No front facing camera found.",
                        Toast.LENGTH_LONG).show();
            } else {
                mCamera = Camera.open(mCameraId);
            }
            return true;
        } else {
            // no camera on this device
            Toast.makeText(this, "No camera on this device", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private int findBackFacingCamera() {
        int cameraId = -1;
        // Search for the front facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            CameraInfo info = new CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
                Log.d(TAG, "Camera found");
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    @Override
    protected void onResume() {
        super.onResume();
        //mSensorManager.registerListener(this, mRotation, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        //mSensorManager.unregisterListener(this);
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
        super.onPause();
    }

    Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        public void onShutter() {
            Log.d(TAG, "onShutter'd");
        }
    };

    Camera.PictureCallback rawCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.i(TAG, "onPictureTaken - raw with data = " + ((data != null) ? data.length : " NULL"));
        }
    };


    Camera.PictureCallback jpegCallback = new Camera.PictureCallback()
    {
        public void onPictureTaken(byte[] data, Camera camera) {
            FileOutputStream imgOutStream = null, txtOutputStream = null;
            String fname = String.format("%d.jpg", System.currentTimeMillis());
            File imgFile = new File(localDir, fname);

            imageNames.add(fname);
            imgCount++;
            threadPool.submit(new FileWritingCallable(data));
            /*
            try {
                // write to local sandbox file system
                imageNames.add(fname);
                imgCount++;
                threadPool.submit(new FileWritingCallable(data));

                //txtOutputStream = new FileOutputStream(txtSensorValsFile, true);
                //txtOutputStream.write(sensorVals.toString().getBytes());
                //txtOutputStream.close();



                Log.i(TAG, "onPictureTaken - jpeg, wrote bytes: " + data.length);
                Toast.makeText(getApplicationContext(), "take photo No: " + imgCount, Toast.LENGTH_SHORT).show();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
            }
            */

            try {
                //camera.startPreview();
                if (imgCount < Constants.NUM_IMG_TAKE) {
                    preview.mCamera.takePicture(shutterCallback, rawCallback,
                            jpegCallback);
                } else {
                    imgCount = 0;

                    //Log.i(TAG, "imageNames: " + imageNames);
                    Intent intent = new Intent();
                    intent.putStringArrayListExtra(IMAGE_NAMES, imageNames);
                    setResult(Activity.RESULT_OK, intent);
                    finish();
                }
            } catch (Exception e) {
                Log.d(TAG, "Error starting preview: " + e.toString());
            }
        }
    };

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        sensorVals[0] = sensorEvent.values[0];
        sensorVals[1] = sensorEvent.values[1];
        sensorVals[2] = sensorEvent.values[2];
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    class FileWritingCallable implements Callable<String> {
        FileOutputStream imgOutStream = null, txtOutputStream = null;
        String fname = String.format("%d.jpg", System.currentTimeMillis());
        File imgFile = new File(localDir, fname);
        byte[] data;

        public FileWritingCallable(byte[] data){
            this.data = data;
        }

        @Override
        public String call() throws Exception {
            imgOutStream = new FileOutputStream(imgFile);
            imgOutStream.write(data);
            imgOutStream.close();
            Log.i(TAG, "onPictureTaken - jpeg, wrote bytes: " + data.length);
            return imgOutStream.toString();
        }
    }
}
