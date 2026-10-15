package uk.collectionofcreations.coc.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.collectionofcreations.coc.common.BizException;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 存储服务：统一管理 E:\personalProjects\coc-platform\.data 下的目录与文件。
 * <p>目录结构：projects\{project_id}\v{n}\source|build、covers、avatars、uploads\tmp|staging
 */
@Component
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final Path root;

    public StorageService(@Value("${coc.storage.root}") String rootDir) {
        this.root = Path.of(rootDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("projects"));
        Files.createDirectories(root.resolve("covers"));
        Files.createDirectories(root.resolve("avatars"));
        Files.createDirectories(root.resolve("uploads").resolve("tmp"));
        Files.createDirectories(root.resolve("uploads").resolve("staging"));
        log.info("存储根目录就绪: {}", root);
    }

    public Path getRoot() {
        return root;
    }

    /** 项目某版本目录：projects/{projectId}/v{n}/{kind} */
    public Path projectVersionDir(long projectId, int versionNo, String kind) throws IOException {
        Path p = root.resolve("projects").resolve(String.valueOf(projectId))
                .resolve("v" + versionNo).resolve(kind);
        Files.createDirectories(p);
        return p;
    }

    public Path projectRootDir(long projectId) {
        return root.resolve("projects").resolve(String.valueOf(projectId));
    }

    /** 上传分片暂存目录：uploads/staging/{sessionId} */
    public Path stagingDir(long sessionId) throws IOException {
        Path p = root.resolve("uploads").resolve("staging").resolve(String.valueOf(sessionId));
        Files.createDirectories(p);
        return p;
    }

    public Path tmpDir() {
        return root.resolve("uploads").resolve("tmp");
    }

    /** 相对路径（用于入库，避免泄漏绝对路径） */
    public String relative(Path p) {
        try {
            return root.relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/');
        } catch (Exception e) {
            return p.toString().replace('\\', '/');
        }
    }

    /** 把库里存的 storage_path 解析为绝对路径（兼容存的是绝对路径或相对路径） */
    public Path resolveStored(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        Path p = Path.of(stored);
        return p.isAbsolute() ? p.normalize() : root.resolve(stored).normalize();
    }

    public String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("计算 sha256 失败: " + e.getMessage(), e);
        }
    }

    /** 按分片号顺序合并分片到目标文件 */
    public void mergeParts(Path partsDir, int totalChunks, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
            for (int i = 1; i <= totalChunks; i++) {
                Path part = partsDir.resolve("part-" + i);
                if (!Files.exists(part)) {
                    throw new BizException(1003, "分片不完整，缺少第 " + i + " 片");
                }
                Files.copy(part, out);
            }
        }
    }

    /** 解压 zip 到目标目录（防目录穿越） */
    public void unzip(Path zip, Path dest) throws IOException {
        Files.createDirectories(dest);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            String singleRoot = null;
            // 先探测是否存在单一根目录需要剥离
            List<String> names = new ArrayList<>();
            try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip.toFile())) {
                zf.stream().forEach(e -> names.add(e.getName()));
            }
            boolean hasTopDir = !names.isEmpty();
            String top = null;
            for (String n : names) {
                int idx = n.indexOf('/');
                if (idx <= 0) {
                    hasTopDir = false;
                    break;
                }
                String t = n.substring(0, idx);
                if (top == null) {
                    top = t;
                } else if (!top.equals(t)) {
                    hasTopDir = false;
                    break;
                }
            }
            if (hasTopDir) {
                singleRoot = top + "/";
            }
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (singleRoot != null && name.startsWith(singleRoot)) {
                    name = name.substring(singleRoot.length());
                }
                if (name.isBlank()) {
                    continue;
                }
                Path out = dest.resolve(name).normalize();
                if (!out.startsWith(dest)) {
                    throw new BizException(1004, "压缩包内含非法路径: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(out,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        zis.transferTo(os);
                    }
                }
            }
        }
    }

    public void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 忽略单个文件删除失败
                }
            });
        } catch (IOException e) {
            log.warn("删除目录失败 {}: {}", path, e.getMessage());
        }
    }

    public void move(Path from, Path to) throws IOException {
        Files.createDirectories(to.getParent());
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    public long size(Path p) throws IOException {
        return Files.exists(p) ? Files.size(p) : 0L;
    }
}
