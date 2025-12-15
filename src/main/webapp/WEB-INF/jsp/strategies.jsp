<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<jsp:include page="common/header.jsp" />

<main>
    <div class="hero">
        <h1>Available Strategies</h1>
        <p>Explore and select a strategy to analyze the market.</p>
    </div>

    <div class="container" style="padding: 2rem;">
        <table style="width: 100%; border-collapse: collapse; margin-top: 2rem;">
            <thead>
                <tr style="background-color: #0056b3; color: white; text-align: left;">
                    <th style="padding: 1rem;">Name</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach var="strategy" items="${strategies}">
                    <tr style="border-bottom: 1px solid #ddd;">
                        <td style="padding: 1rem;">
                            <a href="javascript:void(0)" onclick="fetchStrategyData('${strategy.name}')" style="color: #007bff; text-decoration: none;">
                                ${strategy.name}
                            </a>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty strategies}">
                    <tr>
                        <td colspan="1" style="padding: 1rem; text-align: center;">No strategies found.</td>
                    </tr>
                </c:if>
            </tbody>
        </table>
        
        <div id="strategy-data-container" style="margin-top: 3rem; display: none;">
            <h3>Results for: <span id="selected-strategy-name"></span></h3>
            <table id="strategy-data-table" style="width: 100%; border-collapse: collapse; margin-top: 1rem;">
                <thead>
                    <tr style="background-color: #0056b3; color: white; text-align: left;">
                        <th style="padding: 0.8rem; border: 1px solid #004494;">NSE Code</th>
                        <th style="padding: 0.8rem; border: 1px solid #004494;">Name</th>
                        <th style="padding: 0.8rem; border: 1px solid #004494;">Close Price</th>
                        <th style="padding: 0.8rem; border: 1px solid #004494;">Margin</th>
                    </tr>
                </thead>
                <tbody id="strategy-data-body">
                    <!-- Data will be populated here -->
                </tbody>
            </table>
             <p id="loading-message" style="display:none; text-align:center;">Loading data...</p>
             <p id="error-message" style="display:none; color: red; text-align:center;"></p>
        </div>

        <div style="margin-top: 2rem; text-align: center;">
             <a href="/" class="cta-button" style="background-color: #6c757d;">Back to Home</a>
        </div>
    </div>

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
                    table.style.display = 'table';
                    data.forEach(stock => {
                        const row = document.createElement('tr');
                        row.innerHTML = `
                            <td style="padding: 0.5rem; border: 1px solid #ddd;">\${stock.symbol}</td>
                            <td style="padding: 0.5rem; border: 1px solid #ddd;">\${stock.name}</td>
                            <td style="padding: 0.5rem; border: 1px solid #ddd;">\${stock.close}</td>
                            <td style="padding: 0.5rem; border: 1px solid #ddd;">\${stock.margin}</td>
                        `;
                        tableBody.appendChild(row);
                    });
                } else {
                    errorMsg.textContent = 'No data found for this strategy.';
                    errorMsg.style.display = 'block';
                }
            }
        }
    </script>
</main>

<jsp:include page="common/footer.jsp" />
