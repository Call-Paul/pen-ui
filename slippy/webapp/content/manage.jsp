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
	who = prompt("Enter a username for this working copy. (one word, no spaces)");
	if (who) {
 	  location.href = "editor.jsp?module=" + module + "&version=working&who=" + who;
	}
}
function deploy(module, who) {
	location.href = "bundler?mode=deploy&module=" + module + "&who=" + who;
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
<br />
<br />


<c:if test="${!empty versions}">
	<c:if test="${empty module}">Showing most recent version of all modules</c:if>
	<c:if test="${!empty module}">Showing all versions of ${module}</c:if>

	<table>
		<tr>
			<th>Version</th>
			<th>Module Name</th>
			<th>User</th>
			<th></th>
			<th></th>
		</tr>
		<c:forEach items="${versions}" var="v" varStatus="s">
			<tr>
				<td align="left">${v.version}</td>
				<td align="center"><a
					href="bundler?mode=browse&module=${v.module}">${v.module}</a></td>
				<td align="right"><c:if test="${!empty v.who}">${v.who}</c:if><c:if
					test="${empty v.who}">(none)</c:if></td>
				<td align="left"><a href="javascript:void(0)"
					onclick="javascript:edit('${v.version}','${v.module}')">[Edit]</a></td>
				<td align="right"><c:if test="${v.version != 'working'}">
					<a href="bundler?mode=browse&module=${v.module}">List working
					versions of ${v.module}...</a>
				</c:if><c:if test="${v.version == 'working' }">
					<a href="javascript:void(0)"
						onclick="javascript:deploy('${v.module}', '${v.who}')">Deploy
					as new version</a>
				</c:if></td>
			</tr>
		</c:forEach>
	</table>
</c:if>

</body>
</html>
