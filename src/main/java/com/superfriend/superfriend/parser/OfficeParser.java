package com.superfriend.superfriend.parser;

import com.superfriend.superfriend.dto.ParseResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 * Office 文档解析器
 * 使用 Apache POI 解析 PPT/DOCX/XLSX 等 Office 文档
 */
@Slf4j
@Component
public class OfficeParser implements FileParser {

    // 支持的 Office 文档 MIME 类型
    private static final List<String> SUPPORTED_MIME_TYPES = Arrays.asList(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", // PPTX
            "application/vnd.ms-powerpoint.presentation.macroEnabled.12", // PPTM
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // DOCX
            "application/msword", // DOC
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // XLSX
            "application/vnd.ms-excel" // XLS
    );

    // 支持的文件扩展名
    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pptx", "pptm", "ppt", "docx", "doc", "xlsx", "xls"
    ));

    @Override
    public ParseResult parse(String fileUrl, String mimeType) {
        if (!supports(mimeType)) {
            // 尝试通过扩展名判断
            String extension = getFileExtension(fileUrl);
            if (extension == null || !SUPPORTED_EXTENSIONS.contains(extension.toLowerCase())) {
                return ParseResult.unsupported(mimeType);
            }
        }

        long startTime = System.currentTimeMillis();

        InputStream inputStream = null;
        try {
            // 区分 URL 协议类型
            if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
                inputStream = new URL(fileUrl).openStream();
            } else if (fileUrl.startsWith("temp://")) {
                String path = fileUrl.substring("temp://".length());
                String[] parts = path.split("/", 2);
                if (parts.length != 2) {
                    return ParseResult.failed("无效的 temp:// 文件路径: " + fileUrl);
                }
                String type = parts[0];
                String fileName = parts[1];
                // 检查文件名是否为空
                if (fileName == null || fileName.trim().isEmpty()) {
                    return ParseResult.failed("temp:// URL 中文件名为空: " + fileUrl);
                }
                java.nio.file.Path localPath = java.nio.file.Paths.get("uploads", "temp", type, fileName);
                log.debug("解析 temp:// URL: {} -> {}", fileUrl, localPath.toAbsolutePath());
                inputStream = new FileInputStream(localPath.toFile());
            } else {
                inputStream = new FileInputStream(fileUrl);
            }

            String extension = getFileExtension(fileUrl);
            ParseResult result;

            if (extension != null) {
                switch (extension.toLowerCase()) {
                    case "pptx":
                    case "pptm":
                        result = parsePptx(inputStream);
                        break;
                    case "ppt":
                        result = parsePpt(inputStream);
                        break;
                    case "docx":
                        result = parseDocx(inputStream);
                        break;
                    case "doc":
                        result = parseDoc(inputStream);
                        break;
                    case "xlsx":
                        result = parseXlsx(inputStream);
                        break;
                    case "xls":
                        result = parseXls(inputStream);
                        break;
                    default:
                        result = ParseResult.unsupported(mimeType);
                }
            } else {
                // 通过 MIME 类型判断
                if (mimeType.contains("presentation")) {
                    result = parsePptx(inputStream);
                } else if (mimeType.contains("word") || mimeType.contains("document")) {
                    result = parseDocx(inputStream);
                } else if (mimeType.contains("sheet") || mimeType.contains("excel")) {
                    result = parseXlsx(inputStream);
                } else {
                    result = ParseResult.unsupported(mimeType);
                }
            }

            long parseTime = System.currentTimeMillis() - startTime;
            result.setParseTimeMs(parseTime);
            result.setMimeType(mimeType);

            log.info("Office 文档解析完成，类型: {}，耗时: {}ms", extension, parseTime);
            return result;

        } catch (Exception e) {
            log.error("Office 文档解析失败: {}", e.getMessage(), e);
            return ParseResult.failed("Office 文档解析失败: " + e.getMessage());
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
     * 解析 PPTX 文件
     */
    private ParseResult parsePptx(InputStream inputStream) {
        try (XMLSlideShow ppt = new XMLSlideShow(inputStream)) {
            StringBuilder content = new StringBuilder();
            List<XSLFSlide> slides = ppt.getSlides();

            content.append("PPT 文档，共 ").append(slides.size()).append(" 页\n\n");

            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                content.append("=== 第 ").append(i + 1).append(" 页 ===\n");

                // 提取幻灯片中的所有文本
                for (Object shape : slide.getShapes()) {
                    if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape) {
                        org.apache.poi.xslf.usermodel.XSLFTextShape textShape =
                                (org.apache.poi.xslf.usermodel.XSLFTextShape) shape;
                        String text = textShape.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            content.append(text.trim()).append("\n");
                        }
                    }
                }
                content.append("\n");
            }

            ParseResult result = ParseResult.success(content.toString());
            result.setMetadata(new HashMap<>());
            result.getMetadata().put("slideCount", slides.size());
            result.getMetadata().put("fileType", "PPTX");

            return result;

        } catch (Exception e) {
            log.error("PPTX 解析失败: {}", e.getMessage(), e);
            return ParseResult.failed("PPTX 解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析旧版 PPT 文件（简化处理）
     */
    private ParseResult parsePpt(InputStream inputStream) {
        // 旧版 PPT 格式支持有限，建议转换为 PPTX
        return ParseResult.partial("", "旧版 PPT 格式支持有限，建议转换为 PPTX 格式");
    }

    /**
     * 解析 DOCX 文件
     */
    private ParseResult parseDocx(InputStream inputStream) {
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            StringBuilder content = new StringBuilder();
            List<Map<String, Object>> structuredContent = new ArrayList<>();

            // 提取段落
            content.append("=== 文档内容 ===\n");
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.trim().isEmpty()) {
                    content.append(text).append("\n");
                }
            }

            // 提取表格
            List<XWPFTable> tables = doc.getTables();
            if (!tables.isEmpty()) {
                content.append("\n=== 表格内容 ===\n");
                for (int t = 0; t < tables.size(); t++) {
                    XWPFTable table = tables.get(t);
                    content.append("\n表格 ").append(t + 1).append(":\n");

                    for (XWPFTableRow row : table.getRows()) {
                        StringBuilder rowText = new StringBuilder();
                        Map<String, Object> rowData = new HashMap<>();

                        int cellIndex = 0;
                        for (XWPFTableCell cell : row.getTableCells()) {
                            String cellText = cell.getText();
                            rowText.append(cellText != null ? cellText : "").append("\t");
                            rowData.put("cell_" + cellIndex, cellText);
                            cellIndex++;
                        }
                        content.append(rowText.toString().trim()).append("\n");
                        structuredContent.add(rowData);
                    }
                }
            }

            ParseResult result = ParseResult.success(content.toString(), structuredContent);
            result.setMetadata(new HashMap<>());
            result.getMetadata().put("paragraphCount", doc.getParagraphs().size());
            result.getMetadata().put("tableCount", tables.size());
            result.getMetadata().put("fileType", "DOCX");

            return result;

        } catch (Exception e) {
            log.error("DOCX 解析失败: {}", e.getMessage(), e);
            return ParseResult.failed("DOCX 解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析旧版 DOC 文件
     */
    private ParseResult parseDoc(InputStream inputStream) {
        try (HWPFDocument doc = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(doc)) {

            String text = extractor.getText();

            ParseResult result = ParseResult.success(text);
            result.setMetadata(new HashMap<>());
            result.getMetadata().put("fileType", "DOC");

            return result;

        } catch (Exception e) {
            log.error("DOC 解析失败: {}", e.getMessage(), e);
            return ParseResult.failed("DOC 解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析 XLSX 文件
     */
    private ParseResult parseXlsx(InputStream inputStream) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            StringBuilder content = new StringBuilder();
            List<Map<String, Object>> structuredContent = new ArrayList<>();

            int sheetCount = workbook.getNumberOfSheets();
            content.append("Excel 文档，共 ").append(sheetCount).append(" 个工作表\n\n");

            for (int s = 0; s < sheetCount; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();

                content.append("=== 工作表: ").append(sheetName).append(" ===\n");

                int rowCount = 0;
                int maxRows = Math.min(sheet.getPhysicalNumberOfRows(), 1000); // 限制行数

                for (Row row : sheet) {
                    if (rowCount >= maxRows) break;

                    StringBuilder rowText = new StringBuilder();
                    Map<String, Object> rowData = new HashMap<>();
                    rowData.put("_rowIndex", rowCount + 1);

                    int cellIndex = 0;
                    for (Cell cell : row) {
                        String cellValue = getCellValueAsString(cell);
                        rowText.append(cellValue).append("\t");
                        rowData.put("col_" + cellIndex, cellValue);
                        cellIndex++;
                    }
                    content.append(rowText.toString().trim()).append("\n");
                    structuredContent.add(rowData);
                    rowCount++;
                }
                content.append("\n");
            }

            ParseResult result = ParseResult.success(content.toString(), structuredContent);
            result.setMetadata(new HashMap<>());
            result.getMetadata().put("sheetCount", sheetCount);
            result.getMetadata().put("fileType", "XLSX");

            return result;

        } catch (Exception e) {
            log.error("XLSX 解析失败: {}", e.getMessage(), e);
            return ParseResult.failed("XLSX 解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析旧版 XLS 文件
     */
    private ParseResult parseXls(InputStream inputStream) {
        try (HSSFWorkbook workbook = new HSSFWorkbook(inputStream)) {
            StringBuilder content = new StringBuilder();
            List<Map<String, Object>> structuredContent = new ArrayList<>();

            int sheetCount = workbook.getNumberOfSheets();
            content.append("Excel 文档，共 ").append(sheetCount).append(" 个工作表\n\n");

            for (int s = 0; s < sheetCount; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();

                content.append("=== 工作表: ").append(sheetName).append(" ===\n");

                int rowCount = 0;
                for (Row row : sheet) {
                    if (rowCount >= 1000) break;

                    StringBuilder rowText = new StringBuilder();
                    Map<String, Object> rowData = new HashMap<>();

                    int cellIndex = 0;
                    for (Cell cell : row) {
                        String cellValue = getCellValueAsString(cell);
                        rowText.append(cellValue).append("\t");
                        rowData.put("col_" + cellIndex, cellValue);
                        cellIndex++;
                    }
                    content.append(rowText.toString().trim()).append("\n");
                    structuredContent.add(rowData);
                    rowCount++;
                }
                content.append("\n");
            }

            ParseResult result = ParseResult.success(content.toString(), structuredContent);
            result.setMetadata(new HashMap<>());
            result.getMetadata().put("sheetCount", sheetCount);
            result.getMetadata().put("fileType", "XLS");

            return result;

        } catch (Exception e) {
            log.error("XLS 解析失败: {}", e.getMessage(), e);
            return ParseResult.failed("XLS 解析失败: " + e.getMessage());
        }
    }

    /**
     * 获取单元格值字符串
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String url) {
        if (url == null) return null;
        int lastDot = url.lastIndexOf('.');
        if (lastDot > 0 && lastDot < url.length() - 1) {
            String ext = url.substring(lastDot + 1);
            // 处理 URL 参数
            int queryIndex = ext.indexOf('?');
            if (queryIndex > 0) {
                ext = ext.substring(0, queryIndex);
            }
            return ext.toLowerCase();
        }
        return null;
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        return SUPPORTED_MIME_TYPES.stream()
                .anyMatch(type -> type.equalsIgnoreCase(mimeType));
    }

    @Override
    public String getName() {
        return "OfficeParser-POI";
    }

    @Override
    public List<String> getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
}
