<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Olive IDE</title>
<link rel="stylesheet" type="text/css" href="manage.css" media="all" />
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
</head>
<body>

<div id="title">Olive: a web-based programming tool for sketches</div>

<table>
	<tr>
		<td>
		<p>Olive is an IDE for writing programs in a language called
		Slippy (short for Sketching Language In Progress). As you can see from
		the screen shot, Olive lets you provide freehand sketched input. The
		digital ink data provided there can serve as input to a Slippy
		program. There is an API for working with raw digital ink, including
		the ability to programmatically draw things to the screen. Of course,
		you don't have to use the sketching stuff at all.</p>

		<p>Often researches write interesting algorithms, but it can be
		hard for others to read and play with the source code that implements
		those algorithms. This is not because people are overly protective of
		their code (usually). When programmers intend to write
		proof-of-concept code, cross-platform support is usually pretty low on
		the list of priorities.</p>

		<p>Slippy programs are stored online, and the source code can
		always be viewed and edited by anyone. It is actually impossible to
		write a Slippy program and keep it to yourself!</p>

		<p>The IDE presented here is equipped with a sketching surface
		that allows Slippy code to work with drawn input and create graphical
		output based on it. While Olive doesn't currently have sketch
		recognition built in, it is the hope that recognizers, rectifiers, and
		all manner of novel interaction techniques might be developed with
		Olive.</p>
		</td>
		<td valign="top">
		<div class="sidebar">
		<div id="command"><a href="bundler?mode=browse">Start
		OliveIDE</a></div>
		<div id="command"><a href="help.jsp">Slippy Documentation</a></div>
		</div>
		<br />
		<br />
		<a href="bundler?mode=browse"><img style="margin-top: 24px;"
			border="0" src="img/olive-screenshot.png" width="400" height="325" /></a></td>

	</tr>
</table>
</body>
</html>
