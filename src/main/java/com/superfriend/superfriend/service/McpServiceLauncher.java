package com.superfriend.superfriend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superfriend.superfriend.dto.McpServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 服务启动器
 * 负责解析配置、检查本地资源、下载依赖、启动服务
 */
@Slf4j
@Component
public class McpServiceLauncher {

    @Value("${mcp.local-store-path:src/main/resources/mcp-local}")
    private String localStorePath;

    @Value("${mcp.npm-registry:}")
    private String npmRegistry;

    @Value("${mcp.pypi-index:}")
    private String pypiIndex;

    private final ObjectMapper objectMapper;
    private final Map<String, Boolean> installedPackages = new ConcurrentHashMap<>();

    public McpServiceLauncher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析并准备 MCP 服务配置
     * @param serverName 服务名称
     * @param config 原始配置
     * @return 准备好的配置（可能已转换为本地路径）
     */
    public McpServerConfig prepareConfig(String serverName, McpServerConfig config) {
        String command = config.getCommand();

        // 处理 npx 命令
        if ("npx".equals(command)) {
            return prepareNpxServer(serverName, config);
        }

        // 处理 uvx 命令
        if ("uvx".equals(command)) {
            return prepareUvxServer(serverName, config);
        }

        // 处理 node 命令（直接指定路径）
        if ("node".equals(command)) {
            return prepareNodeServer(serverName, config);
        }

        // 处理 python 命令
        if ("python".equals(command) || "python3".equals(command)) {
            return preparePythonServer(serverName, config);
        }

        // 其他命令直接返回
        return config;
    }

    /**
     * 准备 npx 类型的服务
     */
    private McpServerConfig prepareNpxServer(String serverName, McpServerConfig config) {
        String[] args = config.getArgs();
        if (args == null || args.length < 2 || !"-y".equals(args[0])) {
            log.warn("[{}] npx 命令格式不正确，使用原始配置", serverName);
            return config;
        }

        String packageName = args[1];
        log.info("[{}] 解析 npx 包: {}", serverName, packageName);

        // 检查本地是否已安装
        String localPath = findLocalNpmPackage(packageName);

        if (localPath != null) {
            log.info("[{}] 使用本地安装的包: {}", serverName, localPath);
            return createLocalNodeConfig(config, localPath, args);
        }

        // 尝试安装到本地
        if (installNpmPackage(packageName, serverName)) {
            localPath = findLocalNpmPackage(packageName);
            if (localPath != null) {
                log.info("[{}] 本地安装成功: {}", serverName, localPath);
                return createLocalNodeConfig(config, localPath, args);
            }
        }

        // 本地安装失败，使用 npx 远程启动
        log.info("[{}] 使用 npx 远程启动", serverName);
        return config;
    }

    /**
     * 准备 uvx 类型的服务
     */
    private McpServerConfig prepareUvxServer(String serverName, McpServerConfig config) {
        String[] args = config.getArgs();
        if (args == null || args.length < 1) {
            log.warn("[{}] uvx 命令格式不正确，使用原始配置", serverName);
            return config;
        }

        String packageName = args[0];
        log.info("[{}] 解析 uvx 包: {}", serverName, packageName);

        // 检查本地是否已安装
        String localPath = findLocalPythonPackage(packageName);

        if (localPath != null) {
            log.info("[{}] 使用本地安装的包: {}", serverName, localPath);
            return createLocalPythonConfig(config, localPath, args);
        }

        // 尝试安装到本地，安装成功直接返回
        String installedPath = installPythonPackageAndGetPath(packageName, serverName);
        if (installedPath != null) {
            log.info("[{}] 本地安装成功: {}", serverName, installedPath);
            return createLocalPythonConfig(config, installedPath, args);
        }

        // 本地安装失败，使用 uvx 远程启动
        log.info("[{}] 使用 uvx 远程启动", serverName);
        return config;
    }

