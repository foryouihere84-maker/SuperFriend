package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.LLMRequest;
import com.superfriend.superfriend.entity.AIModelConfig;
import com.superfriend.superfriend.entity.Skill;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 技能包解析服务
 * 负责解压、解析和存储用户上传的技能包
 */
@Slf4j
@Service
public class SkillPackageParserService {

    private static final long MAX_PACKAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String SKILL_MD_FILE = "SKILL.md";

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
        ".md", ".txt", ".json", ".yaml", ".yml",
        ".py", ".js", ".ts", ".sh", ".bash",
        ".html", ".css", ".xml", ".csv",
        ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
        ".pdf", ".docx", ".xlsx"
    ));

    @Autowired
    private SkillService skillService;

    @Autowired
    private AIModelConfigService aiModelConfigService;

    @Autowired
    private TencentCosService tencentCosService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.file.temp-dir:uploads/temp}")
    private String tempDir;

    /**
     * 解析并导入技能包
     */
    @Transactional
    public SkillImportResult importSkillPackage(Long userId, MultipartFile zipFile, String modelConfigId) {
        SkillImportResult result = new SkillImportResult();
        Path tempExtractPath = null;

        try {
            // 1. 验证文件大小
            if (zipFile.getSize() > MAX_PACKAGE_SIZE) {
                return SkillImportResult.failure("技能包大小超过限制（最大10MB）");
            }

            // 2. 创建临时目录
            tempExtractPath = Files.createTempDirectory("skill_package_" + userId + "_");
            log.info("创建临时解压目录: {}", tempExtractPath);

            // 3. 解压文件
            List<ExtractedFile> extractedFiles = unzipFile(zipFile, tempExtractPath);
            result.setExtractedFiles(extractedFiles.size());
            log.info("解压完成，共 {} 个文件", extractedFiles.size());

            // 4. 查找 SKILL.md 文件
            Optional<ExtractedFile> skillMdFile = extractedFiles.stream()
                .filter(f -> f.getFileName().equalsIgnoreCase(SKILL_MD_FILE))
                .findFirst();

            if (!skillMdFile.isPresent()) {
                return SkillImportResult.failure("技能包中未找到 SKILL.md 文件");
            }

            // 5. 解析 SKILL.md 获取基础元数据
            SkillMetadata baseMetadata = parseSkillMd(skillMdFile.get().getContent());
            log.info("解析 SKILL.md 完成: name={}, description={}", baseMetadata.getName(), baseMetadata.getDescription());

            // 6. 使用大模型解析所有文件
            AIModelConfig modelConfig = aiModelConfigService.resolveModelConfig(modelConfigId, userId);
            if (modelConfig == null) {
                modelConfig = aiModelConfigService.getDefaultModel(userId);
            }

            if (modelConfig == null) {
                return SkillImportResult.failure("未找到可用的大模型配置");
            }

            // 7. 使用大模型分析技能包
            SkillAnalysisResult analysisResult = analyzeSkillPackageWithLLM(
                extractedFiles, baseMetadata, modelConfig, userId
            );
            result.setAiAnalysisPerformed(true);

            // 8. 收集脚本文件内容
            List<Map<String, Object>> scriptsList = collectScriptFiles(extractedFiles);

            // 9. 上传资源文件到腾讯云 COS
            List<SkillResource> resources = uploadResourcesToCos(extractedFiles, userId, baseMetadata.getName());
            result.setResourceCount(resources.size());

            // 10. 创建技能实体并保存到数据库
            Skill skill = createSkillEntity(baseMetadata, analysisResult, scriptsList, resources, userId);
            skillService.createUserSkill(skill, userId);

            result.setSuccess(true);
            result.setSkillId(skill.getId());
            result.setSkillName(skill.getName());
            result.setMessage("技能包导入成功");

            log.info("技能包导入成功: userId={}, skillName={}, skillId={}", userId, skill.getName(), skill.getId());

            return result;

        } catch (Exception e) {
            log.error("技能包导入失败: userId={}, error={}", userId, e.getMessage(), e);
            return SkillImportResult.failure("导入失败: " + e.getMessage());
        } finally {
            // 清理临时目录
            if (tempExtractPath != null) {
                try {
                    deleteDirectory(tempExtractPath);
                    log.info("清理临时目录: {}", tempExtractPath);
                } catch (Exception e) {
                    log.warn("清理临时目录失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 解压 ZIP 文件
     */
    private List<ExtractedFile> unzipFile(MultipartFile zipFile, Path targetPath) throws IOException {
        List<ExtractedFile> extractedFiles = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath.resolve(entry.getName()));
                    continue;
                }

                String entryName = entry.getName();
                String fileName = Paths.get(entryName).getFileName().toString();
                String extension = getFileExtension(fileName).toLowerCase();

                // 跳过不允许的文件类型
                if (!ALLOWED_EXTENSIONS.contains(extension)) {
                    log.warn("跳过不允许的文件类型: {}", fileName);
                    continue;
                }

                // 跳过隐藏文件和系统文件
                if (fileName.contains("__MACOSX") || fileName.startsWith(".") || fileName.contains("/.")) {
                    continue;
                }

                Path filePath = targetPath.resolve(fileName);
                Files.createDirectories(filePath.getParent());

                // 读取文件内容 (Java 8 兼容方式)
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[4096];
                int nRead;
                while ((nRead = zis.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                byte[] content = buffer.toByteArray();
                Files.write(filePath, content);

                ExtractedFile extracted = new ExtractedFile();
                extracted.setFileName(fileName);
                extracted.setFilePath(filePath.toString());
                extracted.setFileSize(content.length);
                extracted.setExtension(extension);

                // 对于文本文件，读取内容
                if (isTextFile(extension)) {
                    extracted.setContent(new String(content, StandardCharsets.UTF_8));
                    extracted.setTextFile(true);
                } else {
                    extracted.setTextFile(false);
                    extracted.setBinaryContent(content);
                }

                extractedFiles.add(extracted);
                zis.closeEntry();
            }
        }

        return extractedFiles;
    }

    /**
     * 解析 SKILL.md 文件
     */
    private SkillMetadata parseSkillMd(String content) {
        SkillMetadata metadata = new SkillMetadata();

        try {
            // 解析 YAML frontmatter
            if (content.startsWith("---")) {
                int endIndex = content.indexOf("---", 3);
                if (endIndex > 0) {
                    String frontmatter = content.substring(3, endIndex).trim();
                    String body = content.substring(endIndex + 3).trim();

                    // 解析 frontmatter
                    Map<String, Object> frontmatterMap = parseYamlFrontmatter(frontmatter);

                    metadata.setName((String) frontmatterMap.getOrDefault("name", "unnamed-skill"));
                    metadata.setDescription((String) frontmatterMap.getOrDefault("description", ""));
                    metadata.setCategory((String) frontmatterMap.getOrDefault("category", "general"));
                    metadata.setVersion((String) frontmatterMap.getOrDefault("version", "1.0.0"));
                    metadata.setAuthor((String) frontmatterMap.getOrDefault("author", "user"));

                    if (frontmatterMap.get("tags") instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> tags = (List<String>) frontmatterMap.get("tags");
                        metadata.setTags(tags);
                    }

                    if (frontmatterMap.get("allowed-tools") instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> tools = (List<String>) frontmatterMap.get("allowed-tools");
                        metadata.setAllowedTools(tools);
                    }

                    if (frontmatterMap.get("priority") != null) {
                        metadata.setPriority(convertPriority(frontmatterMap.get("priority")));
                    }

                    metadata.setInstructions(body);
                }
            } else {
                // 没有 frontmatter，使用默认值
                metadata.setName("unnamed-skill-" + System.currentTimeMillis());
                metadata.setDescription("用户上传的技能");
                metadata.setInstructions(content);
            }
        } catch (Exception e) {
            log.warn("解析 SKILL.md 失败，使用默认值: {}", e.getMessage());
            metadata.setName("skill-" + System.currentTimeMillis());
            metadata.setDescription("用户上传的技能");
            metadata.setInstructions(content);
        }

        return metadata;
    }

    /**
     * 简单解析 YAML frontmatter
     */
    private Map<String, Object> parseYamlFrontmatter(String yaml) {
        Map<String, Object> result = new HashMap<>();
        String[] lines = yaml.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // 处理列表项
            if (line.startsWith("- ")) {
                continue; // 简化处理，跳过列表项
            }

            int colonIndex = line.indexOf(":");
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();

                // 移除引号
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1);
                }

                result.put(key, value);
            }
        }

        return result;
    }

    /**
     * 使用大模型分析技能包
     */
    private SkillAnalysisResult analyzeSkillPackageWithLLM(
            List<ExtractedFile> files,
            SkillMetadata baseMetadata,
            AIModelConfig modelConfig,
            Long userId) {

        SkillAnalysisResult result = new SkillAnalysisResult();

        try {
            // 构建分析提示词
            StringBuilder prompt = new StringBuilder();
            prompt.append("请分析以下技能包文件，提取技能的详细信息。\n\n");
            prompt.append("基础信息（来自 SKILL.md）：\n");
            prompt.append("- 名称: ").append(baseMetadata.getName()).append("\n");
            prompt.append("- 描述: ").append(baseMetadata.getDescription()).append("\n");
            prompt.append("- 分类: ").append(baseMetadata.getCategory()).append("\n\n");

            prompt.append("文件列表：\n");
            for (ExtractedFile file : files) {
                prompt.append("- ").append(file.getFileName());
                prompt.append(" (").append(formatFileSize(file.getFileSize())).append(")\n");
            }
            prompt.append("\n");

            // 添加文本文件内容
            prompt.append("文件内容：\n");
            for (ExtractedFile file : files) {
                if (file.isTextFile() && file.getContent() != null) {
                    prompt.append("\n=== ").append(file.getFileName()).append(" ===\n");
                    // 限制内容长度
                    String content = file.getContent();
<<<<<<< HEAD
                    if (content.length() > 5000) {
                        content = content.substring(0, 5000) + "\n... (内容已截断)";
=======
                    if (content.length() > 2000) {
                        content = content.substring(0, 2000) + "\n... (内容已截断)";
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
                    }
                    prompt.append(content).append("\n");
                }
            }

            prompt.append("\n请以 JSON 格式返回以下信息：\n");
            prompt.append("{\n");
            prompt.append("  \"name\": \"技能名称\",\n");
            prompt.append("  \"description\": \"详细描述\",\n");
            prompt.append("  \"category\": \"分类\",\n");
            prompt.append("  \"tags\": [\"标签1\", \"标签2\"],\n");
            prompt.append("  \"allowedTools\": [\"允许的工具\"],\n");
            prompt.append("  \"instructions\": \"使用说明\",\n");
            prompt.append("  \"parameters\": {\"参数定义\": \"描述\"},\n");
            prompt.append("  \"scripts\": {\"脚本类型\": \"说明\"},\n");
            prompt.append("  \"resources\": [\"资源文件说明\"]\n");
            prompt.append("}\n");

            // TODO: 调用大模型 API
            // 当前使用基础元数据作为后备
            result.setName(baseMetadata.getName());
            result.setDescription(baseMetadata.getDescription());
            result.setCategory(baseMetadata.getCategory());
            result.setTags(baseMetadata.getTags() != null ? baseMetadata.getTags() : new ArrayList<String>());
            result.setAllowedTools(baseMetadata.getAllowedTools() != null ? baseMetadata.getAllowedTools() : new ArrayList<String>());
            result.setInstructions(baseMetadata.getInstructions());
            result.setParameters(new HashMap<String, Object>());
            result.setScripts(new HashMap<String, String>());

            log.info("大模型分析完成（当前使用基础元数据）");

        } catch (Exception e) {
            log.error("大模型分析失败: {}", e.getMessage());
            // 使用基础元数据作为后备
            result.setName(baseMetadata.getName());
            result.setDescription(baseMetadata.getDescription());
            result.setCategory(baseMetadata.getCategory());
            result.setTags(baseMetadata.getTags() != null ? baseMetadata.getTags() : new ArrayList<String>());
            result.setAllowedTools(baseMetadata.getAllowedTools() != null ? baseMetadata.getAllowedTools() : new ArrayList<String>());
            result.setInstructions(baseMetadata.getInstructions());
            result.setParameters(new HashMap<String, Object>());
            result.setScripts(new HashMap<String, String>());
        }

        return result;
    }

    /**
     * 收集脚本文件内容
     */
    private List<Map<String, Object>> collectScriptFiles(List<ExtractedFile> files) {
        List<Map<String, Object>> scriptsList = new ArrayList<Map<String, Object>>();

        for (ExtractedFile file : files) {
            String ext = file.getExtension().toLowerCase();
            String scriptType = null;

            // 根据扩展名确定脚本类型
            if (".py".equals(ext)) {
                scriptType = "python";
            } else if (".js".equals(ext)) {
                scriptType = "javascript";
            } else if (".ts".equals(ext)) {
                scriptType = "typescript";
            } else if (".sh".equals(ext) || ".bash".equals(ext)) {
                scriptType = "bash";
            }

            if (scriptType != null && file.getContent() != null) {
                Map<String, Object> scriptMap = new HashMap<String, Object>();
                scriptMap.put("scriptType", scriptType);
                scriptMap.put("scriptName", file.getFileName());
                scriptMap.put("scriptContent", file.getContent());

                // 判断是否为主脚本（文件名为 main.* 的脚本）
                String fileName = file.getFileName().toLowerCase();
                boolean isMain = fileName.startsWith("main.") || fileName.startsWith("index.");
                scriptMap.put("isMain", isMain);
                scriptMap.put("executionOrder", scriptsList.size());

                scriptsList.add(scriptMap);
                log.info("收集脚本文件: {} (type={}, isMain={})", file.getFileName(), scriptType, isMain);
            }
        }

        return scriptsList;
    }

    /**
     * 上传资源文件到腾讯云 COS
     */
    private List<SkillResource> uploadResourcesToCos(
            List<ExtractedFile> files,
            Long userId,
            String skillName) {

        List<SkillResource> resources = new ArrayList<SkillResource>();

        if (!tencentCosService.isEnabled()) {
            log.warn("腾讯云 COS 服务未启用，资源文件将不会被上传");
            return resources;
        }

        String directory = "skills/users/" + userId + "/" + sanitizeSkillName(skillName) + "/resources";

        for (ExtractedFile file : files) {
            // 跳过 SKILL.md 和脚本文件
            if (file.getFileName().equalsIgnoreCase(SKILL_MD_FILE)) {
                continue;
            }
            if (file.getExtension().matches("(py|js|ts|sh|bash)")) {
                continue;
            }

            try {
                // 直接使用字节数据上传
                byte[] content = file.isTextFile()
                    ? file.getContent().getBytes(StandardCharsets.UTF_8)
                    : file.getBinaryContent();

                String mimeType = getMimeType(file.getExtension());

                TencentCosService.CosUploadResult uploadResult = tencentCosService.uploadBytes(
                    content,
                    file.getFileName(),
                    mimeType,
                    directory
                );

                if (uploadResult == null) {
                    log.warn("资源文件上传失败: {}", file.getFileName());
                    continue;
                }

                SkillResource resource = new SkillResource();
                resource.setResourceName(file.getFileName());
                resource.setResourceType(getResourceType(file.getExtension()));
                resource.setStorageType("cos");
                resource.setResourcePath(uploadResult.getObjectKey());
                resource.setResourceUrl(uploadResult.getUrl());
                resource.setFileSize(file.getFileSize());
                resource.setMimeType(mimeType);

                resources.add(resource);

                log.info("资源文件上传到 COS 成功: {} -> {}", file.getFileName(), uploadResult.getObjectKey());

            } catch (Exception e) {
                log.error("上传资源文件失败: {}", file.getFileName(), e);
            }
        }

        return resources;
    }

    /**
     * 创建技能实体
     */
    private Skill createSkillEntity(
            SkillMetadata baseMetadata,
            SkillAnalysisResult analysis,
            List<Map<String, Object>> scriptsList,
            List<SkillResource> resources,
            Long userId) throws Exception {

        Skill skill = new Skill();

        // 确保名称唯一
        String skillName = analysis.getName();
        Skill existing = skillService.findByName(skillName);
        if (existing != null) {
            skillName = skillName + "_" + System.currentTimeMillis();
        }

        skill.setName(skillName);
        skill.setDescription(analysis.getDescription());
        skill.setCategory(analysis.getCategory() != null ? analysis.getCategory() : "general");
        skill.setVersion(baseMetadata.getVersion() != null ? baseMetadata.getVersion() : "1.0.0");
        skill.setAuthor(baseMetadata.getAuthor() != null ? baseMetadata.getAuthor() : "user");
        skill.setInstructions(analysis.getInstructions());
        skill.setTags(analysis.getTags());
        skill.setAllowedTools(analysis.getAllowedTools());
        skill.setParameters(analysis.getParameters());
        skill.setScope(3); // 用户技能
        skill.setStatus(1);
        skill.setPriority(baseMetadata.getPriority() != null ? baseMetadata.getPriority() : 3);
        skill.setTimeout(60000L);

        // 序列化脚本内容到 scriptsJson
        if (scriptsList != null && !scriptsList.isEmpty()) {
            skill.setScriptsJson(objectMapper.writeValueAsString(scriptsList));
            log.info("存储 {} 个脚本到 scriptsJson", scriptsList.size());
        }

        // 序列化资源元数据到 resourcesJson
        if (!resources.isEmpty()) {
            skill.setResourcesJson(objectMapper.writeValueAsString(resources));
            log.info("存储 {} 个资源元数据到 resourcesJson", resources.size());
        }

        return skill;
    }

    // ==================== 辅助方法 ====================

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex) : "";
    }

    private boolean isTextFile(String extension) {
        Set<String> textExtensions = new HashSet<>(Arrays.asList(
            ".md", ".txt", ".json", ".yaml", ".yml", ".py", ".js", ".ts", ".sh", ".bash",
            ".html", ".css", ".xml", ".csv"
        ));
        return textExtensions.contains(extension.toLowerCase());
    }

    private String getMimeType(String extension) {
        Map<String, String> mimeTypes = new HashMap<String, String>();
        mimeTypes.put(".png", "image/png");
        mimeTypes.put(".jpg", "image/jpeg");
        mimeTypes.put(".jpeg", "image/jpeg");
        mimeTypes.put(".gif", "image/gif");
        mimeTypes.put(".svg", "image/svg+xml");
        mimeTypes.put(".pdf", "application/pdf");
        mimeTypes.put(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        mimeTypes.put(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        mimeTypes.put(".json", "application/json");
        mimeTypes.put(".txt", "text/plain");
        mimeTypes.put(".md", "text/markdown");
        mimeTypes.put(".html", "text/html");
        mimeTypes.put(".css", "text/css");
        mimeTypes.put(".js", "application/javascript");
        mimeTypes.put(".py", "text/x-python");
        return mimeTypes.getOrDefault(extension.toLowerCase(), "application/octet-stream");
    }

    private String getResourceType(String extension) {
        Set<String> imageExtensions = new HashSet<>(Arrays.asList(".png", ".jpg", ".jpeg", ".gif", ".svg"));
        Set<String> documentExtensions = new HashSet<>(Arrays.asList(".pdf", ".docx", ".xlsx"));
        Set<String> dataExtensions = new HashSet<>(Arrays.asList(".json", ".xml", ".csv"));

        if (imageExtensions.contains(extension.toLowerCase())) {
            return "image";
        }
        if (documentExtensions.contains(extension.toLowerCase())) {
            return "document";
        }
        if (dataExtensions.contains(extension.toLowerCase())) {
            return "data";
        }
        return "other";
    }

    private int convertPriority(Object priority) {
        if (priority == null) return 3;
        String p = priority.toString().toUpperCase();
        switch (p) {
            case "LOW": return 1;
            case "MEDIUM": return 5;
            case "HIGH": return 8;
            case "CRITICAL": return 10;
            default: return 3;
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    private String sanitizeSkillName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        // ignore
                    }
                });
        }
    }

    // ==================== 数据类 ====================

    @Data
    public static class ExtractedFile {
        private String fileName;
        private String filePath;
        private long fileSize;
        private String extension;
        private boolean isTextFile;
        private String content;      // 文本内容
        private byte[] binaryContent; // 二进制内容
    }

    @Data
    public static class SkillMetadata {
        private String name;
        private String description;
        private String category;
        private String version;
        private String author;
        private List<String> tags;
        private List<String> allowedTools;
        private String instructions;
        private Integer priority;
    }

    @Data
    public static class SkillAnalysisResult {
        private String name;
        private String description;
        private String category;
        private List<String> tags;
        private List<String> allowedTools;
        private String instructions;
        private Map<String, Object> parameters;
        private Map<String, String> scripts;
    }

    @Data
    public static class SkillResource {
        private String resourceName;
        private String resourceType;
        private String storageType;
        private String resourcePath;
        private String resourceUrl;
        private long fileSize;
        private String mimeType;
    }

    @Data
    public static class SkillImportResult {
        private boolean success;
        private String message;
        private Long skillId;
        private String skillName;
        private int extractedFiles;
        private int resourceCount;
        private boolean aiAnalysisPerformed;

        public static SkillImportResult failure(String message) {
            SkillImportResult result = new SkillImportResult();
            result.setSuccess(false);
            result.setMessage(message);
            return result;
        }
    }
}
