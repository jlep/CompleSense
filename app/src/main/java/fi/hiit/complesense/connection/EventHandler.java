package fi.hiit.complesense.connection;

import java.nio.channels.SelectionKey;

/**
 * Created by hxguo on 20.8.2014.
 */
public interface EventHandler {

    public void handleEvent(SelectionKey handle) throws Exception;

}