    /**
     * 准备 node 类型的服务（直接指定路径）
     */
    private McpServerConfig prepareNodeServer(String serverName, McpServerConfig config) {
        String[] args = config.getArgs();
        if (args == null || args.length < 1) {
            return config;
        }

        // 替换路径占位符
        String scriptPath = replacePlaceholders(args[0]);
        if (!new File(scriptPath).exists()) {
            log.warn("[{}] Node 脚本不存在: {}", serverName, scriptPath);
        }

        McpServerConfig newConfig = new McpServerConfig();
        newConfig.setCommand("node");
        newConfig.setArgs(Arrays.stream(args)
            .map(this::replacePlaceholders)
            .toArray(String[]::new));
        newConfig.setEnv(replaceEnvPlaceholders(config.getEnv()));
        newConfig.setDisabled(config.isDisabled());
        newConfig.setTimeout(config.getTimeout());
        newConfig.setDescription(config.getDescription());

        return newConfig;
    }

    /**
     * 准备 python 类型的服务
     */
    private McpServerConfig preparePythonServer(String serverName, McpServerConfig config) {
        String[] args = config.getArgs();
        if (args == null) {
            return config;
        }

<<<<<<< HEAD
        // 自动检测系统可用的 Python 命令
        String pythonCommand = detectPythonCommand();
        log.info("[{}] 检测到 Python 命令: {}", serverName, pythonCommand);

        // 替换路径占位符
        McpServerConfig newConfig = new McpServerConfig();
        newConfig.setCommand(pythonCommand);
=======
        // 替换路径占位符
        McpServerConfig newConfig = new McpServerConfig();
        newConfig.setCommand(config.getCommand());
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
        newConfig.setArgs(Arrays.stream(args)
            .map(this::replacePlaceholders)
            .toArray(String[]::new));
        newConfig.setEnv(replaceEnvPlaceholders(config.getEnv()));
        newConfig.setDisabled(config.isDisabled());
        newConfig.setTimeout(config.getTimeout());
        newConfig.setDescription(config.getDescription());

        return newConfig;
    }

    /**
<<<<<<< HEAD
     * 检测系统可用的 Python 命令
     * 优先级：python3 > python
     */
    private String detectPythonCommand() {
        String osName = System.getProperty("os.name").toLowerCase();

        // Windows 上优先使用 python
        if (osName.contains("win")) {
            if (isCommandAvailable("python")) {
                return "python";
            }
            if (isCommandAvailable("python3")) {
                return "python3";
            }
        } else {
            // Linux/Mac 上优先使用 python3
            if (isCommandAvailable("python3")) {
                return "python3";
            }
            if (isCommandAvailable("python")) {
                return "python";
            }
        }

        // 都不可用，返回配置中的原始值
        log.warn("未检测到可用的 Python 命令");
        return "python";
    }

