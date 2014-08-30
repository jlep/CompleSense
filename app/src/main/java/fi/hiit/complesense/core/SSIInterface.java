package fi.hiit.complesense.core;

/**
 * Created by hxguo on 13.8.2014.
 */
public interface SSIInterface
{
    public void on_Q(); //Query
    public void on_A(); //Query reply
    public void on_C(); //Discover sensors
    public void on_N(); //Discover reply
    public void on_Z(); //Reset sensor device
    public void on_G(); //Get configuration data for a sensor.
    public void on_X(); //Configuration data response
    public void on_S(); //Set configuration data for a sensor
    public void on_R(); //Request sensor data
    public void on_V(); //Sensor data response
    public void on_D(); //Sensor response with one byte status field
    public void on_M(); //Sensor response with many data points
    public void on_O(); //Create sensor observer
    public void on_Y(); //Observer created
    public void on_K(); //Delete sensor observer / listener
    public void on_U(); //Observer / listener finished
    public void on_L(); //Request sensor listener
    public void on_J(); //Sensor listener created
    public void on_E(); //Error
    public void on_F(); //Free data for custom purposes
}
