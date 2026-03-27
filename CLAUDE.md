# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 3.5.6 (Java 21) backend for AI-powered web page generation. The system uses LangChain4j to interface with LLMs (DeepSeek by default) and generates HTML/CSS/JS code based on user prompts. Generated code is saved to the filesystem under `tmp/code_output/`.

## Build and Development

### Building
```bash
mvn clean compile     # Compile the project
mvn package           # Build JAR package
```

**Note**: The project uses Lombok for boilerplate code generation. Ensure your IDE has the Lombok plugin installed for proper code completion.

### Running
```bash
mvn spring-boot:run   # Start the application locally
```
The application runs on port 8123 with context path `/api`. API documentation is available at `http://localhost:8123/api/doc.html` (Knife4j).

### Testing
```bash
mvn test                          # Run all tests
mvn test -Dtest=ClassName         # Run specific test class
mvn test -Dtest=ClassName#method  # Run specific test method
```

Tests are located in `src/test/java/` and use Spring Boot's test framework.

## Architecture

### Core AI Pipeline
1. **AiCodeGeneratorFacade** ([src/main/java/com/miu/codemain/core/AiCodeGeneratorFacade.java](src/main/java/com/miu/codemain/core/AiCodeGeneratorFacade.java)): Main entry point that coordinates code generation and saving.
2. **AiCodeGeneratorServiceFactory** ([src/main/java/com/miu/codemain/ai/AiCodeGeneratorServiceFactory.java](src/main/java/com/miu/codemain/ai/AiCodeGeneratorServiceFactory.java)): Creates LangChain4j AI service instances (currently partially implemented).
3. **AiCodeGeneratorService** ([src/main/java/com/miu/codemain/ai/AiCodeGeneratorService.java](src/main/java/com/miu/codemain/ai/AiCodeGeneratorService.java)): Interface with LLM, defines generation methods for different code types.
4. **CodeParserExecutor** ([src/main/java/com/miu/codemain/core/parser/CodeParserExecutor.java](src/main/java/com/miu/codemain/core/parser/CodeParserExecutor.java)): Parses raw AI output into structured objects (HtmlCodeResult, MultiFileCodeResult).
5. **CodeFileSaverExecutor** ([src/main/java/com/miu/codemain/core/saver/CodeFileSaverExecutor.java](src/main/java/com/miu/codemain/core/saver/CodeFileSaverExecutor.java)): Saves parsed code to filesystem using template pattern.

### Code Generation Types
- **HTML**: Single HTML file with inline CSS and JS (prompt: `prompt/codegen-html-system-prompt.txt`)
- **MULTI_FILE**: Separate `index.html`, `style.css`, `script.js` files (prompt: `prompt/codegen-multi-file-system-prompt.txt`)
- **VUE_PROJECT**: Planned but not yet implemented (would use tool calling system)

### Tool System (for future Vue project generation)
- **BaseTool** ([src/main/java/com/miu/codemain/ai/tool/BaseTool.java](src/main/java/com/miu/codemain/ai/tools/BaseTool.java)): Abstract base class for AI tools.
- **ToolManager** ([src/main/java/com/miu/codemain/ai/tool/ToolManager.java](src/main/java/com/miu/codemain/ai/tools/ToolManager.java)): Registers and manages all tool instances via Spring dependency injection.
- Tools would enable AI to perform actions like creating files, installing dependencies, etc.

### Data Layer
- **MyBatis Flex** ORM with MySQL database.
- Entities in `model/entity/`, mappers in `mapper/`, services in `service/`.
- MyBatis Code Generator at `src/main/java/com/miu/codemain/generator/MyBatisCodeGenerator.java` can generate boilerplate code.

### API Layer
- Controllers in `controller/` package.
- Standard REST endpoints with JSON responses.
- Authentication/authorization via `AuthInterceptor` and `@AuthCheck` annotation.
- Common response wrappers (`BaseResponse`, `ResultUtils`) and exception handling (`GlobalExceptionHandler`).

## Configuration

