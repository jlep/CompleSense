package fi.hiit.complesense.core;

/**
 * Created by hxguo on 7/16/14.
 */
public class SensorValues
{
    private String src; // source socket address

    private int sensorType;
    private float[] values;
    private String key; // Used for retrieving data in Map

    public SensorValues(String src, int type, float[] values)
    {
        this.src = src;
        this.sensorType = type;
        this.values = values;
        key = src + ":" + Integer.toString(this.sensorType);
    }

    /**
     *
     * @param src
     * @param type
     * @return socketAddr:type
     */
    public static String genKey(String src, int type)
    {
        return src + ":" + Integer.toString(type);
    }

    public void setValues(float[] values)
    {
        this.values[0] = values[0];
        this.values[1] = values[1];
        this.values[2] = values[2];
    }
    public float[] getValues()
    {
        return values;
    }
}