    /**
     * 检查命令是否可用
     */
    private boolean isCommandAvailable(String command) {
        try {
            ProcessBuilder pb;
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", command, "--version");
            } else {
                pb = new ProcessBuilder(command, "--version");
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            process.destroy();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
=======
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
     * 查找本地安装的 npm 包
     */
    private String findLocalNpmPackage(String packageName) {
        String userDir = System.getProperty("user.dir");

        // 1. 检查本地存储目录
        String localPath = userDir + "/" + localStorePath + "/npm/node_modules/" + packageName + "/dist/index.js";
        if (new File(localPath).exists()) {
            return localPath;
        }

        // 2. 检查项目 mcp-servers 目录
        String mcpServersPath = userDir + "/src/main/resources/mcp-servers/node_modules/" + packageName + "/dist/index.js";
        if (new File(mcpServersPath).exists()) {
            return mcpServersPath;
        }

        // 3. 检查项目根目录 node_modules
        String rootPath = userDir + "/node_modules/" + packageName + "/dist/index.js";
        if (new File(rootPath).exists()) {
            return rootPath;
        }

        return null;
    }

    /**
     * 查找本地安装的 Python 包
     */
    private String findLocalPythonPackage(String packageName) {
        String userDir = System.getProperty("user.dir");

        // 1. 检查本地存储目录的虚拟环境
        String osName = System.getProperty("os.name").toLowerCase();
        String venvPython;
        if (osName.contains("win")) {
            venvPython = userDir + "/" + localStorePath + "/python/venv/Scripts/python.exe";
        } else {
            venvPython = userDir + "/" + localStorePath + "/python/venv/bin/python";
        }

        if (new File(venvPython).exists()) {
            // 检查包是否已安装
            try {
                ProcessBuilder pb = new ProcessBuilder(venvPython, "-c", "import " + packageName.replace("-", "_"));
                pb.redirectErrorStream(true);
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return venvPython;
                }
            } catch (Exception e) {
                log.debug("检查 Python 包失败: {}", e.getMessage());
            }
        }

        // 2. 检查项目 mcp-python 目录
        if (osName.contains("win")) {
            venvPython = userDir + "/src/main/resources/mcp-python/venv/Scripts/python.exe";
        } else {
            venvPython = userDir + "/src/main/resources/mcp-python/venv/bin/python";
        }

        if (new File(venvPython).exists()) {
            try {
                ProcessBuilder pb = new ProcessBuilder(venvPython, "-c", "import " + packageName.replace("-", "_"));
                pb.redirectErrorStream(true);
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return venvPython;
                }
            } catch (Exception e) {
                log.debug("检查 Python 包失败: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * 安装 npm 包到本地
     */
    private boolean installNpmPackage(String packageName, String serverName) {
        if (installedPackages.containsKey(packageName)) {
            return installedPackages.get(packageName);
        }

        try {
            String userDir = System.getProperty("user.dir");
            String targetDir = userDir + "/" + localStorePath + "/npm";

            // 创建目录
            File dir = new File(targetDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            log.info("[{}] 正在安装 npm 包 {} 到本地...", serverName, packageName);

            List<String> command = new ArrayList<>();
            command.add("npm");
            command.add("install");
            command.add(packageName);

            // 如果配置了私有 registry
            if (npmRegistry != null && !npmRegistry.isEmpty()) {
                command.add("--registry");
                command.add(npmRegistry);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(dir);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 读取输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[npm] {}", line);
            }

            int exitCode = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS) ? process.exitValue() : -1;

            boolean success = exitCode == 0;
            installedPackages.put(packageName, success);

            if (success) {
                log.info("[{}] npm 包 {} 安装成功", serverName, packageName);
            } else {
                log.warn("[{}] npm 包 {} 安装失败，exitCode={}", serverName, packageName, exitCode);
            }

            return success;

        } catch (Exception e) {
            log.error("[{}] 安装 npm 包 {} 失败: {}", serverName, packageName, e.getMessage());
            installedPackages.put(packageName, false);
            return false;
        }
    }

    /**
     * 安装 Python 包到本地虚拟环境
     */
    private boolean installPythonPackage(String packageName, String serverName) {
        if (installedPackages.containsKey(packageName)) {
            return installedPackages.get(packageName);
        }

        try {
            String userDir = System.getProperty("user.dir");
            String targetDir = userDir + "/" + localStorePath + "/python";
            String venvDir = targetDir + "/venv";

            String osName = System.getProperty("os.name").toLowerCase();
            String pipCmd;
            String pythonCmd;

            // 创建虚拟环境
            if (!new File(venvDir).exists()) {
                log.info("[{}] 创建 Python 虚拟环境...", serverName);
                ProcessBuilder pb = new ProcessBuilder("python", "-m", "venv", venvDir);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            }

            if (osName.contains("win")) {
                pipCmd = venvDir + "/Scripts/pip.exe";
                pythonCmd = venvDir + "/Scripts/python.exe";
            } else {
                pipCmd = venvDir + "/bin/pip";
                pythonCmd = venvDir + "/bin/python";
            }

            log.info("[{}] 正在安装 Python 包 {} 到本地虚拟环境...", serverName, packageName);

            List<String> command = new ArrayList<>();
            command.add(pipCmd);
            command.add("install");
            command.add(packageName);

            // 如果配置了私有 PyPI
            if (pypiIndex != null && !pypiIndex.isEmpty()) {
                command.add("-i");
                command.add(pypiIndex);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 读取输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[pip] {}", line);
            }

            int exitCode = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS) ? process.exitValue() : -1;

            boolean success = exitCode == 0;
            installedPackages.put(packageName, success);

            if (success) {
                log.info("[{}] Python 包 {} 安装成功", serverName, packageName);
            } else {
                log.warn("[{}] Python 包 {} 安装失败，exitCode={}", serverName, packageName, exitCode);
            }

            return success;

        } catch (Exception e) {
            log.error("[{}] 安装 Python 包 {} 失败: {}", serverName, packageName, e.getMessage());
            installedPackages.put(packageName, false);
            return false;
        }
    }

    /**
     * 安装 Python 包并返回 Python 可执行文件路径
     */
    private String installPythonPackageAndGetPath(String packageName, String serverName) {
        if (installedPackages.containsKey(packageName) && installedPackages.get(packageName)) {
            // 已安装，返回路径
            return getVenvPythonPath(localStorePath + "/python/venv");
        }

        try {
            String userDir = System.getProperty("user.dir");
            String targetDir = userDir + "/" + localStorePath + "/python";
            String venvDir = targetDir + "/venv";

            String osName = System.getProperty("os.name").toLowerCase();
            String pipCmd;
            String pythonCmd;

            // 创建虚拟环境
            if (!new File(venvDir).exists()) {
                log.info("[{}] 创建 Python 虚拟环境...", serverName);
                ProcessBuilder pb = new ProcessBuilder("python", "-m", "venv", venvDir);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            }

            if (osName.contains("win")) {
                pipCmd = venvDir + "/Scripts/pip.exe";
                pythonCmd = venvDir + "/Scripts/python.exe";
            } else {
                pipCmd = venvDir + "/bin/pip";
                pythonCmd = venvDir + "/bin/python";
            }

            log.info("[{}] 正在安装 Python 包 {} 到本地虚拟环境...", serverName, packageName);

            List<String> command = new ArrayList<>();
            command.add(pipCmd);
            command.add("install");
            command.add(packageName);

            if (pypiIndex != null && !pypiIndex.isEmpty()) {
                command.add("-i");
                command.add(pypiIndex);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[pip] {}", line);
            }

            int exitCode = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS) ? process.exitValue() : -1;

            if (exitCode == 0) {
                log.info("[{}] Python 包 {} 安装成功", serverName, packageName);
                installedPackages.put(packageName, true);

                // 检查是否有可执行文件（如 shell-mcp-server.exe）
                String executablePath = findPythonPackageExecutable(venvDir, packageName, osName);
                if (executablePath != null) {
                    log.info("[{}] 找到可执行文件: {}", serverName, executablePath);
                    return executablePath;
                }

                return pythonCmd;
            } else {
                log.warn("[{}] Python 包 {} 安装失败，exitCode={}", serverName, packageName, exitCode);
                installedPackages.put(packageName, false);
                return null;
            }

        } catch (Exception e) {
            log.error("[{}] 安装 Python 包 {} 失败: {}", serverName, packageName, e.getMessage());
            installedPackages.put(packageName, false);
            return null;
        }
    }

    /**
     * 查找 Python 包的可执行文件
     */
    private String findPythonPackageExecutable(String venvDir, String packageName, String osName) {
        String scriptsDir;
        if (osName.contains("win")) {
            scriptsDir = venvDir + "/Scripts";
        } else {
            scriptsDir = venvDir + "/bin";
        }

        // 将包名转换为可执行文件名（如 shell-mcp-server -> shell-mcp-server.exe on Windows）
        String executableName = packageName;
        if (osName.contains("win")) {
            executableName = packageName + ".exe";
        }

        File executable = new File(scriptsDir + "/" + executableName);
        if (executable.exists()) {
            return executable.getAbsolutePath();
        }

        return null;
    }

    /**
     * 获取虚拟环境 Python 路径
     */
    private String getVenvPythonPath(String venvDir) {
        String osName = System.getProperty("os.name").toLowerCase();
        String userDir = System.getProperty("user.dir");
        String fullPath = userDir + "/" + venvDir;

        if (osName.contains("win")) {
            return fullPath + "/Scripts/python.exe";
        } else {
            return fullPath + "/bin/python";
        }
    }

    /**
     * 创建本地 Node 配置
     */
    private McpServerConfig createLocalNodeConfig(McpServerConfig original, String localPath, String[] originalArgs) {
        McpServerConfig config = new McpServerConfig();
        config.setCommand("node");

        // 构建新的参数：本地脚本路径 + 原始参数中跳过 -y 和包名的部分
        List<String> newArgs = new ArrayList<>();
        newArgs.add(localPath);

        // 添加原始参数中包名之后的部分
        if (originalArgs.length > 2) {
            for (int i = 2; i < originalArgs.length; i++) {
                newArgs.add(replacePlaceholders(originalArgs[i]));
            }
        }

        config.setArgs(newArgs.toArray(new String[0]));
        config.setEnv(replaceEnvPlaceholders(original.getEnv()));
        config.setDisabled(original.isDisabled());
        config.setTimeout(original.getTimeout());
        config.setDescription(original.getDescription() + " (本地)");

        return config;
    }

    /**
     * 创建本地 Python 配置
     */
    private McpServerConfig createLocalPythonConfig(McpServerConfig original, String pythonPath, String[] originalArgs) {
        McpServerConfig config = new McpServerConfig();

        // 检查是否是可执行文件（如 shell-mcp-server.exe）
        boolean isExecutable = pythonPath.endsWith(".exe") ||
            (originalArgs.length > 0 && pythonPath.contains(originalArgs[0].replace("-", "_")));

        if (isExecutable && !pythonPath.endsWith("python.exe") && !pythonPath.endsWith("python")) {
            // 直接使用可执行文件
            config.setCommand(pythonPath);

            List<String> newArgs = new ArrayList<>();
            // 添加原始参数中包名之后的部分
            if (originalArgs.length > 1) {
                for (int i = 1; i < originalArgs.length; i++) {
                    newArgs.add(replacePlaceholders(originalArgs[i]));
                }
            }

            config.setArgs(newArgs.toArray(new String[0]));
        } else {
            // 使用 python -m module 方式
            config.setCommand(pythonPath);

            List<String> newArgs = new ArrayList<>();
            newArgs.add("-m");

            // 第一个参数是包名，转换为模块名
            String moduleName = originalArgs[0].replace("-", "_");
            newArgs.add(moduleName);

            // 添加其他参数
            if (originalArgs.length > 1) {
                for (int i = 1; i < originalArgs.length; i++) {
                    newArgs.add(replacePlaceholders(originalArgs[i]));
                }
            }

            config.setArgs(newArgs.toArray(new String[0]));
        }

        config.setEnv(replaceEnvPlaceholders(original.getEnv()));
        config.setDisabled(original.isDisabled());
        config.setTimeout(original.getTimeout());
        config.setDescription(original.getDescription() + " (本地)");

        return config;
    }

    /**
     * 替换环境变量 Map 中的占位符
     */
    private Map<String, String> replaceEnvPlaceholders(Map<String, String> env) {
        if (env == null) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            result.put(entry.getKey(), replacePlaceholders(entry.getValue()));
        }
        return result;
    }

    /**
     * 替换路径占位符
     */
    private String replacePlaceholders(String str) {
        if (str == null) return null;

        String userHome = System.getProperty("user.home");
        String userDir = System.getProperty("user.dir");
        String tmpDir = System.getProperty("java.io.tmpdir");

        String result = str
            .replace("${user.home}", userHome)
            .replace("${user.dir}", userDir)
            .replace("${java.io.tmpdir}", tmpDir)
            .replace("${mcp.local-store-path}", localStorePath);

        // 处理环境变量占位符 ${env.VAR_NAME} 或 ${env.VAR_NAME:default}
        java.util.regex.Pattern envPattern = java.util.regex.Pattern.compile("\\$\\{env\\.([A-Za-z0-9_]+)(?::([^}]*))?\\}");
        java.util.regex.Matcher matcher = envPattern.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String envVar = matcher.group(1);
            String defaultValue = matcher.group(2) != null ? matcher.group(2) : "";
            String envValue = System.getenv(envVar);
            String replacement = envValue != null ? envValue : defaultValue;
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }
}
