package fi.hiit.complesense.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.JsonReader;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.SystemConfig;
import fi.hiit.complesense.service.AbstractGroupService;

/**
 * Created by hxguo on 7/14/14.
 */
public class SystemUtil {

    private static final String TAG = "SystemUtil";

    public static boolean isServiceRunning(String serviceName, Context context) {
        Log.i(TAG, "serviceIsRunning(" + serviceName + ")");
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service :
                manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceName.equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, String> generateTxtRecord(AbstractGroupService abstractGroupService)
    {
        Log.i(TAG,"generateTxtRecord()");

        Map<String, String> record = new HashMap<String, String>();
        record.put(Constants.TXTRECORD_PROP_VISIBILITY, "visible");

        //-------- get available sensor list
        List<Integer> sensorList = new ArrayList<Integer>();
        sensorList.add(SensorUtil.SENSOR_CAMERA);
        sensorList.add(SensorUtil.SENSOR_MIC);
        sensorList.add(SensorUtil.SENSOR_GPS);
        sensorList.addAll(SensorUtil.getLocalSensorTypeList(abstractGroupService));

        record.put(Constants.TXTRECORD_SENSOR_TYPE_LIST,
                sensorList.toString());
        Log.i(TAG,sensorList.toString());

        //-------- get available network connections
        List<Integer> availableConns = new ArrayList<Integer>();
        ConnectivityManager connMgr = (ConnectivityManager)
                abstractGroupService.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] networkInfos = connMgr.getAllNetworkInfo();
        for(NetworkInfo ni:networkInfos){
            if(ni !=null){
                if(ni.isConnectedOrConnecting()){
                    Log.i(TAG, ni.getTypeName());
                    availableConns.add(ni.getType());
                }
            }
        }
        if(availableConns.size()>0)
            record.put(Constants.TXTRECORD_NETWORK_INFO, availableConns.toString() );

        //-------- get battery level
        record.put(Constants.TXTRECORD_BATTERY_LEVEL,
                Float.toString(abstractGroupService.getBatteryLevel()));


        return record;
    }

    public static String parseErrorCode(int errorCode)
    {
        switch (errorCode) {
            case WifiP2pManager.ERROR:
                return "Error";
            case WifiP2pManager.BUSY:
                return "Busy";
            case WifiP2pManager.P2P_UNSUPPORTED:
                return "P2P_Unsupported";
            case WifiP2pManager.NO_SERVICE_REQUESTS:
                return "No service request added";
            default:
                return "Unknown errorcode: " + Integer.toString(errorCode);
        }
    }

    public static void sendStatusTextUpdate(Messenger uiMessenger, String txt)
    {
        if(uiMessenger!=null)
        {
            Message msg = Message.obtain();
            msg.obj = txt;
            msg.what = Constants.MSG_UPDATE_STATUS_TXT;
            try {
                uiMessenger.send(msg);
            } catch (RemoteException e) {
                Log.i(TAG,e.toString());
            }
        }
    }

    public static void sendSelfInfoUpdate(Messenger uiMessenger, WifiP2pDevice device)
    {
        if(uiMessenger !=null)
        {
            Message msg = Message.obtain();
            msg.obj = device;
            msg.what = Constants.MSG_SELF_INFO_UPDATE;
            try {
                uiMessenger.send(msg);
            } catch (RemoteException e) {
                Log.i(TAG,e.toString());
            }
        }
    }

    public static void sendDnsFoundUpdate(Messenger uiMessenger, WifiP2pDevice srcDevice,
                                          String instanceName)
    {
        if(uiMessenger!=null)
        {
            Message msg = Message.obtain();
            Bundle b = new Bundle();
            b.putString(Constants.EXTENDED_DATA_INSTANCE_NAME, instanceName);

            msg.what = Constants.MSG_DNS_SERVICE_FOUND;
            msg.obj = srcDevice;
            msg.setData(b);
            try {
                uiMessenger.send(msg);
            } catch (RemoteException e) {
                Log.i(TAG, e.toString());
            }
        }

    }

    public static void sendTakeImageReq(Messenger uiMessenger, SocketAddress socketAddress)
    {
        if(uiMessenger!=null)
        {
            Message msg = Message.obtain();
            msg.obj = socketAddress;
            msg.what = Constants.MSG_TAKE_IMAGE;
            try {
                uiMessenger.send(msg);
            } catch (RemoteException e) {
                Log.i(TAG,e.toString());
            }
        }
    }



