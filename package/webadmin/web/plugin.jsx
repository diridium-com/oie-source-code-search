/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025-2026 Diridium Technologies Inc.
 *
 * OIE Source Code Search — web administrator plugin (React).
 *
 * Web counterpart of com.diridium.sourcecodesearch.SourceCodeSearchDialog
 * (Swing). Talks to the engine plugin's REST servlet
 * (SourceCodeSearchServletInterface, @Path("/extensions/oie-source-code-search")):
 *
 *   GET /count   — phase 1, match count (large-result confirmation, like Swing)
 *   GET /search  — phase 2, List<SearchMatch>
 *
 * Both take: query, caseSensitive, regex, channelIds (CSV, empty = all),
 * searchChannels, searchCodeTemplates, searchGlobalScripts,
 * searchMessageTemplates, searchConnectorProperties.
 *
 * SearchMatch fields: groupType (CHANNEL | CODE_TEMPLATE | GLOBAL_SCRIPT),
 * channelId, channelName, location (breadcrumb like
 * "Source > Transformer > Step 2 (JavaScript): name"), lineNumber, lineText.
 * Message template and connector property matches arrive as groupType CHANNEL
 * with template/properties breadcrumbs. SearchMatch has no @XStreamAlias, so
 * the JSON list is keyed by its FQCN — normalized via platform.api.asList.
 *
 * React port: searchView() (which built an imperative DOM tree with
 * platform.ui.h) is now the React function component SourceCodeSearchView,
 * registered via platform.reactView. All form + results state lives in
 * React.useState; the EXACT endpoints, search params, two-phase count→search
 * flow, result normalization, grouping, click-through navigation, highlighting,
 * and JSON/CSV exports are preserved VERBATIM. Only the rendering layer changed.
 * Taskbar buttons reuse platform.ui.taskButton (mounted via a ref) so the
 * button markup (icon SVG + classes) stays byte-identical to the original.
 */

import { platform } from '@oie/web-shell';
import { SCS_CSS } from './scs-css.generated.js';
const React = platform.React;

const EXT = '/extensions/oie-source-code-search';
const RESULT_WARNING_THRESHOLD = 5000;
const STYLE_ID = 'scs-style';

