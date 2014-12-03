package fi.hiit.complesense.core;

import android.hardware.Sensor;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fi.hiit.complesense.util.SensorUtil;

/**
 * Created by hxguo on 10.11.2014.
 * Parse the JSON config file
 */
public class SystemConfig
{
    private static final String TAG = SystemConfig.class.getSimpleName();
    private final double version;
    private List<Parameter> paramters = new ArrayList<Parameter>();

    public SystemConfig(double version)
    {
        this.version = version;
    }

    public List<Parameter> getParamters() {
        return paramters;
    }

    public void addParam(JSONObject param) throws JSONException
    {
        JSONObject jsonDataDeliver = param.getJSONObject(SystemConfig.DataDeliver.TAG);
        JSONObject jsonGroup = param.getJSONObject(SystemConfig.Group.TAG);
        Parameter parameter = new Parameter(jsonDataDeliver, jsonGroup);

        JSONArray sensors = param.getJSONArray(SystemConfig.SensorConfig.TAG);
        for(int j=0;j<sensors.length();j++)
        {
            JSONObject sensor = (JSONObject) sensors.get(j);
            parameter.addSensor(sensor);
        }

        paramters.add(parameter);
    }

    @Override
    public String toString() {
        return "{" +
                "\"version\": " + version +
                ", \"parameters\": " + paramters +
                '}';
    }

    public List<SensorConfig> reqSensors() {
        List<SensorConfig> reqSensors = new ArrayList<SensorConfig>();
        for(Parameter param : paramters)
        {
            for(SensorConfig sc : param.sensors)
                reqSensors.add(sc);
        }
        return reqSensors;
    }

    public Set<Integer> reqSensorTypes() {
        Set<Integer> types = new HashSet<Integer>();
        for(Parameter param : paramters){
            for(SensorConfig sc : param.sensors)
                types.add(sc.getType());
        }
        return types;
    }


    public static Set<Integer> getSensorTypesFromJson(JSONArray sensorConfigJson) throws JSONException {
        Set<Integer> reqSensorTypes = new HashSet<Integer>();
        for(int i=0;i<sensorConfigJson.length();i++){
            String jsonStr = sensorConfigJson.get(i).toString();
            //Log.i(TAG, "jsonStr: " + jsonStr);
            JSONObject jsonObject = new JSONObject(jsonStr);
            reqSensorTypes.add(jsonObject.getInt(SensorConfig.TYPE));
        }
        return reqSensorTypes;
    }

    public static class Parameter
    {
        public static final String TAG = "Parameter";
        private DataDeliver dataDeliver;
        private Group group;
        private List<SensorConfig> sensors = new ArrayList<SensorConfig>();

        public Parameter(JSONObject jsonDataDeliver, JSONObject jsonGroup) throws JSONException {
            this.dataDeliver = new DataDeliver(jsonDataDeliver);
            this.group = new Group(jsonGroup);
        }

        public void addSensor(JSONObject sensor) throws JSONException {
            SensorConfig sensorConfig = new SensorConfig(sensor);
            sensors.add(sensorConfig);
        }

        @Override
        public String toString() {

            StringBuilder sb = new StringBuilder();
            for(SensorConfig sc:sensors)
            {
                sb.append(sc.toString());
                //Log.i(TAG, "sc: " + sc.toString());
            }

            return "{" +
                    "\"DataDeliver\": " + dataDeliver +
                    ", \"Group\": " + group +
                    ", \"Sensor\": " + sensors +
                    '}';
        }

        public DataDeliver getDataDeliver() {
            return dataDeliver;
        }

        public Group getGroup() {
            return group;
        }

        public List<SensorConfig> getSensors() {
            return sensors;
        }
    }

    public static class Group
    {
        public static final String TAG = "Group";
        private static final String MAX_MEMBERS = "MaxMembers";
        private static final String LATE_JOINS = "LateJoins";
        private static final String MIN_MEMBERS = "MinMembers";

        public final int maxMembers;
        public final int minMemebers;
        public final boolean lateJoins;

        public Group(int maxMembers, int minMemebers, boolean lateJoins) {
            this.maxMembers = maxMembers;
            this.minMemebers = minMemebers;
            this.lateJoins = lateJoins;
        }

        public Group(JSONObject jsonGroup) throws JSONException {
            this.maxMembers = jsonGroup.getInt(MAX_MEMBERS);
            this.minMemebers = jsonGroup.getInt(MIN_MEMBERS);
            this.lateJoins = jsonGroup.getBoolean(LATE_JOINS);
        }

