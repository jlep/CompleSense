package fi.hiit.complesense.connection;

import android.util.Log;

import com.koushikdutta.async.http.WebSocket;

import org.json.JSONException;
import org.json.JSONObject;

import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.json.JsonSSI;

/**
 * Created by hxguo on 19.11.2014.
 */
public class WebConnection extends AbsSystemThread implements WebSocket.PongCallback
{
    private static final String TAG = WebConnection.class.getSimpleName();

    public WebConnection(String name, ServiceHandler serviceHandler) {
        super(name, serviceHandler);
    }

    @Override
    public void stopThread() {

    }

    @Override
    public void onPongReceived(String s) {
        try {
            JSONObject jsonObject = new JSONObject(s);
            int rounds = jsonObject.getInt(JsonSSI.ROUNDS);
            rounds--;
            if(rounds==0){
                                
            }

        } catch (JSONException e) {
            Log.i(TAG, e.toString());
        }
    }



}
