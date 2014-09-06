package fi.hiit.complesense.connection;

import android.os.Environment;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 9/5/14.
 */
public class HttpConnectionRunnable implements Runnable
{
    private static final String TAG = "HttpConnectionRunnable";
    private final ServiceHandler serviceHandler;
    final HttpURLConnection conn;
    final FileInputStream fileInputStream;
    DataOutputStream dos;
    DataInputStream inStream = null;
    String existingFileName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/romance.wav";

    String lineEnd = "\r\n";
    String twoHyphens = "--";
    String boundary = "*****";
    int bytesRead, bytesAvailable, bufferSize;
    byte[] buffer;
    int maxBufferSize = 1 * 1024 * 1024;
    String responseFromServer = "";


    public HttpConnectionRunnable(ServiceHandler serviceHandler) throws IOException {
        this.serviceHandler = serviceHandler;

        //------------------ CLIENT REQUEST
        fileInputStream = new FileInputStream(new File(existingFileName));
        // open a URL connection to the Servlet
        URL url = new URL(Constants.URL_UPLOAD);
        // Open a HTTP connection to the URL
        conn = (HttpURLConnection) url.openConnection();
        // Allow Inputs
        conn.setDoInput(true);
        // Allow Outputs
        conn.setDoOutput(true);
        // Don't use a cached copy.
        conn.setUseCaches(false);
        // Use a post method.
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
        dos = new DataOutputStream(conn.getOutputStream());
    }

    private void doFileUpload()
    {
        try
        {
            serviceHandler.updateStatusTxt("doUploadFile()");
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; " +
                    "name=\"uploadedfile\";filename=\"" + existingFileName + "\"" + lineEnd);
            dos.writeBytes(lineEnd);
            // create a buffer of maximum size
            bytesAvailable = fileInputStream.available();
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            buffer = new byte[bufferSize];
            // read file and write it into form...
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);

            while (bytesRead > 0) {

                dos.write(buffer, 0, bufferSize);
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);

            }

            // send multipart form data necesssary after file data...
            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            // close streams
            Log.e("Debug", "File is written");
            fileInputStream.close();
            dos.flush();
            dos.close();

        } catch (MalformedURLException e) {
            Log.i(TAG, e.toString());
        } catch (FileNotFoundException e) {
            Log.i(TAG, e.toString());
        } catch (ProtocolException e) {
            Log.i(TAG, e.toString());
        } catch (IOException e) {
            Log.i(TAG, e.toString());
        }

        //------------------ read the SERVER RESPONSE
        try {

            inStream = new DataInputStream(conn.getInputStream());
            String str;

            while ((str = inStream.readLine()) != null) {

                Log.e("Debug", "Server Response " + str);

            }

            inStream.close();

        } catch (IOException ioex) {
            Log.e("Debug", "error: " + ioex.getMessage(), ioex);
        }
    }

    @Override
    public void run() {
        Log.i(TAG,"run();");
        doFileUpload();
    }

    public void stopRunnable()
    {
        try {
            if(inStream!=null)
                inStream.close();
            if(dos!=null)
                dos.close();
            if(conn!=null)
                conn.disconnect();
        } catch (IOException e) {
            Log.i(TAG,e.toString());
        }

    }

}