        @Override
        public String toString() {
            return "{" +
                    "\""+ MAX_MEMBERS +"\": " + maxMembers +
                    ", \""+ LATE_JOINS +"\": " + minMemebers +
                    ", \""+ MIN_MEMBERS +"\": " + lateJoins +
                    '}';
        }
    }

    public static class DataDeliver
    {
        public static final String TAG = "DataDeliver";
        public static final String DELIVERY_TYPE = "DeliveryType";
        public static final String BATCH_SIZE = "BatchSize";
        public static final String BATCH_DELAY = "BatchDelay";
        public static final String BURST_LENGTH = "BurstLength";
        public static final String BURST_DELAY = "BurstDelay";

        public static final String BATCHES = "Batches";

        private final String deliveryType;
        private final int batchSize;
        private final int batchDelay;
        private final int burstLength;
        private final int burstDelay;

        public DataDeliver(String deliveryType, int batchSize, int batchDelay, int burstLength, int burstDelay)
        {
            this.deliveryType = deliveryType;
            this.batchSize = batchSize;
            this.batchDelay = batchDelay;
            this.burstLength = burstLength;
            this.burstDelay = burstDelay;
        }

        public DataDeliver(JSONObject jsonDataDeliver) throws JSONException {
            this.deliveryType = (String) jsonDataDeliver.get(DELIVERY_TYPE);
            this.batchSize = jsonDataDeliver.getInt(BATCH_SIZE);
            this.batchDelay = jsonDataDeliver.getInt(BATCH_DELAY);
            this.burstLength = jsonDataDeliver.getInt(BURST_LENGTH);
            this.burstDelay = jsonDataDeliver.getInt(BURST_DELAY);
        }

        @Override
        public String toString() {
            return "{" +
                    "\""+ DELIVERY_TYPE + "\": " + "\"" + deliveryType + "\"" +
                    ", \""+ BATCH_SIZE +"\": " + batchSize +
                    ", \""+ BATCH_DELAY +"\": " + batchDelay +
                    ", \""+ BURST_LENGTH +"\": " + burstLength +
                    ", \""+ BURST_DELAY +"\": " + burstDelay +
                    '}';
        }
    }

    public static class SensorSelection
    {
        private static final String INFORMATION_GAIN = "InformationGain";
        private static final String TRANSMIT_TRIGGER = "TransmitTrigger";
        private final String Energy_To_Accuracy_Ratio ="EnergyToAccuracyRatio";

        public final String informationGain;
        public final String similarityFilter;
        public final int energyToAccuracyRatio;


        public SensorSelection(String informationGain, String similarityFilter, int energyToAccuracyRatio) {
            this.informationGain = informationGain;
            this.similarityFilter = similarityFilter;
            this.energyToAccuracyRatio = energyToAccuracyRatio;
        }

        public SensorSelection(int energyToAccuracyRatio)
        {
            this.informationGain = InformationGain.SIMILARITY;
            this.similarityFilter = Thresholds.STATIONARY_FILTER;
            this.energyToAccuracyRatio = energyToAccuracyRatio;
        }

        public SensorSelection(JSONObject jsonObject) throws JSONException {
            this.informationGain = jsonObject.getString(INFORMATION_GAIN);
            this.similarityFilter = jsonObject.getString(TRANSMIT_TRIGGER);
            this.energyToAccuracyRatio = jsonObject.getInt(Energy_To_Accuracy_Ratio);
        }


        @Override
        public String toString() {
            return "{" +
                    "\""+ Energy_To_Accuracy_Ratio +"\": " + energyToAccuracyRatio +
                    ", \""+ TRANSMIT_TRIGGER +"\": " + "\"" + similarityFilter +"\"" +
                    ", \""+ INFORMATION_GAIN +"\": " + "\"" + informationGain +"\"" +
                    '}';
        }
    }

    public static class InformationGain
    {
        public static final String SIMILARITY = "Similarity";
    }

    public static class Thresholds
    {
        public static final String STATIONARY_FILTER = "Stationary_Filter";
    }

    public static class SensorConfig
    {
        public enum SamplingRate{SLOW, NORMAL, FAST, UNKNOWN};


        public static final String TAG = "Sensor";
        private static final String TYPE = "SensorType";
        private static final String SAMPLE_RATE = "SamplingRate";
        private static final String SENSOR_SELECTION = "SensorSelection";

