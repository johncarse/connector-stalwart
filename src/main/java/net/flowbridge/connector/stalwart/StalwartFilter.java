package net.flowbridge.connector.stalwart;

/**
 * Filter representation for Stalwart principal searches.
 */
public class StalwartFilter {

    public enum FilterType {
        BY_UID,
        BY_NAME,
        ALL
    }

    private final FilterType type;
    private final String value;

    private StalwartFilter(FilterType type, String value) {
        this.type = type;
        this.value = value;
    }

    public static StalwartFilter byUid(String uid) {
        return new StalwartFilter(FilterType.BY_UID, uid);
    }

    public static StalwartFilter byName(String name) {
        return new StalwartFilter(FilterType.BY_NAME, name);
    }

    public static StalwartFilter all() {
        return new StalwartFilter(FilterType.ALL, null);
    }

    public FilterType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }
}
