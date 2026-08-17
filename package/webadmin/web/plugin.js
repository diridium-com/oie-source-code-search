// web/plugin.jsx
import { platform } from "@oie/web-shell";

// web/scs-css.generated.js
var SCS_CSS = '/*! tailwindcss v4.3.1 | MIT License | https://tailwindcss.com */\n@layer properties;\n@layer theme, utilities;\n@layer theme {\n  :root, :host {\n    --font-mono: var(--font-mono);\n  }\n}\n@layer utilities {\n  .my-\\[4px\\] {\n    margin-block: 4px;\n  }\n  .mt-\\[6px\\] {\n    margin-top: 6px;\n  }\n  .mb-\\[2px\\] {\n    margin-bottom: 2px;\n  }\n  .ml-\\[8px\\] {\n    margin-left: 8px;\n  }\n  .ml-\\[22px\\] {\n    margin-left: 22px;\n  }\n  .contents {\n    display: contents;\n  }\n  .flex {\n    display: flex;\n  }\n  .max-h-\\[180px\\] {\n    max-height: 180px;\n  }\n  .w-\\[420px\\] {\n    width: 420px;\n  }\n  .max-w-\\[460px\\] {\n    max-width: 460px;\n  }\n  .min-w-\\[70px\\] {\n    min-width: 70px;\n  }\n  .min-w-\\[320px\\] {\n    min-width: 320px;\n  }\n  .flex-none {\n    flex: none;\n  }\n  .transform {\n    transform: var(--tw-rotate-x,) var(--tw-rotate-y,) var(--tw-rotate-z,) var(--tw-skew-x,) var(--tw-skew-y,);\n  }\n  .cursor-pointer {\n    cursor: pointer;\n  }\n  .flex-col {\n    flex-direction: column;\n  }\n  .flex-wrap {\n    flex-wrap: wrap;\n  }\n  .items-baseline {\n    align-items: baseline;\n  }\n  .items-center {\n    align-items: center;\n  }\n  .gap-\\[2px\\] {\n    gap: 2px;\n  }\n  .gap-\\[12px\\] {\n    gap: 12px;\n  }\n  .gap-x-\\[18px\\] {\n    column-gap: 18px;\n  }\n  .gap-y-\\[4px\\] {\n    row-gap: 4px;\n  }\n  .overflow-auto {\n    overflow: auto;\n  }\n  .rounded-\\[2px\\] {\n    border-radius: 2px;\n  }\n  .rounded-\\[3px\\] {\n    border-radius: 3px;\n  }\n  .border {\n    border-style: var(--tw-border-style);\n    border-width: 1px;\n  }\n  .border-line {\n    border-color: var(--line);\n  }\n  .bg-accent {\n    background-color: var(--accent);\n  }\n  .px-\\[4px\\] {\n    padding-inline: 4px;\n  }\n  .px-\\[8px\\] {\n    padding-inline: 8px;\n  }\n  .px-\\[10px\\] {\n    padding-inline: 10px;\n  }\n  .px-px {\n    padding-inline: 1px;\n  }\n  .py-0 {\n    padding-block: 0;\n  }\n  .py-\\[2px\\] {\n    padding-block: 2px;\n  }\n  .py-\\[3px\\] {\n    padding-block: 3px;\n  }\n  .py-\\[6px\\] {\n    padding-block: 6px;\n  }\n  .text-right {\n    text-align: right;\n  }\n  .font-mono {\n    font-family: var(--font-mono);\n  }\n  .text-\\[11px\\] {\n    font-size: 11px;\n  }\n  .text-\\[12px\\] {\n    font-size: 12px;\n  }\n  .break-all {\n    word-break: break-all;\n  }\n  .whitespace-pre-wrap {\n    white-space: pre-wrap;\n  }\n  .text-accent-ink {\n    color: var(--accent-ink);\n  }\n  .text-text-faint {\n    color: var(--text-faint);\n  }\n  .hover\\:bg-accent-glow {\n    &:hover {\n      @media (hover: hover) {\n        background-color: var(--accent-glow);\n      }\n    }\n  }\n}\n@property --tw-rotate-x {\n  syntax: "*";\n  inherits: false;\n}\n@property --tw-rotate-y {\n  syntax: "*";\n  inherits: false;\n}\n@property --tw-rotate-z {\n  syntax: "*";\n  inherits: false;\n}\n@property --tw-skew-x {\n  syntax: "*";\n  inherits: false;\n}\n@property --tw-skew-y {\n  syntax: "*";\n  inherits: false;\n}\n@property --tw-border-style {\n  syntax: "*";\n  inherits: false;\n  initial-value: solid;\n}\n@layer properties {\n  @supports ((-webkit-hyphens: none) and (not (margin-trim: inline))) or ((-moz-orient: inline) and (not (color:rgb(from red r g b)))) {\n    *, ::before, ::after, ::backdrop {\n      --tw-rotate-x: initial;\n      --tw-rotate-y: initial;\n      --tw-rotate-z: initial;\n      --tw-skew-x: initial;\n      --tw-skew-y: initial;\n      --tw-border-style: solid;\n    }\n  }\n}\n';

