# Review History

A record of the review passes this repository has been through, what each one found, and what was decided. Later passes append to this document rather than rewriting it, so the reasoning behind a decision stays available to whoever inherits it.

---

## 2026-08-04 — Authorization review (1.3.0)

**Scope:** how the plugin's REST operations interact with server-side authorization, prompted by a question about whether the plugin respected role-based access control the way the rest of the engine does.

**Findings: 3, all fixed in [#6](https://github.com/diridium-com/oie-source-code-search/pull/6).**

| Finding | Outcome |
|---|---|
| No `ExtensionPermission` was registered. `plugin.xml` declared no `<serverClasses>`, so no `ServicePlugin` existed and no permission was ever published to the authorization controller. | Fixed. Added `SourceCodeSearchServicePlugin` publishing a **Search Source Code** permission, with operation names derived by reflecting over the servlet interface so a later operation cannot ship unregistered. |
| The declared `Permissions.CHANNELS_VIEW` could never match. Extension servlet operations reach the controller as the composite `"<pluginName>#<opName>"`, which no core permission map contains. The annotation read as protection but had no effect. | Fixed. Replaced with the plugin's own permission constant. |
| Per-role channel restrictions were not honored. `SearchEngine` read channels directly from `ChannelController` and never consulted the engine's `ChannelAuthorizer`. | Fixed. The servlet now builds a predicate from `doesUserHaveChannelRestrictions()` and `getChannelAuthorizer()` and applies it to **both** `search` and `count`. |

**Impact, for the record:** on servers running a role-based authorization controller, any user holding any role could search every channel script, connector property, code template, and global script regardless of the channels their role was limited to, and the searches produced no audit event. Stock OIE installs were unaffected, since the default controller permits every operation for every authenticated user and the Administrator already exposes the same content. Disclosed publicly before the fix shipped, in [#5](https://github.com/diridium-com/oie-source-code-search/issues/5).

**Design decisions worth recording:**

`count` is filtered identically to `search`. Filtering only `search` would leave `count` usable as an oracle: with regex enabled, a caller could confirm a string's presence in channels they cannot see by reading the returned number alone.

A restriction reported with no authorizer denies everything rather than allowing. That combination should not occur, but guessing "allow" would defeat the restriction entirely.

Code templates are limited to libraries in scope for at least one accessible channel, and templates belonging to no library are excluded, since there is no channel to authorize them against.

---

## 2026-08-04 — Irritable developer check (1.3.0)

**Scope:** recent work, meaning the 1.3.0 change set and the files it touched.

**Findings: 8.** Three fixed, five accepted and tracked. Categories reported clean: error handling, resource and thread safety, serialization, static-analysis cleanup, and strays.

| # | Category | Finding | Outcome |
|---|---|---|---|
| 1 | Tests | No regression test proves the channel restriction is applied. `visitChannels`, the servlet's `channelFilter()`, and `count` filtering have no coverage. | **Accepted**, tracked as [#7](https://github.com/diridium-com/oie-source-code-search/issues/7). See rationale below. |
| 2 | Security | The restriction notice discloses how many channels the caller cannot see. | **Accepted.** Judged more useful than the disclosure is harmful: a user needs to know the size of what was withheld to judge whether to ask for access. Tracked in [#10](https://github.com/diridium-com/oie-source-code-search/issues/10). |
| 3 | Security | Enabling auditing writes search query strings to the server event log, so a search for a patient identifier records that identifier. | **Accepted**, tracked in [#10](https://github.com/diridium-com/oie-source-code-search/issues/10). Auditing what was searched is the point of the audit trail; the alternative is an audit event that cannot answer the question it exists for. |
| 4 | OIE | Channel restriction is used as a proxy for global-script permission. A role can be channel-restricted and still legitimately hold `viewGlobalScripts`. | **Accepted**, tracked as [#9](https://github.com/diridium-com/oie-source-code-search/issues/9). An extension servlet has no way to query the caller's core permissions, so the proxy is the only signal available. Over-restriction is the safe direction. |
| 5 | Smell | Ten parameters on `SearchEngine.count` and `search`, six of them consecutive booleans. | **Fixed** in [#13](https://github.com/diridium-com/oie-source-code-search/pull/13) by collapsing the flags into a `SearchScope` record. The REST layer still takes them flat, because that is what `@QueryParam` binds. |
| 6 | API | `SearchResults.getMatches()` returns null for count responses. | **Accepted.** Documented on the type; the client null-checks it. An empty list plus the existing `matchCount` would be marginally cleaner and is not worth a shared-class change on its own. |
| 7 | Regret | The engine version was declared in three places (`pom.xml`, the CI workflow, and the install script) with nothing checking them against each other. | **Fixed** in [#12](https://github.com/diridium-com/oie-source-code-search/pull/12). CI now reads `mc.version` from the POM and derives the release tag and tarball name from it. |
| 8 | Regret | Follow-up work existed only in conversation, with no issue numbers. | **Fixed.** Filed as [#7](https://github.com/diridium-com/oie-source-code-search/issues/7) through [#11](https://github.com/diridium-com/oie-source-code-search/issues/11). |

**Rationale for accepting finding 1**, the most significant deferral. The obstacle is testability, not effort. `SearchEngine` resolves three engine controllers from static singletons in its constructor, so a test cannot construct it without materializing all three. The engine jars are installed via `install:install-file`, which generates a minimal POM declaring no transitives, so every engine class a test touches requires its transitive closure to be hand-declared. An attempt at mocking accumulated four test-scope dependencies (`reflections`, `commons-lang3`, `log4j-api`, and then Rhino) with no clear end, and was abandoned as the wrong approach. The fix is dependency injection: give `SearchEngine` a constructor taking its controllers, keep the no-arg one delegating to the singletons, and test against plain fakes. That is a `src/main` change and was not worth rushing into a release that had already been signed.

---

## Empirical verification

Each entry below is a probe that was actually run, and the decision it justified. Nothing here is inferred from documentation or memory.

| Probe | Result | Decision it justified |
|---|---|---|
| Enumerated every `checkTask` call site in the engine client | Seven sites. Five pass hardcoded engine constants, one covers settings panels, and only `Frame.setVisibleTasks` is generic. | Established that the permission's `taskNames` cannot hide a plugin's menu entry. |
| Read the index ranges `ChannelPanel` passes to `setVisibleTasks` | Widest is 1 through `TASK_CHANNEL_VIEW_MESSAGES` = 15. A plugin task appended by `addTask` sits at 16 or later. | Confirmed the menu entry is never evaluated, so no amount of correct registration would hide it. Filed as [#8](https://github.com/diridium-com/oie-source-code-search/issues/8) and upstream as `role-based-access-control#8`. |
| Read the RBAC client's permission loading | `SecureAuthorizationController.loadPermissions()` does merge extension task permissions and would answer correctly if asked. | Ruled out an RBAC-side defect, which was the competing hypothesis. |
| Read `CodeTemplate.DEFAULT_CODE` and `getCode()` | The body includes the doc comment and a `function` declaration; `CodeTemplateType` has three values and only `FUNCTION` and `COMPILED_CODE` carry a declaration. | Established that function templates were already findable by name only by convention, and that a drag-and-drop snippet has no declaration at all. Shaped the names and descriptions feature. |
| Read `CodeTemplateLibrary` scoping fields | A library reaches a channel when it is in `enabledChannelIds`, or when `includeNewChannels` is set and the channel is not in `disabledChannelIds`. | Drove `isLibraryInScope` and the seven cases in `SearchEngineLibraryScopeTest`. |
| Checked which surefire version this Maven binds by default | Maven 3.9.12 already binds 3.2.5, the same version pinned in the POM. | The pin is a no-op on the maintainer's machine. Kept anyway, because a build on Maven 3.8 or older would skip the entire JUnit 5 suite and still report success. |
| Ran CI after deriving the tarball name from `mc.version` | The cache step hit rather than re-downloading. | Proved the derived name matches what the previous hardcoded run stored, so the derivation is correct rather than merely plausible. |
| `jarsigner -verify` on the release jars | All three report "jar verified"; signer certificate expires 2026-11-29, timestamp expires 2031-11-09. | Confirmed the signatures outlive the certificate. |
| Downloaded the published release assets and re-verified | The sidecar validates against the published ZIP, and its digest matches the one generated before upload byte for byte. | Confirmed nothing was altered in transit and the sidecar corresponds to the signed artifact rather than an earlier unsigned build. |

### Live verification against a role-based server (2026-08-04)

Performed manually against a running server with a role-based authorization plugin installed. This is point-in-time evidence: nothing re-verifies it continuously, and it should be repeated on any change to the authorization path.

- The **Search Source Code** permission appears in the role editor, under the plugin's own heading. This is the only way to confirm the registration reaches the controller at startup; the unit test only confirms the permission object is well formed.
- A role **without** the permission is denied when it runs a search.
- A role **with** the permission, restricted to a subset of channels, sees results only from its own channels, with the exclusion notice displayed and a skipped-channel count matching the number withheld.
- The dialog's controls fit at the default window size.

**Found by this pass:** the menu entry remains visible to a role lacking the permission. The search itself is correctly denied, so the finding is cosmetic. It became [#8](https://github.com/diridium-com/oie-source-code-search/issues/8).

**Not yet verified live:** code template and global script filtering for a channel-restricted role, and the restriction markers in JSON and CSV exports.

---

## Standing results

Current as of 2026-08-04, and re-verified continuously by CI unless noted.

**Tests:** 14, across three classes.

| Class | Covers |
|---|---|
| `SourceCodeSearchServicePluginTest` | Permission registration: extension name matches the servlet's, every annotated operation is registered and auditable, both task names declared |
| `SearchEngineLibraryScopeTest` | Code template library scoping, including the `includeNewChannels` and `disabledChannelIds` interaction and null-set handling |
| `SourceCodeSearchPluginTaskTest` | Task names resolve to real callback methods on the client plugin |

**Not covered:** the channel filtering itself. See [#7](https://github.com/diridium-com/oie-source-code-search/issues/7).

**CI:** `.github/workflows/build.yml` runs on every push to `main` and every pull request. It resolves the engine version from the POM, installs the engine jars from the published OIE distribution, and runs `mvn clean verify`. A final step fails the build if no surefire reports were produced, so a configuration change that silently skips the suite cannot pass as green.

**Static analysis:** none configured beyond the Java compiler. Compiler output is clean with `-Xlint:all`.

**Expected warnings.** These appear on every build and are not defects:

- `SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder"` during tests. No SLF4J binder is on the test classpath, since logging is provided by the engine at runtime. Tests log to a no-op logger.

**Known build-environment limitation:** the project uses `${revision}` for CI-friendly versioning without `flatten-maven-plugin`, so installed POMs contain the literal placeholder. The reactor build is unaffected, but per-module Maven tooling such as `dependency:build-classpath` cannot resolve a sibling module.
