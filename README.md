Moved to Codeberg: https://codeberg.org/celesteh/KeepInstallationAlive

# KeepInstallationAlive
A SuperCollider Quark to help keep installations running

This gives a few classes for keeping track if your threads and the server are still going, with some ability to recover from errors.  It also can generate some bash files for you to do even more error checking and installation-restarting and some of the files you need to start a program on boot.

In addition, it contains some guides (adapted from blog posts) about how to to deal with crashing, etc in an installation.


Feedback is extremely welcome at this point, for both the help files and the functionality/API.

The current state of things:

* This is still under development, but the API is becoming fairly stable. Some of the classes related to synthdefs, groups and buses are goung to be removed because they're not actually needed.
* The bash wizard has a few bugs, but as it just saved me sa lot of time in deploying my own installation, those will be fixed and it's being kept.
* Server monitoring and limiting are now separated

Questions I'm pondering include:

* Should server monitoring be separated into two different methods: one for silence listening and one for OSC? 
* IS it a good idea to necessarily rely so heavily on Server.default? Are there cases where an installation might be runnign across multiple servers? 
* How do I get the Installation class to put the latest version of Server.default into the global variable s?
* Should config files have their own class / quark? 


Answers I'm thinking:
* Yes, those things should have separate methods, but they can use the same synth and osc responder
* Some methods may get an optional end argument to specify a server (this will also slightly complicate server monitoring)
* Still not looked into this.
* They should have their own class. They're super handy, but are dead simple and probably don't need to be their own quark. There may already be a config file quark - I need to check this.  The reason I chose the file format that I did is because it's dead easy to load in bash and is also readable across languages, so I'm sticking with it.