// web/plugin.jsx
var React = platform.React;
var EXT = "/extensions/oie-source-code-search";
var RESULT_WARNING_THRESHOLD = 5e3;
var STYLE_ID = "scs-style";
function register(platform2) {
  const api = platform2.api;
  const {
    h,
    toast,
    taskButton,
    confirmDialog,
    downloadFile
  } = platform2.ui;
  const notInstalled = (e) => e && (e.status === 404 || e.status === 501);
  function ensureStyle() {
    if (!document.getElementById(STYLE_ID)) {
      document.head.appendChild(h("style", { id: STYLE_ID }, SCS_CSS));
    }
  }
  function asInt(value) {
    if (typeof value === "number") return value;
    if (value && typeof value === "object") {
      for (const v of Object.values(value)) {
        const n2 = Number(v);
        if (!isNaN(n2)) return n2;
      }
      return 0;
    }
    const n = Number(value);
    return isNaN(n) ? 0 : n;
  }
  function normalizeMatch(m) {
    if (!m || typeof m !== "object") return null;
    return {
      groupType: String(m.groupType ?? ""),
      channelId: m.channelId === void 0 || m.channelId === null || m.channelId === "" ? null : String(m.channelId),
      channelName: String(m.channelName ?? ""),
      location: String(m.location ?? ""),
      lineNumber: Number(m.lineNumber) || 0,
      lineText: m.lineText === void 0 || m.lineText === null ? "" : String(m.lineText)
    };
  }
  function idNamePairs(map) {
    const out = [];
    if (!map || typeof map !== "object") return out;
    for (const entry of api.asList(map.entry)) {
      if (!entry || typeof entry !== "object") continue;
      if (Array.isArray(entry.string)) {
        out.push({
          id: String(entry.string[0]),
          name: String(entry.string[1] !== void 0 ? entry.string[1] : entry.string[0])
        });
        continue;
      }
      const vals = Object.entries(entry).filter(([k]) => !k.startsWith("@")).map(([, v]) => v);
      if (vals.length >= 2) out.push({ id: String(vals[0]), name: String(vals[1]) });
    }
    return out.sort((a, b) => a.name.localeCompare(b.name));
  }
  function buildHighlighter(query, caseSensitive, regex) {
    try {
      const src = regex ? query : query.replace(/[.*+?^$()|[\]{}\\]/g, "\\$&");
      return new RegExp(src, caseSensitive ? "g" : "gi");
    } catch (e) {
      return null;
    }
  }
  function HighlightedLine({ text, re }) {
    if (!re) return /* @__PURE__ */ React.createElement("span", { className: "whitespace-pre-wrap break-all" }, text);
    const parts = [];
    let last = 0;
    let guard = 0;
    let key = 0;
    re.lastIndex = 0;
    let m;
    while ((m = re.exec(text)) !== null && guard++ < 500) {
      if (m[0].length === 0) {
        re.lastIndex++;
        continue;
      }
      if (m.index > last) parts.push(text.slice(last, m.index));
      parts.push(/* @__PURE__ */ React.createElement("mark", { className: "bg-accent text-accent-ink rounded-[2px] px-px py-0", key: key++ }, m[0]));
      last = m.index + m[0].length;
    }
    parts.push(text.slice(last));
    return /* @__PURE__ */ React.createElement("span", { className: "whitespace-pre-wrap break-all" }, parts);
  }
  function matchTarget(m) {
    if (m.groupType === "CODE_TEMPLATE") return "/code-templates";
    if (m.groupType === "GLOBAL_SCRIPT") return "/global-scripts";
    if (m.groupType !== "CHANNEL" || !m.channelId) return null;
    const loc = m.location;
    let meta = null;
    if (loc.indexOf("Source") === 0) {
      meta = "0";
    } else {
      const d = /^Dest (\d+):/.exec(loc);
      if (d) meta = d[1];
    }
    const base = "/channels/" + encodeURIComponent(m.channelId);
    if (meta !== null) {
      if (loc.indexOf(" > Response Transformer > Step ") !== -1) return base + "/response/" + meta;
      if (loc.indexOf(" > Transformer > Step ") !== -1) return base + "/transformer/" + meta;
      if (loc.indexOf(" > Filter > Step ") !== -1) return base + "/filter/" + meta;
    }
    return base + "/edit";
  }
  function escapeCsv(value) {
    if (value === null || value === void 0) return "";
    const s = String(value);
    if (s.indexOf(",") !== -1 || s.indexOf('"') !== -1 || s.indexOf("\n") !== -1) {
      return '"' + s.replace(/"/g, '""') + '"';
    }
    return s;
  }
  const csvRow = (values) => values.map(escapeCsv).join(",");
  function TaskBtn({ label, icon, onClick, primary, disabled }) {
    const hostRef = React.useRef(null);
    const onClickRef = React.useRef(onClick);
    onClickRef.current = onClick;
    React.useEffect(() => {
      const host = hostRef.current;
      const btn = taskButton(
        label,
        icon,
        (e) => onClickRef.current && onClickRef.current(e),
        { primary, disabled }
      );
      host.appendChild(btn);
      return () => host.replaceChildren();
    }, [label, icon, primary, disabled]);
    return /* @__PURE__ */ React.createElement("span", { ref: hostRef, className: "contents" });
  }
  function SourceCodeSearchView() {
    ensureStyle();
    const [query, setQuery] = React.useState("");
    const [mode, setMode] = React.useState("literal");
    const [caseSensitive, setCaseSensitive] = React.useState(false);
    const [searchChannels, setSearchChannels] = React.useState(true);
    const [searchCodeTemplates, setSearchCodeTemplates] = React.useState(true);
    const [searchGlobalScripts, setSearchGlobalScripts] = React.useState(true);
    const [searchMessageTemplates, setSearchMessageTemplates] = React.useState(false);
    const [searchConnectorProperties, setSearchConnectorProperties] = React.useState(true);
    const [scope, setScope] = React.useState("all");
    const [channels, setChannels] = React.useState(null);
    const [selectedIds, setSelectedIds] = React.useState(() => /* @__PURE__ */ new Set());
    const [results, setResults] = React.useState(null);
    const [lastParams, setLastParams] = React.useState(null);
    const [searching, setSearching] = React.useState(false);
    const [status, setStatus] = React.useState(" ");
    const [resultsState, setResultsState] = React.useState({ kind: "idle" });
    const [notInstalledStatus, setNotInstalledStatus] = React.useState(null);
    const queryInputRef = React.useRef(null);
    const searchingRef = React.useRef(false);
    const warnedRef = React.useRef(false);
    searchingRef.current = searching;
    React.useEffect(() => {
      queryInputRef.current && queryInputRef.current.focus();
    }, []);
    async function loadChannels() {
      let pairs;
      try {
        pairs = idNamePairs(await api.channels.idsAndNames());
      } catch (e) {
        setChannels([]);
        toast("Failed to load channels: " + e.message, "error");
        return;
      }
      setChannels(pairs);
    }
    function onScopeChange(value) {
      setScope(value);
      if (value === "selected" && channels === null) loadChannels();
    }
    function toggleChannel(id, checked) {
      setSelectedIds((prev) => {
        const next = new Set(prev);
        if (checked) next.add(id);
        else next.delete(id);
        return next;
      });
    }
    function showNotInstalled(httpStatus) {
      setNotInstalledStatus(httpStatus);
      if (!warnedRef.current) {
        warnedRef.current = true;
        toast("OIE Source Code Search engine plugin is not installed on this engine", "warn");
      }
    }
    function exportJson() {
      if (!results || !results.length) return;
      const p = lastParams;
      const payload = {
        query: p.query,
        caseSensitive: p.caseSensitive,
        regex: p.regex,
        searchChannels: p.searchChannels,
        searchCodeTemplates: p.searchCodeTemplates,
        searchGlobalScripts: p.searchGlobalScripts,
        searchMessageTemplates: p.searchMessageTemplates,
        searchConnectorProperties: p.searchConnectorProperties,
        resultCount: results.length,
        results
      };
      downloadFile("search-results.json", JSON.stringify(payload, null, 2), "application/json");
    }
    function exportCsv() {
      if (!results || !results.length) return;
      const rows = [csvRow(["groupType", "channelId", "channelName", "location", "lineNumber", "lineText"])];
      for (const m of results) {
        rows.push(csvRow([m.groupType, m.channelId, m.channelName, m.location, m.lineNumber, m.lineText]));
      }
      downloadFile("search-results.csv", rows.join("\n") + "\n", "text/csv");
    }
    function gatherParams() {
      const params = {
        query,
        caseSensitive,
        regex: mode === "regex",
        channelIds: "",
        searchChannels,
        searchCodeTemplates,
        searchGlobalScripts,
        searchMessageTemplates,
        searchConnectorProperties
      };
      if (scope === "selected" && selectedIds.size) {
        params.channelIds = [...selectedIds].join(",");
      }
      return params;
    }
    async function performSearch() {
      if (searchingRef.current) return;
      const params = gatherParams();
      if (!params.query) {
        queryInputRef.current && queryInputRef.current.focus();
        return;
      }
      if (scope === "selected" && !selectedIds.size) {
        toast("Select at least one channel, or search all channels", "warn");
        return;
      }
      searchingRef.current = true;
      setSearching(true);
      setResults(null);
      setStatus("Counting matches\u2026");
      setResultsState({ kind: "loading", text: "Counting matches\u2026" });
      try {
        const matchCount = asInt(await api.get(EXT + "/count", params));
        setNotInstalledStatus(null);
        if (matchCount === 0) {
          setStatus("No matches found.");
          setResultsState({ kind: "hint", text: "No matches found." });
          return;
        }
        if (matchCount >= RESULT_WARNING_THRESHOLD) {
          const go = await confirmDialog(
            "Large Result Set",
            "Found " + matchCount + " matches. This may take a moment to display. Continue, or refine your search?",
            { okLabel: "Continue" }
          );
          if (!go) {
            setStatus(matchCount + " matches found. Search cancelled.");
            setResultsState({ kind: "hint", text: "Search cancelled." });
            return;
          }
        }
        setStatus("Searching\u2026");
        setResultsState({ kind: "loading", text: "Searching\u2026" });
        const raw = api.asList(await api.get(EXT + "/search", params), "searchMatch");
        const matches = raw.map(normalizeMatch).filter(Boolean);
        setResults(matches);
        setLastParams(params);
        const artifactCount = new Set(matches.map((m) => m.groupType + ":" + (m.channelId || m.channelName))).size;
        setStatus(matches.length + (matches.length === 1 ? " match" : " matches") + " in " + artifactCount + (artifactCount === 1 ? " artifact" : " artifacts") + ".");
        setResultsState({ kind: "results", matches, params });
      } catch (e) {
        setResultsState({ kind: "hint", text: "Search failed: " + e.message });
        setStatus("Search failed.");
        if (notInstalled(e)) {
          showNotInstalled(e.status);
        } else {
          toast("Search failed: " + e.message, "error");
        }
      } finally {
        searchingRef.current = false;
        setSearching(false);
      }
    }
    function MatchRow({ m, re }) {
      const target = matchTarget(m);
      return /* @__PURE__ */ React.createElement(
        "div",
        {
          className: "flex gap-[12px] py-[2px] px-[8px] ml-[22px] cursor-pointer rounded-[3px] font-mono text-[12px] items-baseline hover:bg-accent-glow",
          title: target ? "Open " + target : null,
          onClick: target ? () => platform2.router.navigate(target) : null
        },
        /* @__PURE__ */ React.createElement("span", { className: "text-text-faint min-w-[70px] flex-none text-right" }, "Line " + m.lineNumber),
        /* @__PURE__ */ React.createElement(HighlightedLine, { text: m.lineText, re })
      );
    }
    function grouped(list, keyOf) {
      const map = /* @__PURE__ */ new Map();
      for (const m of list) {
        const key = keyOf(m);
        if (!map.has(key)) map.set(key, []);
        map.get(key).push(m);
      }
      return map;
    }
    function ArtifactDetails({ label, items, children }) {
      return /* @__PURE__ */ React.createElement("details", { className: "my-[4px]", open: true }, /* @__PURE__ */ React.createElement("summary", { className: "cursor-pointer py-[3px] px-[4px] rounded-[3px] hover:bg-accent-glow" }, label, /* @__PURE__ */ React.createElement("span", { className: "text-text-faint ml-[8px] text-[11px]" }, items.length + (items.length === 1 ? " match" : " matches"))), children);
    }
    function ResultsBody() {
      if (resultsState.kind === "idle") {
        return /* @__PURE__ */ React.createElement("div", { className: "hint" }, "Enter a search and press Search. Click a match to open it in the matching editor.");
      }
      if (resultsState.kind === "loading") {
        return /* @__PURE__ */ React.createElement(Loading, { text: resultsState.text });
      }
      if (resultsState.kind === "hint") {
        return /* @__PURE__ */ React.createElement("div", { className: "hint" }, resultsState.text);
      }
      const { matches, params } = resultsState;
      const re = buildHighlighter(params.query, params.caseSensitive, params.regex);
      const channelMatches = matches.filter((m) => m.groupType === "CHANNEL");
      const templateMatches = matches.filter((m) => m.groupType === "CODE_TEMPLATE");
      const scriptMatches = matches.filter((m) => m.groupType === "GLOBAL_SCRIPT");
      const panels = [];
      if (channelMatches.length) {
        const groups = [];
        for (const [name, items] of grouped(channelMatches, (m) => m.channelName)) {
          const locBlocks = [];
          for (const [loc, locItems] of grouped(items, (m) => m.location)) {
            locBlocks.push(/* @__PURE__ */ React.createElement("div", { className: "text-text-faint text-[11px] mt-[6px] mb-[2px] ml-[22px]", key: "loc:" + loc }, loc));
            locItems.forEach((m, i) => locBlocks.push(/* @__PURE__ */ React.createElement(MatchRow, { key: loc + "#" + i, m, re })));
          }
          groups.push(
            /* @__PURE__ */ React.createElement(ArtifactDetails, { label: name, items, key: "ch:" + name }, /* @__PURE__ */ React.createElement("div", null, locBlocks))
          );
        }
        panels.push(
          /* @__PURE__ */ React.createElement("div", { className: "panel", key: "channels" }, /* @__PURE__ */ React.createElement("div", { className: "panel-header" }, "Channels \u2014 " + channelMatches.length + " matches"), /* @__PURE__ */ React.createElement("div", { className: "panel-body" }, groups))
        );
      }
      if (templateMatches.length) {
        const libOf = (m) => {
          const sep = m.location.indexOf(" > ");
          return sep > 0 ? m.location.slice(0, sep) : "(No Library)";
        };
        const tmplOf = (m) => {
          const sep = m.location.indexOf(" > ");
          return sep > 0 ? m.location.slice(sep + 3) : m.location;
        };
        const groups = [];
        for (const [lib, libItems] of grouped(templateMatches, libOf)) {
          const tmplBlocks = [];
          for (const [tmpl, tmplItems] of grouped(libItems, tmplOf)) {
            tmplBlocks.push(/* @__PURE__ */ React.createElement("div", { className: "text-text-faint text-[11px] mt-[6px] mb-[2px] ml-[22px]", key: "tmpl:" + tmpl }, tmpl));
            tmplItems.forEach((m, i) => tmplBlocks.push(/* @__PURE__ */ React.createElement(MatchRow, { key: tmpl + "#" + i, m, re })));
          }
          groups.push(
            /* @__PURE__ */ React.createElement(ArtifactDetails, { label: lib, items: libItems, key: "lib:" + lib }, /* @__PURE__ */ React.createElement("div", null, tmplBlocks))
          );
        }
        panels.push(
          /* @__PURE__ */ React.createElement("div", { className: "panel", key: "templates" }, /* @__PURE__ */ React.createElement("div", { className: "panel-header" }, "Code Templates \u2014 " + templateMatches.length + " matches"), /* @__PURE__ */ React.createElement("div", { className: "panel-body" }, groups))
        );
      }
      if (scriptMatches.length) {
        const groups = [];
        for (const [name, items] of grouped(scriptMatches, (m) => m.channelName)) {
          const rows = items.map((m, i) => /* @__PURE__ */ React.createElement(MatchRow, { key: name + "#" + i, m, re }));
          groups.push(
            /* @__PURE__ */ React.createElement(ArtifactDetails, { label: name, items, key: "gs:" + name }, /* @__PURE__ */ React.createElement("div", null, rows))
          );
        }
        panels.push(
          /* @__PURE__ */ React.createElement("div", { className: "panel", key: "scripts" }, /* @__PURE__ */ React.createElement("div", { className: "panel-header" }, "Global Scripts \u2014 " + scriptMatches.length + " matches"), /* @__PURE__ */ React.createElement("div", { className: "panel-body" }, groups))
        );
      }
      if (!matches.length) {
        panels.push(/* @__PURE__ */ React.createElement("div", { className: "hint", key: "empty" }, "No matches found."));
      }
      return /* @__PURE__ */ React.createElement(React.Fragment, null, panels);
    }
    const exportDisabled = !results || !results.length;
    return /* @__PURE__ */ React.createElement("div", { className: "view" }, /* @__PURE__ */ React.createElement("div", { className: "taskbar", "data-pane-title": "Source Code Search Tasks" }, /* @__PURE__ */ React.createElement(TaskBtn, { label: "Search", icon: "search", primary: true, disabled: searching, onClick: performSearch }), /* @__PURE__ */ React.createElement("span", { className: "sep" }), /* @__PURE__ */ React.createElement(TaskBtn, { label: "Export JSON", icon: "export", disabled: exportDisabled, onClick: exportJson }), /* @__PURE__ */ React.createElement(TaskBtn, { label: "Export CSV", icon: "export", disabled: exportDisabled, onClick: exportCsv })), /* @__PURE__ */ React.createElement("div", { className: "view-body" }, notInstalledStatus !== null && /* @__PURE__ */ React.createElement("div", { className: "panel" }, /* @__PURE__ */ React.createElement("div", { className: "panel-header" }, "Status"), /* @__PURE__ */ React.createElement("div", { className: "panel-body" }, /* @__PURE__ */ React.createElement("div", { className: "hint" }, "The OIE Source Code Search engine plugin is not installed on this engine (" + EXT + " answered " + notInstalledStatus + "). Install the oie-source-code-search extension and restart the engine."))), /* @__PURE__ */ React.createElement("div", { className: "panel" }, /* @__PURE__ */ React.createElement("div", { className: "panel-header" }, "Search"), /* @__PURE__ */ React.createElement("div", { className: "panel-body" }, /* @__PURE__ */ React.createElement("div", { className: "field" }, /* @__PURE__ */ React.createElement("label", null, "Search For"), /* @__PURE__ */ React.createElement(
      "input",
      {
        type: "text",
        ref: queryInputRef,
        placeholder: "Text or regular expression",
        className: "w-[420px]",
        value: query,
        onChange: (e) => setQuery(e.target.value),
        onKeyDown: (e) => {
          if (e.key === "Enter") performSearch();
        }
      }
    )), /* @__PURE__ */ React.createElement("div", { className: "field" }, /* @__PURE__ */ React.createElement("label", null, "Match"), /* @__PURE__ */ React.createElement("div", { className: "flex flex-wrap gap-y-[4px] gap-x-[18px] items-center" }, /* @__PURE__ */ React.createElement("select", { value: mode, onChange: (e) => setMode(e.target.value) }, /* @__PURE__ */ React.createElement("option", { value: "literal" }, "Literal"), /* @__PURE__ */ React.createElement("option", { value: "regex" }, "Regular Expression")), /* @__PURE__ */ React.createElement("label", { className: "check" }, /* @__PURE__ */ React.createElement(
      "input",
      {
        type: "checkbox",
        checked: caseSensitive,
        onChange: (e) => setCaseSensitive(e.target.checked)
      }
    ), "Case Sensitive"))), /* @__PURE__ */ React.createElement("div", { className: "field" }, /* @__PURE__ */ React.createElement("label", null, "Search In"), /* @__PURE__ */ React.createElement("div", { className: "flex flex-wrap gap-y-[4px] gap-x-[18px] items-center" }, /* @__PURE__ */ React.createElement("label", { className: "check" }, /* @__PURE__ */ React.createElement(
      "input",
      {
        type: "checkbox",
        checked: searchChannels,
        onChange: (e) => setSearchChannels(e.target.checked)
      }
    ), "Channels"), /* @__PURE__ */ React.createElement("label", { className: "check" }, /* @__PURE__ */ React.createElement(
      "input",
      {
        type: "checkbox",
        checked: searchCodeTemplates,
        onChange: (e) => setSearchCodeTemplates(e.target.checked)
      }
    ), "Code Templates"), /* @__PURE__ */ React.createElement("label", { className: "check" }, /* @__PURE__ */ React.createElement(
      "input",
      {
        type: "checkbox",
        checked: searchGlobalScripts,
        onChange: (e) => setSearchGlobalScripts(e.target.checked)
      }
    ), "Global Scripts"), /* @__PURE__ */ React.createElement("label", { className: "check" }, /* @__PURE__ */ React.createElement(
      "input",
      {
        type: "checkbox",
        checked: searchMessageTemplates,
        onChange: (e) => setSearchMessageTemplates(e.target.checked)
      }
    ), "Message Templates"), /* @__PURE__ */ React.createElement("label", { className: "check" }, /* @__PURE__ */ React.createElement(
      "input",
      {
        type: "checkbox",
        checked: searchConnectorProperties,
        onChange: (e) => setSearchConnectorProperties(e.target.checked)
      }
    ), "Connector Properties"))), /* @__PURE__ */ React.createElement("div", { className: "field" }, /* @__PURE__ */ React.createElement("label", null, "Channel Scope"), /* @__PURE__ */ React.createElement("div", null, /* @__PURE__ */ React.createElement("select", { value: scope, onChange: (e) => onScopeChange(e.target.value) }, /* @__PURE__ */ React.createElement("option", { value: "all" }, "All Channels"), /* @__PURE__ */ React.createElement("option", { value: "selected" }, "Selected Channels")), /* @__PURE__ */ React.createElement("div", { className: "flex flex-col gap-[2px] max-h-[180px] overflow-auto min-w-[320px] max-w-[460px] border border-line rounded-[3px] py-[6px] px-[10px] mt-[6px]", style: { display: scope === "selected" ? "" : "none" } }, channels === null ? /* @__PURE__ */ React.createElement("div", { className: "hint" }, "Loading channels\u2026") : !channels.length ? /* @__PURE__ */ React.createElement("div", { className: "hint" }, "No channels available") : channels.map((ch) => /* @__PURE__ */ React.createElement("label", { className: "check", key: ch.id }, /* @__PURE__ */ React.createElement(
      "input",
      {
        type: "checkbox",
        checked: selectedIds.has(ch.id),
        onChange: (e) => toggleChannel(ch.id, e.target.checked)
      }
    ), ch.name)))), /* @__PURE__ */ React.createElement("div", { className: "hint" }, "Limits which channels are searched for channel scripts, message templates, and connector properties")))), /* @__PURE__ */ React.createElement("div", { className: "panel" }, /* @__PURE__ */ React.createElement("div", { className: "panel-header" }, "Results \u2014 ", /* @__PURE__ */ React.createElement("span", { className: "text-text-faint" }, status)), /* @__PURE__ */ React.createElement("div", { className: "panel-body" }, /* @__PURE__ */ React.createElement(ResultsBody, null)))));
  }
  function Loading({ text }) {
    const ref = React.useRef(null);
    React.useEffect(() => {
      const host = ref.current;
      host.appendChild(platform2.ui.loading(text));
      return () => host.replaceChildren();
    }, [text]);
    return /* @__PURE__ */ React.createElement("div", { ref, className: "contents" });
  }
  platform2.registerNavItem({
    id: "source-code-search",
    label: "Source Code Search",
    icon: "search",
    path: "/source-code-search",
    section: "Plugins",
    order: 40
  });
  platform2.registerView("/source-code-search", platform2.reactView(SourceCodeSearchView), { title: "Source Code Search" });
}
export {
  register
};
