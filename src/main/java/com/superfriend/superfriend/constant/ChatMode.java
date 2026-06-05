package com.superfriend.superfriend.constant;

public final class ChatMode {

    private ChatMode() {
    }

    public static final String LITE_TASK = "lite-task";
    public static final String MEDIUM_TASK = "medium-task";
    public static final String COMPLEX_TASK = "complex-task";
    public static final String MCP = "mcp";
    public static final String AI = "ai";
    public static final String DEFAULT = LITE_TASK;

    public static boolean isValid(String mode) {
        if (mode == null) {
            return false;
        }
        return LITE_TASK.equals(mode)
            || MEDIUM_TASK.equals(mode)
            || COMPLEX_TASK.equals(mode)
            || MCP.equals(mode)
            || AI.equals(mode);
    }

    public static String normalize(String mode) {
        if (mode == null || !isValid(mode)) {
            return DEFAULT;
        }
        return mode;
    }
}
