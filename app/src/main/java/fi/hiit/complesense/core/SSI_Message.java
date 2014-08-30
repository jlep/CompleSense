package fi.hiit.complesense.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by hxguo on 21.8.2014.
 */
public class SSI_Message
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

    public SSI_Message(short cmd, byte[] payload)
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

    public int getCmd() {
        return cmd;
    }
    public byte[] getPayload()
    {
        return payload;
    }


}
