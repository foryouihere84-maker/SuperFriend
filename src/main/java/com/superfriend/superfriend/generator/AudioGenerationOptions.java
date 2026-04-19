package com.superfriend.superfriend.generator;

import lombok.Data;

/**
 * 音频生成选项
 */
@Data
public class AudioGenerationOptions {

    /**
     * 音色/声音
     * 智谱: female, male
     * OpenAI: alloy, echo, fable, onyx, nova, shimmer
     */
    private String voice;

    /**
     * 语速 (0.25 - 4.0)，默认 1.0
     */
    private double speed = 1.0;

    /**
     * 输出格式: mp3, wav, pcm, opus, aac, flac
     * 默认 mp3
     */
    private String responseFormat = "mp3";

    public static AudioGenerationOptions defaults() {
        return new AudioGenerationOptions();
    }

    public AudioGenerationOptions withVoice(String voice) {
        this.voice = voice;
        return this;
    }

    public AudioGenerationOptions withSpeed(double speed) {
        this.speed = speed;
        return this;
    }

    public AudioGenerationOptions withResponseFormat(String format) {
        this.responseFormat = format;
        return this;
    }
}
