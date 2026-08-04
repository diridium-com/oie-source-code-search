# OIE Source Code Search

[![build](https://github.com/diridium-com/oie-source-code-search/actions/workflows/build.yml/badge.svg)](https://github.com/diridium-com/oie-source-code-search/actions/workflows/build.yml) [![release](https://img.shields.io/github/v/release/diridium-com/oie-source-code-search?label=release&color=blue)](https://github.com/diridium-com/oie-source-code-search/releases/latest) [![License: MPL 2.0](https://img.shields.io/badge/License-MPL%202.0-green.svg)](LICENSE) ![Java](https://img.shields.io/badge/Java-17%2B-blue.svg) [![OIE](https://img.shields.io/badge/OIE-4.6.0-blue.svg)](https://www.openintegrationengine.org/)

A plugin for [Open Integration Engine](https://www.openintegrationengine.org/) (OIE) that provides grep-like search across all channel scripts, code templates, global scripts, and message templates — directly from the Administrator UI.

![Source Code Search in action](https://raw.githubusercontent.com/wiki/diridium-com/oie-source-code-search/images/4.png)

## Features

- **Full-text search** across all artifact types in a single query
- **Literal and regex** search modes, case-sensitive or insensitive
- **Scope control** — search Channels, Code Templates, Global Scripts, and Message Templates independently
- **Names and descriptions included** — channel, code template, and global script names and descriptions are searched alongside their code, so finding an artifact by name just works
- **Channel scoping** — search all channels, selected channels, or the current channel from the editor
- **Hierarchical results** with location breadcrumbs and match highlighting
- **Export** results as JSON (with metadata) or CSV
- **Non-modal dialog** — search while you work
- **Permission aware** — publishes a "Search Source Code" permission and honors per-role channel restrictions

## Permissions

The plugin registers a single permission, **Search Source Code**, covering both of its REST
operations and both of its menu entries.

On a stock OIE install this changes nothing: the default authorization controller allows every
operation for every authenticated user, exactly as it does for the channel editor.

On installs running a role-based authorization controller, the permission must be granted to a
role before its users can search, and results stay within the channels that role can access.
Channels outside the role are skipped, code templates are limited to libraries in scope for at
least one accessible channel, and global scripts are excluded, since they are server-wide rather
than channel-scoped. The same applies to names and descriptions: a channel a role cannot access is
never visited, so its name cannot surface through a name search either. When anything is filtered the dialog says so, and the notice travels into
JSON and CSV exports so a partial result set is never mistaken for a complete one.

> **Upgrading from 1.2.0 or earlier on a role-based server:** search was previously ungated, so
> every role could use it. After upgrading, an administrator must grant the new permission to
> each role that should keep it. See [issue #5](https://github.com/diridium-com/oie-source-code-search/issues/5).

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
