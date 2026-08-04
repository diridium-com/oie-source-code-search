# Design Notes

Why parts of this plugin are shaped the way they are, and what the test suite does and does not guarantee. Written for someone changing the code, so that decisions made deliberately are not undone by accident.

Last updated 2026-08-04 (1.3.0).

---

## Authorization

The plugin reads channel scripts, connector properties, code templates, and global scripts. On a server running a role-based authorization controller, that content is exactly what a role is meant to be able to restrict, so the authorization behavior is load-bearing rather than incidental.

**The plugin declares its own permission, not a core one.** Extension servlet operations reach the authorization controller as the composite name `"<pluginName>#<opName>"`, which can never match a core permission map keyed by bare engine operation names. A `permission = Permissions.CHANNELS_VIEW` on the interface would read as protection and have none. `SourceCodeSearchServletInterface.PERMISSION_SEARCH` is the real gate.

**The `ExtensionPermission`'s extension name must equal the string passed to the `MirthServlet` constructor.** They are joined to form that composite key. A mismatch registers a key nothing ever looks up, and the permission silently fails to apply.

**Operation names are derived by reflection**, not hardcoded, so an operation added to the interface later cannot ship unregistered. That failure would produce no error, just an ungated endpoint.

**`count` is filtered identically to `search`.** This is the non-obvious one. Filtering only the endpoint that returns data would leave `count` usable as an oracle: with regex enabled, a caller can confirm a string's presence in channels they cannot see by reading the returned number alone. Any future endpoint that reflects filtered content, including summaries and counts, needs the same treatment.

**A channel restriction reported with no authorizer denies everything.** That combination should not occur, but guessing "allow" there would defeat the restriction entirely, so the safe reading wins.

**Code templates are scoped by library.** A restricted caller sees a template only if its library reaches at least one channel they can access, following the engine's own rule: the channel is in `enabledChannelIds`, or the library sets `includeNewChannels` and the channel is not in `disabledChannelIds`. Templates belonging to no library are excluded, since there is no channel to authorize them against.

**Global scripts are excluded for channel-restricted callers, and this is a deliberate approximation.** Global scripts are server-wide, so there is no channel to check them against. In principle a role could be channel-restricted and still legitimately hold `viewGlobalScripts`, but an extension servlet has no way to query the caller's core permissions, so channel restriction is the only available signal. This over-restricts, which is the safe direction. Revisit if the engine ever exposes effective permissions to extension servlets.

**Names cannot leak through the name search.** Unauthorized channels are never visited at all, so a restricted caller cannot discover a hidden channel's name even though names are searchable content.

### What the caller is told

A partial result set that looks complete is worse than a denial, particularly because results can be exported and the file outlives the dialog. Both operations therefore return a `SearchResults` envelope carrying whether the role filtered the search, and the count of channels withheld.

`count` returns the same envelope even though it produces no matches, so the notice can be shown after the count phase. Without that, a restricted user whose query matches only in hidden channels would see a bare "No matches found" and never learn the search was filtered.

The skipped-channel **count** is disclosed on purpose. It tells a restricted user how much was withheld, which is what they need in order to judge whether to ask for access. The alternative wording, "some channels were excluded," withholds that at no security benefit worth the ambiguity.

Search **queries are written to the audit log**, since both operations are auditable and the query is a parameter. This is intended: an audit event that cannot say what was searched cannot answer the question it exists for. Be aware that a search for a patient identifier records that identifier in the event log.

---

## Search scope model

Each scope is an independent category of content rather than a filter on the others. Message Templates can be searched without Channels, and always could be. New scopes should follow that rule.

**Names and descriptions travel with their owning scope** rather than having a control of their own. They amount to a line or two per artifact, so a dedicated checkbox bought almost no noise reduction in exchange for another control and another combination to reason about. Unchecking Channels therefore also excludes channel names.

