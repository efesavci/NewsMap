package main.newsmap.web;

import main.newsmap.web.PipelineRunStore.PipelineRunDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pipeline")
public class PipelineController {
    private final PipelineRunStore runs;

    public PipelineController(PipelineRunStore runs) {
        this.runs = runs;
    }

    @GetMapping("/runs")
    public List<PipelineRunDTO> listRuns(@RequestParam(defaultValue = "20") int limit) {
        return runs.list(limit);
    }
}
