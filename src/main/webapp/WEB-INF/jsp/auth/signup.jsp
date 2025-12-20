<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
    <jsp:include page="../common/header.jsp" />

    <main class="container my-5 flex-grow-1 d-flex align-items-center justify-content-center">
        <div class="row justify-content-center w-100">
            <div class="col-md-6 col-lg-5">
                <div class="card shadow-2-strong">
                    <div class="card-body p-5">
                        <div class="hero text-center mb-4">
                            <h2 class="fw-bold text-primary mb-2">Create Account</h2>
                            <p class="text-muted">Join Shahbaz Trades today</p>
                        </div>

                        <c:if test="${not empty error}">
                            <div class="alert alert-danger" role="alert">
                                <i class="fas fa-exclamation-triangle me-2"></i>
                                ${error}
                            </div>
                        </c:if>

                        <c:if test="${not empty message}">
                            <div class="alert alert-success" role="alert">
                                <i class="fas fa-check-circle me-2"></i>
                                ${message}
                            </div>
                        </c:if>

                        <c:if test="${not otpSent}">
                            <form action="signup" method="post">
                                <div class="form-outline mb-4">
                                    <input type="email" id="email" name="email" class="form-control form-control-lg"
                                        required />
                                    <label class="form-label" for="email">Email Address</label>
                                </div>

                                <div class="form-outline mb-4">
                                    <input type="password" id="password" name="password"
                                        class="form-control form-control-lg" required />
                                    <label class="form-label" for="password">Password</label>
                                </div>

                                <div class="form-outline mb-4">
                                    <input type="password" id="confirmPassword" name="confirmPassword"
                                        class="form-control form-control-lg" required />
                                    <label class="form-label" for="confirmPassword">Confirm Password</label>
                                </div>

                                <div class="d-grid">
                                    <button class="btn btn-primary btn-lg" type="submit">
                                        <i class="fas fa-user-plus me-2"></i>Send OTP
                                    </button>
                                </div>
                            </form>
                        </c:if>

                        <c:if test="${otpSent}">
                            <form action="verify-otp" method="post">
                                <div class="form-outline mb-4">
                                    <input type="text" id="otp" name="otp" class="form-control form-control-lg" required
                                        maxlength="6" pattern="[0-9]{6}" />
                                    <label class="form-label" for="otp">Enter 6-digit OTP</label>
                                </div>

                                <div class="d-grid">
                                    <button class="btn btn-success btn-lg" type="submit">
                                        <i class="fas fa-check me-2"></i>Verify & Create Account
                                    </button>
                                </div>
                            </form>
                        </c:if>

                        <c:if test="${not otpSent}">
                            <div class="text-center mt-4">
                                <p class="mb-0">Already have an account?
                                    <a href="login" class="text-primary fw-bold">Sign in</a>
                                </p>
                            </div>
                        </c:if>
                    </div>
                </div>
            </div>
        </div>
    </main>

    <jsp:include page="../common/footer.jsp" />