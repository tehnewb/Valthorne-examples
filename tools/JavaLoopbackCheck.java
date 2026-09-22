// SPDX-License-Identifier: Apache-2.0

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.Selector;

/**
 * Diagnoses Java's local connections without Gradle or engine dependencies.
 *
 * <p>Run with {@code java tools/JavaLoopbackCheck.java} in the affected launch environment. TCP and
 * selector creation are checked separately because a Windows selector may use a Unix-domain wakeup
 * socket. Failures print their complete cause chain. No settings are changed.
 */
public final class JavaLoopbackCheck {
    /** Prevents construction of the diagnostic entry point. */
    private JavaLoopbackCheck() {}

    /**
     * Reports the runtime, probes a local TCP connection and opens a Java NIO selector.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        System.out.println("Java: " + System.getProperty("java.runtime.version"));
        System.out.println("Java home: " + System.getProperty("java.home"));
        System.out.println(
                "OS: " + System.getProperty("os.name") + " / " + System.getProperty("os.arch"));
        System.out.println("Java temp: " + System.getProperty("java.io.tmpdir"));
        System.out.println("TEMP: " + System.getenv("TEMP"));
        System.out.println(
                "Socket temp override: "
                        + System.getProperty("jdk.net.unixdomain.tmpdir", "<unset>"));
        boolean failed = false;
        System.out.println("Checking TCP loopback...");
        try (var listener = new ServerSocket();
                var client = new Socket()) {
            listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            listener.setSoTimeout(3000);
            client.connect(listener.getLocalSocketAddress(), 3000);
            try (var accepted = listener.accept()) {
                System.out.println("TCP loopback: PASS");
            }
        } catch (Exception failure) {
            failed = true;
            failure.printStackTrace(System.out);
        }
        System.out.println("Checking NIO selector (may use a different local socket mechanism)...");
        try (var selector = Selector.open()) {
            System.out.println("NIO selector: PASS (" + selector.getClass().getName() + ")");
        } catch (Exception failure) {
            failed = true;
            failure.printStackTrace(System.out);
        }
        if (failed) System.exit(1);
    }
}
