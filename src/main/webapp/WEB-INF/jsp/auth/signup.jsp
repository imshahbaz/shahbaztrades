<jsp:include page="common/header.jsp" />
<%@ taglib uri="http://www.springframework.org/tags/form" prefix="form"%>
<html>
<head><title>Signup</title></head>
<body>
<h2>Signup</h2>

<c:if test="${not empty error}">
    <p style="color:red">${error}</p>
</c:if>

<form action="signup" method="post">
    Username: <input type="text" name="username" required /><br/>
    Password: <input type="password" name="password" required /><br/>
    <button type="submit">Signup</button>
</form>

<p>Already have an account? <a href="login">Login</a></p>
</body>
</html>
<jsp:include page="common/footer.jsp" />
