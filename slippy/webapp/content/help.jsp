<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<%@ page import="org.six11.slippy.SlippyHtmlSyntaxColorizer"%>

<%
  SlippyHtmlSyntaxColorizer c = new SlippyHtmlSyntaxColorizer();
  c.setBaseDir(application.getRealPath("help"));
  String colorFile = getServletContext().getInitParameter("colors");
  if (colorFile != null && colorFile.length() > 0) {
    c.setStyleProperties(colorFile);
  }
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<link rel="stylesheet" type="text/css" href="manage.css" media="all" />
<link rel="stylesheet" type="text/css" href="help.css" media="all" />
<title>Slippy Documentation</title>
</head>
<body>

<h1>Slippy Language Documentation</h1>

<div class="sidebar">
<div id="command"><a href="index.jsp">Go Home</a></div>
<div id="command"><a href="bundler?mode=browse">Start OliveIDE</a></div>
</div>

<p>The Slippy Language is designed to be simple. While this might
seem limiting, the lack of complexity is liberating because it allows
you to focus on your problems, rather than figuring out how the language
works, or fighting with arcane tool-oriented problems.</p>

<div id="topic">codeset</div>
<div id="code"><%=c.walk("codeset", false)%></div>
<div id="description">The codeset phrase appears at the top of a
file and indicates which group of code this source file is in. It acts
like Java's package keyword. Any classes defined below will be
considered a member of the indicated codeset.</div>

<div id="topic">define class</div>
<div id="code"><%=c.walk("define-class", false)%></div>
<div id="description">Define a class with 'class X', followed by a
block of code, and close it off with a 'done' statement.</div>

<div id="topic">strings</div>
<div id="code"><%=c.walk("strings", false)%></div>
<div id="description">Strings are text surrounded by double
quotes. You can concatenate them with the + operator (which doesn't
introduce any extra spaces), or in the special case of the 'print'
function, you can use commas to separate strings and it will insert
spaces for you. In general you'll probably want to use + though.</div>

<div id="topic">arithmetic</div>
<div id="code"><%=c.walk("arithmetic", false)%></div>
<div id="description">Arithmetic is straightforward, and supports
addition (+), subtraction (-), multiplication (*), division (/), and
modulus (%). Slippy uses the same precedence rules as languages like
Java and Ruby. You can use parens to group terms together. The only
nonstandard thing about numbers in Slippy is that they are all
interpreted as floating point values.</div>

