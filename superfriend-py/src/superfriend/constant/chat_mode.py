class ChatMode:
    LITE_TASK = "lite-task"
    MEDIUM_TASK = "medium-task"
    COMPLEX_TASK = "complex-task"
    MCP = "mcp"
    AI = "ai"
    DEFAULT = LITE_TASK

    @classmethod
    def is_valid(cls, mode: str | None) -> bool:
        if mode is None:
            return False
        return (
            cls.LITE_TASK == mode
            or cls.MEDIUM_TASK == mode
            or cls.COMPLEX_TASK == mode
            or cls.MCP == mode
            or cls.AI == mode
        )

    @classmethod
    def normalize(cls, mode: str | None) -> str:
        if not cls.is_valid(mode):
            return cls.DEFAULT
        return mode or cls.DEFAULT