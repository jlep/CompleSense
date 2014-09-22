package fi.hiit.complesense.core;

import android.hardware.Sensor;
import android.util.Log;

import java.io.Serializable;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Based on SSI (Simple Sensor Interface)
 * url: http://en.wikipedia.org/wiki/Simple_Sensor_Interface_protocol
 *
 *
 *
 * Created by hxguo on 6/24/14.
 */
public class SystemMessage implements Serializable
{

    private static final String TAG = "SystemMessage";
    public static final int ID_SYSTEM_MESSAGE = 10245;


    /**
     * Fields of SystemMessage class
     */
    private short cmd;
    private int lenPayload;
    private byte[] payload;
    private static final long serialVersionUID = 5950169519310111575L;

    /**
     * Commands
     */

    public static final short R = 0x52; //Request sensor data
    public static final short V = 0x56; //Sensor data response
    public static final short C = 0x43; //Discover sensors
    public static final short N = 0x4E; //Discover reply

    // Streaming Commands
    public static final short L = 0x4C; //Request sensor listener
    public static final short J = 0x4A; //Sensor listener created
    public static final short O = 0x4F; //create sensor observer
    public static final short Y = 0x59; //observer created

    public static final short INIT = 0x10; //Discover reply
    public static final short RTT = 0x51;


    public static final int TYPE_AUDIO_STREAM = 30;
    public static final short CLOCK_SYNC = 31;



    public SystemMessage(short cmd, byte[] payload)
    {
        this.cmd = cmd;
        this.payload = payload;
        if(payload!=null)
            this.lenPayload = this.payload.length;
        else
            lenPayload = 0;
    }


    public static byte[] int2Bytes( final int i )
    {
        ByteBuffer bb = ByteBuffer.allocate(Integer.SIZE/8);
        bb.putInt(i);
        return bb.array();
    }

    public static byte[] double2Bytes( final double d )
    {
        ByteBuffer bb = ByteBuffer.allocate(Double.SIZE/8);
        bb.putDouble(d);
        return bb.array();
    }

    public static byte[] float2Bytes( final float f )
    {
        ByteBuffer bb = ByteBuffer.allocate(Float.SIZE/8);
        bb.putFloat(f);
        return bb.array();
    }

    public static int byteArray2Int(byte[] b)
    {
        final ByteBuffer bb = ByteBuffer.wrap(b);
        bb.order(ByteOrder.BIG_ENDIAN);
        return bb.getInt();
    }

    public static int byteArray2Int(byte[] b, int start, int len)
    {
        final ByteBuffer bb = ByteBuffer.wrap(b,start,len);
        bb.order(ByteOrder.BIG_ENDIAN);
        return bb.getInt();
    }

    public static float byteArray2Float(byte[] b, int start, int len)
    {
        final ByteBuffer bb = ByteBuffer.wrap(b,start,len);
        bb.order(ByteOrder.BIG_ENDIAN);
        return bb.getFloat();
    }

    public String toString()
    {
        String str = "";
        switch (cmd)
        {
            case SystemMessage.R:
                str += "Request Sensor data: ";
                str += "Sensor Type=" + byteArray2Int(payload);
                return str;
            case SystemMessage.V:
                int pos = 0;
                str += "Reply Sensor data: ";
                int sType = byteArray2Int(payload,pos,Integer.SIZE / 8);
                str += "Sensor Type=" + sType;

                if(sType < TYPE_AUDIO_STREAM)
                {
                    // sensor values
                    pos += Integer.SIZE / 8;
                    str += " x=" + byteArray2Float(payload, pos, Float.SIZE / 8);
                    pos += Float.SIZE / 8;
                    str += " y=" + byteArray2Float(payload, pos, Float.SIZE / 8);
                    pos += Float.SIZE / 8;
                    str += " z=" + byteArray2Float(payload, pos,Float.SIZE / 8);
                    return str;
                }
                str = "Streaming data";
                return str;

            case SystemMessage.C:
                str += "Sensor Discovery request";
                return str;
            case SystemMessage.N:
                str += "Sensor Discovery reply";
                return str;
            case SystemMessage.RTT:
                str += "RTT Query";
                return str;
            case SystemMessage.O:
                str += "Streaming request";
                return str;
            case SystemMessage.L:
                str += "Relay request";
                return str;
            case SystemMessage.J:
                str += "Relay response";
                return str;
            default:
                return null;
        }
    }

