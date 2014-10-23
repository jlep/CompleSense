package fi.hiit.complesense;

import android.os.Environment;

import java.io.File;
import java.net.URL;

/**
 * Created by hxguo on 7/11/14.
 */
public class Constants
{
    public static final int NUM_CLIENTS = 3;

    public static final String ROOT_DIR = Environment.getExternalStorageDirectory() + "/CompleSense/";

    public static final int MEDIA_TYPE_IMAGE = 100;
    public static final int MEDIA_TYPE_VIDEO = 101;

    // TXT RECORD properties
    public static final String TXTRECORD_PROP_VISIBILITY = "vb";
    public static final String TXTRECORD_SENSOR_TYPE_LIST = "tp";
    public static final String TXTRECORD_NETWORK_INFO = "cn";
    public static final String TXTRECORD_BATTERY_LEVEL = "bt";
    public static final String TXTRECORD_NUM_CLIETNS = "nc";


    public static final int SERVER_PORT = 30243; // group owner port
    public static final int CLOUD_SERVER_PORT = 10002; // cloud server port
    public static final String URL = "universe.hiit.fi";// server url
    public static final String URL_UPLOAD = URL+":"+CLOUD_SERVER_PORT+"/upload/";
    public static final int MAX_BUF = 1024;

    public static final String SELF_INFO_UPDATE_ACTION = "fi.hiit.complesense.ui.SELF_INFO_UPDATE_ACTION";
    public static final String STATUS_TEXT_UPDATE_ACTION = "fi.hiit.complesense.ui.STATUS_TEXT";

    public static final String EXTENDED_DATA_SELF_INFO = "fi.hiit.complesense.ui.SELF_INFO";
    public static final String EXTENDED_DATA_STATUS_TEXT = "fi.hiit.complesense.ui.STATUS_TEXT";
    public static final String EXTENDED_DATA_DNS_FOUND = "fi.hiit.complesense.ui.DNS_FOUND";
    public static final String EXTENDED_DATA_INSTANCE_NAME = "fi.hiit.complesense.ui.INSTANCE_NAME";


    /**
     * Messages sent from service to Activity
     */
    public static final int MSG_SELF_INFO_UPDATE = 1;
    public static final int MSG_SERVICE_INIT_DONE = 2;
    public static final int MSG_DNS_SERVICE_FOUND = 3;
    public static final int MSG_UPDATE_STATUS_TXT = 7;
    public static final int MSG_SERVER_INFO = 8;
    public static final int MSG_CLIENTS_LISTS_UPDATE = 9;
    public static final int MSG_TAKE_IMAGE = 10;

    /**
     * Messages send to service
     */
    public static final int SERVICE_MSG_INIT_SERVICE = 20;

    public static final int SERVICE_MSG_START = 21;
    public static final int SERVICE_MSG_STOP = 22;

    public static final int SERVICE_MSG_REGISTER_SERVICE = 23;
    public static final int SERVICE_MSG_FIND_SERVICE = 24;

    public static final int SERVICE_MSG_CANCEL_CONNECT = 25;
    public static final int SERVICE_MSG_CONNECT = 26;

    public static final int SERVICE_MSG_STATUS_TXT_UPDATE = 30;
    public static final int SERVICE_MSG_STOP_CLIENT_SERVICE = 40;

    public static final int SERVICE_MSG_STEREO_IMG_REQ = 50;
    public static final int SERVICE_MSG_SEND_IMG = 51;


    /*
     * Messages sent to thread
     */
    public static final int THREAD_MSG_SEND_IMG = 151;

    public static final String INIT_CONNECTION = "INIT";
    public static final int RTT_ROUNDS = 5;
    public static final int SAMPLE_RATE = 8000;
    public static final int SAMPLE_INTERVAL = 20; // milliseconds
    public static final int SAMPLE_SIZE = 2; // bytes per sample
    public static final short BIT_SAMPLE = 16; // 16 bits per sample
    public static final int BUF_SIZE = SAMPLE_INTERVAL * SAMPLE_INTERVAL * SAMPLE_SIZE * 2;
    public static final short NUM_CHANNELS = 1;

    public static final int LOCAL_WEBSOCKET_PORT = 12000;
    public static final int FRAME_SIZE = 256; // milliseconds
    public static final String PROTOCOL = "ws";



    public static float[] dummyValues = {-1.0f,-1.0f,-1.0f};
}
