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
function edit(version, module) {
	who = prompt("Enter a username for this working copy. (one word, no spaces)");
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
	type="hidden" name="mode" value="create" /> <span id="command">
<a href="javascript:void(0)" onclick="whackCreate('create_textbox')">Create
a new module...</a> <span id="create_textbox" style="display: none;">
<input type="text" name="module" /> </span> </span></form>
<div id="command"><a href="bundler?mode=browse">Browse All
Modules</a></div>
<div id="command"><a href="index.jsp">Go To Home Page</a></div>
</div>


<c:if test="${!empty versions}">
	<c:if test="${empty module}">
		<div id="instr">Showing most recent version of all modules</div>
	</c:if>
	<c:if test="${!empty module}">
		<div id="instr">Showing all versions of ${module}</div>
	</c:if>

	<p>Each Slippy 'module' is completely self-contained, and there can
	be many different versions of each. There are working versions (which
	somebody is working on), and deployed versions (which are made based on
	a working copy, and the working copy goes away after deploying). To see
	working versions, click on the 'All Versions' link below.</p>

	<p>Each user can only have one working copy of a module at a time.
	You can make a new version based on any other deployed version simply
	by clicking 'edit'. It won't overwrite what the other person has done,
	so go please feel free to mess around.</p>

	<div id="msg">This is totally beta. Email bugs/comments to
	johnsogg@cmu.edu.</div>

	<table>
		<tr>
			<th>Version</th>
			<th>Module</th>
			<th>User</th>
			<th colspan="4">Actions</th>
		</tr>
		<c:forEach items="${versions}" var="v" varStatus="s">
			<tr>
				<!-- Version column -->
				<td align="center">${v.version}</td>

				<!-- Module column -->
				<td align="center"><a
					href="bundler?mode=browse&module=${v.module}">${v.module}</a></td>

				<!--  User column -->
				<td align="right"><c:if test="${!empty v.who}">${v.who}</c:if>
				<c:if test="${empty v.who}">(none)</c:if></td>

				<!-- Edit column -->
				<td align="left"><c:if test="${v.version != 'working'}">
					<a href="javascript:void(0)"
						onclick="javascript:edit('${v.version}','${v.module}','${v.who}')">[Edit]</a>
				</c:if></td>

				<!-- List working column -->
				<td><c:if test="${v.version != 'working'}">
					<a href="bundler?mode=browse&module=${v.module}">[All Versions]</a>
				</c:if></td>

				<!-- Deploy column -->
				<td><c:if test="${v.version == 'working' }">
					<a href="javascript:void(0)"
						onclick="javascript:deploy('${v.module}', '${v.who}')">[Deploy]</a>
				</c:if></td>

				<!-- View code column -->
				<td><a
					href="listClassesFrames.jsp?mode=list&module=${v.module}&version=${v.version}&who=${v.who}&html=true">[View
				Code]</a></td>
			</tr>
		</c:forEach>
	</table>
</c:if>

</body>
</html>
