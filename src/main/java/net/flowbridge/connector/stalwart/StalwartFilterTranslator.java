package net.flowbridge.connector.stalwart;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.*;

/**
 * Translates ConnId filter queries into StalwartFilter instances.
 */
public class StalwartFilterTranslator extends AbstractFilterTranslator<StalwartFilter> {

    @Override
    protected StalwartFilter createEqualsExpression(EqualsFilter filter, boolean not) {
        if (not) {
            return null;
        }

        Attribute attr = filter.getAttribute();
        if (attr == null || attr.getValue() == null || attr.getValue().isEmpty()) {
            return null;
        }

        String value = attr.getValue().get(0).toString();

        if (Uid.NAME.equals(attr.getName())) {
            return StalwartFilter.byUid(value);
        }
        if (Name.NAME.equals(attr.getName())) {
            return StalwartFilter.byName(value);
        }

        return null;
    }
}
