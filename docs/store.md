# Source Code Search

A plugin for Open Integration Engine that provides **grep-like search across all
channel scripts, code templates, global scripts, and message templates** — directly
from the Administrator UI. Stop exporting channels to find where a function, queue
name, or magic string is used.

## Features

- Search **channel scripts** (source/destination transformers, filters, response,
  deploy/undeploy, preprocessor/postprocessor).
- Search **code templates** and **global scripts**.
- Search **message templates** and **connector properties**.
- Channel, code template, and global script **names and descriptions** are searched
  alongside their code.
- Fast, engine-side search with match context, so large servers stay responsive.
- Works in both the Swing Administrator and the web administrator.

## Permissions

Search is gated by a **Search Source Code** permission. On a stock OIE install this
changes nothing: the default authorization controller allows it for every user.

On a server running a role-based authorization plugin, grant the permission to each
role that should search. Results stay within the channels that role can access, and
the results say so when anything was filtered out.

**Upgrading from 1.2.0 or earlier on a role-based server:** search used to be
ungated. After upgrading, grant **Search Source Code** to every role that should keep
it, or those users lose search.

## Requirements

- Open Integration Engine **4.6.0** or newer.
- An engine restart after install to activate the plugin.

## Installing

Install from the Community Store, then **restart the engine**. Source Code Search then
appears in the Administrator, and in the web administrator's navigation.

See the [project README](https://github.com/diridium-com/oie-source-code-search#readme)
for usage and screenshots.

## Credits

Built by Diridium Technologies Inc. Published under the MPL-2.0 license.