<div id="topic">variables</div>
<div id="code"><%=c.walk("variables", false)%></div>
<div id="description">Variables are assigned with the = operator
(that's one = character). The variable is on the left, and the value is
on the right. You can start using variables whenever you like, but if
you haven't given it a value, it has the default value of 'nil'. You can
explicitly assign nil to a value if you like. Variables are typed, but
you don't have to declare what type they are, and you can reuse the same
variable for storing values of different types.</div>

<div id="topic">if, else if, else</div>
<div id="code"><%=c.walk("if", false)%></div>
<div id="description">You can have conditional branching with the
'if' construct. It will execute the first 'if' (or 'else if') clause
that evaluates to true. When no clause matches, it will execute the
'else' clause, if it is present. The 'if' construct is closed with the
'done' keyword.</div>

<div id="topic">while</div>
<div id="code"><%=c.walk("while", false)%></div>
<div id="description">While loops will execute a block of code as
long as the statement at top evaluates to some form of true. End a while
loop with the 'done' keyword. This is almost equivalent to a 'loop'
construct. The difference is that while loops will always try to resolve
the parenthetical statement as a boolean value, even if you give it a
number, string, list, or object.</div>

<div id="topic">loop</div>
<div id="code"><%=c.walk("loop", false)%></div>
<div id="description">There are three flavors of 'loop'. The first
simply counts down a set number of times by giving it a scalar number.
The second type is almost equivalent to a while loop, and runs so long
as its boolean expression is true. The last type has slightly different
syntax, and lets you iterate through lists. A loop is closed with the
'done' keyword.</div>

<div id="topic">functions</div>
<div id="code"><%=c.walk("functions", false)%></div>
<div id="description">A function in Slippy is made with the
'define' keyword followed by the function's name, followed by a
parenthetical list of parameters, a block of code, and ended with the
'done' keyword. You <i>must</i> include the parens, even if there are no
arguments. The return value of a function is the value of the last thing
it evaluates. Also notice you can treat functions as variables if you
leave off the parens, so you can pass them into another function, and
invoke them.</div>

<div id="topic">shadow variables</div>
<div id="code"><%=c.walk("shadow", false)%></div>
<div id="description">Variables declared at a high level (like x
and y here) don't get overwritten when you assign to them inside
functions. This is different from some programming languages, and I made
it this way because I wanted to reduce the headaches associated with
global variables that change when you don't expect them to. You can
still read from global primitive values, but you can't write to them
inside functions.</div>

<div id="topic">mixes</div>
<div id="code"><%=c.walk("mixes", false)%></div>
<div id="description">The 'mixes' keyword lets you get a sort of
multiple-inheritance effect with your classes. A class may only extend
one class, but it may mix in any number of other classes with a
comma-separated list. When a class C mixes a class M, all of M's members
and functions are infused with C's as though they were copied directly
into the file. Further, if M has a function named 'mix', it is executed
(after the constructor is called). The 'mix' function is provided as an
alternative to a constructor.</div>

<div id="topic">object instances</div>
<div id="code"><%=c.walk("objects", false)%></div>
<div id="description">You may instantiate a class any number of
times and as you expect, the instances are separate. The constructor for
a class (if present) is always called 'init'. Member variables and
functions (those things defined inside a class) are publicly visible, so
we could easily access p1.x or p1.y to get the first point's location
components.</div>

<div id="topic">mystery members / generic objects</div>
<div id="code"><%=c.walk("mystery-members", false)%></div>
<div id="description">You can make a generic, 'untyped' object
with 'new Object()', and arbitrarily assign values to 'mystery members'.
You may also assign values to mystery members of other object types
(e.g. those that instantiate a class). Mystery members may be functions
or variables. This is useful, for example, if you want to return several
values from a function. Since Slippy doesn't support tuples, this is a
suboptimal workaround. When assigning mystery members, the object gets a
<i>copy</i> of whatever you assign (refer the last two lines of the
example).</div>

<div id="topic">import</div>
<div id="code"><%=c.walk("import", false)%></div>
<div id="description">The 'import' keyword(s) appears just below
the 'codeset' keyword. This tells Slippy where to look for other code
that is referred to in the current file. There is no wildcard: you must
specify class files by name.</div>

<div id="topic">list operations</div>
<div id="code"><%=c.walk("lists", false)%></div>
<div id="description">There are many operations built in to lists.
The examples above should demonstrate them fairly well.</div>

<div id="topic">maps</div>
<div id="code"><%=c.walk("maps", false)%></div>
<div id="description">Slippy has associative arrays, which are
often called maps or hashes. This data type is a little deficient, as it
doesn't have any fanciful features like lists do. To make a map, use the
curly-brace syntax, with key-value pairs separated by colons, and
mappings separated by commas. To use a map, use square brackets with the
key value inside.</div>

<div id="topic">lambda</div>
<div id="code"><%=c.walk("lambda", false)%></div>
<div id="description">A lambda is an anonymous function that may
be defined wherever you like, stored in variables and passed around, and
executed. It does <i>not</i> guarantee that variables refered to within
the closure will always work, so it wouldn't be quite right to call them
closures. There are two ways to make a lambda, both of which are shown
above. The long version is probably more legible, but sometimes the
short version is more appropriate (even though its syntax involves lots
of punctuation).</div>

<div id="topic">built-in math functions</div>
<div id="code"><%=c.walk("math-functions", false)%></div>
<div id="description">There are many built-in math functions and
scalar values. These are summarized above.</div>

<div id="topic">built-in non-math functions</div>
<div id="code"><%=c.walk("non-math-functions", false)%></div>
<div id="description">Some non-math functions. These include 'now'
(for getting the current system clock in milliseconds) and 'getType'
(used to determine what type something is). 'getType' will <i>not</i>
tell you which class an object instantiates, rather it will rather
unhelpfully tell you it is an 'Instance'. There are two useful debugging
functions: 'showStacktrace' and 'printMembers'.</div>

<div id="topic">to_s</div>
<div id="code"><%=c.walk("to_s", false)%></div>
<div id="description">If an object instance has a to_s function,
that will be used whenever the object must be cast to a string.
Otherwise it will give you a string that is vaguely helpful, since it
gives you the class name and a unique hash code (which can be used to
differentiate instances of the same type).</div>

<a name="end"></a>



<%-- 
<div id="topic">&</div>
<div id="code"><%=c.walk("&", false)%></div>
<div id="description">&</div>
 --%>
</body>
</html>