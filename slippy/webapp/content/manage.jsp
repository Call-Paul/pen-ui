<%@ page import="org.six11.olive.SlippyBundler"%>
<%@ page import="org.six11.olive.SlippyBundler.Version"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Olive: Manage Slippy Modules</title>

<script type="text/javascript">
function edit(version, module) {
	who = prompt("What is your name?");
 	location.href = "editor.jsp?module=" + module + "&version=working&who=" + who;
}
</script>
</head>
<body>

<c:if test="${!empty msg}">
	<p>${msg}</p>
</c:if>

<form name="create" action="bundler" method="post">
<table>
	<tr>
		<td>Create a new module:</td>
		<td><input type="hidden" name="mode" value="create" /></td>
		<td><input type="text" name="module" /></td>
		<td><input type="submit" value="Create!" /></td>
	</tr>
</table>
</form>

<a href="bundler?mode=browse">Browse All Existing Modules</a>

<c:if test="${!empty versions}">
	<table>
		<th>Version</th>
		<th>Module Name</th>
		<th>User</th>
		<th></th>
		<c:forEach items="${versions}" var="v" varStatus="s">
			<tr>
				<td><a href="javascript:edit(${v.version},${v.module})">${v.version}</a></td>
				<td><a href="bundler?mode=browse&module=${v.module}">${v.module}</a></td>
				<td><c:if test="${!empty v.who}">${v.who}</c:if></td>
				<td><a href="javascript:void(0)"
					onclick="javascript:edit(${v.version},'${v.module}')">[Edit]</a></td>
			</tr>
		</c:forEach>
	</table>
</c:if>

</body>
</html>
