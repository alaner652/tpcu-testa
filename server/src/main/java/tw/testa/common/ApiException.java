package tw.testa.common;

import org.springframework.http.HttpStatus;

/** 帶 HTTP 狀態碼、可直接顯示給使用者的錯誤。 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

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
