<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Shahbaz Trades - Welcome</title>
    <!-- Use local FontAwesome -->
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/fontawesome.css">
    <!-- Google Fonts -->
    <link href="https://fonts.googleapis.com/css?family=Roboto:300,400,500,700&display=swap" rel="stylesheet" />
    <!-- MDB -->
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/mdb.min.css">
    <!-- Custom Styles -->
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/mdb.min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/bootstrap.bundle.min.js"></script>
    <link rel="manifest" href="${pageContext.request.contextPath}/manifest.json">
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon/favicon.ico">
    <link rel="apple-touch-icon" sizes="180x180" href="${pageContext.request.contextPath}/images/favicon/apple-touch-icon.png">
    <link rel="icon" type="image/png" sizes="32x32" href="${pageContext.request.contextPath}/images/favicon/favicon-32x32.png">
    <link rel="icon" type="image/png" sizes="16x16" href="${pageContext.request.contextPath}/images/favicon/favicon-16x16.png">
    <script>
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('${pageContext.request.contextPath}/service-worker.js')
        .then(reg => console.log('SW registered', reg))
        .catch(err => console.log('SW failed', err));
    }
    </script>
</head>
<body class="d-flex flex-column min-vh-100">
    <header>
        <!-- Navbar -->
        <nav class="navbar navbar-expand-lg navbar-light bg-light shadow-2">
            <div class="container">
                <a class="navbar-brand mt-2 mt-lg-0" href="${pageContext.request.contextPath}/">
                    <i class="fas fa-chart-line fa-lg text-primary me-2"></i>
                    <span class="fw-bold text-primary">Shahbaz Trades</span>
                </a>
                
                <button class="navbar-toggler" type="button" data-bs-toggle="collapse" 
                        data-bs-target="#navbarNav" aria-controls="navbarNav" 
                        aria-expanded="false" aria-label="Toggle navigation">
                    <i class="fas fa-bars"></i>
                </button>
                
                <div class="collapse navbar-collapse" id="navbarNav">
                    <ul class="navbar-nav ms-auto mb-2 mb-lg-0">
                        <li class="nav-item">
                            <a class="nav-link" href="#" onclick="toggleTheme(); return false;">
                                <i class="fas fa-moon me-1"></i> Toggle Theme
                            </a>
                        </li>

                        <c:if test="${not empty sessionScope.user}">
                            <li class="nav-item dropdown">
                                <a class="nav-link dropdown-toggle" href="#" id="navbarDropdown" role="button" data-bs-toggle="dropdown" aria-expanded="false">
                                    <i class="fas fa-user me-1"></i> ${sessionScope.user.username}
                                </a>
                                <ul class="dropdown-menu" aria-labelledby="navbarDropdown">
                                    <li><a class="dropdown-item" href="${pageContext.request.contextPath}/settings">
                                        <i class="fas fa-cog me-2"></i> Settings
                                    </a></li>
                                    <li><hr class="dropdown-divider"></li>
                                    <li><a class="dropdown-item" href="${pageContext.request.contextPath}/logout">
                                        <i class="fas fa-sign-out-alt me-2"></i> Logout
                                    </a></li>
                                </ul>
                            </li>
                        </c:if>

                        <c:if test="${empty sessionScope.user && !fn:contains(pageContext.request.requestURI, 'login') && !fn:contains(pageContext.request.requestURI, 'signup')}">
                            <li class="nav-item">
                                <a class="nav-link" href="${pageContext.request.contextPath}/login">
                                    <i class="fas fa-sign-in-alt me-1"></i> Login
                                </a>
                            </li>
                        </c:if>
                    </ul>
                </div>
            </div>
        </nav>
        <!-- Navbar -->
    </header>
    
    <script>
        // Set theme variables from server-side
        const isLoggedIn = <c:out value="${not empty sessionScope.user ? 'true' : 'false'}"/>;
        const userTheme = '<c:out value="${sessionScope.user.theme}"/>';

        function toggleTheme() {
            const body = document.body;
            const newTheme = body.classList.contains('dark') ? 'light' : 'dark';

            if (body.classList.contains('dark')) {
                body.classList.remove('dark');
            } else {
                body.classList.add('dark');
            }

            if (isLoggedIn) {
                // For logged-in users, save to database
                fetch('${pageContext.request.contextPath}/theme', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                    },
                    body: 'theme=' + newTheme.toUpperCase()
                }).then(response => {
                    if (!response.ok) {
                        console.error('Failed to save theme');
                    }
                }).catch(error => {
                    console.error('Error saving theme:', error);
                });
            } else {
                // For guests, use localStorage
                localStorage.setItem('theme', newTheme);
            }
        }

        // Load theme on page load
        document.addEventListener('DOMContentLoaded', function() {
            if (isLoggedIn) {
                // For logged-in users, use theme from user object (default dark)
                if (userTheme === 'LIGHT') {
                    document.body.classList.remove('dark');
                } else {
                    document.body.classList.add('dark');
                }
            } else {
                // For non-logged-in users, use localStorage (default dark)
                const savedTheme = localStorage.getItem('theme');
                if (savedTheme === 'light') {
                    document.body.classList.remove('dark');
                } else {
                    document.body.classList.add('dark');
                }
            }
        });
    </script>
