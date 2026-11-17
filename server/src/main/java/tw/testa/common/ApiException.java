package tw.testa.common;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** An error with an HTTP status and a message that can be shown to the user as is. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    /** Optional per-field messages, keyed like bean validation paths (e.g. "people[2].email"). */
    private final Map<String, String> errors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, String> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = errors;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Map<String, String> errors() { return errors; }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", message);
    }
    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "forbidden", "你沒有這個操作的權限。");
    }
}
