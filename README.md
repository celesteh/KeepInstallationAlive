# Installation
A SuperCollider Quark to help keep installations running

This gives a few classes for keeping track if your threads and the server are still going, with some ability to recover from errors.  It also can generate some bash files for you to do even more error checking and installation-restarting and some of the files you need to start a program on boot.

In addition, it contains some guides (adapted from blog posts) about how to to deal with crashing, etc in an installation.


Feedback is extremely welcome at this point, for both the help files and the functionality/API.

Questions I'm pondering include:

* Is a bash file creation wizard /actually/ a good idea?
* Is it an overstep to have server monitoring also limit output?
* Should server monitoring be separated into two different methods: one for silence listening and one for OSC? 
* How do I get the Installation class to put the latest version of Server.default into the global variable s?
* Should config files have their own class / quark?
