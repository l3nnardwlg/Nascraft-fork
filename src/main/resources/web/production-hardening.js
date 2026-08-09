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
    let activeChartRange = sessionStorage.getItem('nascraft-chart-range') || 'all';
    if (!chartRanges.some(range => range.key === activeChartRange)) activeChartRange = 'all';

    function hookLightweightChart() {
        if (!window.LightweightCharts || typeof window.LightweightCharts.createChart !== 'function') return;
        if (window.LightweightCharts.__nascraftRangeHooked) return;

        const originalCreateChart = window.LightweightCharts.createChart.bind(window.LightweightCharts);
        window.LightweightCharts.createChart = function(container, options) {
            const chart = originalCreateChart(container, options);
            if (container?.id === 'item-price-chart-container') {
                itemPriceChart = chart;

                // Prices in Nascraft change discretely. A stepped line avoids the
                // misleading diagonal interpolation between sparse historical points.
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
                sessionStorage.setItem('nascraft-chart-range', activeChartRange);
                refreshChartRangeButtons();
                applyChartRange();
            });
            controls.appendChild(button);
        }

        chartContainer.parentElement?.insertBefore(controls, chartContainer);
        refreshChartRangeButtons();
        setTimeout(applyChartRange, 250);
    }

    // The original chart calls fitContent() after loading a new item. Re-apply a
    // selected short range afterwards so switching assets keeps the chosen view.
    setInterval(() => {
        if (activeChartRange !== 'all') applyChartRange();
    }, 1500);

    function randomRequestId() {
        if (window.crypto?.randomUUID) return window.crypto.randomUUID();
        const bytes = new Uint8Array(16);
        window.crypto?.getRandomValues?.(bytes);
        return Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('') || `${Date.now()}-${Math.random()}`;
    }

    // Prevent accidental duplicate BUY/SELL submits from double-clicks/retries in the same browser tab.
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
            } catch (_) {
                // Fall through to the legacy copy path.
            }
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
        try {
            copied = document.execCommand('copy');
        } catch (_) {
            copied = false;
        }
        textarea.remove();
        return copied;
    }

    // Capture before the original handler so HTTP deployments get a reliable fallback.
    document.addEventListener('click', async (event) => {
        const container = event.target.closest?.('#login-command-container');
        if (!container) return;

        event.preventDefault();
        event.stopImmediatePropagation();
        const text = document.getElementById('login-command-text')?.textContent?.trim();
        if (!text) return;

        const copied = await copyText(text);
        if (typeof window.showToast === 'function') {
            window.showToast(copied ? 'Command copied to clipboard!' : 'Copy failed. Please copy the command manually.', copied ? 'success' : 'warning');
        } else {
            const previousTitle = container.getAttribute('title');
            container.setAttribute('title', copied ? 'Copied!' : 'Press Ctrl+C to copy');
            setTimeout(() => {
                if (previousTitle) container.setAttribute('title', previousTitle);
                else container.removeAttribute('title');
            }, 1500);
        }
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

        const amountRow = amountInput.parentElement;
        if (amountRow) amountRow.appendChild(button);

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

        confirmButton.addEventListener('click', () => {
            confirmButton.disabled = true;
            confirmButton.classList.add('opacity-60', 'cursor-not-allowed');
            const oldText = confirmButton.textContent;
            confirmButton.textContent = 'Processing...';
            setTimeout(() => {
                if (document.body.contains(confirmButton)) {
                    confirmButton.disabled = false;
                    confirmButton.classList.remove('opacity-60', 'cursor-not-allowed');
                    confirmButton.textContent = oldText;
                }
            }, 5000);
        }, { once: true });
    }

    const observer = new MutationObserver(() => addMaxButton());
    const modalBody = document.getElementById('modal-body');
    if (modalBody) observer.observe(modalBody, { childList: true, subtree: true });
    document.addEventListener('DOMContentLoaded', () => {
        addMaxButton();
        addChartRangeControls();
    });
})();