    public static void sendClientsListUpdate(Messenger uiMessenger, WifiP2pGroup group)
    {
        if(uiMessenger!=null)
        {
            Message msg = Message.obtain();
            msg.what = Constants.MSG_CLIENTS_LISTS_UPDATE;
            msg.obj = group;
            try {
                uiMessenger.send(msg);
            } catch (RemoteException e) {
                Log.i(TAG,e.toString());
            }
        }
    }

    public static void sendServerInfoUpdate(Messenger uiMessenger, WifiP2pDevice server)
    {
        if(uiMessenger!=null)
        {
            Message msg = Message.obtain();
            msg.what = Constants.MSG_SERVER_INFO;
            msg.obj = server;
            try {
                uiMessenger.send(msg);
            } catch (RemoteException e) {
                Log.i(TAG,e.toString());
            }
        }

    }

    public static String getHost(String socketAddrStr)
    {
        String host = socketAddrStr.substring(socketAddrStr.indexOf("/")+1,
                socketAddrStr.lastIndexOf(":"));
        return host;
    }

    public static int getPort(String socketAddrStr)
    {
        int port = Integer.parseInt(socketAddrStr.substring(socketAddrStr.lastIndexOf(":")+1));
        return port;
    }

    public static void writeAlivenessFile(String startTime, String recvSocketAddr)
    {

        String fileName = recvSocketAddr + "-" +startTime +".txt";
        String filePath = Constants.ROOT_DIR + fileName;

        Log.i(TAG, "writeLogFile("+filePath+")");

        try {
            BufferedWriter output = new BufferedWriter(new FileWriter(filePath, false)) ;
            output.write(Long.toString(System.currentTimeMillis()));
            output.flush();
            output.close();
        } catch (FileNotFoundException e) {
            Log.i(TAG,e.toString());
        } catch (IOException e) {
            Log.i(TAG,e.toString());
        }
    }

    public static void writeAlivenessFile(long threadId)
    {
        String fileName = threadId +".txt";
        String filePath = Constants.ROOT_DIR + fileName;

        Log.i(TAG, "writeLogFile("+filePath+")");

        try {
            BufferedWriter output = new BufferedWriter(new FileWriter(filePath, false)) ;
            output.write(Long.toString(System.currentTimeMillis()));
            output.flush();
            output.close();
        } catch (FileNotFoundException e) {
            Log.i(TAG,e.toString());
        } catch (IOException e) {
            Log.i(TAG,e.toString());
        }
    }

    public static void deleteRecursive(File fileOrDirectory)
    {
        if (fileOrDirectory.isDirectory())
        {
            /*
            * Do not delete files for testing purpose
             */
            if(!fileOrDirectory.getName().startsWith("config")&&!fileOrDirectory.getName().startsWith("test_"))
            {
                for (File child : fileOrDirectory.listFiles())
                    deleteRecursive(child);
            }
        }
        fileOrDirectory.delete();
    }

    public static void cleanRootDir()
    {
        File rootFolder = new File(Constants.ROOT_DIR);
        if(rootFolder.exists())
            SystemUtil.deleteRecursive(rootFolder);
        rootFolder.mkdir();
    }

    /** Create a file Uri for saving an image or video */
    public static Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /** Create a File for saving an image or video */
    private static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Constants.ROOT_DIR, "pic");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d(TAG, "Failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == Constants.MEDIA_TYPE_IMAGE)
        {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        }
        else if(type == Constants.MEDIA_TYPE_VIDEO)
        {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    public static SystemConfig loadConfigFile() throws IOException, JSONException {
        File configDir = new File(Constants.ROOT_DIR, "config");
        configDir.mkdirs();

        String line;
        StringBuilder builder = new StringBuilder();
        for (File child : configDir.listFiles())
        {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(child)) );
            while ((line = br.readLine()) != null) {
                builder.append(line);
            }
            break;
        }

        if(builder.length()>0)
        {
            JSONObject reader = new JSONObject(builder.toString());
            double version  = reader.getDouble("version");
            SystemConfig systemConfig = new SystemConfig(version);

            JSONArray parameters = reader.getJSONArray("parameters");
            for(int i=0;i<parameters.length();i++)
            {
                JSONObject param = (JSONObject) parameters.get(i);
                systemConfig.addParam(param);
            }
            Log.i(TAG, systemConfig.toString());
            return systemConfig;
        }

        return null;
    }
}
