package fi.hiit.complesense.core;

import android.os.Handler;
import android.util.Log;

import com.koushikdutta.async.http.socketio.Acknowledge;
import com.koushikdutta.async.http.socketio.ConnectCallback;
import com.koushikdutta.async.http.socketio.EventCallback;
import com.koushikdutta.async.http.socketio.SocketIOClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import fi.hiit.complesense.Constants;

/**
 * Created by hxguo on 9/10/14.
 */
public class AudioStreamingClient
{

    private static final String TAG = "AudioStreamingClient";

    public SocketIOClient client;
    private final MessageHandler messageHandler = new MessageHandler();
    private SocketIOListener mListener;



    public interface SocketIOListener
    {
        void onAddRemoteOuputStream();
    }

    private class MessageHandler implements EventCallback
    {
        //private HashMap<String, Command> commandMap;

        public MessageHandler()
        {
            /*
            this.commandMap = new HashMap<String, Command>();
            commandMap.put("init", new CreateOfferCommand());
            commandMap.put("offer", new CreateAnswerCommand());
            commandMap.put("answer", new SetRemoteSDPCommand());
            commandMap.put("candidate", new AddIceCandidateCommand());
            */
        }

        @Override
        public void onEvent(String s, JSONArray jsonArray, Acknowledge acknowledge) {
            try {
                Log.i(TAG, "MessageHandler.onEvent() " + (s == null ? "nil" : s));
                if (s.equals("id"))
                {
                    mListener.onAddRemoteOuputStream();
                } else {
                    JSONObject json = jsonArray.getJSONObject(0);
                    String from = json.getString("from");
                    String type = json.getString("type");
                    JSONObject payload = null;
                    Log.i(TAG, "jsonType:" + type);

                    if (!type.equals("init")) {
                        payload = json.getJSONObject("payload");
                    }


                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public AudioStreamingClient(SocketIOListener listener, String host)
    {
        Log.i(TAG, "AudioStreamingClient()");
        mListener = listener;

        SocketIOClient.connect(host, new ConnectCallback()
        {
            @Override
            public void onConnectCompleted(Exception ex, SocketIOClient socket) {
                if (ex != null) {
                    Log.e(TAG, "WebRtcClient connect failed: " + ex.getMessage());
                    return;
                }
                Log.e(TAG, "WebRtcClient connected.");
                client = socket;

                // specify which events you are interested in receiving
                client.addListener("id", messageHandler);
                client.addListener("message", messageHandler);
            }
        }, new Handler());
    }

    public void sendMessage(String to, String type, JSONObject payload)
            throws JSONException
    {
        JSONObject message = new JSONObject();
        message.put("to", to);
        message.put("type", type);
        message.put("payload", payload);
        client.emit("message", new JSONArray().put(message));
    }
}
