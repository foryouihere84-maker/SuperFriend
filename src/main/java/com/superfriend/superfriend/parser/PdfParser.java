package com.superfriend.superfriend.parser;

import com.superfriend.superfriend.dto.ParseResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * PDF 解析器
 * 使用 Apache PDFBox 提取文本内容
 */
@Slf4j
@Component
public class PdfParser implements FileParser {

    // 支持的 PDF MIME 类型
    private static final List<String> SUPPORTED_MIME_TYPES = Arrays.asList(
            "application/pdf"
    );

    // 最大提取页数（防止超大文件）
    private static final int MAX_PAGES = 100;

    @Override
    public ParseResult parse(String fileUrl, String mimeType) {
        if (!supports(mimeType)) {
            return ParseResult.unsupported(mimeType);
        }

        long startTime = System.currentTimeMillis();

        InputStream inputStream = null;
        try {
            inputStream = resolveFileInputStream(fileUrl);
            if (inputStream == null) {
                return ParseResult.failed("无法读取文件: " + fileUrl);
            }

            PDDocument document = PDDocument.load(inputStream);

            log.info("开始解析 PDF: {}", fileUrl);

            int totalPages = document.getNumberOfPages();
            int pagesToExtract = Math.min(totalPages, MAX_PAGES);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(pagesToExtract);

            String textContent = stripper.getText(document);

            long parseTime = System.currentTimeMillis() - startTime;
            log.info("PDF 解析完成，共 {} 页，提取 {} 页，耗时: {}ms，文本长度: {}",
                    totalPages, pagesToExtract, parseTime, textContent.length());

            ParseResult result = ParseResult.success(textContent);
            result.setMimeType(mimeType);
            result.setParseTimeMs(parseTime);

            result.setMetadata(new HashMap<>());
            result.getMetadata().put("totalPages", totalPages);
            result.getMetadata().put("extractedPages", pagesToExtract);
            result.getMetadata().put("textLength", textContent.length());

            if (document.getDocumentInformation() != null) {
                result.getMetadata().put("title", document.getDocumentInformation().getTitle());
                result.getMetadata().put("author", document.getDocumentInformation().getAuthor());
                result.getMetadata().put("creator", document.getDocumentInformation().getCreator());
            }

            return result;

        } catch (Exception e) {
            log.error("PDF 解析失败: {}", e.getMessage(), e);
            return ParseResult.failed("PDF 解析失败: " + e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    log.warn("关闭输入流失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 解析 PDF 输入流
     */
    public ParseResult parse(InputStream inputStream) {
        long startTime = System.currentTimeMillis();

        try (PDDocument document = PDDocument.load(inputStream)) {

            int totalPages = document.getNumberOfPages();
            int pagesToExtract = Math.min(totalPages, MAX_PAGES);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(pagesToExtract);

            String textContent = stripper.getText(document);

            long parseTime = System.currentTimeMillis() - startTime;
            log.info("PDF 解析完成，共 {} 页，耗时: {}ms", totalPages, parseTime);

            ParseResult result = ParseResult.success(textContent);
            result.setParseTimeMs(parseTime);
            result.setMimeType("application/pdf");

            result.setMetadata(new HashMap<>());
            result.getMetadata().put("totalPages", totalPages);

            return result;

        } catch (Exception e) {
            log.error("PDF 解析失败: {}", e.getMessage(), e);
            return ParseResult.failed("PDF 解析失败: " + e.getMessage());
        }
    }

    /**
     * 提取指定页范围的内容
     */
    public ParseResult parsePages(String fileUrl, int startPage, int endPage) {
        InputStream inputStream = null;
        try {
            inputStream = resolveFileInputStream(fileUrl);
            if (inputStream == null) {
                return ParseResult.failed("无法读取文件: " + fileUrl);
            }

            PDDocument document = PDDocument.load(inputStream);

            int totalPages = document.getNumberOfPages();
            int actualStart = Math.max(1, startPage);
            int actualEnd = Math.min(totalPages, endPage);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(actualStart);
            stripper.setEndPage(actualEnd);

            String textContent = stripper.getText(document);

            ParseResult result = ParseResult.success(textContent);
            result.setMetadata(new HashMap<>());
            result.getMetadata().put("totalPages", totalPages);
            result.getMetadata().put("extractedPages", actualEnd - actualStart + 1);

            return result;

        } catch (Exception e) {
            log.error("PDF 页面提取失败: {}", e.getMessage(), e);
            return ParseResult.failed("PDF 页面提取失败: " + e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    log.warn("关闭输入流失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 根据文件 URL 获取输入流
     * 支持 http://, https://, temp:// 协议和本地文件路径
     */
    private InputStream resolveFileInputStream(String fileUrl) {
        try {
            if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
                return new URL(fileUrl).openStream();
            } else if (fileUrl.startsWith("temp://")) {
                String path = fileUrl.substring("temp://".length());
                String[] parts = path.split("/", 2);
                if (parts.length != 2) {
                    log.error("无效的 temp:// 文件路径: {}", fileUrl);
                    return null;
                }
                String type = parts[0];
                String fileName = parts[1];
                // 检查文件名是否为空
                if (fileName == null || fileName.trim().isEmpty()) {
                    log.error("temp:// URL 中文件名为空: {}", fileUrl);
                    return null;
                }
                Path localPath = Paths.get("uploads", "temp", type, fileName);
                log.debug("解析 temp:// URL: {} -> {}", fileUrl, localPath.toAbsolutePath());
                return new FileInputStream(localPath.toFile());
            } else {
                return new FileInputStream(fileUrl);
            }
        } catch (Exception e) {
            log.error("解析文件 URL 失败: {}, workDir={}, error: {}", fileUrl, System.getProperty("user.dir"), e.getMessage());
            return null;
        }
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        return SUPPORTED_MIME_TYPES.contains(mimeType.toLowerCase());
    }

    @Override
    public String getName() {
        return "PdfParser-PDFBox";
    }

    @Override
    public List<String> getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
}
