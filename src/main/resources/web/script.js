const dateFns = Chart._adapters._date.fns;

document.addEventListener('DOMContentLoaded', () => {
    const itemListElement = document.getElementById('item-list');
    const searchInputElement = document.getElementById('search-input');
    const selectedItemIconElement = document.getElementById('selected-item-icon');
    const selectedItemNameElement = document.getElementById('selected-item-name');
    const selectedItemDescElement = document.getElementById('selected-item-description');
    const currentPriceElement = document.getElementById('current-price');
    const sellPriceDisplayElement = document.getElementById('sell-price-display');
    const buyPriceDisplayElement = document.getElementById('buy-price-display');
    const itemPriceChartContainer = document.getElementById('item-price-chart-container');
    const cpiChartCanvas = document.getElementById('cpi-chart');
    const marketCapTreemapContainer = document.getElementById('treemap-container');
    const inflationCheckbox = document.getElementById('inflation-adjust-checkbox');
    const logScaleCheckbox = document.getElementById('log-scale-checkbox');
    const marketRankElement = document.getElementById('market-rank');
    const allTimeHighElement = document.getElementById('all-time-high');
    const allTimeLowElement = document.getElementById('all-time-low');
    const resetButton = document.getElementById('reset-zoom-button');
    const chartSpinner = document.getElementById('chart-loading-spinner');
    const change1hElement = document.getElementById('change-1h');
    const inceptionReturnElement = document.getElementById('inception-return');
    const sortControlsContainer = document.getElementById('sort-controls');
    const treemapTooltip = document.getElementById('treemap-tooltip');
    const volatilityElement = document.getElementById('volatility');
    const maxDrawdownElement = document.getElementById('max-drawdown');
    const topPortfoliosContainer = document.getElementById('top-portfolios-container');


    let lightweightChart = null;
    let mainPriceSeries = null;
    let cpiChart = null;

    let allItems = [];
    let previousPrices = new Map();
    let selectedItemIdentifier = null;
    let cpiDataStore = [];
    let currentItemNominalData = [];
    let priceSortState = 0;
    let changeSortState = 0;
    let operationsSortState = 0;
    let pollingIntervalId = null;


    const API_BASE_URL = '/api';
    const POLLING_INTERVAL = 3000;

    const defaultChartJsOptions = {
        maintainAspectRatio: false, responsive: true,
        animation: { duration: 800, easing: 'easeInOutQuad', },
        plugins: { legend: { display: false }, tooltip: { mode: 'index', intersect: false, backgroundColor: 'rgba(31, 41, 55, 0.9)', titleColor: '#f3f4f6', bodyColor: '#d1d5db', borderColor: '#4b5563', borderWidth: 1, padding: 10, cornerRadius: 4, callbacks: { title: function(tooltipItems) { if (tooltipItems.length > 0) { const item = tooltipItems[0]; try { const date = new Date(item.parsed.x); return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }); } catch (e) { console.error("Error formatting tooltip title with native Date:", e); return ''; } } return ''; } } } },
        scales: { x: { grid: { color: 'rgba(75, 85, 99, 0.2)' }, ticks: { color: '#9ca3af', maxRotation: 0, autoSkip: true, } }, y: { grid: { color: 'rgba(75, 85, 99, 0.2)' }, ticks: { color: '#9ca3af' } } },
        layout: { padding: 5 },
    };

    const lightweightChartOptions = {
        layout: { background: { color: 'transparent' }, textColor: '#D1D5DB', },
        grid: { vertLines: { color: 'rgba(75, 85, 99, 0.3)' }, horzLines: { color: 'rgba(75, 85, 99, 0.3)' }, },
        crosshair: { mode: LightweightCharts.CrosshairMode.Normal, },
        rightPriceScale: { borderColor: 'rgba(192, 192, 192, 0.3)', mode: LightweightCharts.PriceScaleMode.Normal, autoScale: true },
        timeScale: { borderColor: 'rgba(192, 192, 192, 0.3)', timeVisible: true, secondsVisible: false, },
        handleScroll: true, handleScale: true,
    };
    const mainPriceSeriesOptions = {
        topLineColor: 'rgba(52, 211, 153, 1)',
        topFillColor1: 'rgba(52, 211, 153, 0.2)',
        topFillColor2: 'rgba(52, 211, 153, 0.02)',
        bottomLineColor: 'rgba(248, 113, 113, 1)',
        bottomFillColor1: 'rgba(248, 113, 113, 0.02)',
        bottomFillColor2: 'rgba(248, 113, 113, 0.2)',
        lineWidth: 2,
        priceFormat: { type: 'price', precision: 2, minMove: 0.01, },
    };


    function formatCurrency(value, defaultVal = '-') {
        if (value === null || value === undefined || isNaN(value)) {
            return defaultVal;
        }
        return '$' + value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    function calculateVolatility(prices) {
        const n = prices.length;
        if (n < 2) return 0;
        const mean = prices.reduce((a, b) => a + b, 0) / n;
        if (Math.abs(mean) < 1e-9) return 0;
        const variance = prices.reduce((sq, val) => sq + Math.pow(val - mean, 2), 0) / n;
        const stdDev = Math.sqrt(variance);
        return stdDev / mean;
    }

    function calculateMaxDrawdown(prices) {
        if (!prices || prices.length < 2) return 0;
        let maxDrawdown = 0;
        let peak = prices[0];
        for (let i = 1; i < prices.length; i++) {
            if (prices[i] > peak) {
                peak = prices[i];
            } else if (peak > 0) {
                const drawdown = (peak - prices[i]) / peak;
                if (drawdown > maxDrawdown) {
                    maxDrawdown = drawdown;
                }
            }
        }
        return -maxDrawdown;
    }

    function calculateInceptionReturn(prices) {
        if (!prices || prices.length < 2) return null;
        const firstPrice = prices[0];
        const lastPrice = prices[prices.length - 1];
        if (firstPrice === undefined || firstPrice === 0 || lastPrice === undefined) return null;
        return (lastPrice - firstPrice) / firstPrice;
    }


    async function fetchData(endpoint) {
        try {
            const response = await fetch(`${API_BASE_URL}${endpoint}`);
            if (!response.ok) { let errorBody = ''; try { errorBody = await response.text(); } catch (e) {} console.error(`HTTP error! Status: ${response.status} on ${endpoint}. Body: ${errorBody}`); throw new Error(`HTTP error! Status: ${response.status} on ${endpoint}`); }
            if (response.status === 204) { return null; }
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.indexOf("application/json") !== -1) { return await response.json(); }
            else { console.warn(`Received non-JSON response from ${endpoint}. Content-Type: ${contentType}`); return await response.text(); }
        } catch (error) { console.error(`Failed to fetch ${endpoint}:`, error); throw error; }
     }

     function applyFlashAnimation(element, direction) {
        if (!element) return;
        const animationClass = direction === 'up' ? 'flash-up' : 'flash-down';
        if (element.dataset.isAnimating === 'true') return;
        element.dataset.isAnimating = 'true';
        const flashElement = document.createElement('div');
        flashElement.className = `update-flash ${animationClass}`;
        element.style.position = 'relative';
        element.style.zIndex = '0';
        element.prepend(flashElement);
        setTimeout(() => {
            flashElement.remove();
            delete element.dataset.isAnimating;
        }, 700);
    }

    function renderItemList(itemsToRender, priceChangesMap = null, hottestItemId = null) {
        if (!itemListElement) { console.error("Item list element not found!"); return; }
        const fragment = document.createDocumentFragment();
        let hasContent = false;

        if (itemsToRender && itemsToRender.length > 0) {
            hasContent = true;
            itemsToRender.forEach(item => {
                const itemIdentifier = item?.identifier;
                const itemName = item?.name || 'Unknown Item';
                const itemPrice = item?.price;
                const itemChangePercent = item?.changePercent;

                if (itemIdentifier === null || itemIdentifier === undefined) { console.warn('Skipping item in list due to missing identifier:', item); return; }
                const itemIdentifierStr = String(itemIdentifier);
                const selectedIdentifierStr = selectedItemIdentifier !== null ? String(selectedItemIdentifier) : null;

                const li = document.createElement('li');
                li.className = `flex items-center justify-between p-2 rounded-md cursor-pointer hover:bg-gray-700 transition duration-150 ease-in-out ${itemIdentifierStr === selectedIdentifierStr ? 'item-selected' : ''}`;
                li.dataset.identifier = itemIdentifierStr;

                const priceDiv = `<div class="text-sm font-semibold text-white">${formatCurrency(itemPrice)}</div>`;
                const changeDiv = `<div class="text-xs ${getTrendClass(itemChangePercent)}">${formatTrend(itemChangePercent, false)}</div>`;

                const fireIconHTML = itemIdentifierStr === hottestItemId ? '<img src="/api/icons/fire.png" alt="Hot" class="fire-icon">' : '';

                const iconContainerHTML = `
                    <div class="flex items-center mr-2 min-w-0" data-role="icon-container">
                        <img src="${API_BASE_URL}/icons/${itemIdentifierStr}.png" alt="${itemName}" class="w-6 h-6 rounded shrink-0" onerror="this.src='https://placehold.co/24x24/374151/9ca3af?text=?'; this.onerror=null;">
                        ${fireIconHTML}
                        <span class="truncate text-sm font-medium text-gray-100 inline-flex items-center ml-1">
                            ${itemName}
                        </span>
                    </div>`;

                li.innerHTML = `
                    ${iconContainerHTML}
                    <div data-role="price-change-container" class="text-right shrink-0 ml-2 relative">
                        ${priceDiv}
                        ${changeDiv}
                    </div>`;

                if (priceChangesMap) {
                    const changeDirection = priceChangesMap.get(itemIdentifierStr);
                    if (changeDirection === 'up' || changeDirection === 'down') {
                        const priceChangeContainer = li.querySelector('[data-role="price-change-container"]');
                        if(priceChangeContainer) applyFlashAnimation(priceChangeContainer, changeDirection);
                    }
                }

                li.addEventListener('click', () => handleItemSelection(itemIdentifierStr));
                fragment.appendChild(li);
            });
        }

        itemListElement.innerHTML = '';
        if (hasContent) {
             itemListElement.appendChild(fragment);
        } else {
             const searchTerm = searchInputElement.value;
             itemListElement.innerHTML = `<li class="p-2 text-gray-400">${searchTerm ? 'No items match search.' : 'No items found.'}</li>`;
        }
    }


    async function handleItemSelection(identifier) {
        selectedItemIdentifier = identifier;
        document.querySelectorAll('#item-list li').forEach(li => { li.classList.toggle('item-selected', li.dataset.identifier === identifier); });
        const selectedItem = allItems.find(item => String(item?.identifier) === identifier);

        currentItemNominalData = [];
        let calculatedRank = null;
        let calculatedATH = null;
        let calculatedATL = null;
        let calculatedVolatility = null;
        let calculatedMaxDrawdown = null;
        let calculatedInceptionReturn = null;

        if (chartSpinner) chartSpinner.classList.remove('hidden');
        clearLightweightChart();

        if (selectedItem) {
            selectedItemIconElement.src = `${API_BASE_URL}/icons/${selectedItem.identifier}.png`;
            selectedItemIconElement.onerror = () => { selectedItemIconElement.src='https://placehold.co/32x32/374151/9ca3af?text=?'; selectedItemIconElement.onerror=null; };
            selectedItemNameElement.textContent = selectedItem.name || 'Unknown Item';
            selectedItemDescElement.textContent = `Loading price evolution for ${selectedItem.name}...`;

            const sortedByPrice = [...allItems]
                .filter(item => item && item.price !== null && item.price !== undefined && !isNaN(item.price))
                .sort((a, b) => b.price - a.price);
            const rankIndex = sortedByPrice.findIndex(item => String(item?.identifier) === identifier);
            calculatedRank = rankIndex !== -1 ? rankIndex + 1 : null;

             updateSelectedItemDetails(selectedItem, calculatedRank, null, null, null, null, null);

            try {
                const endpoint = `/charts/item/${identifier}`;
                const itemChartDataRaw = await fetchData(endpoint);

                if (Array.isArray(itemChartDataRaw) && itemChartDataRaw.length > 0) {
                    currentItemNominalData = itemChartDataRaw
                        .filter(dp => dp.time !== null && dp.time !== undefined && !isNaN(dp.time) && dp.value !== null && dp.value !== undefined && !isNaN(dp.value))
                        .sort((a, b) => a.time - b.time);

                    const prices = currentItemNominalData.map(dp => dp.value);
                    if (prices.length > 0) {
                        calculatedATH = Math.max(...prices);
                        calculatedATL = Math.min(...prices);
                        calculatedVolatility = calculateVolatility(prices);
                        calculatedMaxDrawdown = calculateMaxDrawdown(prices);
                        calculatedInceptionReturn = calculateInceptionReturn(prices);
                    }

                    updateSelectedItemDetails(selectedItem, calculatedRank, calculatedATH, calculatedATL, calculatedVolatility, calculatedMaxDrawdown, calculatedInceptionReturn);
                    displayCurrentItemChart();

                } else {
                    updateSelectedItemDetails(selectedItem, calculatedRank, null, null, null, null, null);
                    selectedItemDescElement.textContent = `No price history found for ${selectedItem.name}.`;
                    clearLightweightChart();
                }
            } catch (error) {
                console.error(`Failed to fetch or update chart for ${identifier}:`, error);
                updateSelectedItemDetails(selectedItem, calculatedRank, null, null, null, null, null);
                selectedItemDescElement.textContent = `Error loading price history for ${selectedItem.name}.`;
                clearLightweightChart();
            } finally {
                 if (chartSpinner) chartSpinner.classList.add('hidden');
            }
        } else {
            updateSelectedItemDetails(null, null, null, null, null, null, null);
            clearLightweightChart();
            if (chartSpinner) chartSpinner.classList.add('hidden');
        }
    }

    function updateSelectedItemDetails(item, rank, ath, atl, volatility, maxDrawdown, inceptionReturn) {
        if (!currentPriceElement || !marketRankElement || !allTimeHighElement || !allTimeLowElement || !sellPriceDisplayElement || !buyPriceDisplayElement || !change1hElement || !inceptionReturnElement || !volatilityElement || !maxDrawdownElement) {
            console.error("One or more item detail elements are missing!");
            return;
        }

        if (item) {
            const itemIdentifierStr = String(item?.identifier);
            const itemName = item?.name || 'Unknown Item';
            const itemChangePercent = item?.changePercent;
            const itemPrice = item?.price;

            selectedItemIconElement.src = `${API_BASE_URL}/icons/${itemIdentifierStr}.png`;
            selectedItemIconElement.onerror = () => { selectedItemIconElement.src='https://placehold.co/32x32/374151/9ca3af?text=?'; selectedItemIconElement.onerror=null; };
            selectedItemNameElement.textContent = itemName;

             if (!selectedItemDescElement.textContent?.startsWith('Loading')) {
                 selectedItemDescElement.textContent = item?.description || `Evolution of ${itemName}.`;
             } else if (ath !== null || atl !== null) {
                 selectedItemDescElement.textContent = item?.description || `Evolution of ${itemName}.`;
             }


            currentPriceElement.textContent = formatCurrency(itemPrice);
            marketRankElement.textContent = rank ? `#${rank}` : '-';
            allTimeHighElement.textContent = formatCurrency(ath, '-');
            allTimeLowElement.textContent = formatCurrency(atl, '-');
            change1hElement.textContent = formatTrend(itemChangePercent, true, '-');
            change1hElement.className = `text-lg font-semibold ${getTrendClass(itemChangePercent)}`;

            inceptionReturnElement.textContent = (inceptionReturn !== null && !isNaN(inceptionReturn))
                ? formatTrend(inceptionReturn * 100, true, '-')
                : '-';
            inceptionReturnElement.className = `text-lg font-semibold ${getTrendClass(inceptionReturn * 100)}`;


            volatilityElement.textContent = (volatility !== null && !isNaN(volatility))
                ? `${(volatility * 100).toFixed(1)}%`
                : '-';
             volatilityElement.className = 'text-base font-medium text-blue-300';

            maxDrawdownElement.textContent = (maxDrawdown !== null && !isNaN(maxDrawdown))
                ? formatTrend(maxDrawdown * 100, true, '-')
                : '-';
            maxDrawdownElement.className = `text-base font-medium ${getTrendClass(maxDrawdown * 100)}`;


            sellPriceDisplayElement.textContent = formatCurrency(item?.sell, '-');
            buyPriceDisplayElement.textContent = formatCurrency(item?.buy, '-');

        } else {
            selectedItemIconElement.src = 'https://placehold.co/32x32/374151/9ca3af?text=?';
            selectedItemNameElement.textContent = 'Select an Item';
            selectedItemDescElement.textContent = 'Select an item to see its price evolution.';
            currentPriceElement.textContent = '-';
            marketRankElement.textContent = '-';
            change1hElement.textContent = '-'; change1hElement.className = 'text-lg font-semibold';
            inceptionReturnElement.textContent = '-'; inceptionReturnElement.className = 'text-lg font-semibold';
            allTimeHighElement.textContent = '-'; allTimeLowElement.textContent = '-';
            volatilityElement.textContent = '-'; volatilityElement.className = 'text-base font-medium text-blue-300';
            maxDrawdownElement.textContent = '-'; maxDrawdownElement.className = 'text-base font-medium text-orange-400';
            sellPriceDisplayElement.textContent = '-'; buyPriceDisplayElement.textContent = '-';
        }
    }

    function formatTrend(changePercent, showSign = true, defaultVal = '-') {
        const trendNum = parseFloat(changePercent);
        if (changePercent === null || changePercent === undefined || isNaN(trendNum)) { return defaultVal; }
        const sign = showSign && trendNum > 0 ? '+' : '';
        if (!showSign && trendNum < 0) {
             return `${trendNum.toFixed(2)}%`;
        }
        return `${sign}${trendNum.toFixed(2)}%`;
     }

    function getTrendClass(changePercent) {
        const trendNum = parseFloat(changePercent);
        if (changePercent === null || changePercent === undefined || isNaN(trendNum) || Math.abs(trendNum) < 0.001) { return 'trend-neutral'; }
        else if (trendNum > 0) { return 'trend-up'; }
        else { return 'trend-down'; }
     }

    function sortAndRenderItems(priceChangesMap = null) {
         if (!Array.isArray(allItems)) {
             renderItemList([], null, null);
             return;
         }
         const searchTerm = searchInputElement.value.toLowerCase().trim();
         let itemsToDisplay = [...allItems];

         if (searchTerm) {
             itemsToDisplay = itemsToDisplay.filter(item => {
                 const itemName = item?.name ? item.name.toLowerCase() : '';
                 const itemIdentifier = item?.identifier ? String(item.identifier).toLowerCase() : '';
                 return itemName.includes(searchTerm) || itemIdentifier.includes(searchTerm);
             });
         }

         let sortCriteria = 'name';
         if (priceSortState === 1) sortCriteria = 'price_desc';
         else if (priceSortState === 2) sortCriteria = 'price_asc';
         else if (changeSortState === 1) sortCriteria = 'change_desc';
         else if (changeSortState === 2) sortCriteria = 'change_asc';
         else if (operationsSortState === 1) sortCriteria = 'operations_desc';
         else if (operationsSortState === 2) sortCriteria = 'operations_asc';

         let hottestItemId = null;
         if (allItems.length > 0) {
             let maxOps = -1;
             allItems.forEach(item => {
                 const ops = item.operations ?? 0;
                 if (ops > maxOps) {
                     maxOps = ops;
                     hottestItemId = String(item.identifier);
                 }
             });
              if (maxOps <= 0) hottestItemId = null;
         }


         itemsToDisplay.sort((a, b) => {
             const aValPrice = a?.price ?? 0;
             const bValPrice = b?.price ?? 0;
             const aValChange = a?.changePercent ?? 0;
             const bValChange = b?.changePercent ?? 0;
             const aValOps = a?.operations ?? 0;
             const bValOps = b?.operations ?? 0;
             const aName = a?.name ?? '';
             const bName = b?.name ?? '';

             switch (sortCriteria) {
                 case 'price_desc': return bValPrice - aValPrice;
                 case 'price_asc': return aValPrice - bValPrice;
                 case 'change_desc': return bValChange - aValChange;
                 case 'change_asc': return aValChange - bValChange;
                 case 'operations_desc': return bValOps - aValOps;
                 case 'operations_asc': return aValOps - bValOps;
                 case 'name': default: return aName.localeCompare(bName);
             }
         });

         renderItemList(itemsToDisplay, priceChangesMap, hottestItemId);
    }


    function handleSortClick(event) {
         const clickedButton = event.target.closest('.sort-button');
         if (!clickedButton || !sortControlsContainer.contains(clickedButton)) {
             return;
         }

         const sortCategory = clickedButton.dataset.sortCategory;

         if (sortCategory === 'price') {
             priceSortState = (priceSortState + 1) % 3;
             changeSortState = 0;
             operationsSortState = 0;
         } else if (sortCategory === 'change') {
             changeSortState = (changeSortState + 1) % 3;
             priceSortState = 0;
             operationsSortState = 0;
         } else if (sortCategory === 'operations') {
             operationsSortState = (operationsSortState + 1) % 3;
             priceSortState = 0;
             changeSortState = 0;
         } else {
             return;
         }

         sortControlsContainer.querySelectorAll('.sort-button').forEach(btn => {
             const category = btn.dataset.sortCategory;
             const indicator = btn.querySelector('.sort-indicator');
             let state = 0;
             if (category === 'price') state = priceSortState;
             else if (category === 'change') state = changeSortState;
             else if (category === 'operations') state = operationsSortState;


             btn.classList.toggle('active-sort', state > 0);
             if (indicator) {
                 if (state === 1) {
                     indicator.textContent = '▼';
                     indicator.style.display = 'inline-block';
                 } else if (state === 2) {
                     indicator.textContent = '▲';
                     indicator.style.display = 'inline-block';
                 } else {
                     indicator.textContent = '';
                     indicator.style.display = 'none';
                 }
             }
         });

         sortAndRenderItems();
    }


    function findCpiForTimestamp(timestampSec, sortedCpiData) {
        if (!sortedCpiData || sortedCpiData.length === 0) return null;
        let bestMatchIndex = -1;
        for (let i = 0; i < sortedCpiData.length; i++) { if (sortedCpiData[i].time <= timestampSec) { bestMatchIndex = i; } else { break; } }
        if (bestMatchIndex !== -1) { return sortedCpiData[bestMatchIndex].value; }
        return null;
    }

    function adjustPricesForInflation(nominalPrices, cpiData) {
        if (!cpiData || cpiData.length === 0) { return nominalPrices; }
        return nominalPrices.map(pricePoint => {
            const cpiValue = findCpiForTimestamp(pricePoint.time, cpiData);
            let adjustedValue = pricePoint.value;
            if (cpiValue !== null && cpiValue > 0) { adjustedValue = pricePoint.value / (cpiValue / 100.0); }
            return { time: pricePoint.time, value: adjustedValue };
        }).filter(dp => dp.value !== undefined && !isNaN(dp.value));
     }

     function displayCurrentItemChart() {
         if (!currentItemNominalData || currentItemNominalData.length === 0) {
             clearLightweightChart();
             return;
         }
         const adjust = inflationCheckbox?.checked ?? false;
         let dataToDisplay = adjust ? adjustPricesForInflation(currentItemNominalData, cpiDataStore) : currentItemNominalData;
         updateLightweightChart(dataToDisplay);
     }

     function handleInflationToggle() {
         displayCurrentItemChart();
     }

     function handleLogScaleToggle() {
        if (!lightweightChart) return;
        const useLogScale = logScaleCheckbox?.checked ?? false;
        lightweightChart.applyOptions({
             rightPriceScale: {
                 mode: useLogScale ? LightweightCharts.PriceScaleMode.Logarithmic : LightweightCharts.PriceScaleMode.Normal,
             },
        });
     }

     function handleResetZoom() {
         if (lightweightChart) {
             lightweightChart.timeScale().fitContent();
             lightweightChart.applyOptions({
                 rightPriceScale: { autoScale: true },
             });
             if (logScaleCheckbox?.checked) {
                 logScaleCheckbox.checked = false;
                 lightweightChart.applyOptions({
                     rightPriceScale: { mode: LightweightCharts.PriceScaleMode.Normal },
                 });
             }
         }
     }


    function initializeLightweightChart() {
        if (!itemPriceChartContainer) { console.error("Lightweight Chart container not found!"); return; }
        if (typeof LightweightCharts === 'undefined' || !LightweightCharts) { console.error("LightweightCharts library is not loaded!"); itemPriceChartContainer.innerHTML = '<div class="flex items-center justify-center h-full text-red-500 p-4">Charting library failed to load.</div>'; return; }
        try {
            lightweightChart = LightweightCharts.createChart(itemPriceChartContainer, lightweightChartOptions);
            if (lightweightChart && typeof lightweightChart.addBaselineSeries === 'function') {
                mainPriceSeries = lightweightChart.addBaselineSeries(mainPriceSeriesOptions);
            } else {
                 console.error('lightweightChart.addBaselineSeries is not available or not a function.');
                 throw new TypeError('lightweightChart.addBaselineSeries is not available or not a function.');
            }
            const resizeObserver = new ResizeObserver(entries => { if (!entries || entries.length === 0) { return; } const rect = entries[0].contentRect; if (rect.width > 0 && rect.height > 0 && lightweightChart) { lightweightChart.resize(rect.width, rect.height); } });
            resizeObserver.observe(itemPriceChartContainer);
        } catch (error) { console.error("Failed to initialize Lightweight Chart:", error); itemPriceChartContainer.innerHTML = '<div class="flex items-center justify-center h-full text-red-500 p-4">Error initializing chart. Check console.</div>'; mainPriceSeries = null; lightweightChart = null; }
    }


    function updateLightweightChart(timeSeriesData) {
        if (!mainPriceSeries) { console.error("Main price series not initialized. Cannot update chart."); return; }
        if (!Array.isArray(timeSeriesData)) { console.error("Invalid timeSeriesData for Lightweight Chart:", timeSeriesData); clearLightweightChart(); return; }

        const formattedData = timeSeriesData
            .filter(dp => dp.value !== undefined && dp.time !== null && dp.time !== undefined && !isNaN(dp.time))
            .sort((a, b) => a.time - b.time);

        if (formattedData.length === 0) {
             clearLightweightChart();
             return;
        }

        try {
            const firstValue = formattedData[0].value;
            mainPriceSeries.applyOptions({
                 baseValue: { type: 'price', price: firstValue }
            });

            mainPriceSeries.setData(formattedData);

            if (lightweightChart) {
                 lightweightChart.timeScale().fitContent();
                 lightweightChart.priceScale().applyOptions({ autoScale: true });
            }
        }
        catch (error) { console.error("Error setting data on Lightweight Chart:", error); }
     }

    function clearLightweightChart() {
        if (mainPriceSeries) { try { mainPriceSeries.setData([]); } catch (error) { console.error("Error clearing data on Lightweight Chart:", error); } }
     }

    function createCpiGradient(context) {
        const chart = context.chart; const {ctx, chartArea} = chart;
        if (!chartArea) { return 'rgba(167, 139, 250, 0.2)'; }
        const gradient = ctx.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
        gradient.addColorStop(0, 'rgba(167, 139, 250, 0.6)'); gradient.addColorStop(0.8, 'rgba(167, 139, 250, 0.1)'); gradient.addColorStop(1, 'rgba(167, 139, 250, 0)');
        return gradient;
     }


    function updateCpiChart(timeSeriesData) {
        if (!cpiChartCanvas) { console.error("CPI chart canvas not found!"); return; }
        if (!Array.isArray(timeSeriesData)) { console.error("Invalid timeSeriesData for CPI Chart:", timeSeriesData); return; }
         const formattedData = timeSeriesData
             .map(dp => ({ x: new Date(dp.time * 1000), y: dp.value !== null && dp.value !== undefined ? parseFloat(dp.value) : null }))
             .filter(dp => !isNaN(dp.x.getTime()) && dp.y !== null);
         formattedData.sort((a, b) => a.x - b.x);

        const chartData = {
            datasets: [{
                label: 'CPI', data: formattedData, borderColor: '#a78bfa', backgroundColor: createCpiGradient,
                fill: true, borderWidth: 1.5, pointRadius: 0, tension: 0
            }]
        };

        if (cpiChart) { cpiChart.data = chartData; cpiChart.update(); }
        else {
            const config = { type: 'line', data: chartData,
                options: {
                    ...defaultChartJsOptions,
                    interaction: { mode: 'index', intersect: false },
                    plugins: {
                         ...defaultChartJsOptions.plugins,
                         tooltip: {
                              ...defaultChartJsOptions.plugins.tooltip,
                              callbacks: {
                                   ...defaultChartJsOptions.plugins.tooltip.callbacks,
                                   label: function(context) {
                                        let label = context.dataset.label || '';
                                        if (label) { label += ': '; }
                                        if (context.parsed.y !== null) { label += context.parsed.y.toFixed(1); }
                                        return label;
                                   }
                              }
                         }
                    },
                    scales: {
                        x: {
                            ...defaultChartJsOptions.scales.x, type: 'time',
                            time: { unit: 'day',
                                displayFormats: { millisecond: 'HH:mm:ss.SSS', second: 'HH:mm:ss', minute: 'HH:mm', hour: 'HH:mm', day: 'MMM dd', week: 'MMM dd', month: 'MMM<y_bin_46>', quarter: 'qqq<y_bin_46>', year: 'yyyy' }
                            },
                            ticks: { ...defaultChartJsOptions.scales.x.ticks, maxTicksLimit: 6 }
                        },
                        y: { ...defaultChartJsOptions.scales.y, beginAtZero: false, ticks: { ...defaultChartJsOptions.scales.y.ticks, callback: (v) => v.toFixed(1) } }
                    }
                 }
            };
            if (window.cpiChartInstance) { try { window.cpiChartInstance.destroy(); } catch(e) { console.error("Error destroying previous CPI chart", e); } }
            cpiChart = new Chart(cpiChartCanvas, config); window.cpiChartInstance = cpiChart;
        }
     }

     function updateD3Treemap(allItemsData) {
        if (!marketCapTreemapContainer) { return; }
        if (!Array.isArray(allItemsData)) { return; }

        const treemapData = allItemsData
            .map(item => ({
                name: item.name || 'Unknown',
                value: item.price ?? 0,
                itemData: item
            }))
            .filter(item => item.value > 0)
            .sort((a, b) => b.value - a.value);


        const containerWidth = marketCapTreemapContainer.clientWidth;
        const containerHeight = marketCapTreemapContainer.clientHeight;

        d3.select(marketCapTreemapContainer).select('svg').remove();

        if (treemapData.length === 0 || containerWidth <= 0 || containerHeight <= 0) {
            return;
        }

        const root = d3.hierarchy({ name: "root", children: treemapData })
            .sum(d => d.value)
            .sort((a, b) => b.value - a.value);

        const treemapLayout = d3.treemap()
            .size([containerWidth, containerHeight])
            .padding(1);

        treemapLayout(root);

        const changes = allItemsData.map(item => item.changePercent ?? 0).filter(c => c !== 0);
        const maxPositiveChange = Math.max(0.01, ...changes.filter(c => c > 0));
        const minNegativeChange = Math.min(-0.01, ...changes.filter(c => c < 0));

        const getColorForChange = (change) => {
            const changeNum = parseFloat(change);
            const solidGreen = '5, 150, 105';
            const solidRed = '239, 68, 68';
            const neutralGray = '75, 85, 99';
            const minOpacity = 0.15;
            const maxAbsChange = Math.max(0.01, Math.abs(maxPositiveChange), Math.abs(minNegativeChange));

            if (change === null || change === undefined || isNaN(changeNum) || Math.abs(changeNum) < 0.001) {
                return `rgba(${neutralGray}, ${minOpacity})`;
            }

            const absChangeRatio = Math.abs(changeNum) / maxAbsChange;
            const opacity = Math.min(1, Math.max(minOpacity, absChangeRatio));

            if (changeNum > 0) {
                return `rgba(${solidGreen}, ${opacity})`;
            } else {
                return `rgba(${solidRed}, ${opacity})`;
            }
        };


        const svg = d3.select(marketCapTreemapContainer)
            .append("svg")
            .attr("viewBox", `0 0 ${containerWidth} ${containerHeight}`)
            .attr("preserveAspectRatio", "xMidYMid meet");

        const nodes = svg.selectAll("g")
            .data(root.leaves())
            .enter()
            .append("g")
            .attr("transform", d => `translate(${d.x0},${d.y0})`);

        nodes.append("rect")
            .attr("width", d => d.x1 - d.x0)
            .attr("height", d => d.y1 - d.y0)
            .attr("fill", d => getColorForChange(d.data.itemData.changePercent))
            .attr("stroke", "rgba(255, 255, 255, 0.3)")
            .attr("stroke-width", 0.5);

        nodes.each(function(d) {
                const nodeGroup = d3.select(this);
                const blockWidth = d.x1 - d.x0;
                const blockHeight = d.y1 - d.y0;
                const minDimForIcon = 20;
                const minIconSize = 12;
                const maxIconSize = 36;
                const textHeight = 12;
                const iconPadding = 4;

                if (blockWidth > minDimForIcon && blockHeight > (minDimForIcon + textHeight + iconPadding)) {
                    const availableWidth = blockWidth - (2 * iconPadding);
                    const availableHeightForIcon = blockHeight - textHeight - iconPadding;
                    const smallerDim = Math.min(availableWidth, availableHeightForIcon);
                    let targetIconSize = Math.max(minIconSize, Math.min(maxIconSize, smallerDim * 0.7));

                    let targetFontSize = Math.max(8, Math.min(11, availableHeightForIcon * 0.2, availableWidth * 0.15));

                    if (targetIconSize + targetFontSize + iconPadding > blockHeight) {
                        const scaleDown = blockHeight / (targetIconSize + targetFontSize + iconPadding);
                        targetIconSize *= (scaleDown * 0.9);
                        targetFontSize *= (scaleDown* 0.9);
                        targetIconSize = Math.max(minIconSize, targetIconSize);
                        targetFontSize = Math.max(8, targetFontSize);
                    }

                     if (blockWidth < targetIconSize || blockHeight < (targetIconSize + targetFontSize + iconPadding)) {
                         return;
                     }

                    nodeGroup.append("image")
                        .attr('xlink:href', `${API_BASE_URL}/icons/${d.data.itemData.identifier}.png`)
                        .attr('x', (blockWidth - targetIconSize) / 2)
                        .attr('y', (blockHeight - targetIconSize - targetFontSize - iconPadding) / 2)
                        .attr('width', targetIconSize)
                        .attr('height', targetIconSize);

                    nodeGroup.append("text")
                        .attr("class", "treemap-label")
                        .attr("x", blockWidth / 2)
                        .attr("y", (blockHeight + targetIconSize - targetFontSize) / 2 + iconPadding)
                        .attr("font-size", `${targetFontSize}px`)
                        .attr("fill", "#ffffff")
                        .attr("text-anchor", "middle")
                        .attr("dominant-baseline", "middle")
                        .text(formatTrend(d.data.itemData.changePercent, true, ''));
                }
             });


        const tooltip = d3.select(treemapTooltip);

        nodes.on("mouseover", (event, d) => {
            tooltip.style("display", "block");
        })
        .on("mousemove", (event, d) => {
            const itemData = d.data.itemData;
            const price = itemData?.price ?? 0;
            const change = itemData?.changePercent;
            const name = itemData?.name ?? 'Unknown';

            tooltip.html(`
                <div class="font-semibold">Nascraft</div>
                <div>Price: ${formatCurrency(price)}</div>
                <div>Change: <span class="${getTrendClass(change)}">${formatTrend(change, true, '-')}</span></div>
            `)
            .style("left", (event.pageX + 10) + "px")
            .style("top", (event.pageY - 10) + "px");
        })
        .on("mouseout", () => {
            tooltip.style("display", "none");
        });

    }

    async function pollData() {
        try {
            const newItemsData = await fetchData('/items');
            if (!Array.isArray(newItemsData)) {
                return;
            }

            const priceChangesMap = new Map();
            newItemsData.forEach(newItem => {
                const identifier = String(newItem.identifier);
                const oldPrice = previousPrices.get(identifier);
                const newPrice = newItem.price;
                let direction = 'none';
                if (oldPrice !== undefined && newPrice !== undefined && newPrice !== oldPrice) {
                    direction = newPrice > oldPrice ? 'up' : 'down';
                }
                priceChangesMap.set(identifier, direction);
            });

            allItems = newItemsData;

            sortAndRenderItems(priceChangesMap);
            updateD3Treemap(allItems);

            if (selectedItemIdentifier) {
                const selectedItem = allItems.find(item => String(item.identifier) === selectedItemIdentifier);
                const changeDirection = priceChangesMap.get(selectedItemIdentifier);
                if (selectedItem) {

                    const newPriceText = formatCurrency(selectedItem.price, '-');
                    const newChangeText = formatTrend(selectedItem.changePercent, true, '-');
                    const newChangeClass = getTrendClass(selectedItem.changePercent);

                    if (currentPriceElement.textContent !== newPriceText) {
                        currentPriceElement.textContent = newPriceText;
                         if (changeDirection && changeDirection !== 'none' && currentPriceElement.parentNode) {
                             applyFlashAnimation(currentPriceElement.parentNode, changeDirection);
                         }
                    }

                    if (change1hElement.textContent !== newChangeText || !change1hElement.classList.contains(newChangeClass)) {
                         change1hElement.textContent = newChangeText;
                         change1hElement.className = `text-lg font-semibold ${newChangeClass}`;
                          if (changeDirection && changeDirection !== 'none' && change1hElement.parentNode) {
                              applyFlashAnimation(change1hElement.parentNode, changeDirection);
                          }
                    }

                    const sortedByPrice = [...allItems]
                        .filter(item => item && item.price !== null && item.price !== undefined && !isNaN(item.price))
                        .sort((a, b) => b.price - a.price);
                    const rankIndex = sortedByPrice.findIndex(item => String(item?.identifier) === selectedItemIdentifier);
                    const calculatedRank = rankIndex !== -1 ? rankIndex + 1 : null;
                    marketRankElement.textContent = calculatedRank ? `#${calculatedRank}` : '-';
                }
            }

            previousPrices = new Map(allItems.map(item => [String(item.identifier), item.price]));

        } catch (error) {
            console.error("Polling failed:", error);
        }
    }

    async function initialize() {
        const requiredElementIds = [
            'item-list', 'search-input', 'selected-item-icon', 'selected-item-name',
            'selected-item-description', 'current-price', 'sell-price-display',
            'buy-price-display', 'item-price-chart-container',
            'cpi-chart', 'treemap-container',
            'inflation-adjust-checkbox', 'log-scale-checkbox', 'market-rank',
            'all-time-high', 'all-time-low', 'reset-zoom-button',
            'chart-loading-spinner', 'change-1h', 'inception-return',
            'sort-controls', 'treemap-tooltip',
            'volatility', 'max-drawdown', 'top-portfolios-container'
        ];
        const missingElement = requiredElementIds.find(id => !document.getElementById(id));
        if (missingElement) {
             console.error(`Critical UI element missing: #${missingElement}. Aborting initialization.`);
             document.body.innerHTML = '<div class="p-4 text-red-500">Error: UI elements missing. Cannot initialize application.</div>';
             return;
         }
        initializeLightweightChart();
        await checkAuth().catch(e => console.error("Failed auth check", e));

        try {
            const [itemsData, fetchedCpiData, popularItemData, topPortfoliosData] = await Promise.all([
                fetchData('/items').catch(e => { console.error("Failed to load items", e); return []; }),
                fetchData('/charts/cpi').catch(e => { console.error("Failed to load CPI data", e); return []; }),
                fetchData('/popular-item').catch(e => { console.error("Failed to load popular item", e); return null; }),
                fetchData('/top-portfolios').catch(e => { console.error("Failed to load top portfolios", e); return []; })
            ]);

            allItems = Array.isArray(itemsData) ? itemsData : [];
            previousPrices = new Map(allItems.map(item => [String(item.identifier), item.price]));
            sortAndRenderItems();


            if (allItems.length > 0) { updateD3Treemap(allItems); }
            else { console.warn("No item data available for Treemap."); }

            if (Array.isArray(fetchedCpiData) && fetchedCpiData.length > 0) {
                cpiDataStore = fetchedCpiData
                    .filter(dp => dp.time !== null && dp.time !== undefined && !isNaN(dp.time) && dp.value !== null && dp.value !== undefined && !isNaN(dp.value))
                    .sort((a, b) => a.time - b.time);
                updateCpiChart(fetchedCpiData);
            } else {
                cpiDataStore = []; updateCpiChart([]);
            }

             renderTopPortfolios(topPortfoliosData);

             let initialItemIdentifier = null;
             if (popularItemData && popularItemData?.identifier) { initialItemIdentifier = String(popularItemData.identifier); }
             else if (allItems.length > 0 && allItems[0]?.identifier) { initialItemIdentifier = String(allItems[0].identifier); }

             if (initialItemIdentifier && lightweightChart) {
                 await handleItemSelection(initialItemIdentifier);
             } else if (!lightweightChart) {
                  updateSelectedItemDetails(null, null, null, null, null, null, null);
                  selectedItemDescElement.textContent = "Chart failed to load. Select an item.";
             } else {
                 updateSelectedItemDetails(null, null, null, null, null, null, null);
                 clearLightweightChart();
                 selectedItemDescElement.textContent = "No items loaded or first item lacks identifier.";
             }

             searchInputElement.addEventListener('input', () => sortAndRenderItems());
             inflationCheckbox.addEventListener('change', handleInflationToggle);
             logScaleCheckbox.addEventListener('change', handleLogScaleToggle);
             resetButton.addEventListener('click', handleResetZoom);
             if (sortControlsContainer) {
                 sortControlsContainer.addEventListener('click', handleSortClick);
             }

             if (pollingIntervalId) clearInterval(pollingIntervalId);
             pollingIntervalId = setInterval(pollData, POLLING_INTERVAL);


        } catch (error) {
            console.error("Initialization failed:", error);
            if (itemListElement) { itemListElement.innerHTML = '<li class="p-2 text-red-400">Error loading data. Check console.</li>'; }
        }
        console.log("Initialization complete.");
    }

    function renderTopPortfolios(portfolioData) {
        if (!topPortfoliosContainer) {
            console.error("Top portfolios container not found!");
            return;
        }
         topPortfoliosContainer.innerHTML = '';

        if (!Array.isArray(portfolioData) || portfolioData.length === 0) {
            topPortfoliosContainer.innerHTML = '<p class="text-gray-400 text-center">No portfolio data available.</p>';
            return;
        }

        const cardContainer = document.createElement('div');
        cardContainer.className = 'grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4';

        const top5 = portfolioData.slice(0, 5);

        top5.forEach((portfolio, index) => {
            const card = document.createElement('div');
            card.className = 'portfolio-card bg-gray-700 p-3 rounded-lg flex flex-col items-center text-center border-2 border-transparent';

            const rank = index + 1;
            if (index === 0) card.classList.add('rank-1');
            else if (index === 1) card.classList.add('rank-2');
            else if (index === 2) card.classList.add('rank-3');

            const ownerName = portfolio.ownerName || 'Unknown Owner';
            const value = portfolio.value;
            const items = Object.entries(portfolio.content || {});
            items.sort(([, qtyA], [, qtyB]) => qtyB - qtyA);
            const top3Items = items.slice(0, 3);
            const remainingCount = items.length - top3Items.length;

            let itemsHTML = '';
            top3Items.forEach(([itemId, quantity]) => {
                itemsHTML += `
                    <div class="inline-flex items-center bg-gray-800/50 px-1.5 py-0.5 rounded text-xs mx-0.5 my-0.5" title="${itemId.replace('_', ' ')}">
                        <img src="/api/icons/${itemId}.png" class="w-3 h-3 mr-1 flex-shrink-0" alt="${itemId}" onerror="this.style.display='none'">
                        <span class="text-gray-300">${quantity}</span>
                    </div>`;
            });
            if (remainingCount > 0) {
                 itemsHTML += `<span class="portfolio-more-items"> and ${remainingCount} more...</span>`;
            }

            card.innerHTML = `
                 <div class="w-full mb-2 text-left">
                      <span class="font-bold text-xl text-gray-500">${rank}</span>
                 </div>
                 <div class="flex flex-col items-center mb-2">
                      <img src="https://mc-heads.net/head/${encodeURIComponent(ownerName)}" alt="${ownerName}'s head" class="w-8 h-8 rounded-md mb-1" onerror="this.style.display='none'">
                      <span class="font-semibold text-indigo-300 text-sm truncate max-w-[100px]" title="${ownerName}">${ownerName}</span>
                 </div>
                 <div class="text-lg font-bold text-white mb-2">${formatCurrency(value)}</div>
                 <div class="flex flex-wrap justify-center gap-1 mt-auto pt-2 border-t border-gray-600 w-full min-h-[30px]">
                     ${itemsHTML || '<span class="text-xs text-gray-500">Empty</span>'}
                 </div>
            `;
            cardContainer.appendChild(card);
        });

        topPortfoliosContainer.appendChild(cardContainer);
    }


    // =========================================================================
    // NASCRAFT 1.9.5 AUTHENTICATION, PORTFOLIO & TRADING FRONTEND CODE
    // =========================================================================

    // Auth state and polling variables
    let authState = {
        loggedIn: false,
        uuid: null,
        username: null
    };
    let portfolioData = null;
    let activeTab = 'market';
    let currentPoller = null;

    // Element references
    const loginBtn = document.getElementById('login-btn');
    const authControls = document.getElementById('auth-controls');
    const tabMarket = document.getElementById('tab-market');
    const tabPortfolio = document.getElementById('tab-portfolio');
    const marketView = document.getElementById('market-view');
    const portfolioView = document.getElementById('portfolio-view');
    const modalContainer = document.getElementById('modal-container');
    const modalClose = document.getElementById('modal-close');
    const modalBody = document.getElementById('modal-body');
    const portfolioGrid = document.getElementById('portfolio-grid');
    const portfolioValueText = document.getElementById('portfolio-value');
    const portfolioSlotsCountText = document.getElementById('portfolio-slots-count');
    const portfolioBalanceText = document.getElementById('portfolio-balance');
    const recentTradesBody = document.getElementById('recent-trades-body');

    // Register Tab Listeners
    if (tabMarket) tabMarket.addEventListener('click', () => switchTab('market'));
    if (tabPortfolio) tabPortfolio.addEventListener('click', () => switchTab('portfolio'));

    // Register Modal Close
    if (modalClose) modalClose.addEventListener('click', closeModal);
    if (modalContainer) {
        modalContainer.addEventListener('click', (e) => {
            if (e.target === modalContainer) closeModal();
        });
    }

    // Hook up public market pane buttons
    if (sellPriceDisplayElement) {
        const btn = sellPriceDisplayElement.closest('button');
        if (btn) btn.addEventListener('click', (e) => {
            e.stopPropagation();
            handlePublicBuySell('sell');
        });
    }
    if (buyPriceDisplayElement) {
        const btn = buyPriceDisplayElement.closest('button');
        if (btn) btn.addEventListener('click', (e) => {
            e.stopPropagation();
            handlePublicBuySell('buy');
        });
    }

    async function postData(endpoint, body = {}) {
        try {
            const response = await fetch(`${API_BASE_URL}${endpoint}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            if (!response.ok) {
                let errorBody = '';
                try { errorBody = await response.text(); } catch (e) {}
                console.error(`HTTP error! Status: ${response.status} on ${endpoint}. Body: ${errorBody}`);
                throw new Error(errorBody || `HTTP error! Status: ${response.status} on ${endpoint}`);
            }
            if (response.status === 204) return null;
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.indexOf("application/json") !== -1) {
                return await response.json();
            } else {
                return await response.text();
            }
        } catch (error) {
            console.error(`Failed to post ${endpoint}:`, error);
            throw error;
        }
    }

    async function checkAuth() {
        try {
            const data = await fetchData('/me');
            if (data && data.uuid) {
                authState.loggedIn = true;
                authState.uuid = data.uuid;
                authState.username = data.username;
                updateAuthUI();
                await loadPortfolio();
            } else {
                authState.loggedIn = false;
                updateAuthUI();
            }
        } catch (e) {
            authState.loggedIn = false;
            updateAuthUI();
        }
    }

    function updateAuthUI() {
        if (authState.loggedIn) {
            authControls.innerHTML = `
                <div class="flex items-center gap-2">
                    <span id="user-display" class="text-xs font-semibold text-gray-300 truncate max-w-[80px]" title="${authState.username}">${authState.username}</span>
                    <button id="logout-btn" class="text-gray-400 hover:text-white text-xs font-medium transition duration-150">
                        Logout
                    </button>
                </div>
            `;
            const logoutBtn = document.getElementById('logout-btn');
            if (logoutBtn) logoutBtn.addEventListener('click', handleLogout);
            if (tabPortfolio) tabPortfolio.classList.remove('hidden');
        } else {
            authControls.innerHTML = `
                <button id="login-btn" class="bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold py-1.5 px-3 rounded-md transition duration-150 ease-in-out">
                    Login
                </button>
            `;
            const loginBtn = document.getElementById('login-btn');
            if (loginBtn) loginBtn.addEventListener('click', handleLoginClick);
            if (tabPortfolio) tabPortfolio.classList.add('hidden');
            switchTab('market');
        }
    }

    async function handleLoginClick() {
        showModal(`
            <h3 class="text-xl font-bold text-white mb-4">Login with Minecraft</h3>
            <div class="flex flex-col items-center justify-center py-4 bg-gray-900/60 rounded-md border border-gray-700/50 mb-4">
                <span class="text-xs text-indigo-400 uppercase tracking-widest font-semibold mb-1">Your Login Code</span>
                <span id="login-code-display" class="text-4xl font-extrabold text-white tracking-widest">Loading...</span>
            </div>
            <p class="text-sm text-gray-300 mb-4">
                Run this command in-game to link your Minecraft account:
            </p>
            <div id="login-command-container" class="bg-black/80 p-3 rounded font-mono text-sm text-emerald-400 mb-4 select-all cursor-pointer flex items-center justify-between group">
                <span id="login-command-text">/market webcode ------</span>
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-4 h-4 text-gray-500 group-hover:text-gray-300">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M8.25 7.5V6.108c0-1.135.845-2.098 1.976-2.192.373-.03.748-.057 1.123-.08M15.75 18H18a2.25 2.25 0 0 0 2.25-2.25V6.108c0-1.135-.845-2.098-1.976-2.192a48.424 48.424 0 0 0-1.123-.08M15.75 18.75v-1.875a3.375 3.375 0 0 0-3.375-3.375h-1.5a1.125 1.125 0 0 1-1.125-1.125v-1.5A3.375 3.375 0 0 0 6.375 7.5H5.25m11.9-3.664A2.251 2.251 0 0 0 15 2.25h-1.5a2.251 2.251 0 0 0-2.15 1.586m5.8 0c.065.21.1.433.1.664v.75h-6V4.5c0-.231.035-.454.1-.664M6.75 7.5H4.875c-.621 0-1.125.504-1.125 1.125v12c0 .621.504 1.125 1.125 1.125h9.75c.621 0 1.125-.504 1.125-1.125V16.5a9 9 0 0 0-9-9Z" />
                </svg>
            </div>
            <div class="flex items-center gap-2 text-xs text-gray-400">
                <div class="w-2.5 h-2.5 bg-indigo-500 rounded-full animate-ping"></div>
                <span>Waiting for command execution in-game...</span>
            </div>
        `);
        
        try {
            const data = await postData('/auth/request');
            if (data && data.code) {
                document.getElementById('login-code-display').textContent = data.code;
                document.getElementById('login-command-text').textContent = `/market webcode ${data.code}`;
                
                const cmdContainer = document.getElementById('login-command-container');
                if (cmdContainer) {
                    cmdContainer.addEventListener('click', () => {
                        navigator.clipboard.writeText(`/market webcode ${data.code}`);
                        showToast('Command copied to clipboard!', 'success');
                    });
                }
                startLoginPolling();
            } else {
                showModalError('Failed to get login code. Please try again.');
            }
        } catch (e) {
            showModalError(e.message || 'Error requesting login code.');
        }
    }

    function startLoginPolling() {
        if (currentPoller) clearInterval(currentPoller);
        
        currentPoller = setInterval(async () => {
            try {
                const data = await fetchData('/auth/status');
                if (data) {
                    if (data.status === 'player_found') {
                        clearInterval(currentPoller);
                        currentPoller = null;
                        showConfirmUsernameModal(data.username);
                    } else if (data.status === 'expired') {
                        clearInterval(currentPoller);
                        currentPoller = null;
                        showModalError('Login request expired. Please request a new code.');
                    } else if (data.status === 'cancelled') {
                        clearInterval(currentPoller);
                        currentPoller = null;
                        closeModal();
                    }
                }
            } catch (e) {
                console.error('Error polling login status:', e);
            }
        }, 2000);
    }

    function showConfirmUsernameModal(username) {
        showModal(`
            <h3 class="text-xl font-bold text-white mb-2 text-center">Minecraft Account Found</h3>
            <div class="flex flex-col items-center justify-center py-6">
                <img id="confirm-avatar" src="https://minotar.net/helm/${encodeURIComponent(username)}/64.png" alt="Avatar" class="w-16 h-16 rounded-md mb-3 border border-gray-700 shadow-md">
                <span id="confirm-username" class="text-2xl font-bold text-white mb-2">${username}</span>
                <span class="text-xs text-gray-400">Is this your Minecraft account?</span>
            </div>
            <div class="grid grid-cols-2 gap-3">
                <button id="btn-cancel-confirm" class="w-full bg-gray-700 hover:bg-gray-600 text-white font-semibold py-2 px-4 rounded-md transition duration-150">
                    No, cancel
                </button>
                <button id="btn-accept-confirm" class="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-semibold py-2 px-4 rounded-md transition duration-150">
                    Yes, continue
                </button>
            </div>
        `);

        document.getElementById('btn-cancel-confirm').addEventListener('click', () => {
            closeModal();
        });

        document.getElementById('btn-accept-confirm').addEventListener('click', async () => {
            try {
                const data = await postData('/auth/confirm');
                if (data && data.uuid) {
                    authState.loggedIn = true;
                    authState.uuid = data.uuid;
                    authState.username = data.username;
                    updateAuthUI();
                    showToast('Login successful!', 'success');
                    closeModal();
                    await loadPortfolio();
                    switchTab('portfolio');
                } else {
                    showModalError('Login confirmation failed.');
                }
            } catch (e) {
                showModalError(e.message || 'Error confirming login.');
            }
        });
    }

    async function handleLogout() {
        try {
            await postData('/auth/logout');
            authState.loggedIn = false;
            authState.uuid = null;
            authState.username = null;
            portfolioData = null;
            updateAuthUI();
            showToast('Logged out successfully.', 'info');
        } catch (e) {
            console.error('Logout error:', e);
            authState.loggedIn = false;
            updateAuthUI();
        }
    }

    function switchTab(tab) {
        if (tab === 'market') {
            activeTab = 'market';
            if (tabMarket) tabMarket.className = "flex-1 pb-2 border-b-2 border-indigo-500 text-white text-center";
            if (tabPortfolio) tabPortfolio.className = "flex-1 pb-2 border-b-2 border-transparent text-gray-500 hover:text-gray-300 text-center";
            if (marketView) marketView.classList.remove('hidden');
            if (portfolioView) portfolioView.classList.add('hidden');
        } else if (tab === 'portfolio' && authState.loggedIn) {
            activeTab = 'portfolio';
            if (tabMarket) tabMarket.className = "flex-1 pb-2 border-b-2 border-transparent text-gray-500 hover:text-gray-300 text-center";
            if (tabPortfolio) tabPortfolio.className = "flex-1 pb-2 border-b-2 border-indigo-500 text-white text-center";
            if (marketView) marketView.classList.add('hidden');
            if (portfolioView) portfolioView.classList.remove('hidden');
            loadPortfolio();
            loadRecentTrades();
        }
    }

    async function loadPortfolio() {
        try {
            const data = await fetchData('/me/portfolio');
            if (data) {
                portfolioData = data;
                renderPortfolio();
            }
        } catch (e) {
            console.error('Failed to load portfolio:', e);
        }
    }

    function renderPortfolio() {
        if (!portfolioData) return;
        
        if (portfolioValueText) portfolioValueText.textContent = formatCurrency(portfolioData.portfolioValue);
        if (portfolioSlotsCountText) portfolioSlotsCountText.textContent = `${portfolioData.unlockedSlots} / ${portfolioData.maximumSlots}`;
        if (portfolioBalanceText) portfolioBalanceText.textContent = formatCurrency(portfolioData.balance);

        if (!portfolioGrid) return;
        portfolioGrid.innerHTML = '';
        
        portfolioData.slots.forEach(slot => {
            const slotDiv = document.createElement('div');
            
            if (slot.locked) {
                slotDiv.className = "aspect-square bg-gray-900/40 border-2 border-dashed border-gray-800 rounded-md flex flex-col items-center justify-center p-2 cursor-pointer hover:bg-gray-800/40 hover:border-indigo-500/50 transition duration-150 group relative";
                slotDiv.innerHTML = `
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-6 h-6 text-gray-600 group-hover:text-indigo-400 mb-1 transition duration-150">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 1 0-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 0 0 2.25-2.25v-6.75a2.25 2.25 0 0 0-2.25-2.25H6.75a2.25 2.25 0 0 0-2.25 2.25v6.75a2.25 2.25 0 0 0 2.25 2.25Z" />
                    </svg>
                    <span class="text-[10px] text-gray-500 group-hover:text-gray-300 font-semibold text-center leading-none">Unlock<br>${formatCurrency(portfolioData.nextSlotPrice)}</span>
                `;
                slotDiv.addEventListener('click', () => handleUnlockSlotClick(portfolioData.nextSlotPrice));
            } else if (slot.item === null) {
                slotDiv.className = "aspect-square bg-gray-900/60 border-2 border-gray-800 rounded-md flex flex-col items-center justify-center p-2";
                slotDiv.innerHTML = `
                    <span class="text-[10px] text-gray-700 font-medium">Empty</span>
                `;
            } else {
                slotDiv.className = "aspect-square bg-gray-800 border-2 border-gray-700 hover:border-indigo-500 rounded-md flex flex-col items-center justify-center p-1.5 cursor-pointer relative group transition duration-150";
                const itemTotalValue = slot.amount * slot.price;
                
                slotDiv.innerHTML = `
                    <img src="/api/icons/${slot.item}.png" alt="${slot.displayName}" class="w-10 h-10 object-contain" onerror="this.src='https://placehold.co/40x40/374151/9ca3af?text=?';">
                    <span class="absolute bottom-1 right-1.5 bg-gray-950/80 px-1 py-0.5 rounded text-[10px] font-bold text-white leading-none">x${slot.amount}</span>
                    
                    <div class="absolute bottom-full left-1/2 transform -translate-x-1/2 mb-2 w-48 bg-gray-950 border border-gray-700 text-white text-xs p-2 rounded-md shadow-2xl z-20 pointer-events-none hidden group-hover:block whitespace-normal">
                        <p class="font-bold text-indigo-300 text-sm mb-1">${slot.displayName}</p>
                        <div class="space-y-0.5">
                            <div class="flex justify-between"><span class="text-gray-400">Owned:</span><span>${slot.amount}</span></div>
                            <div class="flex justify-between"><span class="text-gray-400">Price:</span><span>${formatCurrency(slot.price)}</span></div>
                            <div class="flex justify-between border-t border-gray-800 mt-1 pt-1 font-semibold text-emerald-400"><span class="text-gray-400">Value:</span><span>${formatCurrency(itemTotalValue)}</span></div>
                        </div>
                    </div>
                `;
                slotDiv.addEventListener('click', () => handleItemTradeClick(slot.item, slot.displayName, slot.amount, slot.price));
            }
            portfolioGrid.appendChild(slotDiv);
        });
    }

    function handleUnlockSlotClick(cost) {
        showModal(`
            <h3 class="text-xl font-bold text-white mb-4">Unlock Portfolio Slot</h3>
            <p class="text-sm text-gray-300 mb-4">
                Are you sure you want to unlock the next portfolio slot?
            </p>
            <div class="p-4 bg-gray-900/60 rounded-md border border-gray-700/50 mb-4 space-y-2 text-sm">
                <div class="flex justify-between">
                    <span class="text-gray-400">Slot Cost:</span>
                    <span id="unlock-cost" class="font-bold text-white">${formatCurrency(cost)}</span>
                </div>
                <div class="flex justify-between">
                    <span class="text-gray-400">Your Balance:</span>
                    <span id="unlock-balance" class="font-semibold text-emerald-400">${formatCurrency(portfolioData.balance)}</span>
                </div>
            </div>
            <button id="btn-confirm-unlock" class="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-2.5 px-4 rounded-md transition duration-150">
                Confirm Unlock
            </button>
        `);

        document.getElementById('btn-confirm-unlock').addEventListener('click', async () => {
            try {
                const data = await postData('/me/portfolio/unlock-slot');
                if (data && data.unlockedSlots) {
                    portfolioData = data;
                    renderPortfolio();
                    showToast('Slot unlocked successfully!', 'success');
                    closeModal();
                } else {
                    showModalError('Failed to unlock slot.');
                }
            } catch (e) {
                showModalError(e.message || 'Error unlocking slot.');
            }
        });
    }

    function handleItemTradeClick(identifier, displayName, ownedAmount, currentPrice) {
        openTradeModal(identifier, displayName, ownedAmount, currentPrice, 'sell');
    }

    function handlePublicBuySell(type) {
        if (!authState.loggedIn) {
            handleLoginClick();
            showToast('Please login to trade.', 'warning');
            return;
        }
        
        const item = allItems.find(i => String(i.identifier) === String(selectedItemIdentifier));
        if (!item) return;
        
        let owned = 0;
        if (portfolioData) {
            const slot = portfolioData.slots.find(s => s.item === item.identifier);
            if (slot) owned = slot.amount;
        }
        
        openTradeModal(item.identifier, item.name, owned, type === 'buy' ? item.buyPrice : item.sellPrice, type);
    }

    function openTradeModal(identifier, displayName, ownedAmount, initialUnitPrice, initialType) {
        let currentType = initialType;
        let unitPrice = initialUnitPrice;
        let amount = 1;

        const renderTradeContent = () => {
            const item = allItems.find(i => String(i.identifier) === String(identifier));
            if (item) {
                unitPrice = currentType === 'buy' ? item.buyPrice : item.sellPrice;
            }
            const estimatedTotal = amount * unitPrice;
            const actionColor = currentType === 'buy' ? 'emerald' : 'red';
            const actionText = currentType === 'buy' ? 'Buy' : 'Sell';

            showModal(`
                <h3 class="text-xl font-bold text-white mb-4">${actionText} Item</h3>
                <div class="flex items-center gap-3 mb-4 p-3 bg-gray-900/60 rounded-md border border-gray-700/50">
                    <img src="/api/icons/${identifier}.png" alt="${displayName}" class="w-12 h-12 object-contain" onerror="this.src='https://placehold.co/48x48/374151/9ca3af?text=?';">
                    <div>
                        <h4 class="font-bold text-white text-base">${displayName}</h4>
                        <p class="text-xs text-gray-400">Owned in portfolio: ${ownedAmount}</p>
                    </div>
                </div>
                
                <div class="flex border-b border-gray-700 mb-4 text-sm font-semibold">
                    <button id="m-tab-buy" class="flex-1 pb-2 text-center border-b-2 ${currentType === 'buy' ? 'border-emerald-500 text-emerald-400' : 'border-transparent text-gray-400 hover:text-white'}">Buy</button>
                    <button id="m-tab-sell" class="flex-1 pb-2 text-center border-b-2 ${currentType === 'sell' ? 'border-red-500 text-red-400' : 'border-transparent text-gray-400 hover:text-white'}">Sell</button>
                </div>

                <div class="space-y-4">
                    <div>
                        <label for="trade-amount" class="block text-xs text-gray-400 mb-1">Quantity</label>
                        <div class="flex items-center gap-2">
                            <input type="number" id="trade-amount" min="1" value="${amount}" class="flex-1 px-3 py-2 bg-gray-900 border border-gray-700 rounded-md text-white font-semibold text-center focus:outline-none focus:ring-2 focus:ring-indigo-500">
                            <div class="flex flex-col gap-1">
                                <button id="btn-amount-plus" class="bg-gray-700 hover:bg-gray-600 text-white font-bold px-3 py-0.5 rounded text-xs">+</button>
                                <button id="btn-amount-minus" class="bg-gray-700 hover:bg-gray-600 text-white font-bold px-3 py-0.5 rounded text-xs">-</button>
                            </div>
                        </div>
                    </div>
                    
                    <div class="p-3 bg-gray-900/40 rounded-md border border-gray-700/30 text-sm space-y-1.5">
                        <div class="flex justify-between">
                            <span class="text-gray-400">Unit Price:</span>
                            <span class="font-semibold text-white">${formatCurrency(unitPrice)}</span>
                        </div>
                        <div class="flex justify-between">
                            <span class="text-gray-400">Estimated Total:</span>
                            <span class="font-bold text-white">${formatCurrency(estimatedTotal)}</span>
                        </div>
                    </div>
                    
                    <button id="btn-confirm-trade" class="w-full bg-${actionColor}-600 hover:bg-${actionColor}-700 text-white font-bold py-2.5 px-4 rounded-md transition duration-150">
                        Confirm ${actionText}
                    </button>
                </div>
            `);

            document.getElementById('m-tab-buy').addEventListener('click', () => {
                currentType = 'buy';
                renderTradeContent();
            });
            document.getElementById('m-tab-sell').addEventListener('click', () => {
                currentType = 'sell';
                renderTradeContent();
            });

            const amountInput = document.getElementById('trade-amount');
            if (amountInput) {
                amountInput.addEventListener('change', (e) => {
                    let val = parseInt(e.target.value);
                    if (isNaN(val) || val < 1) val = 1;
                    amount = val;
                    amountInput.value = amount;
                    updatePricesOnly();
                });
            }
            
            const btnPlus = document.getElementById('btn-amount-plus');
            if (btnPlus) {
                btnPlus.addEventListener('click', () => {
                    amount++;
                    if (amountInput) amountInput.value = amount;
                    updatePricesOnly();
                });
            }
            
            const btnMinus = document.getElementById('btn-amount-minus');
            if (btnMinus) {
                btnMinus.addEventListener('click', () => {
                    if (amount > 1) {
                        amount--;
                        if (amountInput) amountInput.value = amount;
                        updatePricesOnly();
                    }
                });
            }

            const updatePricesOnly = () => {
                const estTotal = amount * unitPrice;
                const confirmBtn = document.getElementById('btn-confirm-trade');
                if (confirmBtn) {
                    const totalSpan = confirmBtn.previousElementSibling.querySelector('span:last-child');
                    if (totalSpan) totalSpan.textContent = formatCurrency(estTotal);
                }
            };

            const confirmBtn = document.getElementById('btn-confirm-trade');
            if (confirmBtn) {
                confirmBtn.addEventListener('click', async () => {
                    try {
                        const url = currentType === 'buy' ? '/trade/buy' : '/trade/sell';
                        const data = await postData(url, { item: identifier, amount: amount });
                        
                        if (data && data.success) {
                            showToast(`Successfully ${currentType === 'buy' ? 'bought' : 'sold'} ${amount} ${displayName}!`, 'success');
                            closeModal();
                            
                            await loadPortfolio();
                            if (activeTab === 'portfolio') {
                                renderPortfolio();
                                loadRecentTrades();
                            }
                        } else {
                            showModalError(`Trade failed.`);
                        }
                    } catch (e) {
                        showModalError(e.message || `Error executing trade.`);
                    }
                });
            }
        };

        renderTradeContent();
    }

    async function loadRecentTrades() {
        if (!recentTradesBody) return;
        try {
            const data = await fetchData('/me/trades');
            if (data && Array.isArray(data)) {
                recentTradesBody.innerHTML = '';
                if (data.length === 0) {
                    recentTradesBody.innerHTML = `
                        <tr>
                            <td colspan="5" class="px-4 py-4 text-center text-gray-500 italic">No recent trades</td>
                        </tr>
                    `;
                    return;
                }
                
                data.forEach(t => {
                    const row = document.createElement('tr');
                    row.className = "border-b border-gray-800 hover:bg-gray-800/20";
                    
                    const actionClass = t.buy ? 'text-emerald-400 font-semibold' : 'text-red-400 font-semibold';
                    const actionText = t.buy ? 'BUY' : 'SELL';
                    const sign = t.buy ? '-' : '+';
                    const valueClass = t.buy ? 'text-white' : 'text-emerald-400 font-semibold';
                    
                    const date = new Date(t.date);
                    const formattedDate = date.toLocaleString();
                    
                    row.innerHTML = `
                        <td class="px-4 py-2.5 ${actionClass}">${actionText}</td>
                        <td class="px-4 py-2.5 font-medium text-white flex items-center gap-1.5">
                            <img src="/api/icons/${t.item}.png" alt="${t.displayName}" class="w-5 h-5 object-contain" onerror="this.style.display='none';">
                            <span>${t.displayName}</span>
                        </td>
                        <td class="px-4 py-2.5">x${t.amount}</td>
                        <td class="px-4 py-2.5 ${valueClass}">${sign}${formatCurrency(t.value)}</td>
                        <td class="px-4 py-2.5 text-xs text-gray-400">${formattedDate}</td>
                    `;
                    recentTradesBody.appendChild(row);
                });
            }
        } catch (e) {
            console.error('Failed to load recent trades:', e);
        }
    }

    function showModal(html) {
        if (!modalContainer || !modalBody) return;
        modalBody.innerHTML = html;
        modalContainer.classList.remove('hidden');
    }

    function closeModal() {
        if (!modalContainer || !modalBody) return;
        modalContainer.classList.add('hidden');
        modalBody.innerHTML = '';
        if (currentPoller) {
            clearInterval(currentPoller);
            currentPoller = null;
        }
    }

    function showModalError(message) {
        if (!modalBody) return;
        
        let translated = message;
        if (message === 'LOGIN_CODE_EXPIRED') translated = 'The login request has expired. Please try again.';
        else if (message === 'LOGIN_CODE_INVALID') translated = 'The login code is invalid or already confirmed.';
        else if (message === 'LOGIN_CODE_ALREADY_USED') translated = 'This login code was already used.';
        else if (message === 'SESSION_EXPIRED') translated = 'Your session has expired. Please log in again.';
        else if (message === 'NOT_AUTHENTICATED') translated = 'You must be logged in to do this.';
        else if (message === 'MARKET_CLOSED') translated = 'The market is currently closed.';
        else if (message === 'ITEM_NOT_FOUND') translated = 'The item was not found.';
        else if (message === 'INVALID_AMOUNT') translated = 'The requested amount is invalid.';
        else if (message === 'NOT_ENOUGH_MONEY') translated = 'You do not have enough money to complete this transaction.';
        else if (message === 'NOT_ENOUGH_PORTFOLIO_ITEMS') translated = 'You do not have enough of this item in your portfolio to sell.';
        else if (message === 'PORTFOLIO_FULL') translated = 'Your portfolio is full. Unlock more slots to trade this item.';
        else if (message === 'NO_MORE_PORTFOLIO_SLOTS') translated = 'You have already unlocked the maximum number of portfolio slots.';
        else if (message === 'CURRENCY_UNAVAILABLE') translated = 'The economy provider is currently unavailable.';
        else if (message === 'PRICE_LIMIT_REACHED') translated = 'Price limits reached. Trade cannot be executed.';
        else if (message === 'TRADE_FAILED') translated = 'An error occurred while processing your trade.';

        const existing = document.getElementById('modal-error-msg');
        if (existing) {
            existing.textContent = translated;
        } else {
            const errDiv = document.createElement('div');
            errDiv.id = 'modal-error-msg';
            errDiv.className = 'bg-red-900/60 border border-red-700 text-red-200 text-xs p-2.5 rounded-md mt-4 text-center font-semibold';
            errDiv.textContent = translated;
            modalBody.appendChild(errDiv);
        }
    }

    function showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `fixed bottom-4 right-4 z-50 px-4 py-3 rounded-lg shadow-2xl text-sm font-semibold transition duration-300 ease-in-out transform translate-y-10 opacity-0`;
        
        if (type === 'success') {
            toast.classList.add('bg-emerald-600', 'text-white');
        } else if (type === 'warning') {
            toast.classList.add('bg-amber-600', 'text-white');
        } else if (type === 'info') {
            toast.classList.add('bg-indigo-600', 'text-white');
        }
        
        toast.textContent = message;
        document.body.appendChild(toast);
        
        setTimeout(() => {
            toast.classList.remove('translate-y-10', 'opacity-0');
        }, 10);
        
        setTimeout(() => {
            toast.classList.add('translate-y-10', 'opacity-0');
            setTimeout(() => {
                toast.remove();
            }, 300);
        }, 3000);
    }

    initialize();

});