This matters most for channels, whose name appears in no script and was previously unreachable. For code templates it is less visible: a `FUNCTION` template's body usually carries the same name in its `function` declaration, so those were already findable, but only while the name field and the function name stay in sync. A `DRAG_AND_DROP_CODE` snippet has no declaration at all, so its name is the only label it has, and a template's description is a separate property never present in the body.

**`SearchScope` is a record, but the REST layer still takes flat booleans**, because that is what `@QueryParam` binds. The collapse happens at the servlet boundary so the engine is not handed an unlabelled row of seven booleans.

**`SearchResults.getMatches()` returns null for count responses.** A known wart. An empty list plus the existing `matchCount` would be marginally cleaner; it was not worth a shared-class change on its own. The client null-checks it.

---

## Known limitation: the menu entry is not gated

On role-based servers, the Source Code Search entry stays visible to users whose role lacks the permission. Running a search is correctly denied by the server, so this is cosmetic rather than an exposure.

The cause is upstream and the plugin cannot fix it by registering things differently. The only generic `checkTask` call site is `Frame.setVisibleTasks`, which evaluates only components inside the index range it is passed. The widest range the channel list receives is 1 through `TASK_CHANNEL_VIEW_MESSAGES` (15), and a plugin task appended by `addTask` lands at 16 or later, so it is never evaluated. Settings panels go through a different path and are gated correctly.

The task names are registered anyway: they cost nothing and become correct if the engine changes. Tracked as [#8](https://github.com/diridium-com/oie-source-code-search/issues/8), and upstream as [role-based-access-control#8](https://github.com/diridium-com/role-based-access-control/issues/8).

---

## Tests and CI

**14 tests, across three classes.**

| Class | Covers |
|---|---|
| `SourceCodeSearchServicePluginTest` | Permission registration: extension name matches the servlet's, every annotated operation is registered and auditable, both task names declared |
| `SearchEngineLibraryScopeTest` | Code template library scoping, including the `includeNewChannels` and `disabledChannelIds` interaction and null-set handling |
| `SourceCodeSearchPluginTaskTest` | Task names resolve to real callback methods on the client plugin |

**Not covered: the channel filtering itself.** `visitChannels`, the servlet's `channelFilter()`, and `count` filtering have no automated test. Given that the failure mode is silent over-sharing, this is the most valuable missing test in the repo. Tracked as [#7](https://github.com/diridium-com/oie-source-code-search/issues/7).

The obstacle is testability rather than effort, and it is worth understanding before attempting it. `SearchEngine` resolves three engine controllers from static singletons in its constructor, so a test cannot construct it without materializing all three. The engine jars are installed via `install:install-file`, which generates a POM declaring no transitives, so every engine class a test touches needs its dependency closure hand-declared. An attempt at static mocking accumulated four test-scope dependencies with no clear end and was abandoned. **The right fix is dependency injection:** give `SearchEngine` a constructor taking its controllers, keep the no-arg one delegating to the singletons, and test against plain fakes that load no engine classes.

Authorization behavior was verified manually against a running role-based server for 1.3.0: the permission appears in the role editor, a role without it is denied, and a channel-restricted role sees only its own channels with the exclusion notice and a matching skipped count. That is point-in-time evidence, not ongoing coverage, and should be repeated on any change to the authorization path.

**CI** (`.github/workflows/build.yml`) runs on every push to `main` and every pull request. It resolves the engine version from the POM's `mc.version`, installs the engine jars from the published OIE distribution tarball, and runs `mvn clean verify`. A final step fails the build if no surefire reports were produced, so a change that silently skips the suite cannot pass as green.

**Expected build warning**, not a defect: `SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder"` during tests. No SLF4J binder is on the test classpath, because logging is provided by the engine at runtime.

**Known build-environment limitation:** the project uses `${revision}` for CI-friendly versioning without `flatten-maven-plugin`, so installed POMs contain the literal placeholder. The reactor build is unaffected, but per-module tooling such as `dependency:build-classpath` cannot resolve a sibling module.
