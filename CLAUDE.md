# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SuperFriend is an AI agent platform that integrates MCP (Model Context Protocol) servers with a modular Skills system. It supports multiple chat modes (simple task, medium task, complex task) with different execution strategies (direct ReAct loop, task planning, plan-execute mode).

**Tech Stack:**
- Backend: Spring Boot 2.7.x with Java 8
- Frontend: Vue 3 + TypeScript + Element Plus + Pinia
- Database: MySQL
- Build: Maven (backend), npm/Vite (frontend)

## Build & Run Commands

### Backend (Spring Boot)
```bash
# Build (Windows: use mvnw.cmd instead of ./mvnw)
./mvnw clean package -DskipTests

# Run
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run single test class
./mvnw test -Dtest=ClassName
```

### Frontend (Vue 3)
```bash
cd ui
npm install          # Install dependencies
npm run dev          # Development server (http://localhost:5173)
npm run build        # Production build
npm run lint         # Run ESLint
npm run format       # Format with Prettier
```

### Database
- MySQL database: `superfriend`
- Configure connection in `src/main/resources/application.yml`
- SQL schema files in `src/main/resources/sql/`:
  - `superfriend.sql` - Main schema
  - `chat_history.sql`, `ai_process_history.sql` - Chat and process tracking
  - `knowledge_graph*.sql` - Knowledge graph schema
  - `skill_selection.sql`, `user_skill_selection.sql` - Skill routing
  - `ai_model_config*.sql` - Model configuration
  - `task_plan.sql` - Plan execution tracking
  - `permission.sql` - Access control

## Architecture

### Backend Package Structure
```
com.superfriend.superfriend
├── agent/           # Core agent logic
│   ├── error/       # Error classification, recovery, retry
│   ├── executor/    # Tool execution
│   ├── orchestration/ # Agent orchestrator, ReAct loop, skill router
│   ├── planner/     # Task decomposition, dependency management
│   ├── retry/       # Retry strategies
│   ├── skill/       # Skill registry, execution, hooks
│   └── tool/        # Tool registry, selection, web search, data analysis
├── config/          # Spring configuration (CORS, Async, Jackson, OpenAPI)
├── controller/      # REST endpoints
├── dto/             # Data transfer objects
├── entity/          # JPA entities
├── mapper/          # MyBatis mappers
├── service/         # Business logic
└── typehandler/     # MyBatis type handlers
```

### Key Services

- **McpHostService**: Central service for MCP server lifecycle management, tool discovery, and tool invocation. Handles chatWithMCP(), chatWithMCPComplex(), simpleTaskChat() methods.
- **AgentExecutionService**: Executes agent tasks with optimizations (parallel execution, progress tracking, reflection).
- **SkillRegistry**: Manages skill registration, selection, and resource loading. Supports both filesystem-based (skills/system/) and database-stored skills.
- **LLMClient**: Handles streaming LLM API calls with tool support.
- **AIService**: High-level AI chat service with memory and skill injection.
- **MemoryService**: Long-term memory storage and retrieval with embedding-based semantic search.

### Knowledge Graph & Memory

- **Knowledge Graph**: Separate from conversation history - stores entities and relationships for long-term context. Nodes must be unique per conversation (see `memory/` project notes)
- **Memory entities**: Stored in database with embedding vectors for semantic search
- **Conversation history**: Separate from knowledge graph, used for chat continuity

### Orchestration Flow

```
User Message → AIService
                    ↓
         SkillRegistry (select relevant skills)
                    ↓
         LLMClient (inject skills + memory into context)
                    ↓
         AgentExecutionService (execute ReAct loop)
                    ↓
         ToolRegistry → McpHostService (invoke MCP tools)
                    ↓
         MemoryService (store important results)
                    ↓
         Response to User
```

### MCP Integration

- MCP server configs: `src/main/resources/mcpserverconfig/mcp-servers-config.json`
- Selected servers config: `src/main/resources/mcpserverconfig/mcp-servers-small-config.json`
- MCP tools are prefixed with `serverName__toolName` (double underscore)
- Special built-in tools: `load_skill`, `read_skill_resource`, `run_skill_script`

