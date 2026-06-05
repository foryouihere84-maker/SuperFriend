package com.superfriend.superfriend.generator;

import lombok.Data;

/**
 * 视频生成选项
 */
@Data
public class VideoGenerationOptions {

    /**
     * 视频时长（秒）
     */
    private Integer duration;

    /**
     * 分辨率: "720p", "1080p" 等
     */
    private String resolution;

    /**
     * 帧率
     */
    private Integer fps;

    public static VideoGenerationOptions defaults() {
        return new VideoGenerationOptions();
    }

    public VideoGenerationOptions withDuration(int duration) {
        this.duration = duration;
        return this;
    }

    public VideoGenerationOptions withResolution(String resolution) {
        this.resolution = resolution;
        return this;
    }

    public VideoGenerationOptions withFps(int fps) {
        this.fps = fps;
        return this;
    }
}
