package net.flowbridge.connector.stalwart;

import com.evolveum.polygon.rest.AbstractRestConnector;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Implements CRUD operations against the Stalwart REST Management API.
 * <p>
 * Stalwart API endpoints:
 *   GET    /api/principal?type=individual         (list principals)
 *   GET    /api/principal/{name}                   (get principal)
 *   POST   /api/principal                          (create principal)
 *   PATCH  /api/principal/{name}                   (update principal — array of operations)
 *   DELETE /api/principal/{name}                   (delete principal)
 * <p>
 * Note: Stalwart PATCH body is a JSON array of operation objects, not a flat map.
 * Example: [{"action":"set","field":"description","value":"new desc"}]
 */
public class StalwartOperations {

    private static final Log LOG = Log.getLog(StalwartOperations.class);

    private final StalwartConfiguration config;
    private final AbstractRestConnector<?> connector;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public StalwartOperations(StalwartConfiguration config, AbstractRestConnector<?> connector) {
        this.config = config;
        this.connector = connector;
    }

    // ---- API Helpers ----

    private String basicAuthHeader() {
        String username = config.getUsername();
        StringBuilder password = new StringBuilder();
        config.getPassword().access(chars -> password.append(new String(chars)));
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String apiBase() {
        String addr = config.getServiceAddress();
        // Ensure it ends without trailing slash
        return addr.endsWith("/") ? addr.substring(0, addr.length() - 1) : addr;
    }

    private HttpRequest.Builder apiRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(apiBase() + path))
                .header("Authorization", basicAuthHeader())
                .header("Content-Type", "application/json");
    }

    private JsonNode apiGet(String path) {
        try {
            HttpRequest request = apiRequest(path).GET().build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() >= 400) {
                throw new ConnectorException("Stalwart API error: HTTP "
                        + response.statusCode() + " on GET " + path + ": " + response.body());
            }

            JsonNode body = mapper.readTree(response.body());
            // Stalwart wraps responses in {"data": ...}
            return body.has("data") ? body.get("data") : body;
        } catch (IOException | InterruptedException e) {
            throw new ConnectorException("Stalwart API request failed: GET " + path, e);
        }
    }

    private void apiPost(String path, String jsonBody) {
        try {
            HttpRequest request = apiRequest(path)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 409) {
                throw new AlreadyExistsException("Principal already exists");
            }
            if (response.statusCode() >= 400) {
                throw new ConnectorException("Stalwart API error: HTTP "
                        + response.statusCode() + " on POST " + path + ": " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new ConnectorException("Stalwart API request failed: POST " + path, e);
        }
    }

    private void apiPatch(String path, String jsonBody) {
        try {
            HttpRequest request = apiRequest(path)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                throw new UnknownUidException("Principal not found");
            }
            if (response.statusCode() >= 400) {
                throw new ConnectorException("Stalwart API error: HTTP "
                        + response.statusCode() + " on PATCH " + path + ": " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new ConnectorException("Stalwart API request failed: PATCH " + path, e);
        }
    }

    private void apiDelete(String path) {
        try {
            HttpRequest request = apiRequest(path).DELETE().build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                throw new UnknownUidException("Principal not found");
            }
            if (response.statusCode() >= 400) {
                throw new ConnectorException("Stalwart API error: HTTP "
                        + response.statusCode() + " on DELETE " + path + ": " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new ConnectorException("Stalwart API request failed: DELETE " + path, e);
        }
    }

    // ---- CRUD Operations ----

    public Uid createPrincipal(Set<Attribute> attributes) {
        ObjectNode principal = mapper.createObjectNode();

        String principalName = null;
        for (Attribute attr : attributes) {
            String attrName = attr.getName();
            if (Name.NAME.equals(attrName)) {
                principalName = AttributeUtil.getStringValue(attr);
                principal.put("name", principalName);
            } else if (StalwartConnector.ATTR_EMAILS.equals(attrName)) {
                ArrayNode emails = mapper.createArrayNode();
                attr.getValue().forEach(v -> emails.add(v.toString()));
                principal.set("emails", emails);
            } else if (StalwartConnector.ATTR_TYPE.equals(attrName)) {
                principal.put("type", AttributeUtil.getStringValue(attr));
            } else if (StalwartConnector.ATTR_QUOTA.equals(attrName)) {
                principal.put("quota", AttributeUtil.getLongValue(attr));
            } else if (StalwartConnector.ATTR_DESCRIPTION.equals(attrName)) {
                principal.put("description", AttributeUtil.getStringValue(attr));
            } else if (StalwartConnector.ATTR_TENANT.equals(attrName)) {
                principal.put("tenant", AttributeUtil.getStringValue(attr));
            }
        }

        // Apply defaults
        if (!principal.has("type")) {
            principal.put("type", "individual");
        }
        if (!principal.has("quota")) {
            principal.put("quota", config.getDefaultQuotaBytes());
        }
        if (!principal.has("tenant") && config.getDefaultTenant() != null) {
            principal.put("tenant", config.getDefaultTenant());
        }

        // Generate default emails if not provided
        if (!principal.has("emails") && principalName != null) {
            String domain = config.getDefaultDomain();
            ArrayNode emails = mapper.createArrayNode();
            emails.add(principalName + "@" + domain);
            emails.add(principalName + "-support@" + domain);
            emails.add(principalName + "-alerts@" + domain);
            emails.add(principalName + "-notify@" + domain);
            principal.set("emails", emails);
        }

        try {
            apiPost("/principal", mapper.writeValueAsString(principal));
            LOG.info("Created Stalwart principal: {0}", principalName);
            // Stalwart uses the principal name as the identifier
            return new Uid(principalName);
        } catch (IOException e) {
            throw new ConnectorException("Failed to serialize principal", e);
        }
    }

    public Set<AttributeDelta> updatePrincipal(Uid uid, Set<AttributeDelta> modifications) {
        // Stalwart PATCH uses array of operations
        ArrayNode operations = mapper.createArrayNode();

        for (AttributeDelta delta : modifications) {
            if (delta.getValuesToReplace() == null || delta.getValuesToReplace().isEmpty()) {
                continue;
            }

            String name = delta.getName();
            List<?> values = delta.getValuesToReplace();

            if (StalwartConnector.ATTR_DESCRIPTION.equals(name)) {
                ObjectNode descOp = mapper.createObjectNode();
                descOp.put("action", "set");
                descOp.put("field", "description");
                descOp.put("value", values.get(0).toString());
                operations.add(descOp);
            } else if (StalwartConnector.ATTR_EMAILS.equals(name)) {
                ObjectNode emailOp = mapper.createObjectNode();
                emailOp.put("action", "set");
                emailOp.put("field", "emails");
                ArrayNode emails = mapper.createArrayNode();
                values.forEach(v -> emails.add(v.toString()));
                emailOp.set("value", emails);
                operations.add(emailOp);
            } else if (StalwartConnector.ATTR_QUOTA.equals(name)) {
                ObjectNode quotaOp = mapper.createObjectNode();
                quotaOp.put("action", "set");
                quotaOp.put("field", "quota");
                quotaOp.put("value", (Long) values.get(0));
                operations.add(quotaOp);
            } else if (OperationalAttributes.ENABLE_NAME.equals(name)) {
                ObjectNode enableOp = mapper.createObjectNode();
                enableOp.put("action", "set");
                enableOp.put("field", "enabled");
                enableOp.put("value", (Boolean) values.get(0));
                operations.add(enableOp);
            }
        }

        if (operations.size() > 0) {
            try {
                apiPatch("/principal/" + URLEncoder.encode(uid.getUidValue(), StandardCharsets.UTF_8),
                        mapper.writeValueAsString(operations));
            } catch (IOException e) {
                throw new ConnectorException("Failed to serialize principal update", e);
            }
        }

        return Collections.emptySet();
    }

    public Uid replacePrincipal(Uid uid, Set<Attribute> replaceAttributes) {
        Set<AttributeDelta> deltas = new HashSet<>();
        for (Attribute attr : replaceAttributes) {
            deltas.add(AttributeDeltaBuilder.build(attr.getName(), attr.getValue()));
        }
        updatePrincipal(uid, deltas);
        return uid;
    }

    public void deletePrincipal(Uid uid) {
        apiDelete("/principal/" + URLEncoder.encode(uid.getUidValue(), StandardCharsets.UTF_8));
        LOG.info("Deleted Stalwart principal: {0}", uid.getUidValue());
    }

    public void searchPrincipals(StalwartFilter filter, ResultsHandler handler) {
        if (filter != null && (filter.getType() == StalwartFilter.FilterType.BY_UID
                || filter.getType() == StalwartFilter.FilterType.BY_NAME)) {
            String name = filter.getValue();
            JsonNode principal = apiGet("/principal/" + URLEncoder.encode(name, StandardCharsets.UTF_8));
            if (principal != null) {
                handler.handle(principalToConnectorObject(principal));
            }
            return;
        }

        // List all principals of type individual
        JsonNode principals = apiGet("/principal?type=individual");
        if (principals != null && principals.isArray()) {
            for (JsonNode nameNode : principals) {
                String name = nameNode.asText();
                JsonNode principal = apiGet("/principal/" + URLEncoder.encode(name, StandardCharsets.UTF_8));
                if (principal != null) {
                    if (!handler.handle(principalToConnectorObject(principal))) {
                        break;
                    }
                }
            }
        }
    }

    public void testConnection() {
        JsonNode result = apiGet("/principal?type=individual&limit=1");
        if (result == null) {
            throw new ConnectorException("Failed to connect to Stalwart Admin API");
        }
        LOG.info("Stalwart connection test successful");
    }

    // ---- Mapping ----

    private ConnectorObject principalToConnectorObject(JsonNode principal) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(new ObjectClass(StalwartConnector.OBJECT_CLASS_PRINCIPAL));

        String name = principal.path("name").asText();
        builder.setUid(name);
        builder.setName(name);

        // Emails
        if (principal.has("emails") && principal.get("emails").isArray()) {
            List<String> emails = new ArrayList<>();
            principal.get("emails").forEach(e -> emails.add(e.asText()));
            builder.addAttribute(StalwartConnector.ATTR_EMAILS, emails);
        }

        // Type
        if (principal.has("type") && !principal.get("type").isNull()) {
            builder.addAttribute(StalwartConnector.ATTR_TYPE,
                    principal.get("type").asText());
        }

        // Quota
        if (principal.has("quota")) {
            builder.addAttribute(StalwartConnector.ATTR_QUOTA,
                    principal.get("quota").asLong());
        }

        // Description
        if (principal.has("description") && !principal.get("description").isNull()) {
            builder.addAttribute(StalwartConnector.ATTR_DESCRIPTION,
                    principal.get("description").asText());
        }

        // Tenant
        if (principal.has("tenant") && !principal.get("tenant").isNull()) {
            builder.addAttribute(StalwartConnector.ATTR_TENANT,
                    principal.get("tenant").asText());
        }

        // Enabled status
        builder.addAttribute(OperationalAttributes.ENABLE_NAME,
                principal.path("enabled").asBoolean(true));

        return builder.build();
    }
}
