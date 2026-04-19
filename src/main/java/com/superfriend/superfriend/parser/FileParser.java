package com.superfriend.superfriend.parser;

import com.superfriend.superfriend.dto.ParseResult;

/**
 * 文件解析器接口
 * 所有文件解析器都需要实现此接口
 */
public interface FileParser {

    /**
     * 解析文件
     *
     * @param fileUrl  文件 URL（可以是 HTTP URL 或 OSS objectKey）
     * @param mimeType 文件 MIME 类型
     * @return 解析结果
     */
    ParseResult parse(String fileUrl, String mimeType);

    /**
     * 解析文件（带 base64 数据）
     *
     * @param base64Data base64 编码的文件数据
     * @param mimeType   文件 MIME 类型
     * @return 解析结果
     */
    default ParseResult parseBase64(String base64Data, String mimeType) {
        return ParseResult.unsupported(mimeType);
    }

    /**
     * 判断是否支持该 MIME 类型
     *
     * @param mimeType MIME 类型
     * @return 是否支持
     */
    boolean supports(String mimeType);

    /**
     * 获取解析器名称
     *
     * @return 解析器名称
     */
    String getName();

    /**
     * 获取支持的 MIME 类型列表
     *
     * @return 支持的 MIME 类型列表
     */
    java.util.List<String> getSupportedMimeTypes();
}
