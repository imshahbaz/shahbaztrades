<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:include page="../common/header.jsp" />

    <main class="container flex-grow-1">
        <div class="py-5">
            <div class="row justify-content-center">
                <div class="col-md-8 col-lg-6">
                    <div class="card shadow">
                        <div class="card-header bg-primary text-white">
                            <h4 class="mb-0">
                                <i class="fas fa-cog me-2"></i>Account Settings
                            </h4>
                        </div>
                        <div class="card-body">
                            <c:if test="${not empty successMessage}">
                                <div class="alert alert-success alert-dismissible fade show" role="alert">
                                    <i class="fas fa-check-circle me-2"></i>${successMessage}
                                    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
                                </div>
                            </c:if>

                            <c:if test="${not empty errorMessage}">
                                <div class="alert alert-danger alert-dismissible fade show" role="alert">
                                    <i class="fas fa-exclamation-triangle me-2"></i>${errorMessage}
                                    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
                                </div>
                            </c:if>

                            <form action="${pageContext.request.contextPath}/settings" method="post">
                                <div class="mb-3">
                                    <label for="email" class="form-label">
                                        <i class="fas fa-envelope me-1"></i>Email Address
                                    </label>
                                    <input type="email" class="form-control" id="email" value="${user.email}" readonly>
                                    <div class="form-text">Email address cannot be changed</div>
                                </div>

                                <div class="mb-3">
                                    <label for="username" class="form-label">
                                        <i class="fas fa-user me-1"></i>Username
                                    </label>
                                    <input type="text" class="form-control" id="username" name="username"
                                           value="${user.username}" required minlength="3" maxlength="50">
                                    <div class="form-text">Choose a username (3-50 characters)</div>
                                </div>

                                <div class="d-grid">
                                    <button type="submit" class="btn btn-primary">
                                        <i class="fas fa-save me-2"></i>Save Changes
                                    </button>
                                </div>
                            </form>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </main>

    <!-- Include Footer -->
    <jsp:include page="../common/footer.jsp" />