    public static SystemMessage makeSensorsListQueryMessage()
    {
        byte[] payload = int2Bytes(Sensor.TYPE_ALL);
        return new SystemMessage(SystemMessage.C, payload);
    }

    public int getCmd() {
        return cmd;
    }
    public byte[] getPayload()
    {
        return payload;
    }

    public static SystemMessage makeSensorsListReplyMessage(List<Integer> sensorList)
    {
        byte[] payload = sensorTypeList2Bytes(sensorList);
        return new SystemMessage(SystemMessage.N, payload);
    }

    public static SystemMessage makeAudioStreamingRequest(int port2send, String foreignSocketAddr)
    {
        ByteBuffer bb = ByteBuffer.allocate(Integer.SIZE / 8 + foreignSocketAddr.length());
        bb.putInt(port2send);
        bb.put(foreignSocketAddr.getBytes());

        byte[] payload = bb.array();

        return new SystemMessage(SystemMessage.O, payload);
    }

    public static SystemMessage makeAudioStreamingReply(byte[] bytes, int bytes_read)
    {
        int totalLen = (Integer.SIZE + bytes_read * Byte.SIZE) / 8;
        final ByteBuffer bb = ByteBuffer.allocate(totalLen);

        bb.putInt(0,TYPE_AUDIO_STREAM);
        bb.put(bytes);

        return new SystemMessage(SystemMessage.V, bb.array());
    }

    public static SystemMessage makeRelayListenerRequest()
    {
        byte[] payload = int2Bytes( 0);
        return new SystemMessage(SystemMessage.L, payload);
    }

    public static SystemMessage makeRelayListenerReply()
    {
        byte[] payload = (new String("")).getBytes();
        return new SystemMessage(SystemMessage.J, payload);
    }


    private static byte[] sensorTypeList2Bytes(List<Integer> sensorList)
    {
        int numTypes = sensorList.size();
        ByteBuffer bb = ByteBuffer.allocate((Integer.SIZE / 8)*numTypes);
        for(int t:sensorList)
        {
//            Log.i(TAG,"t: " + t);
            bb.putInt(t);
        }

        return bb.array();
    }

    public static float[] parseSensorValues(SystemMessage sm)
    {
        float[] v = new float[3];

        int pos = 0;
        int sensorType = byteArray2Int(sm.payload,pos,Integer.SIZE / 8);
        pos += Integer.SIZE / 8;
        switch (sensorType)
        {
            case Sensor.TYPE_PRESSURE:
                v[0] = byteArray2Float(sm.payload, pos, Float.SIZE / 8);
                v[1] = 0;
                v[2] = 0;
                return v;
            default:
                v[0] = byteArray2Float(sm.payload, pos, Float.SIZE / 8);
                pos += Float.SIZE / 8;
                v[1] = byteArray2Float(sm.payload, pos, Float.SIZE / 8);
                pos += Float.SIZE / 8;
                v[2] = byteArray2Float(sm.payload, pos, Float.SIZE / 8);
                return v;
        }

    }

    public static List<Integer> parseSensorTypeList(SystemMessage sm)
    {
        Log.i(TAG,"parseSensorTypeList()");
        List<Integer> sensorTypeList = new ArrayList<Integer>();

        byte[] payload = sm.getPayload();
        int sType, offset = 0;

        while(offset < sm.lenPayload)
        {
            sType = byteArray2Int(payload, offset, Integer.SIZE / 8);
            sensorTypeList.add(sType);
            //Log.i(TAG,"sType: " + sType);
            offset += Integer.SIZE / 8;
        }

        return sensorTypeList;
    }

