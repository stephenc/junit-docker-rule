package pl.domzal.junit.docker.rule;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Info;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import org.junit.AssumptionViolatedException;

/**
 * Utility class with some helper methods for testing {@link DockerRule}.
 */
public final class DockerRuleTestingHelper {

    private DockerRuleTestingHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Attempts to determine the IP address that the supplied {@link DockerRule} will expose ports on.
     *
     * @param rule the rule.
     * @return the IP address.
     * @throws DockerException If things go wrong.
     * @throws InterruptedException If interrupted.
     * @throws SocketException If things go wrong.
     * @throws UnknownHostException If the IP address cannot be resolved.
     * @throws AssumptionViolatedException If the Docker daemon is running on a Mac/Windows machine that is not
     *         connected to any networks and hence only has loopback addresses available.
     */
    public static String exposedPortAddress(DockerRule rule)
            throws DockerException, InterruptedException, SocketException, UnknownHostException {
        DockerClient client = rule.getDockerClient();
        Info info = client.info();
        if (info.operatingSystem().matches("^Docker (for Windows|for Mac|Desktop)$")) {
            // Mac and Windows do not expose the ports on the gateway, rather on the host directly
            if ("localhost".equals(client.getHost()) || "127.0.0.1".equals(client.getHost())) {
                // need different address that can be routed
                Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                while (networkInterfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = networkInterfaces.nextElement();
                    Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress inetAddress = inetAddresses.nextElement();
                        if (!(inetAddress instanceof Inet4Address)) {
                            // TODO determine what the correct rules for eliminating the IPv6 addresses are
                            continue;
                        }
                        if (inetAddress.getHostAddress().startsWith("127.")) {
                            continue;
                        }
                        return inetAddress.getHostAddress();
                    }
                }
                throw new AssumptionViolatedException("Could not find a non-loopback IPv4 address for this machine");
            } else {
                return InetAddress.getByName(client.getHost()).getHostAddress();
            }
        } else {
            // Linux has the happy coincidence that the gateway is also the host and hence the ports exposed on the
            // host are also exposed on the gateway
            return rule.getDockerContainerGateway();
        }
    }
}
