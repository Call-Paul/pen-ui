<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<title>Slippy Code Repository</title>
</head>

<frameset rows="60,*">
	<frame
		src="codeHeader.jsp?module=<%=request.getParameter("module")%>&version=<%=request.getParameter("version")%>&who=<%=request.getParameter("who")%>" />
	<frameset cols="180,*">
		<frame
			src="code?module=<%=request.getParameter("module")%>&version=<%=request.getParameter("version")%>&who=<%=request.getParameter("who")%>&mode=list&html=true&frames=true" />
		<frame name="codeFrame" />
	</frameset>
</frameset>
</html>