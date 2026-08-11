(() => {
    'use strict';

    let tradeRequestInFlight = false;
    const nativeFetch = window.fetch.bind(window);

    let itemPriceChart = null;
    const chartRanges = [
        { key: '1m', label: '1M', seconds: 60 },
        { key: '5m', label: '5M', seconds: 5 * 60 },
        { key: '30m', label: '30M', seconds: 30 * 60 },
        { key: '1h', label: '1H', seconds: 60 * 60 },
        { key: '12h', label: '12H', seconds: 12 * 60 * 60 },
        { key: '1d', label: '1D', seconds: 24 * 60 * 60 },
        { key: '7d', label: '7D', seconds: 7 * 24 * 60 * 60 },
        { key: 'all', label: 'ALL', seconds: null }
    ];
    let activeChartRange = localStorage.getItem('nascraft-chart-range') || 'all';
    if (!chartRanges.some(range => range.key === activeChartRange)) activeChartRange = 'all';

    function hookLightweightChart() {
        if (!window.LightweightCharts || typeof window.LightweightCharts.createChart !== 'function') return;
        if (window.LightweightCharts.__nascraftRangeHooked) return;

        const originalCreateChart = window.LightweightCharts.createChart.bind(window.LightweightCharts);
        window.LightweightCharts.createChart = function(container, options) {
            const chart = originalCreateChart(container, options);
            if (container?.id === 'item-price-chart-container') {
                itemPriceChart = chart;
                if (typeof chart.addBaselineSeries === 'function') {
                    const originalAddBaselineSeries = chart.addBaselineSeries.bind(chart);
                    chart.addBaselineSeries = function(seriesOptions = {}) {
                        const withSteps = window.LightweightCharts.LineType?.WithSteps;
                        return originalAddBaselineSeries(withSteps === undefined
                            ? seriesOptions
                            : { ...seriesOptions, lineType: withSteps });
                    };
                }
            }
            return chart;
        };
        window.LightweightCharts.__nascraftRangeHooked = true;
    }

    hookLightweightChart();

    function applyChartRange() {
        if (!itemPriceChart) return;
        const timeScale = itemPriceChart.timeScale();
        if (!timeScale) return;

        const range = chartRanges.find(entry => entry.key === activeChartRange) || chartRanges.at(-1);
        if (!range.seconds) {
            timeScale.fitContent();
            return;
        }

        const to = Math.floor(Date.now() / 1000);
        try {
            timeScale.setVisibleRange({ from: to - range.seconds, to });
        } catch (error) {
            console.debug('[Nascraft] Could not apply chart range yet:', error);
        }
    }

    function refreshChartRangeButtons() {
        document.querySelectorAll('[data-nascraft-chart-range]').forEach(button => {
            const active = button.dataset.nascraftChartRange === activeChartRange;
            button.classList.toggle('bg-emerald-600', active);
            button.classList.toggle('text-white', active);
            button.classList.toggle('bg-gray-700', !active);
            button.classList.toggle('text-gray-300', !active);
        });
    }

    function addChartRangeControls() {
        const chartContainer = document.getElementById('item-price-chart-container');
        if (!chartContainer || document.getElementById('nascraft-chart-ranges')) return;

        const controls = document.createElement('div');
        controls.id = 'nascraft-chart-ranges';
        controls.className = 'flex flex-wrap items-center gap-1 mb-2';
        controls.setAttribute('aria-label', 'Chart timeframe');

        for (const range of chartRanges) {
            const button = document.createElement('button');
            button.type = 'button';
            button.dataset.nascraftChartRange = range.key;
            button.textContent = range.label;
            button.className = 'px-2 py-1 rounded text-xs font-semibold transition hover:bg-gray-600';
            button.title = range.seconds ? `Show the last ${range.label}` : 'Show all available price history';
            button.addEventListener('click', () => {
                activeChartRange = range.key;
                localStorage.setItem('nascraft-chart-range', activeChartRange);
                refreshChartRangeButtons();
                applyChartRange();
            });
            controls.appendChild(button);
        }

        chartContainer.parentElement?.insertBefore(controls, chartContainer);
        refreshChartRangeButtons();
        setTimeout(applyChartRange, 250);
    }

    setInterval(() => {
        if (activeChartRange !== 'all') applyChartRange();
    }, 1500);

    function randomRequestId() {
        if (window.crypto?.randomUUID) return window.crypto.randomUUID();
        const bytes = new Uint8Array(16);
        window.crypto?.getRandomValues?.(bytes);
        return Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('') || `${Date.now()}-${Math.random()}`;
    }

    window.fetch = async function(input, init = {}) {
        const url = typeof input === 'string' ? input : input?.url || '';
        const isTrade = /\/api\/trade\/(buy|sell)(?:$|\?)/.test(url) && (init.method || 'GET').toUpperCase() === 'POST';

        if (!isTrade) return nativeFetch(input, init);
        if (tradeRequestInFlight) {
            return new Response(JSON.stringify({ success: false, error: 'TRADE_ALREADY_PROCESSING', message: 'A trade is already being processed.' }), {
                status: 409,
                headers: { 'Content-Type': 'application/json' }
            });
        }

        tradeRequestInFlight = true;
        try {
            const headers = new Headers(init.headers || {});
            headers.set('X-Nascraft-Request-ID', randomRequestId());
            return await nativeFetch(input, { ...init, headers });
        } finally {
            tradeRequestInFlight = false;
        }
    };

    async function copyText(text) {
        if (navigator.clipboard && window.isSecureContext) {
            try {
                await navigator.clipboard.writeText(text);
                return true;
            } catch (_) {}
        }

        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.setAttribute('readonly', '');
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        textarea.style.pointerEvents = 'none';
        document.body.appendChild(textarea);
        textarea.focus();
        textarea.select();
        textarea.setSelectionRange(0, textarea.value.length);
        let copied = false;
        try { copied = document.execCommand('copy'); } catch (_) { copied = false; }
        textarea.remove();
        return copied;
    }

    document.addEventListener('click', async (event) => {
        const container = event.target.closest?.('#login-command-container');
        if (!container) return;

        event.preventDefault();
        event.stopImmediatePropagation();
        const text = document.getElementById('login-command-text')?.textContent?.trim();
        if (!text) return;

        const copied = await copyText(text);
        const previousTitle = container.getAttribute('title');
        container.setAttribute('title', copied ? 'Copied!' : 'Press Ctrl+C to copy');
        setTimeout(() => {
            if (previousTitle) container.setAttribute('title', previousTitle);
            else container.removeAttribute('title');
        }, 1500);
    }, true);

    function parseNumber(text) {
        if (!text) return 0;
        const normalized = text.replace(/[^0-9,.-]/g, '').replace(/,/g, '');
        const value = Number.parseFloat(normalized);
        return Number.isFinite(value) ? value : 0;
    }

    function addMaxButton() {
        const amountInput = document.getElementById('trade-amount');
        const confirmButton = document.getElementById('btn-confirm-trade');
        if (!amountInput || !confirmButton || document.getElementById('btn-amount-max')) return;

        const button = document.createElement('button');
        button.id = 'btn-amount-max';
        button.type = 'button';
        button.textContent = 'Max';
        button.className = 'bg-gray-700 hover:bg-gray-600 text-white font-bold px-3 py-2 rounded text-xs';
        amountInput.parentElement?.appendChild(button);

        button.addEventListener('click', () => {
            const modalText = document.getElementById('modal-body')?.textContent || '';
            const sellMode = /Confirm\s+Sell/i.test(confirmButton.textContent || '');
            let max = 1;

            if (sellMode) {
                const ownedMatch = modalText.match(/Owned in portfolio:\s*([0-9]+)/i);
                max = ownedMatch ? Number.parseInt(ownedMatch[1], 10) : 1;
            } else {
                const balance = parseNumber(document.getElementById('portfolio-balance')?.textContent || '');
                const unitMatch = modalText.match(/Unit Price:\s*\$?([0-9,.]+)/i);
                const unitPrice = unitMatch ? parseNumber(unitMatch[1]) : 0;
                max = unitPrice > 0 ? Math.max(1, Math.floor(balance / unitPrice)) : 1;
            }

            amountInput.value = Math.max(1, max);
            amountInput.dispatchEvent(new Event('change', { bubbles: true }));
        });
    }

    function polishBrandHeader() {
        const header = document.querySelector('aside > div.flex.items-center.justify-between');
        const brand = header?.firstElementChild;
        const logo = brand?.querySelector('img');
        const name = brand?.querySelector('span');
        if (!brand || !logo || !name || brand.dataset.nascraftPolished) return;

        brand.dataset.nascraftPolished = 'true';
        brand.className = 'flex flex-col items-start gap-0.5 min-w-[72px] shrink-0';
        logo.className = 'w-8 h-8 shrink-0';
        name.className = 'font-bold text-white text-xs tracking-wide leading-none';
        name.textContent = 'Nascraft';
    }

    function applyWebPreferences() {
        const reduceMotion = localStorage.getItem('nascraft-reduce-motion') === 'true';
        const compactMode = localStorage.getItem('nascraft-compact-mode') === 'true';
        document.documentElement.style.scrollBehavior = reduceMotion ? 'auto' : '';
        document.body.classList.toggle('nascraft-reduce-motion', reduceMotion);
        document.body.classList.toggle('nascraft-compact-mode', compactMode);
    }

    function closeSettings() {
        document.getElementById('nascraft-settings-overlay')?.remove();
    }

    function openSettings() {
        closeSettings();
        const overlay = document.createElement('div');
        overlay.id = 'nascraft-settings-overlay';
        overlay.className = 'fixed inset-0 z-[100] bg-black/70 flex items-center justify-center p-4';
        overlay.innerHTML = `
            <div class="w-full max-w-md bg-gray-900 border border-gray-700 rounded-xl shadow-2xl p-5">
                <div class="flex items-center justify-between mb-4">
                    <div><h3 class="text-lg font-bold text-white">Settings</h3><p class="text-xs text-gray-400">Nascraft Web 1.9.9</p></div>
                    <button id="nascraft-settings-close" class="text-gray-400 hover:text-white text-xl">×</button>
                </div>
                <div class="space-y-3">
                    <label class="flex items-center justify-between gap-4 p-3 bg-gray-800/70 rounded-lg cursor-pointer">
                        <div><div class="text-sm font-semibold text-white">Reduce animations</div><div class="text-xs text-gray-400">Useful on slower browsers and mobile devices.</div></div>
                        <input id="nascraft-reduce-motion" type="checkbox" class="h-4 w-4" ${localStorage.getItem('nascraft-reduce-motion') === 'true' ? 'checked' : ''}>
                    </label>
                    <label class="flex items-center justify-between gap-4 p-3 bg-gray-800/70 rounded-lg cursor-pointer">
                        <div><div class="text-sm font-semibold text-white">Compact market</div><div class="text-xs text-gray-400">Reduces spacing so more items and data fit on screen.</div></div>
                        <input id="nascraft-compact-mode" type="checkbox" class="h-4 w-4" ${localStorage.getItem('nascraft-compact-mode') === 'true' ? 'checked' : ''}>
                    </label>
                    <div class="p-3 bg-gray-800/70 rounded-lg">
                        <div class="text-sm font-semibold text-white mb-2">Default chart timeframe</div>
                        <select id="nascraft-default-chart-range" class="w-full bg-gray-900 border border-gray-700 rounded-md px-3 py-2 text-sm text-gray-200">
                            ${chartRanges.map(range => `<option value="${range.key}" ${range.key === activeChartRange ? 'selected' : ''}>${range.label}</option>`).join('')}
                        </select>
                    </div>
                    <div class="p-3 border border-dashed border-gray-700 rounded-lg text-xs text-gray-400">
                        Discord alerts and additional integrations: <span class="font-semibold text-indigo-300">Coming Soon</span>
                    </div>
                </div>
            </div>`;
        document.body.appendChild(overlay);
        overlay.addEventListener('click', event => { if (event.target === overlay) closeSettings(); });
        document.getElementById('nascraft-settings-close')?.addEventListener('click', closeSettings);
        document.getElementById('nascraft-reduce-motion')?.addEventListener('change', event => {
            localStorage.setItem('nascraft-reduce-motion', String(event.target.checked));
            applyWebPreferences();
        });
        document.getElementById('nascraft-compact-mode')?.addEventListener('change', event => {
            localStorage.setItem('nascraft-compact-mode', String(event.target.checked));
            applyWebPreferences();
        });
        document.getElementById('nascraft-default-chart-range')?.addEventListener('change', event => {
            activeChartRange = event.target.value;
            localStorage.setItem('nascraft-chart-range', activeChartRange);
            refreshChartRangeButtons();
            applyChartRange();
        });
    }

    function ensureUserHeader() {
        const authControls = document.getElementById('auth-controls');
        const userDisplay = document.getElementById('user-display');
        if (!authControls || !userDisplay) return;

        const header = authControls.parentElement;
        const brand = header?.firstElementChild;
        if (!header || !brand) return;

        let identity = document.getElementById('nascraft-player-identity');
        if (!identity) {
            identity = document.createElement('div');
            identity.id = 'nascraft-player-identity';
            identity.className = 'flex items-center gap-2 min-w-0';
            brand.insertAdjacentElement('afterend', identity);
        }

        userDisplay.className = 'text-xs font-semibold text-gray-200 truncate max-w-[96px]';
        identity.appendChild(userDisplay);

        if (!document.getElementById('nascraft-settings-btn')) {
            const button = document.createElement('button');
            button.id = 'nascraft-settings-btn';
            button.type = 'button';
            button.setAttribute('aria-label', 'Open settings');
            button.title = 'Settings';
            button.innerHTML = '⚙';
            button.className = 'text-indigo-300 hover:text-white text-sm leading-none transition duration-150';
            button.addEventListener('click', openSettings);
            identity.appendChild(button);
        }
    }

    const pageObserver = new MutationObserver(() => {
        addMaxButton();
        ensureUserHeader();
    });

    document.addEventListener('DOMContentLoaded', () => {
        addMaxButton();
        addChartRangeControls();
        polishBrandHeader();
        applyWebPreferences();
        ensureUserHeader();
        pageObserver.observe(document.body, { childList: true, subtree: true });
    });

    const style = document.createElement('style');
    style.textContent = `
        .nascraft-reduce-motion *, .nascraft-reduce-motion *::before, .nascraft-reduce-motion *::after { animation-duration: .001ms !important; transition-duration: .001ms !important; }
        .nascraft-compact-mode aside { padding-top: .75rem !important; padding-bottom: .75rem !important; }
        .nascraft-compact-mode #item-list { gap: .125rem !important; }
        .nascraft-compact-mode #item-list li { padding-top: .25rem !important; padding-bottom: .25rem !important; }
    `;
    document.head.appendChild(style);
})();
