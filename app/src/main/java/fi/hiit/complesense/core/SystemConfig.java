package fi.hiit.complesense.core;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by hxguo on 10.11.2014.
 * Parse the JSON config file
 */
public class SystemConfig
{
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
        return "SystemConfig\n{\n" +
                "version=" + version +
                ", paramters=" + paramters +
                '}';
    }

    public static class Parameter
    {
        public static final String TAG = "Parameter";
        private DataDeliver dataDeliver;
        private Group group;
        private List<SensorConfig> sensors = new ArrayList<SensorConfig>();

        public Parameter(JSONObject jsonDataDeliver, JSONObject jsonGroup) throws JSONException {
            this.dataDeliver = new DataDeliver(jsonDataDeliver);;
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
                Log.i(TAG, "sc: " + sc.toString());
            }

            return "Parameter\n{\n" +
                    "dataDeliver=" + dataDeliver +
                    ",\n group=" + group +
                    ",\n sensors=" + sensors + '\n' +
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
            return "Group{" +
                    "maxMembers=" + maxMembers +
                    ", minMemebers=" + minMemebers +
                    ", lateJoins=" + lateJoins +
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
            return "DataDeliver{" +
                    "deliveryType='" + deliveryType + '\'' +
                    ", batchSize=" + batchSize +
                    ", batchDelay=" + batchDelay +
                    ", burstLength=" + burstLength +
                    ", burstDelay=" + burstDelay +
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
            return "SensorSelection{" +
                    "Energy_To_Accuracy_Ratio='" + Energy_To_Accuracy_Ratio + '\'' +
                    ", informationGain='" + informationGain + '\'' +
                    ", similarityFilter='" + similarityFilter + '\'' +
                    ", energyToAccuracyRatio=" + energyToAccuracyRatio +
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
        public static final String TAG = "Sensor";
        private static final String TYPE = "SensorType";
        private static final String SAMPLE_RATE = "SamplingRate";
        private static final String SENSOR_SELECTION = "SensorSelection";

        private final String type;
        private final int sampleRate;
        private final SensorSelection sensorSelection;

        public SensorConfig(String type, int sampleRate, SensorSelection sensorSelection) {
            this.type = type;
            this.sampleRate = sampleRate;
            this.sensorSelection = sensorSelection;
        }

        public SensorConfig(JSONObject sensor) throws JSONException {
            this.type = sensor.getString(TYPE);
            this.sampleRate = sensor.getInt(SAMPLE_RATE);
            JSONObject jsonObject = (JSONObject) sensor.get(SENSOR_SELECTION);
            this.sensorSelection = new SensorSelection(jsonObject);
        }

        @Override
        public String toString() {
            return "SensorConfig{" +
                    "type='" + type + '\'' +
                    ", sampleRate=" + sampleRate +
                    ", " + sensorSelection +
                    '}';
        }
    }

}
