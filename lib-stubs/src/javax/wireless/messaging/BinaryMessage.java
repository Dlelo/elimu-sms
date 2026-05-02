// Compile-time stub of the JSR-120 Wireless Messaging API.
package javax.wireless.messaging;

public interface BinaryMessage extends Message {
    byte[] getPayloadData();
    void   setPayloadData(byte[] data);
}
