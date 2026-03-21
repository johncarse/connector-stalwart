package net.flowbridge.connector.stalwart;

import com.evolveum.polygon.rest.AbstractRestConnector;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.operations.*;

import java.util.Set;

/**
 * ConnId connector for managing Stalwart mail server principals (mailboxes).
 * <p>
 * Uses the Stalwart REST Management API to create, update, delete, and search
 * mail principals. Designed for the Flowbridge Agent Identity Platform.
 * <p>
 * Managed attributes:
 * - name (principal name, e.g. "sandy")
 * - emails (multi-valued: primary + aliases)
 * - enabled
 * - quota
 * - type (individual, group, etc.)
 * - tenant
 * - description
 * <p>
 * Stalwart API reference: https://stalw.art/docs/api/management/overview
 */
@ConnectorClass(
        displayNameKey = "connector.stalwart.display",
        configurationClass = StalwartConfiguration.class
)
public class StalwartConnector
        extends AbstractRestConnector<StalwartConfiguration>
        implements CreateOp, UpdateOp, DeleteOp, SearchOp<StalwartFilter>, TestOp, SchemaOp {

    private static final Log LOG = Log.getLog(StalwartConnector.class);

    public static final String OBJECT_CLASS_PRINCIPAL = "MailPrincipal";

    // Attribute names
    public static final String ATTR_PRINCIPAL_NAME = Name.NAME;
    public static final String ATTR_ENABLED = OperationalAttributes.ENABLE_NAME;
    public static final String ATTR_EMAILS = "emails";
    public static final String ATTR_TYPE = "principalType";
    public static final String ATTR_QUOTA = "quota";
    public static final String ATTR_DESCRIPTION = "description";
    public static final String ATTR_TENANT = "tenant";

    private StalwartOperations operations;

    @Override
    public void init(Configuration configuration) {
        super.init(configuration);
        this.operations = new StalwartOperations(getConfiguration(), this);
        LOG.info("Stalwart connector initialized for domain: {0}",
                getConfiguration().getDefaultDomain());
    }

    @Override
    public Schema schema() {
        SchemaBuilder schemaBuilder = new SchemaBuilder(StalwartConnector.class);

        ObjectClassInfoBuilder principalBuilder = new ObjectClassInfoBuilder();
        principalBuilder.setType(OBJECT_CLASS_PRINCIPAL);

        // Principal name
        principalBuilder.addAttributeInfo(AttributeInfoBuilder.build(
                Name.NAME, String.class));

        // Stalwart internal ID
        principalBuilder.addAttributeInfo(AttributeInfoBuilder.build(
                Uid.NAME, String.class));

        // Enabled/disabled
        principalBuilder.addAttributeInfo(AttributeInfoBuilder.build(
                ATTR_ENABLED, Boolean.class));

        // Email addresses (multi-valued: primary + aliases)
        principalBuilder.addAttributeInfo(AttributeInfoBuilder.define(ATTR_EMAILS)
                .setType(String.class)
                .setMultiValued(true)
                .build());

        // Principal type
        principalBuilder.addAttributeInfo(AttributeInfoBuilder.build(
                ATTR_TYPE, String.class));

        // Quota in bytes
        principalBuilder.addAttributeInfo(AttributeInfoBuilder.build(
                ATTR_QUOTA, Long.class));

        // Description
        principalBuilder.addAttributeInfo(AttributeInfoBuilder.build(
                ATTR_DESCRIPTION, String.class));

        // Tenant
        principalBuilder.addAttributeInfo(AttributeInfoBuilder.build(
                ATTR_TENANT, String.class));

        schemaBuilder.defineObjectClass(principalBuilder.build());
        return schemaBuilder.build();
    }

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> createAttributes,
                      OperationOptions options) {
        validateObjectClass(objectClass);
        LOG.info("Creating Stalwart principal");
        return operations.createPrincipal(createAttributes);
    }

    @Override
    public Set<AttributeDelta> updateDelta(ObjectClass objectClass, Uid uid,
                                           Set<AttributeDelta> modifications,
                                           OperationOptions options) {
        validateObjectClass(objectClass);
        LOG.info("Updating Stalwart principal: {0}", uid.getUidValue());
        return operations.updatePrincipal(uid, modifications);
    }

    @Override
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes,
                      OperationOptions options) {
        validateObjectClass(objectClass);
        LOG.info("Replacing Stalwart principal attributes: {0}", uid.getUidValue());
        return operations.replacePrincipal(uid, replaceAttributes);
    }

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        validateObjectClass(objectClass);
        LOG.info("Deleting Stalwart principal: {0}", uid.getUidValue());
        operations.deletePrincipal(uid);
    }

    @Override
    public FilterTranslator<StalwartFilter> createFilterTranslator(
            ObjectClass objectClass, OperationOptions options) {
        return new StalwartFilterTranslator();
    }

    @Override
    public void executeQuery(ObjectClass objectClass, StalwartFilter query,
                             ResultsHandler handler, OperationOptions options) {
        validateObjectClass(objectClass);
        LOG.info("Searching Stalwart principals");
        operations.searchPrincipals(query, handler);
    }

    @Override
    public void test() {
        LOG.info("Testing Stalwart connection");
        operations.testConnection();
    }

    private void validateObjectClass(ObjectClass objectClass) {
        if (!OBJECT_CLASS_PRINCIPAL.equals(objectClass.getObjectClassValue())) {
            throw new ConnectorException("Unsupported object class: " + objectClass);
        }
    }
}
