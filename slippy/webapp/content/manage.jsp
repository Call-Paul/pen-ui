<%@ page import="org.six11.olive.server.SlippyBundler"%>
<%@ page import="org.six11.olive.server.SlippyBundler.Version"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<link rel="stylesheet" type="text/css" href="manage.css" media="all" />
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Olive: Manage Slippy Modules</title>

<script type="text/javascript">
function edit(version, module, originalWho) {
	who = originalWho;
	if (!who) {
	  who = prompt("Enter a username for this working copy. (one word, no spaces)");
	}
	if (who) {
 	  location.href = "editor.jsp?module=" + module + "&version=working&who=" + who;
	}
}
function deploy(module, who) {
	location.href = "bundler?mode=deploy&module=" + module + "&who=" + who;
}

function whackCreate(obj) {
	var el = document.getElementById(obj);
	if ( el.style.display != 'none' ) {
		el.style.display = 'none';
	} else {
		el.style.display = '';
		document.create.module.focus();
	}
}

</script>

</head>
<body>

<div id="title">Olive: Manage Slippy Modules</div>

<c:if test="${!empty msg}">
	<div id="msg">${msg}</div>
</c:if>

<div class="sidebar">
<form name="create" action="bundler" method="post"><input
	type="hidden" name="mode" value="create" /> <span id="command"><a
	href="javascript:void(0)" onclick="whackCreate('create_textbox')">Create
a new module...</a> <span id="create_textbox" style="display: none;"><input
	type="text" name="module" /><!--<input type="submit" value="Create!" />--></span></span></form>

<span id="command"><a href="bundler?mode=browse">Browse All
Modules</a></span></div>


<c:if test="${!empty versions}">
	<c:if test="${empty module}">
		<div id="instr">Showing most recent version of all modules</div>
	</c:if>
	<c:if test="${!empty module}">
		<div id="instr">Showing all versions of ${module}</div>
	</c:if>

	<table>
		<tr>
			<th>Version</th>
			<th>Module</th>
			<th>User</th>
			<th>Actions</th>
		</tr>
		<c:forEach items="${versions}" var="v" varStatus="s">
			<tr>
				<td align="center">${v.version}</td>
				<td align="center"><a
					href="bundler?mode=browse&module=${v.module}">${v.module}</a></td>
				<td align="right"><c:if test="${!empty v.who}">${v.who}</c:if><c:if
					test="${empty v.who}">(none)</c:if></td>
				<td align="left"><a href="javascript:void(0)"
					onclick="javascript:edit('${v.version}','${v.module}','${v.who}')">[Edit]</a><c:if
					test="${v.version != 'working'}">
					<a href="bundler?mode=browse&module=${v.module}">List working
					versions of ${v.module}...</a>
				</c:if><c:if test="${v.version == 'working' }">
					<a href="javascript:void(0)"
						onclick="javascript:deploy('${v.module}', '${v.who}')">Deploy
					as new version</a>
				</c:if>
				<a href="listClassesFrames.jsp?mode=list&module=${v.module}&version=${v.version}&who=${v.who}&html=true">[View Code]</a></td>
			</tr>
		</c:forEach>
	</table>
</c:if>

</body>
</html>
