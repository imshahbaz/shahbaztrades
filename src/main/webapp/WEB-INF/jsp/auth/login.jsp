<jsp:include page="common/header.jsp" />
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<head><title>Login</title></head>
<body>
<h2>Login</h2>

<c:if test="${param.error != null}">
    <p style="color:red">Invalid username or password</p>
</c:if>

<c:if test="${param.logout != null}">
    <p style="color:green">Logged out successfully</p>
</c:if>

<form action="<c:url value='/login'/>" method="post">
    Username: <input type="text" name="username" required /><br/>
    Password: <input type="password" name="password" required /><br/>
    <button type="submit">Login</button>
</form>

<p><a href="/signup">Sign up</a></p>
</body>
</html>

<jsp:include page="common/footer.jsp" />