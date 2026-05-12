# OIE Source Code Search

A plugin for [Open Integration Engine](https://www.openintegrationengine.org/) (OIE) that provides grep-like search across all channel scripts, code templates, global scripts, and message templates — directly from the Administrator UI.

![Source Code Search in action](https://raw.githubusercontent.com/wiki/diridium-com/oie-source-code-search/images/4.png)

## Features

- **Full-text search** across all artifact types in a single query
- **Literal and regex** search modes, case-sensitive or insensitive
- **Scope control** — search Channels, Code Templates, Global Scripts, and Message Templates independently
- **Channel scoping** — search all channels, selected channels, or the current channel from the editor
- **Hierarchical results** with location breadcrumbs and match highlighting
- **Export** results as JSON (with metadata) or CSV
- **Non-modal dialog** — search while you work

## Documentation

See the [Wiki](https://github.com/diridium-com/oie-source-code-search/wiki) for full documentation, including:

- [Why You Need This](https://github.com/diridium-com/oie-source-code-search/wiki/Why-You-Need-This) — real-world use cases
- [Getting Started](https://github.com/diridium-com/oie-source-code-search/wiki/Getting-Started) — how to launch and use the search
- [Search Options](https://github.com/diridium-com/oie-source-code-search/wiki/Search-Options) — what each scope searches
- [Regex Tips](https://github.com/diridium-com/oie-source-code-search/wiki/Regex-Tips) — common patterns and performance advice
- [FAQ](https://github.com/diridium-com/oie-source-code-search/wiki/FAQ)

## Requirements

| Attribute | Value |
|-----------|-------|
| OIE Version | 4.6.0+ |
| Java | 17+ |

## Installation

Download the latest release ZIP from the [Releases](https://github.com/diridium-com/oie-source-code-search/releases) page and install it through the OIE Administrator plugin manager.

## Building from Source

The public repsy mirror at `repo.repsy.io/mvn/kpalang/mirthconnect` does not yet carry the 4.6.0 engine artifacts. Build the engine (`ant` in `donkey/` then `server/`) from a sibling checkout, then run:

```
ENGINE_DIR=/path/to/engine ./scripts/install-engine-jars.sh
mvn verify
```

The script installs `mirth-server`, `donkey-server`, `mirth-client-core`, and `mirth-client` at version 4.6.0 into your local Maven repository. If `ENGINE_DIR` is unset, it defaults to `../engine` relative to this repo.

## License

[MPL-2.0](LICENSE) — Copyright (c) 2025-2026 Diridium Technologies Inc.

Developed with the moral support of Finnegan the dog.
