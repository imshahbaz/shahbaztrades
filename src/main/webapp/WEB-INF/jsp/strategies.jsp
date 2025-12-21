<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <jsp:include page="common/header.jsp" />

    <main class="container my-5 flex-grow-1">
        <div class="hero text-center mb-5">
            <h1 class="display-4 fw-bold">Available Strategies</h1>
            <p class="lead text-muted">Explore and select a strategy to analyze the market.</p>
        </div>

        <div class="card shadow-2-strong">
            <div class="card-body">
                <table class="table table-striped table-hover mb-0">
                    <thead class="bg-primary text-white">
                        <tr>
                            <th scope="col">Name</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:forEach var="strategy" items="${strategies}">
                            <tr>
                                <td>
                                    <a href="javascript:void(0)" onclick="fetchStrategyData('${strategy.name}')"
                                        class="text-decoration-none">
                                        ${strategy.name}
                                    </a>
                                </td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty strategies}">
                            <tr>
                                <td class="text-center">No strategies found.</td>
                            </tr>
                        </c:if>
                    </tbody>
                </table>
            </div>
        </div>

        <div id="strategy-data-container" class="mt-5" style="display: none;">
            <h3>Results for: <span id="selected-strategy-name" class="text-primary"></span></h3>
            <div class="table-responsive">
                <table id="strategy-data-table" class="table table-bordered table-hover mt-3" style="display: none;">
                    <thead class="table-primary">
                        <tr>
                            <th scope="col">NSE Code</th>
                            <th scope="col">Name</th>
                            <th scope="col">Close Price</th>
                            <th scope="col">Margin</th>
                            <th scope="col">Action</th>
                        </tr>
                    </thead>
                    <tbody id="strategy-data-body">
                        <!-- Data will be populated here -->
                    </tbody>
                </table>
            </div>
            <div id="loading-message" class="text-center my-3" style="display:none;">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
                <p>Loading data...</p>
            </div>
            <p id="error-message" class="text-danger text-center font-weight-bold" style="display:none;"></p>
        </div>

        <div class="mt-5 text-center">
            <a href="/" class="btn btn-secondary btn-lg">Back to Home</a>
        </div>
        </div>

        <script src="https://kite.trade/publisher.js?v=3"></script>
        <script>
            const strategyCache = {};

            function fetchStrategyData(strategyName) {
                const container = document.getElementById('strategy-data-container');
                const tableBody = document.getElementById('strategy-data-body');
                const strategyNameSpan = document.getElementById('selected-strategy-name');
                const loadingMsg = document.getElementById('loading-message');
                const errorMsg = document.getElementById('error-message');
                const table = document.getElementById('strategy-data-table');

                // Toggle logic: If already showing this strategy, close it
                if (container.style.display === 'block' && strategyNameSpan.textContent === strategyName) {
                    container.style.display = 'none';
                    return;
                }

                // Reset and show container
                container.style.display = 'block';
                strategyNameSpan.textContent = strategyName;
                tableBody.innerHTML = '';
                table.style.display = 'none';
                loadingMsg.style.display = 'block';
                errorMsg.style.display = 'none';

                // Check Cache
                if (strategyCache[strategyName]) {
                    loadingMsg.style.display = 'none';
                    renderTable(strategyCache[strategyName]);
                    return;
                }

                fetch('/api/chartink/fetchWithMargin?strategy=' + encodeURIComponent(strategyName))
                    .then(response => {
                        if (!response.ok) {
                            throw new Error('Network response was not ok');
                        }
                        return response.json();
                    })
                    .then(data => {
                        loadingMsg.style.display = 'none';
                        // Store in cache
                        strategyCache[strategyName] = data;
                        renderTable(data);
                    })
                    .catch(error => {
                        loadingMsg.style.display = 'none';
                        errorMsg.textContent = 'Error fetching data: ' + error.message;
                        errorMsg.style.display = 'block';
                        console.error('Error:', error);
                    });

                function renderTable(data) {
                    if (data && data.length > 0) {
                        const kite = new KiteConnect("kitedemo");

                        table.style.display = 'table';
                        data.forEach(stock => {
                            const row = document.createElement('tr');
                            row.innerHTML = `
                            <td>\${stock.symbol}</td>
                            <td>\${stock.name}</td>
                            <td>\${stock.close}</td>
                            <td>\${stock.margin}</td>
                            <td>
                                <button class="buy-trigger button">Buy</button>
                            </td>
                        `;
                            tableBody.appendChild(row);

                            const btn = row.querySelector('.buy-trigger');
                            btn.addEventListener('click', function (e) {
                                e.preventDefault();
                                const kite = new KiteConnect("kitedemo");
                                
                                kite.add({
                                    "exchange": "NSE",
                                    "tradingsymbol": stock.symbol,
                                    "quantity": 1,
                                    "transaction_type": "BUY",
                                    "order_type": "MARKET",
                                    "product": "CNC"
                                });

                                kite.connect();

                            });
                        })

                    } else {
                        errorMsg.textContent = 'No data found for this strategy.';
                        errorMsg.style.display = 'block';
                    }
                }
            }
        </script>

    </main>

    <jsp:include page="common/footer.jsp" />