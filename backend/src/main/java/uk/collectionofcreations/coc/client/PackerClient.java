package uk.collectionofcreations.coc.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 打包服务客户端（Node，端口 8091）。
 * <p>该服务由另一个子会话实现，**可能还没起**：调用失败时返回 null，
 * 由调用方优雅降级（写 build_task status=0 待处理，接口仍返回成功）。
 */
@Component
public class PackerClient {

    private static final Logger log = LoggerFactory.getLogger(PackerClient.class);

    private final RestClient restClient;
    private final String packUrl;

    public PackerClient(@Value("${coc.packer.url}") String packUrl,
                        @Value("${coc.packer.timeout-ms:4000}") int timeoutMs) {
        this.packUrl = packUrl;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(timeoutMs);
        f.setReadTimeout(Math.max(timeoutMs, 60000));
        this.restClient = RestClient.builder().requestFactory(f).build();
    }

    /**
     * 触发打包。返回 null 表示打包服务不可用/失败（调用方降级处理）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> pack(Map<String, Object> payload) {
        try {
            Map<String, Object> resp = restClient.post()
                    .uri(packUrl)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            log.info("打包服务响应: {}", resp);
            return resp;
        } catch (Exception e) {
            log.warn("打包服务不可用（{}）：{}", packUrl, e.getMessage());
            return null;
        }
    }
}
