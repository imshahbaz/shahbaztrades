<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<jsp:include page="common/header.jsp" />

<main class="container my-5 flex-grow-1">
    <div class="hero text-center mb-5">
        <h1 class="display-3 fw-bold text-primary">Trade Calculator</h1>
        <p class="lead text-muted">Analyze your trades with real-time margin and interest calculations.</p>
    </div>

    <div class="card shadow-2-strong">
        <div class="card-body">
            <form id="calculator-form">
                <div class="row mb-4 justify-content-center">
                    <div class="col-md-8" style="position: relative;">
                        <label for="stock-input" class="form-label fw-bold">Stock Symbol</label>
                        <input type="text" class="form-control form-control-lg" id="stock-input" 
                               placeholder="Click to select or type to search..." 
                               autocomplete="off" required>
                        
                        <div id="suggestions" class="suggestions-list" 
                             style="display: none; position: absolute; z-index: 1000; background: white; width: 100%; 
                                    max-height: 300px; overflow-y: auto; border: 1px solid #ddd; border-radius: 0 0 8px 8px; 
                                    box-shadow: 0 8px 16px rgba(0,0,0,0.15);">
                        </div>
                        
                        <input type="hidden" id="selected-leverage" value="">
                        <input type="hidden" id="selected-symbol-raw" value="">
                    </div>
                </div>

                <div class="row mb-4">
                    <div class="col-md-4">
                        <label for="buy-price" class="form-label">Buy Price</label>
                        <input type="number" class="form-control" id="buy-price" step="0.01" required>
                    </div>
                    <div class="col-md-4">
                        <label for="sell-price" class="form-label">Sell Price / %</label>
                        <input type="number" class="form-control" id="sell-price" step="0.01" required>
                    </div>
                    <div class="col-md-4">
                        <label class="form-label">Sell Calculation Type</label>
                        <div class="btn-group w-100" role="group">
                            <input type="radio" class="btn-check" name="sell-type" id="sell-exact" value="exact" checked>
                            <label class="btn btn-outline-primary" for="sell-exact">Exact Price</label>
                            
                            <input type="radio" class="btn-check" name="sell-type" id="sell-percent" value="percent">
                            <label class="btn btn-outline-primary" for="sell-percent">Percentage</label>
                        </div>
                    </div>
                </div>

                <div class="row mb-4">
                    <div class="col-md-4">
                        <label for="days-held" class="form-label">Days Held</label>
                        <input type="number" class="form-control" id="days-held" min="0" required>
                    </div>
                    <div class="col-md-4">
                        <label for="quantity" class="form-label">Quantity / Investment</label>
                        <input type="number" class="form-control" id="quantity" step="0.01" required>
                    </div>
                    <div class="col-md-4">
                        <label class="form-label">Entry Mode</label>
                        <div class="btn-group w-100" role="group">
                            <input type="radio" class="btn-check" name="quantity-type" id="qty-quantity" value="quantity" checked>
                            <label class="btn btn-outline-primary" for="qty-quantity">By Units</label>
                            
                            <input type="radio" class="btn-check" name="quantity-type" id="qty-investment" value="investment">
                            <label class="btn btn-outline-primary" for="qty-investment">By Capital</label>
                        </div>
                    </div>
                </div>

                <div class="text-center mb-4">
                    <button type="button" class="btn btn-primary btn-lg px-5 shadow" onclick="calculateReturns()">Calculate Returns</button>
                </div>
            </form>

            <div id="alert-message" class="alert alert-danger" style="display: none;" role="alert"></div>

            <div id="results" style="display: none;">
                <hr class="my-5">
                <h3 class="text-center mb-4">Calculation Summary</h3>
                <div class="row g-4">
                    <div class="col-md-6">
                        <div class="card h-100 border-0 bg-light">
                            <div class="card-body">
                                <h5 class="card-title text-muted mb-4 text-uppercase small fw-bold">Capital Breakdown</h5>
                                <div class="d-flex justify-content-between mb-2"><span>Total Trade Value:</span> <span class="fw-bold">₹<span id="total-investment"></span></span></div>
                                <div class="d-flex justify-content-between mb-2"><span>Your Margin (Capital Used):</span> <span class="fw-bold text-primary">₹<span id="margin"></span></span></div>
                                <div class="d-flex justify-content-between mb-2"><span>Borrowed Funding:</span> <span class="fw-bold">₹<span id="funding-amount"></span></span></div>
                                <div class="d-flex justify-content-between"><span>MTF Interest (15% p.a.):</span> <span class="fw-bold text-danger">₹<span id="interest"></span></span></div>
                            </div>
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="card h-100 border-0 bg-light">
                            <div class="card-body">
                                <h5 class="card-title text-muted mb-4 text-uppercase small fw-bold">Performance Metrics</h5>
                                <div class="d-flex justify-content-between mb-2"><span>Gross Profit:</span> <span class="fw-bold">₹<span id="profit"></span></span></div>
                                <div class="d-flex justify-content-between mb-2"><span>Taxes & Charges:</span> <span class="fw-bold text-danger">₹<span id="total-charges"></span></span></div>
                                <div class="d-flex justify-content-between mb-2"><span>Net Take-Home Profit:</span> <span class="fw-bold text-success fs-5">₹<span id="net-profit"></span></span></div>
                                <div class="d-flex justify-content-between"><span>Return on Margin:</span> <span class="fw-bold text-success"><span id="profit-percent"></span>%</span></div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<jsp:include page="common/footer.jsp" />

