package uk.collectionofcreations.coc.common;

/**
 * 业务异常：携带业务码与 HTTP 状态码。
 */
public class BizException extends RuntimeException {

    private final int code;
    private final int httpStatus;

    public BizException(int code, String message) {
        this(code, message, 200);
    }

    public BizException(int code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static BizException unauthorized(String msg) {
        return new BizException(401, msg, 401);
    }

    public static BizException forbidden(String msg) {
        return new BizException(403, msg, 403);
    }

    public static BizException notFound(String msg) {
        return new BizException(404, msg, 404);
    }

    public static BizException badRequest(String msg) {
        return new BizException(400, msg, 400);
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
