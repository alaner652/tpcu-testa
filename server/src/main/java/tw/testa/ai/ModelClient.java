package tw.testa.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tw.testa.settings.SettingsService;

/** The only place that talks to a model: an OpenAI-compatible API, configured in the admin pages. */
@Component
public class ModelClient {
    static final JsonMapper JSON = JsonMapper.builder().build();

    private final SettingsService settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    ModelClient(SettingsService settings) {
        this.settings = settings;
    }

    /** message: Chinese text for the administrator. models: what the endpoint offers, for the model picker. */
    public record Probe(boolean ok, String message, List<String> models) {}

    boolean configured() {
        return settings.ai().ready();
    }

    private static String api(String baseUrl) {
        String base = baseUrl.strip().replaceAll("/+$", "");
        return base.endsWith("/v1") ? base : base + "/v1";
    }

    /** One chat completion, answered as JSON matching the schema. */
    JsonNode chatJson(String system, String user, JsonNode schema, int maxTokens) throws IOException, InterruptedException {
        SettingsService.Ai cfg = settings.ai();
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

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(api(cfg.baseUrl()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(cfg.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8));
        if (!cfg.apiKey().isBlank()) req.header("Authorization", "Bearer " + cfg.apiKey());

        HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) throw new IOException("model endpoint returned HTTP " + res.statusCode());
        String content = JSON.readTree(res.body()).path("choices").path(0).path("message").path("content").asString("");
        return JSON.readTree(content);
    }

    /** Asks an endpoint for its model list, to check a URL and key before they are saved. Never throws. */
    public Probe probe(String baseUrl, String apiKey) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(api(baseUrl) + "/models"))
                    .timeout(Duration.ofSeconds(8)).GET();
            if (apiKey != null && !apiKey.isBlank()) req.header("Authorization", "Bearer " + apiKey);
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() == 401 || res.statusCode() == 403) {
                return new Probe(false, "AI 服務拒絕了這次連線，請確認 API 金鑰是否正確。", List.of());
            }
            if (res.statusCode() != 200) {
                return new Probe(false, "這個網址有回應，但不是 AI 服務（HTTP %d）。請確認網址和連接埠。".formatted(res.statusCode()), List.of());
            }
            List<String> models = new ArrayList<>();
            for (JsonNode m : JSON.readTree(res.body()).path("data")) {
                String id = m.path("id").asString("");
                if (!id.isBlank() && models.size() < 50) models.add(id);
            }
            return new Probe(true, models.isEmpty() ? "連線成功。" : "連線成功，找到 %d 個模型。".formatted(models.size()), models);
        } catch (HttpTimeoutException e) {
            return new Probe(false, "等了 8 秒沒有回應。請確認 AI 主機已經開機，而且這台伺服器連得到它。", List.of());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Probe(false, "測試被中斷了，請再試一次。", List.of());
        } catch (Exception e) {
            return new Probe(false, "連不上這個網址。請確認網址和連接埠正確，而且 AI 主機已經開機。", List.of());
        }
    }
}
