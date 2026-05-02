// Compile-time stub of the JSR-120 Wireless Messaging API.
// Real handsets supply a vendor implementation at runtime.
package javax.wireless.messaging;

public interface Message {
    String getAddress();
    void   setAddress(String addr);
    java.util.Date getTimestamp();
}