export function register(platform) {
    const api = platform.api;
    const {
        h, toast, taskButton, confirmDialog, downloadFile
    } = platform.ui;

    const notInstalled = (e) => e && (e.status === 404 || e.status === 501);

    function ensureStyle() {
        if (!document.getElementById(STYLE_ID)) {
            document.head.appendChild(h('style', { id: STYLE_ID }, SCS_CSS));
        }
    }

    /* ---- engine response normalization ---------------------------------- */

    /* GET /count returns a bare int; XStream JSON wraps scalars ({"int": 5})
       and the api client unwraps single-key roots, but stay defensive. */
    function asInt(value) {
        if (typeof value === 'number') return value;
        if (value && typeof value === 'object') {
            for (const v of Object.values(value)) {
                const n = Number(v);
                if (!isNaN(n)) return n;
            }
            return 0;
        }
        const n = Number(value);
        return isNaN(n) ? 0 : n;
    }

    function normalizeMatch(m) {
        if (!m || typeof m !== 'object') return null;
        return {
            groupType: String(m.groupType ?? ''),
            channelId: (m.channelId === undefined || m.channelId === null || m.channelId === '')
                ? null : String(m.channelId),
            channelName: String(m.channelName ?? ''),
            location: String(m.location ?? ''),
            lineNumber: Number(m.lineNumber) || 0,
            lineText: (m.lineText === undefined || m.lineText === null) ? '' : String(m.lineText)
        };
    }

    /* Map<String,String> from /channels/idsAndNames:
       { entry: [{ string: [id, name] }, ...] } — singletons as a bare object. */
    function idNamePairs(map) {
        const out = [];
        if (!map || typeof map !== 'object') return out;
        for (const entry of api.asList(map.entry)) {
            if (!entry || typeof entry !== 'object') continue;
            if (Array.isArray(entry.string)) {
                out.push({
                    id: String(entry.string[0]),
                    name: String(entry.string[1] !== undefined ? entry.string[1] : entry.string[0])
                });
                continue;
            }
            const vals = Object.entries(entry)
                .filter(([k]) => !k.startsWith('@'))
                .map(([, v]) => v);
            if (vals.length >= 2) out.push({ id: String(vals[0]), name: String(vals[1]) });
        }
        return out.sort((a, b) => a.name.localeCompare(b.name));
    }

    /* ---- match highlighting (same semantics as the Swing dialog) -------- */

    function buildHighlighter(query, caseSensitive, regex) {
        try {
            const src = regex ? query : query.replace(/[.*+?^$()|[\]{}\\]/g, '\\$&');
            return new RegExp(src, caseSensitive ? 'g' : 'gi');
        } catch (e) {
            return null;     // invalid client-side regex — skip highlighting
        }
    }

    /* JSX equivalent of the imperative highlightedLine(): same regex walk +
       500-iteration guard + empty-match advance, producing highlighted <mark>
       nodes around each hit inside a wrapping <span>. */
    function HighlightedLine({ text, re }) {
        if (!re) return <span className="whitespace-pre-wrap break-all">{text}</span>;
        const parts = [];
        let last = 0;
        let guard = 0;
        let key = 0;
        re.lastIndex = 0;
        let m;
        while ((m = re.exec(text)) !== null && guard++ < 500) {
            if (m[0].length === 0) { re.lastIndex++; continue; }
            if (m.index > last) parts.push(text.slice(last, m.index));
            parts.push(<mark className="bg-accent text-accent-ink rounded-[2px] px-px py-0" key={key++}>{m[0]}</mark>);
            last = m.index + m[0].length;
        }
        parts.push(text.slice(last));
        return <span className="whitespace-pre-wrap break-all">{parts}</span>;
    }

    /* ---- web-native click-through ----------------------------------------
       Best effort from the location breadcrumb:
         "Source > ..."          → metaDataId 0
         "Dest <n>: name > ..."  → metaDataId n
       Step matches route to the filter/transformer/response editors; other
       channel matches (channel scripts, templates, connector properties) open
       the channel editor. */
    function matchTarget(m) {
        if (m.groupType === 'CODE_TEMPLATE') return '/code-templates';
        if (m.groupType === 'GLOBAL_SCRIPT') return '/global-scripts';
        if (m.groupType !== 'CHANNEL' || !m.channelId) return null;
        const loc = m.location;
        let meta = null;
        if (loc.indexOf('Source') === 0) {
            meta = '0';
        } else {
            const d = /^Dest (\d+):/.exec(loc);
            if (d) meta = d[1];
        }
        const base = '/channels/' + encodeURIComponent(m.channelId);
        if (meta !== null) {
            if (loc.indexOf(' > Response Transformer > Step ') !== -1) return base + '/response/' + meta;
            if (loc.indexOf(' > Transformer > Step ') !== -1) return base + '/transformer/' + meta;
            if (loc.indexOf(' > Filter > Step ') !== -1) return base + '/filter/' + meta;
        }
        return base + '/edit';
    }

    /* ---- CSV (column order and escaping match the Swing export) --------- */

    function escapeCsv(value) {
        if (value === null || value === undefined) return '';
        const s = String(value);
        if (s.indexOf(',') !== -1 || s.indexOf('"') !== -1 || s.indexOf('\n') !== -1) {
            return '"' + s.replace(/"/g, '""') + '"';
        }
        return s;
    }

    const csvRow = (values) => values.map(escapeCsv).join(',');

    /* ---- taskbar button: reuses platform.ui.taskButton (DOM node) so the
       button markup — icon SVG + .btn/.btn-primary classes — stays identical to
       the original. Re-created when label/icon/disabled/primary change. ------ */
    function TaskBtn({ label, icon, onClick, primary, disabled }) {
        const hostRef = React.useRef(null);
        const onClickRef = React.useRef(onClick);
        onClickRef.current = onClick;
        React.useEffect(() => {
            const host = hostRef.current;
            const btn = taskButton(label, icon, (e) => onClickRef.current && onClickRef.current(e),
                { primary, disabled });
            host.appendChild(btn);
            return () => host.replaceChildren();
        }, [label, icon, primary, disabled]);
        return <span ref={hostRef} className="contents" />;
    }

    /* ====================================================================== *
     *  View
     * ====================================================================== */

    function SourceCodeSearchView() {
        ensureStyle();

        /* ---- search form state ---- */
        const [query, setQuery] = React.useState('');
        const [mode, setMode] = React.useState('literal');                     // literal | regex
        const [caseSensitive, setCaseSensitive] = React.useState(false);
        // Scope checkboxes — defaults mirror the Swing dialog.
        const [searchChannels, setSearchChannels] = React.useState(true);
        const [searchCodeTemplates, setSearchCodeTemplates] = React.useState(true);
        const [searchGlobalScripts, setSearchGlobalScripts] = React.useState(true);
        const [searchMessageTemplates, setSearchMessageTemplates] = React.useState(false);
        const [searchConnectorProperties, setSearchConnectorProperties] = React.useState(true);

        // Channel scope.
        const [scope, setScope] = React.useState('all');                       // all | selected
        const [channels, setChannels] = React.useState(null);                  // [{id,name}] or null (not loaded)
        const [selectedIds, setSelectedIds] = React.useState(() => new Set());

        // Results / status.
        const [results, setResults] = React.useState(null);                    // last normalized matches
        const [lastParams, setLastParams] = React.useState(null);              // params behind results (for export)
        const [searching, setSearching] = React.useState(false);
        const [status, setStatus] = React.useState(' ');
        const [resultsState, setResultsState] = React.useState({ kind: 'idle' });
        const [notInstalledStatus, setNotInstalledStatus] = React.useState(null);

        const queryInputRef = React.useRef(null);
        const searchingRef = React.useRef(false);
        const warnedRef = React.useRef(false);
        searchingRef.current = searching;

        // Focus the query input on mount (queueMicrotask in the original).
        React.useEffect(() => { queryInputRef.current && queryInputRef.current.focus(); }, []);

        async function loadChannels() {
            let pairs;
            try {
                pairs = idNamePairs(await api.channels.idsAndNames());
            } catch (e) {
                setChannels([]);
                toast('Failed to load channels: ' + e.message, 'error');
                return;
            }
            setChannels(pairs);
        }

        function onScopeChange(value) {
            setScope(value);
            if (value === 'selected' && channels === null) loadChannels();
        }

        function toggleChannel(id, checked) {
            setSelectedIds(prev => {
                const next = new Set(prev);
                if (checked) next.add(id); else next.delete(id);
                return next;
            });
        }

        function showNotInstalled(httpStatus) {
            setNotInstalledStatus(httpStatus);
            if (!warnedRef.current) {
                warnedRef.current = true;
                toast('OIE Source Code Search engine plugin is not installed on this engine', 'warn');
            }
        }

        /* ---- export ---- */

        // Same shape as the Swing dialog's JSON export (LinkedHashMap order).
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
                results: results
            };
            downloadFile('search-results.json', JSON.stringify(payload, null, 2), 'application/json');
        }

        function exportCsv() {
            if (!results || !results.length) return;
            const rows = [csvRow(['groupType', 'channelId', 'channelName', 'location', 'lineNumber', 'lineText'])];
            for (const m of results) {
                rows.push(csvRow([m.groupType, m.channelId, m.channelName, m.location, m.lineNumber, m.lineText]));
            }
            downloadFile('search-results.csv', rows.join('\n') + '\n', 'text/csv');
        }

        /* ---- search (two-phase, mirroring the Swing dialog) ---- */

        function gatherParams() {
            const params = {
                query: query,
                caseSensitive: caseSensitive,
                regex: mode === 'regex',
                channelIds: '',
                searchChannels: searchChannels,
                searchCodeTemplates: searchCodeTemplates,
                searchGlobalScripts: searchGlobalScripts,
                searchMessageTemplates: searchMessageTemplates,
                searchConnectorProperties: searchConnectorProperties
            };
            if (scope === 'selected' && selectedIds.size) {
                params.channelIds = [...selectedIds].join(',');
            }
            return params;
        }

        async function performSearch() {
            if (searchingRef.current) return;
            const params = gatherParams();
            if (!params.query) { queryInputRef.current && queryInputRef.current.focus(); return; }
            if (scope === 'selected' && !selectedIds.size) {
                toast('Select at least one channel, or search all channels', 'warn');
                return;
            }

            searchingRef.current = true;
            setSearching(true);
            setResults(null);
            setStatus('Counting matches…');
            setResultsState({ kind: 'loading', text: 'Counting matches…' });

            try {
                // Phase 1: count (query string params match the servlet exactly).
                const matchCount = asInt(await api.get(EXT + '/count', params));
                setNotInstalledStatus(null);

                if (matchCount === 0) {
                    setStatus('No matches found.');
                    setResultsState({ kind: 'hint', text: 'No matches found.' });
                    return;
                }
                if (matchCount >= RESULT_WARNING_THRESHOLD) {
                    const go = await confirmDialog('Large Result Set',
                        'Found ' + matchCount + ' matches. This may take a moment to display. '
                        + 'Continue, or refine your search?', { okLabel: 'Continue' });
                    if (!go) {
                        setStatus(matchCount + ' matches found. Search cancelled.');
                        setResultsState({ kind: 'hint', text: 'Search cancelled.' });
                        return;
                    }
                }

                // Phase 2: full results.
                setStatus('Searching…');
                setResultsState({ kind: 'loading', text: 'Searching…' });
                const raw = api.asList(await api.get(EXT + '/search', params), 'searchMatch');
                const matches = raw.map(normalizeMatch).filter(Boolean);

                setResults(matches);
                setLastParams(params);

                // Status mirrors renderResults: "<n> matches in <m> artifacts."
                const artifactCount = new Set(matches.map(m =>
                    m.groupType + ':' + (m.channelId || m.channelName))).size;
                setStatus(matches.length
                    + (matches.length === 1 ? ' match' : ' matches') + ' in '
                    + artifactCount + (artifactCount === 1 ? ' artifact' : ' artifacts') + '.');
                setResultsState({ kind: 'results', matches, params });
            } catch (e) {
                setResultsState({ kind: 'hint', text: 'Search failed: ' + e.message });
                setStatus('Search failed.');
                if (notInstalled(e)) {
                    showNotInstalled(e.status);
                } else {
                    toast('Search failed: ' + e.message, 'error');
                }
            } finally {
                searchingRef.current = false;
                setSearching(false);
            }
        }

        /* ---- results rendering (mirrors the Swing JTree grouping) -------- */

        function MatchRow({ m, re }) {
            const target = matchTarget(m);
            return (
                <div className="flex gap-[12px] py-[2px] px-[8px] ml-[22px] cursor-pointer rounded-[3px] font-mono text-[12px] items-baseline hover:bg-accent-glow"
                    title={target ? 'Open ' + target : null}
                    onClick={target ? () => platform.router.navigate(target) : null}>
                    <span className="text-text-faint min-w-[70px] flex-none text-right">{'Line ' + m.lineNumber}</span>
                    <HighlightedLine text={m.lineText} re={re} />
                </div>
            );
        }

        /* Group matches the way the Swing tree does:
           Channels        > channel name > location breadcrumb > lines
           Code Templates  > library      > template            > lines
           Global Scripts  > script name  > lines */
        function grouped(list, keyOf) {
            const map = new Map();
            for (const m of list) {
                const key = keyOf(m);
                if (!map.has(key)) map.set(key, []);
                map.get(key).push(m);
            }
            return map;
        }

        function ArtifactDetails({ label, items, children }) {
            return (
                <details className="my-[4px]" open>
                    <summary className="cursor-pointer py-[3px] px-[4px] rounded-[3px] hover:bg-accent-glow">
                        {label}
                        <span className="text-text-faint ml-[8px] text-[11px]">
                            {items.length + (items.length === 1 ? ' match' : ' matches')}
                        </span>
                    </summary>
                    {children}
                </details>
            );
        }

        function ResultsBody() {
            if (resultsState.kind === 'idle') {
                return (
                    <div className="hint">
                        Enter a search and press Search. Click a match to open it in the matching editor.
                    </div>
                );
            }
            if (resultsState.kind === 'loading') {
                return <Loading text={resultsState.text} />;
            }
            if (resultsState.kind === 'hint') {
                return <div className="hint">{resultsState.text}</div>;
            }

            // kind === 'results'
            const { matches, params } = resultsState;
            const re = buildHighlighter(params.query, params.caseSensitive, params.regex);

            const channelMatches = matches.filter(m => m.groupType === 'CHANNEL');
            const templateMatches = matches.filter(m => m.groupType === 'CODE_TEMPLATE');
            const scriptMatches = matches.filter(m => m.groupType === 'GLOBAL_SCRIPT');

            const panels = [];

            if (channelMatches.length) {
                const groups = [];
                for (const [name, items] of grouped(channelMatches, m => m.channelName)) {
                    const locBlocks = [];
                    for (const [loc, locItems] of grouped(items, m => m.location)) {
                        locBlocks.push(<div className="text-text-faint text-[11px] mt-[6px] mb-[2px] ml-[22px]" key={'loc:' + loc}>{loc}</div>);
                        locItems.forEach((m, i) => locBlocks.push(<MatchRow key={loc + '#' + i} m={m} re={re} />));
                    }
                    groups.push(
                        <ArtifactDetails label={name} items={items} key={'ch:' + name}>
                            <div>{locBlocks}</div>
                        </ArtifactDetails>);
                }
                panels.push(
                    <div className="panel" key="channels">
                        <div className="panel-header">{'Channels — ' + channelMatches.length + ' matches'}</div>
                        <div className="panel-body">{groups}</div>
                    </div>);
            }

            if (templateMatches.length) {
                const libOf = (m) => {
                    const sep = m.location.indexOf(' > ');
                    return sep > 0 ? m.location.slice(0, sep) : '(No Library)';
                };
                const tmplOf = (m) => {
                    const sep = m.location.indexOf(' > ');
                    return sep > 0 ? m.location.slice(sep + 3) : m.location;
                };
                const groups = [];
                for (const [lib, libItems] of grouped(templateMatches, libOf)) {
                    const tmplBlocks = [];
                    for (const [tmpl, tmplItems] of grouped(libItems, tmplOf)) {
                        tmplBlocks.push(<div className="text-text-faint text-[11px] mt-[6px] mb-[2px] ml-[22px]" key={'tmpl:' + tmpl}>{tmpl}</div>);
                        tmplItems.forEach((m, i) => tmplBlocks.push(<MatchRow key={tmpl + '#' + i} m={m} re={re} />));
                    }
                    groups.push(
                        <ArtifactDetails label={lib} items={libItems} key={'lib:' + lib}>
                            <div>{tmplBlocks}</div>
                        </ArtifactDetails>);
                }
                panels.push(
                    <div className="panel" key="templates">
                        <div className="panel-header">{'Code Templates — ' + templateMatches.length + ' matches'}</div>
                        <div className="panel-body">{groups}</div>
                    </div>);
            }

            if (scriptMatches.length) {
                const groups = [];
                for (const [name, items] of grouped(scriptMatches, m => m.channelName)) {
                    const rows = items.map((m, i) => <MatchRow key={name + '#' + i} m={m} re={re} />);
                    groups.push(
                        <ArtifactDetails label={name} items={items} key={'gs:' + name}>
                            <div>{rows}</div>
                        </ArtifactDetails>);
                }
                panels.push(
                    <div className="panel" key="scripts">
                        <div className="panel-header">{'Global Scripts — ' + scriptMatches.length + ' matches'}</div>
                        <div className="panel-body">{groups}</div>
                    </div>);
            }

            if (!matches.length) {
                panels.push(<div className="hint" key="empty">No matches found.</div>);
            }

            return <>{panels}</>;
        }

        const exportDisabled = !results || !results.length;

        /* ---- layout (same DOM structure + class names as the original) ---- */

        return (
            <div className="view">
                <div className="taskbar" data-pane-title="Source Code Search Tasks">
                    <TaskBtn label="Search" icon="search" primary disabled={searching} onClick={performSearch} />
                    <span className="sep" />
                    <TaskBtn label="Export JSON" icon="export" disabled={exportDisabled} onClick={exportJson} />
                    <TaskBtn label="Export CSV" icon="export" disabled={exportDisabled} onClick={exportCsv} />
                </div>
                <div className="view-body">
                    {notInstalledStatus !== null && (
                        <div className="panel">
                            <div className="panel-header">Status</div>
                            <div className="panel-body">
                                <div className="hint">
                                    {'The OIE Source Code Search engine plugin is not installed on this engine ('
                                        + EXT + ' answered ' + notInstalledStatus + '). Install the oie-source-code-search '
                                        + 'extension and restart the engine.'}
                                </div>
                            </div>
                        </div>
                    )}

                    <div className="panel">
                        <div className="panel-header">Search</div>
                        <div className="panel-body">
                            <div className="field">
                                <label>Search For</label>
                                <input type="text" ref={queryInputRef}
                                    placeholder="Text or regular expression"
                                    className="w-[420px]"
                                    value={query}
                                    onChange={(e) => setQuery(e.target.value)}
                                    onKeyDown={(e) => { if (e.key === 'Enter') performSearch(); }} />
                            </div>

                            <div className="field">
                                <label>Match</label>
                                <div className="flex flex-wrap gap-y-[4px] gap-x-[18px] items-center">
                                    <select value={mode} onChange={(e) => setMode(e.target.value)}>
                                        <option value="literal">Literal</option>
                                        <option value="regex">Regular Expression</option>
                                    </select>
                                    <label className="check">
                                        <input type="checkbox" checked={caseSensitive}
                                            onChange={(e) => setCaseSensitive(e.target.checked)} />
                                        Case Sensitive
                                    </label>
                                </div>
                            </div>

                            <div className="field">
                                <label>Search In</label>
                                <div className="flex flex-wrap gap-y-[4px] gap-x-[18px] items-center">
                                    <label className="check">
                                        <input type="checkbox" checked={searchChannels}
                                            onChange={(e) => setSearchChannels(e.target.checked)} />
                                        Channels
                                    </label>
                                    <label className="check">
                                        <input type="checkbox" checked={searchCodeTemplates}
                                            onChange={(e) => setSearchCodeTemplates(e.target.checked)} />
                                        Code Templates
                                    </label>
                                    <label className="check">
                                        <input type="checkbox" checked={searchGlobalScripts}
                                            onChange={(e) => setSearchGlobalScripts(e.target.checked)} />
                                        Global Scripts
                                    </label>
                                    <label className="check">
                                        <input type="checkbox" checked={searchMessageTemplates}
                                            onChange={(e) => setSearchMessageTemplates(e.target.checked)} />
                                        Message Templates
                                    </label>
                                    <label className="check">
                                        <input type="checkbox" checked={searchConnectorProperties}
                                            onChange={(e) => setSearchConnectorProperties(e.target.checked)} />
                                        Connector Properties
                                    </label>
                                </div>
                            </div>

                            <div className="field">
                                <label>Channel Scope</label>
                                <div>
                                    <select value={scope} onChange={(e) => onScopeChange(e.target.value)}>
                                        <option value="all">All Channels</option>
                                        <option value="selected">Selected Channels</option>
                                    </select>
                                    <div className="flex flex-col gap-[2px] max-h-[180px] overflow-auto min-w-[320px] max-w-[460px] border border-line rounded-[3px] py-[6px] px-[10px] mt-[6px]" style={{ display: scope === 'selected' ? '' : 'none' }}>
                                        {channels === null ? (
                                            <div className="hint">Loading channels…</div>
                                        ) : !channels.length ? (
                                            <div className="hint">No channels available</div>
                                        ) : (
                                            channels.map((ch) => (
                                                <label className="check" key={ch.id}>
                                                    <input type="checkbox"
                                                        checked={selectedIds.has(ch.id)}
                                                        onChange={(e) => toggleChannel(ch.id, e.target.checked)} />
                                                    {ch.name}
                                                </label>
                                            ))
                                        )}
                                    </div>
                                </div>
                                <div className="hint">
                                    Limits which channels are searched for channel scripts, message templates, and connector properties
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="panel">
                        <div className="panel-header">
                            {'Results — '}
                            <span className="text-text-faint">{status}</span>
                        </div>
                        <div className="panel-body">
                            <ResultsBody />
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    /* Loading spinner — reuses platform.ui.loading (DOM node) for parity with
       the original clear(resultsHost).appendChild(loading(...)). */
    function Loading({ text }) {
        const ref = React.useRef(null);
        React.useEffect(() => {
            const host = ref.current;
            host.appendChild(platform.ui.loading(text));
            return () => host.replaceChildren();
        }, [text]);
        return <div ref={ref} className="contents" />;
    }

    platform.registerNavItem({
        id: 'source-code-search',
        label: 'Source Code Search',
        icon: 'search',
        path: '/source-code-search',
        section: 'Plugins',
        order: 40
    });
    platform.registerView('/source-code-search', platform.reactView(SourceCodeSearchView), { title: 'Source Code Search' });
}
