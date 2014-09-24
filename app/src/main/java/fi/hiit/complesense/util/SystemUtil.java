package fi.hiit.complesense.util;

import android.app.ActivityManager;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

import fi.hiit.complesense.Constants;

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

    public static String parseErrorCode(int errorCode)
    {
        switch (errorCode) {
            case WifiP2pManager.ERROR:
                return "Error";
            case WifiP2pManager.BUSY:
                return "Busy";
            case WifiP2pManager.P2P_UNSUPPORTED:
                return "P2P_Unsupported";

        }
        return null;
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

    public static void writeLogFile(String startTime, String recvSocketAddr)
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

    public static void writeLogFile(long threadId)
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

    public static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }
}
