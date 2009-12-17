<%@ page import="org.six11.olive.SlippyBundler"%>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Olive: Sketch Recognition and Interaction Development
Environment</title>
<style type="text/css">
html,body {
	height: 100%;
}
</style>
</head>
<body>

<p>You specified the following relevant params:</p>

<ul>
	<li>mainSlippyClass: <%=request.getParameter("mainSlippyClass")%></li>
	<li>module: <%=request.getParameter("module")%></li>
	<li>version: <%=request.getParameter("version")%></li>
	<li>who: <%=request.getParameter("who")%></li>
</ul>

<p>Hopefully the following makes sense: <%=SlippyBundler.makeVersionedJarName(request.getParameter("module"), request
              .getParameter("version"), request.getParameter("who"))%></p>

<div style="width: 100%; height: 100%;"><applet
	code="org.six11.olive.OliveApplet" archive="slippy.jar" width="98%"
	height="98%" border="1"> Your browser is completely ignoring
	the &lt;APPLET&gt; tag!
	<param name="mainSlippyClass"
		value="<%=request.getParameter("mainSlippyClass")%>" />
	<param name="load-path" value="" />
</applet></div>

</body>
</html>
