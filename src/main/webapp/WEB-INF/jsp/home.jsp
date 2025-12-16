<jsp:include page="common/header.jsp" />

    <main class="container flex-grow-1">
        <div class="hero text-center my-5">
            <h1 class="display-3 fw-bold text-primary">Welcome to Shahbaz Trades Application</h1>
            <p class="lead text-muted">Your premier destination for seamless trading experiences.</p>
            <!--<a href="#" class="btn btn-primary btn-lg">Get Started</a>-->
        </div>

        <div class="row justify-content-center features">
            <div class="col-md-6 col-lg-4 mb-4">
                <div class="card h-100 ripple hover-shadow" onclick="location.href='/strategies';" style="cursor: pointer;">
                    <div class="card-body text-center">
                        <h3 class="card-title text-primary">Strategy</h3>
                        <p class="card-text">Scan and analyze the market with our advanced strategy tools.</p>
                    </div>
                </div>
            </div>
        </div>
    </main>

<jsp:include page="common/footer.jsp" />