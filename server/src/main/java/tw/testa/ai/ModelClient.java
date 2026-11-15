package tw.testa.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tw.testa.config.TestaProperties;

/** The only place that talks to a model: one chat completion on an OpenAI-compatible API, answered as JSON matching a schema. */
@Component
class ModelClient {
    static final JsonMapper JSON = JsonMapper.builder().build();

    private final TestaProperties.Ai cfg;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    ModelClient(TestaProperties props) {
        this.cfg = props.ai();
    }

    boolean configured() {
        return !cfg.baseUrl().isBlank();
    }

    JsonNode chatJson(String system, String user, JsonNode schema, int maxTokens) throws IOException, InterruptedException {
        ObjectNode body = JSON.createObjectNode();
        if (!cfg.model().isBlank()) body.put("model", cfg.model());
        body.put("temperature", 0.1);
        body.put("max_tokens", maxTokens);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
        ObjectNode format = body.putObject("response_format");
        format.put("type", "json_schema");
        ObjectNode js = format.putObject("json_schema");
        js.put("name", "out");
        js.put("strict", true);
        js.set("schema", schema);

        String base = cfg.baseUrl().strip().replaceAll("/+$", "");
        if (!base.endsWith("/v1")) base += "/v1";
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(base + "/chat/completions"))
                .timeout(Duration.ofSeconds(cfg.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8));
        if (!cfg.apiKey().isBlank()) req.header("Authorization", "Bearer " + cfg.apiKey());

        HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new IOException("model endpoint returned HTTP " + res.statusCode());
        String content = JSON.readTree(res.body()).path("choices").path(0).path("message").path("content").asString("");
        return JSON.readTree(content);
    }
}
