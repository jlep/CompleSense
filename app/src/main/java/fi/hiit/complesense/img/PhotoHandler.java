package fi.hiit.complesense.img;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import fi.hiit.complesense.Constants;

/**
 * Created by hxguo on 24.11.2014.
 */
public class PhotoHandler implements Camera.PictureCallback
{
    private static final String TAG = PhotoHandler.class.getSimpleName();
    private final Context context;

    public PhotoHandler(Context context) {
        this.context = context;
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera)
    {
        File localDir = new File(Constants.ROOT_DIR, Constants.LOCAL_SENSOR_DATA_DIR);
        File localFile = new File(localDir, Long.toString(System.currentTimeMillis())+ ".jpg");

        try {
            FileOutputStream fos = new FileOutputStream(localFile);
            fos.write(data);
            fos.close();
            Toast.makeText(context, "New Image saved:" + localFile.toString(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.d(TAG, "File" + localFile.toString() + "not saved: " + e.toString());
            Toast.makeText(context, "Image could not be saved.", Toast.LENGTH_LONG).show();
        }
    }
}
