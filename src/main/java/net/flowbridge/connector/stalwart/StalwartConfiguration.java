package net.flowbridge.connector.stalwart;

import com.evolveum.polygon.rest.AbstractRestConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

/**
 * Configuration for the Stalwart Mail Server connector.
 * Extends AbstractRestConfiguration which provides serviceAddress, username, password, authMethod.
 * <p>
 * The serviceAddress should point to the Stalwart admin API base URL
 * (e.g. https://mail.lessthan.ai/api).
 * Authentication uses Basic auth with admin credentials.
 */
public class StalwartConfiguration extends AbstractRestConfiguration {

    private String defaultTenant;
    private String defaultDomain = "ai.flowbridge.com";
    private long defaultQuotaBytes = 1073741824L; // 1GB

    @ConfigurationProperty(
            displayMessageKey = "stalwart.defaultTenant",
            helpMessageKey = "stalwart.defaultTenant.help",
            order = 10
    )
    public String getDefaultTenant() {
        return defaultTenant;
    }

    public void setDefaultTenant(String defaultTenant) {
        this.defaultTenant = defaultTenant;
    }

    @ConfigurationProperty(
            displayMessageKey = "stalwart.defaultDomain",
            helpMessageKey = "stalwart.defaultDomain.help",
            order = 11,
            required = true
    )
    public String getDefaultDomain() {
        return defaultDomain;
    }

    public void setDefaultDomain(String defaultDomain) {
        this.defaultDomain = defaultDomain;
    }

    @ConfigurationProperty(
            displayMessageKey = "stalwart.defaultQuotaBytes",
            helpMessageKey = "stalwart.defaultQuotaBytes.help",
            order = 12
    )
    public long getDefaultQuotaBytes() {
        return defaultQuotaBytes;
    }

    public void setDefaultQuotaBytes(long defaultQuotaBytes) {
        this.defaultQuotaBytes = defaultQuotaBytes;
    }
}