        private static final String SENSOR_TYPE_ACCELEROMETER = "ACCELEROMETER";
        private static final String SENSOR_TYPE_GYROSCOPE = "GYROSCOPE";
        private static final String SENSOR_TYPE_MAGNETOMETER = "MAGNETOMETER";
        private static final String SENSOR_TYPE_BAROMETER = "BAROMETER";
        private static final String SENSOR_TYPE_ORIENTATION = "ORIENTATION";
        private static final String SENSOR_TYPE_GPS = "GPS";
        private static final String SENSOR_TYPE_MICROPHONE = "MICROPHONE";
        private static final String SENSOR_TYPE_CAMERA = "CAMERA";

        private final int type;
        private final SamplingRate sampleRate;

        public int getType() {
            return type;
        }

        public SamplingRate getSampleRate() {
            return sampleRate;
        }

        public SensorSelection getSensorSelection() {
            return sensorSelection;
        }

        private final SensorSelection sensorSelection;

        public SensorConfig(JSONObject sensor) throws JSONException {
            this.type = getTypeInt(sensor.getString(TYPE) );
            String sr = sensor.getString(SAMPLE_RATE);

            if(sr.equals("SLOW")){
                sampleRate = SamplingRate.SLOW;
            }else{
                if(sr.equals("NORMAL")){
                    sampleRate = SamplingRate.NORMAL;
                }else if(sr.equals("FAST")){
                    sampleRate = SamplingRate.FAST;
                }else{
                    sampleRate = SamplingRate.UNKNOWN;
                }
            }
            JSONObject jsonObject = (JSONObject) sensor.get(SENSOR_SELECTION);
            this.sensorSelection = new SensorSelection(jsonObject);
        }

        private int getTypeInt(String typeTxt)
        {
            Log.v(TAG, "sensor_type: " + typeTxt);
            String typeStr = typeTxt.substring(typeTxt.lastIndexOf('.')+1);

            if(typeStr.equals(SENSOR_TYPE_ACCELEROMETER))
                return Sensor.TYPE_ACCELEROMETER;
            if(typeStr.equals(SENSOR_TYPE_GYROSCOPE))
                return Sensor.TYPE_GYROSCOPE;
            if(typeStr.equals(SENSOR_TYPE_MAGNETOMETER))
                return Sensor.TYPE_MAGNETIC_FIELD;
            if(typeStr.equals(SENSOR_TYPE_BAROMETER))
                return Sensor.TYPE_PRESSURE;
            if(typeStr.equals(SENSOR_TYPE_GPS))
                return SensorUtil.SENSOR_GPS;
            if(typeStr.equals(SENSOR_TYPE_MICROPHONE))
                return SensorUtil.SENSOR_MIC;
            if(typeStr.equals(SENSOR_TYPE_CAMERA))
                return SensorUtil.SENSOR_CAMERA;
            if(typeStr.equals(SENSOR_TYPE_ORIENTATION))
                return Sensor.TYPE_ROTATION_VECTOR;
            return -1;
        }

        private String getTypeStr(){
            if(type == Sensor.TYPE_ACCELEROMETER)
                return SENSOR_TYPE_ACCELEROMETER;
            if(type == Sensor.TYPE_GYROSCOPE)
                return SENSOR_TYPE_GYROSCOPE;
            if(type == Sensor.TYPE_MAGNETIC_FIELD)
                return SENSOR_TYPE_MAGNETOMETER;
            if(type == Sensor.TYPE_PRESSURE)
                return SENSOR_TYPE_BAROMETER;
            if(type == SensorUtil.SENSOR_GPS)
                return SENSOR_TYPE_GPS;
            if(type == SensorUtil.SENSOR_MIC)
                return SENSOR_TYPE_MICROPHONE;
            if(type == SensorUtil.SENSOR_CAMERA)
                return SENSOR_TYPE_CAMERA;
            if(type == Sensor.TYPE_ROTATION_VECTOR)
                return SENSOR_TYPE_ORIENTATION;

            return null;
        }

        @Override
        public String toString() {
            return "{" +
                    "\""+TYPE+"\": " + getTypeStr() +
                    ", \""+SAMPLE_RATE+"\": " + sampleRate +
                    ", \""+SENSOR_SELECTION+"\": " + sensorSelection +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SensorConfig that = (SensorConfig) o;

            if (type != that.type) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return type;
        }
    }

}
