package fi.hiit.complesense.connection;

import java.nio.channels.SocketChannel;

/**
 * Created by hxguo on 27.10.2014.
 */
public class ServerDataEvent {
    public AsyncServer server;
    public SocketChannel socket;
    public byte[] data;

    public ServerDataEvent(AsyncServer server, SocketChannel socket, byte[] data)
    {
        this.server = server;
        this.socket = socket;
        this.data = data;
    }
}
