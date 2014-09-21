package fi.hiit.complesense;

import java.net.URL;

/**
 * Created by hxguo on 7/11/14.
 */
public class Constants
{
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
     * Messages sent from service
     */
    public static final int MSG_SELF_INFO_UPDATE = 1;
    public static final int MSG_SERVICE_INIT_DONE = 2;
    public static final int MSG_DNS_SERVICE_FOUND = 3;
    public static final int MSG_UPDATE_STATUS_TXT = 7;
    public static final int MSG_SERVER_INFO = 8;
    public static final int MSG_CLIENTS_LISTS_UPDATE = 9;

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


    public static final String INIT_CONNECTION = "INIT";
    public static final int RTT_ROUNDS = 5;


    public static float[] dummyValues = {-1.0f,-1.0f,-1.0f};;
}