    public static int parseSensorType(SystemMessage sm)
    {
        if(sm.getCmd()==SystemMessage.V || sm.getCmd()==SystemMessage.R)
        {
            //Log.i(TAG, "parseSensorType() " + Integer.toString(byteArray2Int(sm.getPayload()) ));
            return byteArray2Int(sm.getPayload());
        }
        return -1;
    }

    public static SystemMessage makeSensorDataQueryMessage(int sensorType)
    {
        byte[] payload = int2Bytes(sensorType);
        return new SystemMessage(SystemMessage.R, payload);
    }


    public static SystemMessage makeSensorValuesReplyMessage(int sensorType, float[] values)
    {
        int totalLen = (Integer.SIZE + values.length * Float.SIZE) / 8;
        final ByteBuffer bb = ByteBuffer.allocate(totalLen);

        bb.putInt(sensorType);
        bb.putFloat(values[0]);
        bb.putFloat(values[1]);
        bb.putFloat(values[2]);

        return new SystemMessage(SystemMessage.V, bb.array() );
    }

    public byte[] toBytes()
    {
        int totalLen = Short.SIZE / 8 +
                Integer.SIZE / 8 +
                lenPayload;
        ByteBuffer bb = ByteBuffer.allocate(totalLen);
        bb.putShort(cmd);
        bb.putInt(lenPayload);
        bb.put(payload);

        return bb.array();
    }

    public static SystemMessage getFromBytes(byte[] data)
    {
        //Log.i(TAG,"getFromBytes: " + data.length);

        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.order(ByteOrder.BIG_ENDIAN);
        short cmd = bb.getShort();
        //Log.i(TAG,"cmd: " + cmd);
        int payloadLen = bb.getInt();
        //Log.i(TAG,"payloadLen: " + payloadLen);
        byte[] payload = new byte[payloadLen];
        bb.get(payload, 0, payloadLen);

        return new SystemMessage(cmd, payload);
    }


    /**
     * for multiple hops
     * @param timeStamp
     * @param hops
     * @return
     */
    public static SystemMessage makeRttQuery(long timeStamp, ArrayDeque<String> hops)
    {
        int len = Long.SIZE / 8;
        Iterator<String> iter = hops.iterator();
        while(iter.hasNext())
            len += iter.next().toString().length() + 1;

        ByteBuffer bb = ByteBuffer.allocate(len);
        bb.order(ByteOrder.BIG_ENDIAN);

        bb.putLong(timeStamp);
        iter = hops.iterator();
        while(iter.hasNext())
        {
            bb.put(iter.next().getBytes());
            bb.put((new String(",")).getBytes());
        }
        return new SystemMessage(SystemMessage.RTT, bb.array());
    }

    /**
     * single hop
     * @param timeStamp
     * @param rounds
     * @return
     */
    public static SystemMessage makeRttQuery(long timeStamp,int rounds, SocketAddress localSocketAddr)
    {
        int len = Long.SIZE / 8 + Integer.SIZE / 8 + localSocketAddr.toString().length();
        ByteBuffer bb = ByteBuffer.allocate(len);
        bb.order(ByteOrder.BIG_ENDIAN);

        bb.putLong(timeStamp);
        bb.putInt(rounds);
        bb.put(localSocketAddr.toString().getBytes() );

        return new SystemMessage(SystemMessage.RTT, bb.array());
    }

    public static SystemMessage makeClockQueryRequest() {
        byte[] payload = (new String("")).getBytes();
        return new SystemMessage(SystemMessage.CLOCK_SYNC, payload);
    }

    /*
    public static ArrayDeque<String> parseRttQuery(byte[] payload)
    {
        ByteBuffer bb = ByteBuffer.wrap(payload);
        bb.getLong();

        byte[] hopsBytes = new byte[payload.length - Long.SIZE / 8];
        bb.get(hopsBytes);
        String hopsStr = new String(hopsBytes);
        //Log.i(TAG,"hopsStr: " + hopsStr);
        String[] socketAddrStrs = hopsStr.split(",");
        ArrayDeque<String> hops = new ArrayDeque<String>(socketAddrStrs.length);
        for(String s:socketAddrStrs)
        {
            Log.i(TAG,s);
            hops.add(s);
        }


        return hops;
    }
    */
}
