// Compile-time stub of the JSR-120 Wireless Messaging API.
// Real handsets supply a vendor implementation at runtime; this file only
// exists so our MIDlet sources compile against the WMA interfaces.
package javax.wireless.messaging;

import javax.microedition.io.Connection;
import java.io.IOException;
import java.io.InterruptedIOException;

public interface MessageConnection extends Connection {
    String TEXT_MESSAGE   = "text";
    String BINARY_MESSAGE = "binary";
    String MULTIPART_MESSAGE = "multipart";

    Message newMessage(String type);
    Message newMessage(String type, String address);

    void    send(Message msg) throws IOException, InterruptedIOException;
    Message receive() throws IOException, InterruptedIOException;
    int     numberOfSegments(Message msg);
    void    setMessageListener(MessageListener l) throws IOException;
}
