<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
	
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<link rel="stylesheet" type="text/css" href="manage.css" media="all" />
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Code Header</title>
</head>
<body>
<span id="title">Slippy Module: <i><%=request.getParameter("module")%></i></span>
<% if ("working".equals(request.getParameter("version"))) { %>
Working copy by <%= request.getParameter("who") %>
<% } else { %>
Version #<%= request.getParameter("version") %>
<% } %>

<a href="bundler?mode=browse" target="_top">[Back to module list]</a>

</body>
</html>