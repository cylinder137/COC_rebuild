package uk.collectionofcreations.coc.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.collectionofcreations.coc.common.ApiResponse;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.service.SeriesService;

import java.util.List;
import java.util.Map;

/**
 * 系列：项目系列（kind=project）/ 素材系列（kind=asset）。
 * <p>一个系列只装同一种子项，项目系列里不会有素材。
 */
@RestController
@RequestMapping("/api/v1/series")
public class SeriesController {

    private final SeriesService seriesService;

    public SeriesController(SeriesService seriesService) {
        this.seriesService = seriesService;
    }

    /** 我的系列列表（含每个系列的条目数） */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> mine(@RequestParam(value = "kind", defaultValue = "project") String kind) {
        return ApiResponse.ok(seriesService.listMine(SeriesService.parseKind(kind)));
    }

    /** 某人公开的系列（个人主页用，访客可读） */
    @GetMapping("/public")
    public ApiResponse<List<Map<String, Object>>> publicOf(@RequestParam Map<String, Object> query) {
        SeriesService.Kind kind = SeriesService.parseKind(Params.str(query, "kind", "project"));
        Long userId = Params.lng(query, "user_id");
        if (userId == null) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(seriesService.listPublicOfUser(kind, userId));
    }

    /** 新建系列 */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        SeriesService.Kind kind = SeriesService.parseKind(Params.str(body, "kind", "project"));
        return ApiResponse.ok(seriesService.create(kind, body));
    }

    /** 改名 / 改简介 / 改公开性 */
    @PutMapping("/{series_id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable("series_id") long seriesId,
                                                   @RequestBody Map<String, Object> body) {
        SeriesService.Kind kind = SeriesService.parseKind(Params.str(body, "kind", "project"));
        return ApiResponse.ok(seriesService.update(kind, seriesId, body));
    }

    /** 删系列（子项解绑，不删子项） */
    @DeleteMapping("/{series_id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable("series_id") long seriesId,
                                                   @RequestParam(value = "kind", defaultValue = "project") String kind) {
        return ApiResponse.ok(seriesService.delete(SeriesService.parseKind(kind), seriesId));
    }
}
