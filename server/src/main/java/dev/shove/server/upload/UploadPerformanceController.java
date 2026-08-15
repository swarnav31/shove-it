package dev.shove.server.upload;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/performance")
public final class UploadPerformanceController {

    private final UploadPerformanceService performance;

    public UploadPerformanceController(UploadPerformanceService performance) {
        this.performance = performance;
    }

    @GetMapping
    UploadPerformanceSnapshot current() {
        return performance.current();
    }
}