### Application Properties
- Main configuration: `src/main/resources/application.yml`
- Database: MySQL at `localhost:3306/ai_page_gen` (update credentials)
- AI: DeepSeek API (requires API key in `langchain4j.open-ai.chat-model.api-key`)
- Output directories: `tmp/code_output/` and `tmp/code_deploy/` (relative to project root)

### Important Constants
- `AppConstant.CODE_OUTPUT_ROOT_DIR`: Where generated code is saved
- `AppConstant.CODE_DEPLOY_ROOT_DIR`: Where deployed code would go
- `UserConstant`: User roles and session management

### Database Setup
- Initial schema: `sql/create_table.sql` (run this script to create database and tables)
- MyBatis Flex code generator can create entity/mapper/service/controller boilerplate from existing tables

## Development Notes

### Current State
- Core AI generation pipeline is functional for HTML and multi-file outputs.
- `AiCodeGeneratorServiceFactory.getAiCodeGeneratorService()` currently returns `null` – needs implementation to properly create AI service instances with caching and chat memory.
- Tool system is set up but not yet integrated with AI services.
- Vue project generation is commented out in the facade.

### Key Patterns
- **Facade Pattern**: `AiCodeGeneratorFacade` simplifies complex subsystem interactions.
- **Template Method Pattern**: `CodeFileSaverTemplate` and its implementations.
- **Strategy Pattern**: Different parsers and savers for each code generation type.
- **Factory Pattern**: `AiCodeGeneratorServiceFactory` (partially implemented).

### File Structure Highlights
```
src/main/java/com/miu/codemain/
├── ai/                          # AI integration layer
│   ├── model/                   # AI response models
│   ├── tool/                    # Tool system for AI
│   └── AiCodeGeneratorService*.java
├── core/                        # Core business logic
│   ├── parser/                  # Code parsers
│   ├── saver/                   # Code savers
│   ├── builder/                 # Project builders (Vue)
│   └── AiCodeGeneratorFacade.java
├── controller/                  # REST API endpoints
├── service/                     # Business services
├── mapper/                      # MyBatis data mappers
├── model/                       # Data models
│   ├── entity/                  # Database entities
│   ├── dto/                     # Data transfer objects
│   ├── vo/                      # View objects
│   └── enums/                   # Enumerations
├── common/                      # Shared utilities
├── constant/                    # Application constants
├── exception/                   # Custom exceptions
├── config/                      # Spring configuration
├── aop/                         # Aspect-oriented programming
└── generator/                   # Code generators
```

### Testing
- Tests verify AI service interactions (requires configured API key).
- Consider mocking LLM calls for reliable unit tests.
- Test files follow naming pattern `*Test.java`.

## Common Tasks

### Adding a New Code Generation Type
1. Add enum value to `CodeGenTypeEnum`
2. Create system prompt in `resources/prompt/`
3. Implement parser extending `CodeParser` interface
4. Implement saver extending `CodeFileSaverTemplate`
5. Update `AiCodeGeneratorService` interface and implementation
6. Extend switch statements in `AiCodeGeneratorFacade`, `CodeParserExecutor`, `CodeFileSaverExecutor`

### Adding a New AI Tool
1. Create class extending `BaseTool`
2. Implement `getToolName()`, `getDisplayName()`, `generateToolExecutedResult()`
3. Annotate with `@Component` for Spring auto-detection
4. Tool will be automatically registered by `ToolManager`

### Database Schema Changes
1. Update MySQL schema in `sql/create_table.sql`
2. Regenerate MyBatis Flex code using `MyBatisCodeGenerator`
3. Or manually create entity, mapper, service, controller

## Git Conventions

Recent commits follow a loose conventional commits style (e.g., "feat: 用户模块", "fix: 解决精度问题"). Use descriptive commit messages in Chinese or English that summarize the change.

## Dependencies

- **Spring Boot 3.5.6**: Web, AOP, Test
- **LangChain4j 1.1.0**: AI integration with DeepSeek
- **MyBatis Flex 1.11.0**: ORM with code generation
- **Knife4j 4.4.0**: API documentation
- **Hutool 5.8.38**: Utility library
- **MySQL Connector**: Database driver