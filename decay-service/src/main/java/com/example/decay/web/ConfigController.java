package com.example.decay.web;

import com.example.decay.config.DecayProperties;
import com.example.decay.web.dto.ConfigResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回显链长上限与数值容差等配置。
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final DecayProperties props;

    public ConfigController(DecayProperties props) {
        this.props = props;
    }

    @GetMapping
    public ConfigResponse config() {
        return new ConfigResponse(
                props.maxChainLength(),
                props.nearEqualRelTol(),
                props.maxGridSteps(),
                props.maxTimePoints(),
                props.maxBatchItems(),
                props.conservationRelTol(),
                2);
    }
}
