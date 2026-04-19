package com.superfriend.superfriend.agent.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.List;

@Slf4j
@Service
public class ScriptExecutor {
    
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final long DEFAULT_TIMEOUT = 600000;

    public ScriptExecutionResult executeScript(
        String scriptPath,
        Map<String, Object> parameters,
        File workingDirectory
    ) {
        return executeScript(scriptPath, parameters, workingDirectory, DEFAULT_TIMEOUT);
    }

    public ScriptExecutionResult executeScript(
        String scriptPath,
        Map<String, Object> parameters,
        File workingDirectory,
        long timeoutMs
    ) {
        long startTime = System.currentTimeMillis();
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setScriptPath(scriptPath);

        try {
            File scriptFile = new File(scriptPath);
            if (!scriptFile.exists()) {
                return ScriptExecutionResult.failure("Script file not found: " + scriptPath);
            }

            if (!scriptFile.canExecute()) {
                scriptFile.setExecutable(true);
            }

            String language = detectLanguage(scriptPath);
            
            if (!checkDependencies(language)) {
                return ScriptExecutionResult.failure("Dependencies not available for language: " + language);
            }

            File paramFile = createParameterFile(parameters, workingDirectory);
            
            List<String> command = buildCommand(language, scriptPath, paramFile);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workingDirectory);
            processBuilder.redirectErrorStream(false);

            Map<String, String> env = processBuilder.environment();
            env.put("SKILL_PARAM_FILE", paramFile.getAbsolutePath());
            env.put("SKILL_WORKING_DIR", workingDirectory.getAbsolutePath());
            parameters.forEach((key, value) -> {
                env.put("SKILL_PARAM_" + key.toUpperCase(), String.valueOf(value));
            });

            Process process = processBuilder.start();
            String processId = UUID.randomUUID().toString();
            runningProcesses.put(processId, process);

            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();

            Future<?> outputFuture = executorService.submit(() -> {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    error.append("Error reading output: ").append(e.getMessage());
                }
            });

            Future<?> errorFuture = executorService.submit(() -> {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.error("Error reading error stream: {}", e.getMessage());
                }
            });

            boolean completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            
            if (!completed) {
                process.destroyForcibly();
                runningProcesses.remove(processId);
                cleanupTempFile(paramFile);
                return ScriptExecutionResult.failure("Script execution timeout after " + timeoutMs + "ms");
            }

            outputFuture.get(5, TimeUnit.SECONDS);
            errorFuture.get(5, TimeUnit.SECONDS);
            runningProcesses.remove(processId);

            int exitCode = process.exitValue();
            result.setExitCode(exitCode);
            result.setOutput(output.toString().trim());
            result.setError(error.toString().trim());
            result.setSuccess(exitCode == 0);
            result.setExecutionTime(System.currentTimeMillis() - startTime);

            if (exitCode == 0 && !result.getOutput().isEmpty()) {
                try {
                    Map<String, Object> parsedOutput = objectMapper.readValue(
                        result.getOutput(), 
                        Map.class
                    );
                    result.setParsedOutput(parsedOutput);
                } catch (Exception e) {
                    log.debug("Output is not JSON format, keeping as string");
                }
            }

            if (exitCode != 0) {
                result.setError("Script exited with code " + exitCode + ": " + error.toString());
            }

            cleanupTempFile(paramFile);

            log.info("Script executed: {} (exit={}, time={}ms)", 
                scriptPath, exitCode, result.getExecutionTime());

            return result;

        } catch (Exception e) {
            log.error("Script execution failed: {} - {}", scriptPath, e.getMessage(), e);
            result.setSuccess(false);
            result.setError("Execution failed: " + e.getMessage());
            result.setExecutionTime(System.currentTimeMillis() - startTime);
            return result;
        }
    }

    private File createParameterFile(Map<String, Object> parameters, File workingDirectory) 
            throws IOException {
        File paramFile = File.createTempFile("skill_params_", ".json", workingDirectory);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(paramFile, parameters);
        return paramFile;
    }

    private void cleanupTempFile(File file) {
        if (file != null && file.exists()) {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                log.warn("Failed to cleanup temp file: {}", file.getAbsolutePath());
            }
        }
    }

    private boolean checkDependencies(String language) {
        try {
            String command;
            switch (language) {
                case "python":
                    command = "python --version";
                    break;
                case "bash":
                    command = "bash --version";
                    break;
                case "javascript":
                case "typescript":
                    command = "node --version";
                    break;
                case "ruby":
                    command = "ruby --version";
                    break;
                default:
                    return true;
            }
            
            Process process = Runtime.getRuntime().exec(command);
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            return completed && process.exitValue() == 0;
            
        } catch (Exception e) {
            log.warn("Dependency check failed for {}: {}", language, e.getMessage());
            return false;
        }
    }

    private String detectLanguage(String scriptPath) {
        String extension = scriptPath.substring(scriptPath.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "py":
                return "python";
            case "sh":
                return "bash";
            case "js":
                return "javascript";
            case "ts":
                return "typescript";
            case "rb":
                return "ruby";
            default:
                return "unknown";
        }
    }

    private List<String> buildCommand(String language, String scriptPath, File paramFile) {
        List<String> command = new ArrayList<>();

        switch (language) {
            case "python":
                command.add("python");
                command.add("-u");
                command.add(scriptPath);
                command.add("--param-file");
                command.add(paramFile.getAbsolutePath());
                break;
            case "bash":
                command.add("bash");
                command.add(scriptPath);
                command.add(paramFile.getAbsolutePath());
                break;
            case "javascript":
            case "typescript":
                command.add("node");
                command.add(scriptPath);
                command.add("--param-file");
                command.add(paramFile.getAbsolutePath());
                break;
            default:
                command.add(scriptPath);
                command.add(paramFile.getAbsolutePath());
        }

        return command;
    }

    public void killProcess(String processId) {
        Process process = runningProcesses.get(processId);
        if (process != null) {
            process.destroyForcibly();
            runningProcesses.remove(processId);
            log.info("Killed process: {}", processId);
        }
    }

    public void killAllProcesses() {
        runningProcesses.values().forEach(Process::destroyForcibly);
        runningProcesses.clear();
        log.info("Killed all running processes");
    }

    @Data
    public static class ScriptExecutionResult {
        private boolean success;
        private String scriptPath;
        private int exitCode;
        private String output;
        private String error;
        private long executionTime;
        private Map<String, Object> parsedOutput;
        /** 输出文件列表 */
        private List<String> outputFiles;

        public static ScriptExecutionResult success(String output) {
            ScriptExecutionResult result = new ScriptExecutionResult();
            result.setSuccess(true);
            result.setOutput(output);
            result.setExitCode(0);
            return result;
        }

        public static ScriptExecutionResult failure(String error) {
            ScriptExecutionResult result = new ScriptExecutionResult();
            result.setSuccess(false);
            result.setError(error);
            result.setExitCode(-1);
            return result;
        }

        public boolean hasParsedOutput() {
            return parsedOutput != null && !parsedOutput.isEmpty();
        }

        public boolean hasOutputFiles() {
            return outputFiles != null && !outputFiles.isEmpty();
        }
    }
}
