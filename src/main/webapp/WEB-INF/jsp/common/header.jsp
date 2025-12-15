<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Shahbaz Trades - Welcome</title>
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
<body class="${theme}">
    <header>
        <nav>
            <a href="${pageContext.request.contextPath}/" class="logo">Shahbaz Trades</a>
        </nav>
    </header>
