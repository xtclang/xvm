# Claude Code Configuration for XVM Project

This document explains the Claude Code configuration setup for the XVM project.

## Configuration Files

### CLAUDE.md (Project Root)

The main configuration file at `/CLAUDE.md` defines:

- **Role & Responsibilities**: XVM expert assistant with deep knowledge of Ecstasy language, Gradle builds, Docker containerization, and CI/CD
- **Output Control**: 
  - Default: Concise responses (1-4 lines)
  - Verbose mode: Say "verbose" for complete, untruncated output
  - Concise mode: Say "concise" to return to brief mode
- **Technical Context**: Project-specific knowledge about build system, languages, and architecture
- **Code Standards**: Consistent with project conventions

## File Locations & Hierarchy

Claude Code searches for configuration in this order:

1. **Project Root**: `./CLAUDE.md` (committed to git, shared across team)
2. **Parent Directories**: Searches up the directory tree (useful for monorepos)
3. **Child Directories**: Searches down from current directory
4. **User Home**: `~/.claude/CLAUDE.md` (personal settings, not committed)

## Best Practices

### Team Configuration
- **Commit `CLAUDE.md`** to git for shared team context
- Provides consistent AI assistance across all team members
- New developers get immediate context when they start

### Personal Overrides
- Use `~/.claude/CLAUDE.md` for personal preferences
- Add to `.gitignore` to avoid committing personal settings
- Overrides project settings when needed

### Output Control
This project implements a simple keyword system:
- **"verbose"**: Get complete logs, full command output, untruncated responses
- **"concise"**: Return to brief, summarized responses
- **Default**: Concise mode unless verbose requested

## Technical Integration

The configuration is tailored for XVM development:

- **Build System**: Gradle multi-project builds with custom plugins
- **Languages**: Ecstasy language compilation to XTC bytecode
- **Containerization**: Docker multi-stage builds with BuildKit caching
- **CI/CD**: GitHub Actions with matrix builds (multiple OS/architectures)
- **Performance**: Optimized caching strategies for Docker and Gradle

## Usage Examples

```bash
# Get brief help
"How do I build the XDK?"

# Get complete build output
"verbose - How do I build the XDK?"

# Return to brief mode
"concise - What's the current version?"
```

## File Management

- **Project file**: Always committed to git
- **Personal overrides**: Use `~/.claude/CLAUDE.md` for personal preferences
- **Backups**: Configuration is in version control, no additional backup needed
- **Updates**: Modify `CLAUDE.md` and commit changes for team-wide updates

This configuration ensures consistent, helpful AI assistance while allowing personal customization when needed.