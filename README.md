# Kensa Agent Skills

AI coding skills that teach your agent how to write idiomatic [Kensa](https://kensa.dev) BDD tests in Kotlin and Java. Works with [Claude Code](https://claude.ai/code), [OpenCode](https://opencode.ai), and any agent that supports the `SKILL.md` format.

## What's in the skill

The `kensa-development` skill is an expert reviewer for Kensa tests. It detects violations of Kensa best practices (rendered code must read as fluent English, action lambdas don't belong in rendered contexts, use `Fixtures` and `@RenderedValue` correctly, build a composable test toolbox, wrap raw assertions in semantic functions, prefer typed context objects), produces an improved version, and explains the changes.

Reference material covers Fixtures, `@RenderedValue`, setup steps, interactions and sequence diagrams, and captured outputs.

## Installation

### Claude Code

```
/plugin marketplace add kensa-dev/agent-skills
/plugin install kensa
```

The plugin auto-updates when you run `/plugin marketplace update`, so your agent's knowledge of Kensa stays in sync with releases.

### OpenCode / manual install

Any agent that reads `.claude/skills/` can use the skill content directly. Drop it into your project:

```bash
git clone --depth 1 https://github.com/kensa-dev/agent-skills.git /tmp/kensa-skills && \
  mkdir -p .claude/skills && \
  cp -r /tmp/kensa-skills/plugins/kensa/skills/kensa-development .claude/skills/ && \
  rm -rf /tmp/kensa-skills
```

Re-run when a new Kensa version drops.

## Versioning

The skill version mirrors [Kensa](https://github.com/kensa-dev/kensa) `version.txt` exactly. Tag `0.7.1` corresponds to Kensa `0.7.1`.

## License

[Apache-2.0](LICENSE)
