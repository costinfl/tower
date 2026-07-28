package dev.tower.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Identifies this Tower instance.
 *
 * <p>ADR-009 gives every developer their own instance, and ADR-010 requires an
 * exported Observation to keep naming the instance that first observed it.
 * Without an identity here, an imported fact would silently look as though the
 * receiving instance had seen it — precisely the claim ADR-010 forbids.
 *
 * <p>The name is the machine's host name, overridable through
 * {@code tower.instance-name} in config.yml for anyone who wants something
 * friendlier. It is descriptive rather than secure: nothing is authenticated
 * here, and the name only ever says where a fact came from.
 */
public final class TowerInstance {

    public static final String UNKNOWN = "unknown-instance";

    private final String name;

    public TowerInstance(String configuredName) {
        this.name = resolve(configuredName);
    }

    private static String resolve(String configuredName) {
        if (configuredName != null && !configuredName.isBlank()) {
            return configuredName.trim();
        }
        try {
            String host = InetAddress.getLocalHost().getHostName();
            if (host != null && !host.isBlank()) {
                return host.trim().toLowerCase(Locale.ROOT);
            }
        } catch (UnknownHostException e) {
            // Falls through: an unresolvable host name is not an error, it just
            // means the export says "unknown" rather than inventing a name.
        }
        return UNKNOWN;
    }

    public String name() {
        return name;
    }
}
