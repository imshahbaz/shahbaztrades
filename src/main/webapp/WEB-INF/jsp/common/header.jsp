<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
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
    <link rel="manifest" href="manifest.json">
    <script>
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('service-worker.js')
        .then(reg => console.log('SW registered', reg))
        .catch(err => console.log('SW failed', err));
    }
    </script>
</head>
<body class="${theme} d-flex flex-column min-vh-100">
    <header>
        <!-- Navbar -->
        <nav class="navbar navbar-expand-lg navbar-light bg-light shadow-2">
            <div class="container">
                <a class="navbar-brand mt-2 mt-lg-0" href="${pageContext.request.contextPath}/">
                    <i class="fas fa-chart-line fa-lg text-primary me-2"></i>
                    <span class="fw-bold text-primary">Shahbaz Trades</span>
                </a>
            </div>
        </nav>
        <!-- Navbar -->
    </header>
