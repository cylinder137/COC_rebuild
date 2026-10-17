package uk.collectionofcreations.coc.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理：统一返回 {"code":..,"message":..,"data":null}
 * <p>安全铁律（2026-10-17）：
 * <ul>
 *   <li>对外响应体<b>绝不</b>包含异常原文、SQL、表名、包名、类名等内部信息；</li>
 *   <li>原始异常只写服务端日志（log.error），供运维排查；</li>
 *   <li>参数格式错误 → 400，方法不支持 → 405，其余未预期异常 → 500 + 固定文案。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 对外统一的 500 文案（不含任何内部细节） */
    private static final String MSG_INTERNAL = "服务器内部错误，请稍后重试";

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResource(Exception e) {
        return ResponseEntity.status(404).body(ApiResponse.error(404, "接口或资源不存在"));
    }

    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoHandler(Exception e) {
        return ResponseEntity.status(404).body(ApiResponse.error(404, "接口或资源不存在"));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadBody(Exception e) {
        return ResponseEntity.status(400).body(ApiResponse.error(400, "请求体格式错误（JSON 无法解析）"));
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> handleBiz(BizException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Object>> handleDuplicate(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMessage());
        return ResponseEntity.ok(ApiResponse.error(1002, "数据已存在（唯一键冲突）"));
    }

    /** 参数类型不匹配（如 /projects/abc）→ 400，不回显类型名与参数值 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: name={} value={}", e.getName(), e.getValue());
        return ResponseEntity.status(400)
                .body(ApiResponse.error(400, "参数格式错误：" + safeName(e.getName())));
    }

    /** 缺少必填请求参数 → 400 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(400)
                .body(ApiResponse.error(400, "缺少必要参数：" + safeName(e.getParameterName())));
    }

    /** Content-Type 不支持 → 415 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(415).body(ApiResponse.error(415, "不支持的请求内容类型"));
    }

    /** 方法不支持 → 405（带 Allow 头），不再落到 500 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethod(HttpRequestMethodNotSupportedException e) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(405);
        if (e.getSupportedHttpMethods() != null && !e.getSupportedHttpMethods().isEmpty()) {
            builder.header(HttpHeaders.ALLOW, e.getSupportedHttpMethods().toString().replaceAll("[\\[\\]]", ""));
        }
        return builder.body(ApiResponse.error(405, "请求方法不支持：" + e.getMethod()));
    }

    /** 业务参数非法 → 400（不回显内部原文） */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return ResponseEntity.status(400).body(ApiResponse.error(400, "请求参数不合法"));
    }

    /** 数据库层异常（含 MyBatis DataAccessException / 数据截断 / 约束冲突）→ 500 + 固定文案，绝不回显 SQL */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataAccess(DataAccessException e) {
        log.error("数据库异常（对外隐藏细节）", e);
        return ResponseEntity.status(500).body(ApiResponse.error(500, MSG_INTERNAL));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOther(Exception e) {
        log.error("未处理异常（对外隐藏细节）", e);
        return ResponseEntity.status(500).body(ApiResponse.error(500, MSG_INTERNAL));
    }

    /** 参数名白名单化：只允许字母/数字/下划线，避免把奇怪内容回显出去 */
    private static String safeName(String name) {
        if (name == null || name.isEmpty()) {
            return "参数";
        }
        String cleaned = name.replaceAll("[^A-Za-z0-9_]", "");
        return cleaned.isEmpty() ? "参数" : cleaned;
    }
}