<script>
    //Data injection from JSTL
    const marginData = [
        <c:forEach items="${margins}" var="m" varStatus="status">
            { "symbol": "${m.symbol}", "margin": ${m.margin} }${!status.last ? ',' : ''}
        </c:forEach>
    ];


    document.addEventListener('DOMContentLoaded', function() {
        const stockInput = document.getElementById('stock-input');
        const suggestionsDiv = document.getElementById('suggestions');
        const leverageInput = document.getElementById('selected-leverage');
        const rawSymbolInput = document.getElementById('selected-symbol-raw');

        function renderList(filter = "") {
            suggestionsDiv.innerHTML = '';
            const query = filter.trim().toUpperCase();
            
            const filtered = query.length === 0 
                ? marginData 
                : marginData.filter(item => item.symbol.toUpperCase().includes(query));

            if (filtered.length > 0) {
                filtered.forEach(item => {
                    const div = document.createElement('div');
                    div.className = 'suggestion-item'; 
                    div.style.padding = '12px 20px';
                    div.style.cursor = 'pointer';
                    div.style.borderBottom = '1px solid #f0f0f0';
                    div.innerHTML = `<strong>` + item.symbol + `</strong> <span class="badge bg-secondary float-end">` + item.margin + `x Margin</span>`;
                    
                    div.addEventListener('mousedown', function() {
                        stockInput.value = item.symbol + " (" + item.margin + "x Margin)";
                        leverageInput.value = item.margin;
                        rawSymbolInput.value = item.symbol;
                        suggestionsDiv.style.display = 'none';
                    });
                    
                    div.onmouseover = () => div.style.backgroundColor = '#f1f5fe';
                    div.onmouseout = () => div.style.backgroundColor = 'white';
                    
                    suggestionsDiv.appendChild(div);
                });
                suggestionsDiv.style.display = 'block';
            } else {
                suggestionsDiv.style.display = 'none';
            }
        }

        stockInput.addEventListener('focus', () => {
            if(stockInput.value.includes('Margin')) stockInput.value = '';
            renderList('');
        });
        
        stockInput.addEventListener('input', (e) => renderList(e.target.value));

        stockInput.addEventListener('blur', () => {
            setTimeout(() => { suggestionsDiv.style.display = 'none'; }, 250);
        });
    });

    function calculateReturns() {
        const symbol = document.getElementById('selected-symbol-raw').value;
        const leverage = parseFloat(document.getElementById('selected-leverage').value);
        const buyPrice = parseFloat(document.getElementById('buy-price').value);
        const sellPrice = parseFloat(document.getElementById('sell-price').value);
        const sellType = document.querySelector('input[name="sell-type"]:checked').value;
        const days = parseInt(document.getElementById('days-held').value);
        const quantityVal = parseFloat(document.getElementById('quantity').value);
        const qtyType = document.querySelector('input[name="quantity-type"]:checked').value;

        if (!symbol || isNaN(leverage) || isNaN(buyPrice) || isNaN(sellPrice) || isNaN(quantityVal)) {
            showAlert('Please select a stock and fill all calculation fields.');
            return;
        }

        let sp = (sellType === 'exact') ? sellPrice : buyPrice * (1 + sellPrice / 100);
        let shares = (qtyType === 'quantity') ? quantityVal : Math.trunc((quantityVal * leverage) / buyPrice);

        const totalValue = shares * buyPrice;
        const marginUsed = totalValue / leverage;
        const fundedAmt = totalValue - marginUsed;

        const grossProfit = (sp - buyPrice) * shares;
        const turnover = (buyPrice + sp) * shares;
        
        // standard brokerage assumptions
        const brokerage = 40;
        const STT = (days > 0) ? turnover * 0.001 : shares * sp * 0.00025;
        const stampCharges = shares * buyPrice * (days > 0 ? 0.00015 : 0.00003);
        const transCharges = turnover * 0.0000345;
        const sebiCharges = turnover * 0.000001;
        const gst = 0.18 * (sebiCharges + brokerage + transCharges);
        const totalCharges = brokerage + STT + transCharges + stampCharges + gst +sebiCharges;
        
        const mtfInterest = (fundedAmt * 0.15 * (days || 0)) / 365;
        const netProfit = grossProfit - mtfInterest - totalCharges;
        const roMargin = (netProfit / marginUsed) * 100;

        const formatInr = (n) => n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

        document.getElementById('total-investment').innerText = formatInr(totalValue);
        document.getElementById('margin').innerText = formatInr(marginUsed);
        document.getElementById('funding-amount').innerText = formatInr(fundedAmt);
        document.getElementById('interest').innerText = formatInr(mtfInterest);
        document.getElementById('profit').innerText = formatInr(grossProfit);
        document.getElementById('total-charges').innerText = formatInr(totalCharges);
        document.getElementById('net-profit').innerText = formatInr(netProfit);
        document.getElementById('profit-percent').innerText = roMargin.toFixed(2);

        document.getElementById('results').style.display = 'block';
        hideAlert();
        
        // Scroll to results on mobile
        document.getElementById('results').scrollIntoView({ behavior: 'smooth' });
    }

    function showAlert(msg) {
        const alertDiv = document.getElementById('alert-message');
        alertDiv.innerText = msg;
        alertDiv.style.display = 'block';
    }

    function hideAlert() {
        document.getElementById('alert-message').style.display = 'none';
    }
</script>