### Skills System

Skills are defined in `skills/system/` with SKILL.md files containing:
- YAML frontmatter with metadata (name, description, tags, priority, allowed-tools)
- Markdown body with instructions and examples

Skills can also be stored in the database (entity: `Skill`, `UserSkill`) for user-defined skills.

**Skill selection**: Skills are selected based on task type, conversation context, and user preferences (see `skill_selection.sql`, `user_skill_selection.sql`)

### Execution Modes & ReAct Loop

The agent uses a **ReAct (Reasoning + Acting)** loop pattern:

```
User Input → LLM (Thought) → Tool Execution → Observation → LLM (Thought) → ...
```

**Three execution modes:**

| Mode | Strategy | Max Iterations | Reflection | Skills |
|------|----------|----------------|------------|--------|
| SIMPLE_TASK | Direct ReAct loop | 25 | No | No |
| MEDIUM_TASK | ReAct with skill injection | 40 | Yes | Yes |
| COMPLEX_TASK | Plan-Execute with task decomposition | 80 | Yes | Yes |

- **Reflection**: After max iterations, the agent reviews accumulated errors and attempts recovery
- **Plan-Execute**: Decomposes complex tasks into sub-tasks, executes them with dependency management
- **Skill injection**: Relevant skills are loaded into context based on task type and conversation history

## API Endpoints

Key endpoints (see Swagger UI at `/swagger-ui.html`):
- `/api/chat/**` - Chat endpoints
- `/api/mcp/**` - MCP server management
- `/api/skills/**` - Skills management
- `/api/memory/**` - Long-term memory
- `/api/models/**` - AI model configuration
- `/api/auth/**` - Authentication

## Configuration

Key config in `application.yml`:
- `mcp.host.idle-timeout-ms`: MCP server idle timeout
- `mcp.host.enable-idle-check`: Enable/disable automatic server shutdown
- `skills.system.path`: Path to system skills directory
- `observability.*`: Langfuse/Arize integration
- `cost-tracking.*`: Usage budget settings

## Frontend Structure

```
ui/src/
├── api/            # API client modules
├── components/     # Vue components (business/, UI elements)
├── composables/    # Vue composables (useAuth, useRealtime)
├── layouts/        # Page layouts
├── router/         # Vue Router config
├── stores/         # Pinia stores (chat, mcp, skill, user)
├── types/          # TypeScript type definitions
├── utils/          # Utility functions
└── views/          # Page components
```

**Frontend Design Guidelines:** See `ui/CLAUDE.md` for detailed theme specifications including:
- Color system (light/dark mode with CSS variables)
- Typography, spacing, and border-radius standards
- Component patterns (cards, buttons, forms, dialogs)
- Element Plus dialog style overrides
- Accessibility requirements

## MCP Servers Directory

`mcp-servers/` contains npm-installed MCP server packages. The `puppeteer-wrapper.js` handles browser automation with session persistence.

**MCP Server Configuration:**
- Config file: `src/main/resources/mcpserverconfig/mcp-servers-config.json`
- Enable/disable servers via the `disabled` field
- Servers requiring API keys (brave-search, github, slack, zapier, rube) are disabled by default
- Tool naming convention: `serverName__toolName` (double underscore)

## Python Scripts

`python_scripts/` contains utility scripts for Excel operations, file batch processing, system monitoring, and code analysis. Install dependencies with `pip install -r requirements.txt`.

## Environment Variables

Sensitive configuration in `application.yml` supports environment variable substitution:
- `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY` - Langfuse observability
- `ARIZE_API_KEY`, `ARIZE_SPACE_ID` - Arize observability
- API keys for MCP servers (brave-search, github, slack, zapier, rube) should be configured in `mcp-servers-config.json`

## Important Project Notes

- **Knowledge Graph Uniqueness**: Global and conversation knowledge graphs only allow one node with the same name (see `memory/` project notes)
- **API Keys Required**: Some MCP servers (brave-search, github, slack, zapier, rube) require API keys and are disabled by default
- **MCP Tool Naming**: Tools use `serverName__toolName` format (double underscore) to avoid naming conflicts
