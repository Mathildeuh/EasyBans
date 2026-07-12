package fr.mathilde.easybans.scope;

import com.velocitypowered.api.proxy.ProxyServer;

import java.util.Optional;

/**
 * Resolves a punishment's scope: empty means "global" (applies network-wide), present means
 * "only on this backend server". A player banned locally can still connect to every other
 * server on the proxy - enforced in the listener layer, not here.
 */
public final class ScopeResolver {

    private final ProxyServer proxy;
    private final String defaultScope;

    public ScopeResolver(ProxyServer proxy, String defaultScope) {
        this.proxy = proxy;
        this.defaultScope = defaultScope;
    }

    /** {@code explicitServer} is the value of a command's {@code -server:<name>} flag, if any. */
    public Optional<String> resolve(Optional<String> explicitServer) {
        String raw = explicitServer.orElse(defaultScope);
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("global")) {
            return Optional.empty();
        }
        return Optional.of(raw);
    }

    public boolean isKnownServer(String name) {
        return proxy.getServer(name).isPresent();
    }
}
