package fi.hiit.complesense.connection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 21.8.2014.
 */
public class AcceptorNIO extends AbstractSystemThread
{
    protected AcceptorNIO(ServiceHandler serviceHandler) throws Exception
    {
        super(serviceHandler);
        ServerSocketChannel server = ServerSocketChannel.open();
        server.socket().setReuseAddress(true);
        server.socket().bind(new InetSocketAddress(Constants.SERVER_PORT));
        server.configureBlocking(false);

        Dispatcher dispatcher = new Dispatcher();
        dispatcher.registerChannel(SelectionKey.OP_ACCEPT, server);

        dispatcher.registerEventHandler(
                SelectionKey.OP_ACCEPT, new AcceptEventHandler(
                        dispatcher.getDemultiplexer()));
        dispatcher.registerEventHandler(
                SelectionKey.OP_READ, new ReadEventHandler(
                        dispatcher.getDemultiplexer()));
/*
        dispatcher.registerEventHandler(
                SelectionKey.OP_WRITE, new WriteEventHandler());
*/
        dispatcher.run(); // Run the dispatcher loop
    }

    @Override
    public void stopThread() {

    }

    @Override
    public void pauseThread() {

    }

    private class Dispatcher
    {
        private Map<Integer, EventHandler> registeredHandlers =
                new ConcurrentHashMap<Integer, EventHandler>();
        private Selector demultiplexer;

        public Dispatcher() throws Exception {
            demultiplexer = Selector.open();
        }

        public Selector getDemultiplexer() {
            return demultiplexer;
        }

        public void registerEventHandler(
                int eventType, EventHandler eventHandler) {
            registeredHandlers.put(eventType, eventHandler);
        }

        // Used to register ServerSocketChannel with the
        // selector to accept incoming client connections
        public void registerChannel(
                int eventType, SelectableChannel channel) throws Exception {
            channel.register(demultiplexer, eventType);
        }

        public void run() {
            try {
                while (true)
                { // Loop indefinitely
                    demultiplexer.select();

                    Set<SelectionKey> readyHandles = demultiplexer.selectedKeys();
                    Iterator<SelectionKey> handleIterator = readyHandles.iterator();

                    while (handleIterator.hasNext())
                    {
                        SelectionKey handle = handleIterator.next();

                        if (handle.isAcceptable())
                        {
                            EventHandler handler = registeredHandlers.get(SelectionKey.OP_ACCEPT);
                            handler.handleEvent(handle);
                            // Note : Here we don't remove this handle from
                            // selector since we want to keep listening to
                            // new client connections
                        }

                        if (handle.isReadable()) {
                            EventHandler handler = registeredHandlers.get(SelectionKey.OP_READ);
                            handler.handleEvent(handle);
                            handleIterator.remove();
                        }

                        if (handle.isWritable()) {
                            EventHandler handler =
                                    registeredHandlers.get(SelectionKey.OP_WRITE);
                            handler.handleEvent(handle);
                            handleIterator.remove();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private class ReadEventHandler implements EventHandler
    {
        private Selector selector;
        private ByteBuffer inputBuffer = ByteBuffer.allocate(2048);

        public ReadEventHandler(Selector selector) {
            this.selector = selector;
        }

        @Override
        public void handleEvent(SelectionKey handle) throws Exception
        {
            SocketChannel socketChannel = (SocketChannel) handle.channel();
            socketChannel.read(inputBuffer); // Read data from client

            inputBuffer.flip();
            // Rewind the buffer to start reading from the beginning

            byte[] buffer = new byte[inputBuffer.limit()];
            inputBuffer.get(buffer);

            System.out.println("Received message from client : " +
                    new String(buffer));
            inputBuffer.flip();
            // Rewind the buffer to start reading from the beginning
            // Register the interest for writable readiness event for
            // this channel in order to echo back the message

            socketChannel.register(
                    selector, SelectionKey.OP_WRITE, inputBuffer);
        }
    }

    private class AcceptEventHandler implements EventHandler
    {
        private Selector selector;

        public AcceptEventHandler(Selector selector) {
            this.selector = selector;
        }

        @Override
        public void handleEvent(SelectionKey handle) throws Exception
        {
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) handle.channel();
            SocketChannel socketChannel = serverSocketChannel.accept();

            if (socketChannel != null)
            {
                socketChannel.configureBlocking(false);
                socketChannel.register(selector, SelectionKey.OP_READ, SelectionKey.OP_WRITE);
            }
        }
    }
}
