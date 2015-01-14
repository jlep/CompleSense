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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.util.SensorUtil;

/**
 * Created by hxguo on 24.11.2014.
 */
public class TakePhotoActivity extends Activity implements SensorEventListener
{
    private final static String TAG = TakePhotoActivity.class.getSimpleName();

    public static final String IMAGE_NUM = "image_nums";
    public static final String IMAGE_ORIENTATION_FILE = "image_orientation_file";
    public static final String IMAGE_NAME = "image_name";
    public static final String IMAGE_ORIENTATION_VALS = "image_orientation_values";

    private Camera mCamera;
    private Button mPhotoButton, mFinishButton;
    private CameraPreview mPreview;
    private int imgCount;
    private File localDir;
    private SensorManager mSensorManager;
    private Sensor mRotation;
    private float[] sensorVals = new float[3];
    //private Map<String, float[]> orientations = new HashMap<String, float[]>();
    private List<JSONObject> orientations = new ArrayList<JSONObject>();
    private File orientationsFile;
    private ExecutorService threadPool = Executors.newFixedThreadPool(4);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_preview);

        localDir = new File(Constants.ROOT_DIR, Constants.LOCAL_SENSOR_DATA_DIR);
        orientationsFile = new File(localDir, SensorUtil.SENSOR_CAMERA + ".txt");

        mPhotoButton = (Button)findViewById(R.id.front_take_photos);
        mPhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPreview.mCamera.takePicture(shutterCallback, rawCallback,
                        jpegCallback);
                mPhotoButton.setEnabled(false);
            }
        });

        mFinishButton = (Button)findViewById(R.id.go_back);
        mFinishButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = makeResultIntent();
                setResult(Activity.RESULT_OK, intent);
                writeOrientations();
                finish();
            }
        });

        mPreview = new CameraPreview(this);
        ((FrameLayout) findViewById(R.id.camera_preview)).addView(mPreview);

        if(safeCameraOpen(Camera.CameraInfo.CAMERA_FACING_BACK)){
            //camera = openBackFacingCamera();
            mCamera.setDisplayOrientation(90);
            mPreview.setCamera(mCamera);
        }else{
            finish();
        }

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mRotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    private boolean safeCameraOpen(int id) {
        boolean qOpened = false;

        try {
            releaseCameraAndPreview();
            mCamera = Camera.open(id);
            qOpened = (mCamera != null);
        } catch (Exception e) {
            Log.e(getString(R.string.app_name), "failed to open Camera");
            e.printStackTrace();
        }

        return qOpened;
    }

    private void releaseCameraAndPreview() {
        mPreview.setCamera(null);
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mRotation, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        mSensorManager.unregisterListener(this);
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
        public void onPictureTaken(byte[] data, Camera camera)
        {
            imgCount++;
            String fname = String.format("%d.jpg", System.currentTimeMillis());
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put(IMAGE_NAME, fname);
                JSONArray jsonArray = new JSONArray();
                for(float f: sensorVals)
                    jsonArray.put(f);
                jsonObject.put(IMAGE_ORIENTATION_VALS, jsonArray);
                orientations.add(jsonObject);

                threadPool.submit(new FileWritingCallable(data, fname));

                camera.startPreview();
                if (imgCount < Constants.NUM_IMG_TAKE) {
                    mPreview.mCamera.takePicture(shutterCallback, rawCallback,
                            jpegCallback);
                } else {
                    imgCount = 0;

                    Intent intent = makeResultIntent();
                    setResult(Activity.RESULT_OK, intent);
                    writeOrientations();
                    finish();
                }
            } catch (JSONException e) {

            }catch (Exception e) {
                Log.d(TAG, "Error starting preview: " + e.toString());
            }


        }
    };

    private Intent makeResultIntent() {
        Intent intent = new Intent();
        intent.putExtra(IMAGE_ORIENTATION_FILE, orientationsFile.toString());
        intent.putExtra(IMAGE_NUM, orientations.size());
        return intent;
    }

    private void writeOrientations() {
        if(orientationsFile != null){
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(orientationsFile,true);
                Iterator<JSONObject> iter = orientations.iterator();
                while(iter.hasNext()){
                    StringBuilder sb = new StringBuilder();
                    sb.append(iter.next().toString());
                    sb.append("\n");
                    fos.write(sb.toString().getBytes());
                }
            } catch (FileNotFoundException e) {
                Log.i(TAG,e.toString());
            } catch (IOException e) {
                Log.i(TAG, e.toString());
            }finally {
                if(fos != null){
                    try {
                        fos.close();
                    } catch (IOException e) {
                    }
                }
            }

        }
    }

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
        FileOutputStream imgOutStream = null;

        File imgFile;
        byte[] data;

        public FileWritingCallable(byte[] data, String fname){
            this.data = data;
            imgFile = new File(localDir, fname);
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
