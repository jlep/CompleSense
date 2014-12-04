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

/**
 * Created by hxguo on 4.12.2014.
 */
public class ImageSender
{
    private static final String TAG = ImageSender.class.getSimpleName();

    private final URI mUri;

    public ImageSender(InetAddress ownerInetAddr, int streamPort){
        mUri = URI.create(Constants.WEB_PROTOCOL +"://"+ ownerInetAddr.getHostAddress()+":"+ streamPort + "/streaming_images");
    }

    private void sendFile(String absPath, WebSocket webSocket) throws IOException, JSONException {
        File f = new File(absPath);
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

        sendSendComplete(absPath, webSocket);
    }

    private void sendSendComplete(String absPath, WebSocket webSocket) throws JSONException {
        JSONObject jsonSend = new JSONObject();
        jsonSend.put(JsonSSI.IMAGE_COMMAND, JsonSSI.COMPLETE_SEND_IMG);
        jsonSend.put(JsonSSI.IMAGE_PATH, absPath);
        webSocket.send(jsonSend.toString());
    }

    public void sendImage(final File imgFile){
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
                                        String absPath = jsonObject.getString(JsonSSI.IMAGE_PATH);
                                        sendFile(absPath, webSocket);
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
                            sendImageNameJson(webSocket, imgFile.toString());
                        } catch (JSONException e1) {
                            Log.i(TAG, e.toString());
                        }

                    }
                });
    }

    private void sendImageNameJson(WebSocket webSocket, String filename) throws JSONException {
        JSONObject jsonImageName = new JSONObject();
        jsonImageName.put(JsonSSI.IMAGE_COMMAND, JsonSSI.START_SEND_IMG);
        jsonImageName.put(JsonSSI.IMAGE_PATH, filename);
        webSocket.send(jsonImageName.toString());
    }
}
