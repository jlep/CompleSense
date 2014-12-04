package fi.hiit.complesense.connection;

import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.ui.TakePhotoActivity;

/**
 * Created by hxguo on 4.12.2014.
 */
public class ImageSender
{
    private static final String TAG = ImageSender.class.getSimpleName();

    private final URI mUri;
    private final File mLocalDir;

    public ImageSender(InetAddress ownerInetAddr, int streamPort){
        mUri = URI.create(Constants.WEB_PROTOCOL +"://"+ ownerInetAddr.getHostAddress()+":"+ streamPort + "/streaming_images");
        mLocalDir = new File(Constants.ROOT_DIR, Constants.LOCAL_SENSOR_DATA_DIR);
    }

    private void sendFile(String imageName, WebSocket webSocket) throws IOException, JSONException {
        File f = new File(mLocalDir, imageName);
        FileInputStream fis = new FileInputStream(f);

        int bufferSize = 1024, bytesAvailable = 0;
        byte[] buffer = new byte[bufferSize];

        int bytesRead = fis.read(buffer, 0, bufferSize);
        long byteSend = 0;


        while (bytesRead > 0) {
            byteSend += bytesRead;
            webSocket.send(buffer);
            bytesAvailable = fis.available();
            bufferSize = Math.min(bytesAvailable, bufferSize);
            bytesRead = fis.read(buffer, 0, bufferSize);
        }

        sendSendComplete(imageName, webSocket);
    }

    private void sendSendComplete(String imageName, WebSocket webSocket) throws JSONException {
        JSONObject jsonSend = new JSONObject();
        jsonSend.put(JsonSSI.IMAGE_COMMAND, JsonSSI.COMPLETE_SEND_IMG);
        jsonSend.put(JsonSSI.IMAGE_NAME, imageName);
        webSocket.send(jsonSend.toString());
    }

    public void sendImage(JSONObject jsonObject) throws JSONException
    {
        final JSONObject imageJson = new JSONObject(jsonObject.toString());

        AsyncHttpClient.getDefaultInstance().websocket(mUri.toString(),
                Constants.WEB_PROTOCOL, new AsyncHttpClient.WebSocketConnectCallback() {

                    @Override
                    public void onCompleted(Exception e, final WebSocket webSocket) {
                        Log.i(TAG, "onCompleted(" + mUri.toString() + ")");
                        if (e != null) {
                            Log.e(TAG, e.toString());
                            return;
                        }
                        String str = "Connection with " + mUri.toString() + " is established";
                        Log.i(TAG, str);

                        webSocket.setStringCallback(new WebSocket.StringCallback() {
                            @Override
                            public void onStringAvailable(String s) {
                                try {
                                    JSONObject jsonObject = new JSONObject(s);
                                    String cmd = jsonObject.getString(JsonSSI.IMAGE_COMMAND);
                                    if(cmd.equals(JsonSSI.OK_TO_SEND)){
                                        String imageName = jsonObject.getString(JsonSSI.IMAGE_NAME);
                                        sendFile(imageName, webSocket);
                                    }
                                } catch (JSONException e) {
                                    Log.i(TAG, e.toString());
                                } catch (IOException e1) {
                                    Log.e(TAG,e1.toString());
                                }
                            }
                        });
                        webSocket.setDataCallback(new DataCallback() {
                            @Override
                            public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                                Log.i(TAG, "Streaming connect should not recv binary data: "
                                        + byteBufferList.getAll().array().length + " bytes");
                            }
                        });
                        webSocket.setClosedCallback(new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception e) {
                                if (e != null){
                                    Log.e(TAG, e.toString());
                                }
                            }
                        });

                        try {
                            sendImageNameJson(webSocket, imageJson);
                        } catch (JSONException e1) {
                            Log.i(TAG, e.toString());
                        }

                    }
                });
    }

    private void sendImageNameJson(WebSocket webSocket, JSONObject jsonObject) throws JSONException {
        JSONObject jsonImageName = new JSONObject();
        jsonImageName.put(JsonSSI.IMAGE_COMMAND, JsonSSI.START_SEND_IMG);
        jsonImageName.put(JsonSSI.IMAGE_NAME, jsonObject.getString(TakePhotoActivity.IMAGE_NAME));
        jsonImageName.put(JsonSSI.IMAGE_ORIENTATIONS, jsonObject.getJSONArray(TakePhotoActivity.IMAGE_ORIENTATION_VALS));
        webSocket.send(jsonImageName.toString());
    }
}
