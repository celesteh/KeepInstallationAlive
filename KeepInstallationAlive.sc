KeepInstallationAlive {

	var watchDogs, <config, <>synthdefs, <monitorServer, lastrms, monitorSynth, osc_listener;

	*new {|config_file_path, port|
		^super.new.init(config_file_path, port)
	}

	init {|config_file_path, port|


		// Thread checking
		watchDogs = DogWalker.new;

		// set server
		this.port_(port);

		// read config file
		config = IdentityDictionary.new;
		this.readConfig(config_file_path);


		//synthdefs
		synthdefs=[];
	}

	readConfig{|config_file_path|
		var config_arr;

		config_file_path.notNil.if({
			config = IdentityDictionary.new;

			File.exists(config_file_path).if ({
				config_arr = FileReader.read(config_file_path, true, true, delimiter: $=);
				config_arr.do({|pair|
					(pair.size == 2).if ({ // only get get pairs
						config.put(pair[0].asSymbol, pair[1]);
						//pair.postln;
					})
				});
			});
		});

		config.notNil.if({
			config[\checkin_file].notNil.if({
				watchDogs.writeFile_(config[\checkin_file]);
			});
		});

	}

	port_ {|port|
		port.notNil.if({
			Server.default = Server(\Local, NetAddr("localhost", port.asInteger));
			//s = Server.default;
			//CmdPeriod.run;
		});
	}

	port {|port|
		this.port_(port);
	}

	addWatchDog { |id, dur=5, initialWait=0, tries=3, canQuit=true|
		^watchDogs.addWatchDog(id, dur, initialWait, tries, canQuit);
	}

	removeWatchDog {|id|
		watchDogs.removeAt(id)
	}

	watchDogAt {|id|
		^watchDogs.at(id);
	}

	checkIn {|id|
		watchDogs.checkIn(id);
	}

	s {
		^Server.default;
	}

	addSynthDef {|synthDef|

		synthDef.add;
		synthdefs = synthdefs.add(synthDef);
	}


	loadSynthDefs {


		synthdefs.do({|syn|
			syn.add;
		});

		this.startMonitor;

	}

	startMonitor {

		var shouldStart = true;


		monitorServer.if({
			monitorSynth.notNil.if({
				monitorSynth.isRunning.if({

					shouldStart=false;
					}, {
						// if we're not running for some reason, then quit us
						monitorSynth.free;
				});
			});

			shouldStart.if ({
				Server.default.makeBundle(0, {
					monitorSynth = Synth(\stereoListenForSilence,  nil,
						RootNode(Server.default), \addToTail);
					monitorSynth.register;
				});
			});
		})
	}


	boot { |loadSynths=false, action, checkIn = false, id|
		// This must be called from a thread
		var condition, bundle;

		//"entering boot".postln;

		condition = Condition(false);

		Server.default.boot(onFailure:{});

		try {
			0.1.wait;
		} {
			"This should be called inside a Routine".warn;
		};

		Server.default.doWhenBooted(limit: 5000, onFailure:{1.exit}, // long limit
			onComplete: {
				action.value;
				condition.test = true;
				condition.signal;
		});

		try {
			condition.wait;
		};

		checkIn.if({
			this.checkIn(id);
		});
		"leaving boot".postln;

		try {
			Server.default.sync;
		};

		loadSynths.if({
			this.loadSynthDefs();
		});

		try {
			Server.default.sync;
		};
	}

	checkInWithFile { |filePath|
		watchDogs.writeFile_(filePath)
	}

	setMonitorServer{ |monitor, dur=10, threshold=0.0001, watchDogDur=10, action|

		var dog;

		monitor.if({ // if true

			try {
				Server.default.sync;
			};


			this.addSynthDef(
				SynthDef(\stereoListenForSilence, {|in=0, out=0|
					var input;
					input = In.ar(in, Server.default.options.numOutputBusChannels);
					input = LeakDC.ar(input).tanh; //Limiter
					SendPeakRMS.kr(input, 1, 3, '/loudnessMonitoringForInstallation');
					ReplaceOut.ar(out, input);
				})
			);

			dog = this.addWatchDog(\loudnessMonitoringForInstallation, watchDogDur, 10, canQuit:true); //initialWait is 10 seconds
			dog.addFix({this.loadSynthDefs});
			dog.addFix({this.startMonitor});
			dog.addFix({this.boot(true)});


			//monitorLoudness.not.if({
			//	dur=0; // an empty array will prevent this monitoring from happening
			//});

			lastrms=Array.fill(dur, {1});
			osc_listener = OSCFunc({ |msg, time|
				var rms;
				"osc message".postln;
				this.checkIn(\loudnessMonitoringForInstallation);

				rms = msg[4].asFloat.max(msg[6].asFloat);
				(lastrms.size > 0) .if({
					lastrms.removeAt(0);
					lastrms.add(rms);
					(lastrms.sum <= threshold).if ({
						"too quiet".postln;
						{
							lastrms=Array.fill(dur, {1}); // reset
							action.value();
						}.fork;
					});
				});
			}, '/loudnessMonitoringForInstallation');


			monitorSynth.notNil.if({
				monitorSynth.free;
			});

			try {
				Server.default.sync;
			};

			monitorSynth = Synth(\stereoListenForSilence,  nil, RootNode(Server.default), \addToTail);

			} , { // else


				this.removeWatchDog(\loudnessMonitoringForInstallation);

				osc_listener.notNil.if({
					osc_listener.free;
				});


				monitorSynth.notNil.if({
					monitorSynth.free;
				});

		});

		monitorServer = monitor;

		^dog; // return the watchdog in case the user wants to add some earlier actions

	}

	makeWin {|dialogText ="", nextAction, options, okText = "OK"|

		var layout, win, text, ok, cancel, bg, menu, choice;

		bg = Color(0.6,0.8,0.8);

		win = Window("Installation Wizzard", Rect(128, 64, 340, 300));

		layout = win.addFlowLayout( 10@10, 20@5 );

		win.view.background = bg;

		text = StaticText(win.view, Rect(20, 20, 320, 180)).background_(bg);
		text.string = dialogText;

		layout.nextLine;

		options.notNil.if({

			menu=PopUpMenu(win.view,Rect(10,10,320,20))
			        .items_(options);
			layout.nextLine;

		});


		layout.left = 80;
		//layout.top = 120;
		cancel = Button(win.view, Rect(20, 20, 75, 24));
		cancel.states = [["Cancel", Color.black, bg]];
		cancel.action= {
			win.close;
			"cancel".postln;

		};

		ok = Button(win.view, Rect(20, 20, 75, 24));
		ok.states = [[okText, Color.black, bg]];
		ok.action = {
			win.close;
			menu.notNil.if({
				choice = menu.value
			});
			nextAction.value(choice);
		};

		win.front;
	}


	wizard {

		var dir, name, hasGui, layout, win, text, ok, cancel, bg, remainder;

		remainder = {

			this.generate_bash_files(dir, name, hasGui);
			"cd % ; chmod +x *.sh".format(dir).unixCmd;
			this.makeWin(
				"Installation files now created in %.".format(dir),
				{
					this.makeWin(
						"You should modify your code to read two arguments from the commandline and pass them to KeepInstallationAlive.new:\n\tKeepInstallationAlive(thisProcess.argv[0], thisProcess.argv[1]);",
						{
							hasGui.if({
								this.makeWin(
									("To autostart this installation on a RaspberryPi or Linux machine, \n"
										"Copy %.desktop to ~/.config/autostart/, creating the directory if necessary").format(name)
								);
								} , {
									this.makeWin(
										("To autostart this installation on a RaspberryPi or Linux machine, \n"
											"At the prompt, run:\n"
											"sudo cp %.init.d /etc/init.d/%\n"
											"sudo chmod 755 /etc/init.d/%\n"
											"sudo update-rc.d % defaults"
										).format(name, name, name, name)
									)
							});

						}
					)
				}
			);
		};


		this.makeWin(
			"This will help guide you through the process of turning your program into an installation that will automatically start when your computer boots.",
			{

				this.makeWin(
					"Please locate the scd file that runs your installation.\n(This should be in a directory that only holds files related to your installation. If it is not, please hit cancel, move it, and try again)",
					{
						Dialog.openPanel({|path|

							dir = path.dirname;
							name = path.basename;
							name = name.split($.)[0];

							name.postln;

							this.makeWin(
								"Will you be using a screen, projector or other display to run this installation?",
								{|val|

									(val.value==0).if({

										hasGui = true;
										} , {
											hasGui = false;
									});

									watchDogs.writeFile.isNil.if({
										this.makeWin(
											"Your installation does not currently write to the file system to show it is still alive. Would you like to create and monitor a temporary file in %?".format(Platform.defaultTempDir),
											{|val|

												(val.value==0).if({

													watchDogs.writeFile = Platform.defaultTempDir +/+ name ++".tmp"
												});

												remainder.value;

											},
											["Yes", "No"]
										)
										} , {

											remainder.value;
									});
								},
								["Yes", "No"]
							);
						});
					}
				)

			}
		);



	}


	generate_bash_files{|dir, name, hasGui = true|

		var filepath, file, wrote_config, conf, scpath, sclang, time, osx, alive;

		wrote_config = false;

		Platform.case(
			\osx, { osx = Platform.resourceDir ++ "/../MacOS/sclang";}
		);

		name = name.collect({|c|
			(((c >= $a) && (c <=$z)) || ((c >=$A) && (c <= $Z)) || ((c >= $1) && (c <= $9))
				|| (c == $0) || (c == $_ )
			).if ({
				c
			}, { $_ });
		});

		watchDogs.writeFile.notNil.if({
			conf = (
				"#checkin file\n"
				"checkin_file=%\n"
			).format(watchDogs.writeFile);

			filepath=("%/%.config").format(dir,name);
			File.exists(filepath).not.if({

				file=File(filepath, "w");
				file.write(conf);
				file.close;
				wrote_config = true;
			});

			filepath=("%/keepAlive_%.sh").format(dir,name);
			File.exists(filepath).not.if({
				file=File(filepath, "w");
				file.write(


					(
						"#!/bin/bash\n"
						"\n"
						". %.config\n"
						"\n"
						"sleep_time=% #max time to fail * 2\n"
						"\n"
						"#sleep $initial_pause # initial sleep to let things get started\n"
						"\n"
						"#rm $alive\n"
						"\n"
						"sleep $sleep_time\n"
						"\n"
						"while :\n"
						"    do\n"
						"\n"
						"        if [ ! -f $alive ]; then\n"
						"            echo \"File not found! - %.scd has not checked in and must be hung\"\n"
						"            kill $1\n"
						"            exit 0\n"
						"        else\n"
						"\n"
						"            rm $alive\n"
						"        fi\n"
						"\n"
						"        sleep $sleep_time\n"
						"\n"
						"done\n"
					).format(name, time, name, name, name, name)
				);
				file.close;

			});


			alive = (
				"        ./keepAlive_%.sh $pid &\n"
				"        alive_pid=$!\n"
				"\n"
				"        wait $pid\n"
				"        kill $alive_pid\n"
			).format(name)

			}, {
				alive = "        wait $pid\n"
		});



		scpath =
		"if [[ \"$OSTYPE\" == \"darwin\"* ]]; then\n"
		"    # Mac OSX\n"
		"    sclang=%\n"
		"  else\n"
		"    sclang=sclang\n"
		"fi\n\n";
		scpath = scpath.format(osx);


		hasGui.if ({
			sclang = (
				"        $sclang %.scd %.config $port&\n"
				"        pid=$!\n"
			);
			} , {
				sclang = (
					//"        if [ \"$OSTYPE\" == \"linux-gnu\" ] || [ \"$OSTYPE\" == \"freebsd\"* ] || [ $raspberry -eq 1 ]\n"
					"        if [ $raspberry -eq 1 ]\n"
					"            then\n"
					"                /usr/bin/xvfb-run --server-args=\"-screen 0, 1280x800x24\" $sclang %.scd %.config $port&\n"
					"                pid=$!\n"
					"            else\n"
					"                $sclang %.scd %.config $port&\n"
					"                pid=$!\n"
					"        fi\n"
				);
		});
		sclang = sclang.format(name, name);

		filepath=("%/%.sh").format(dir,name);
		File.exists(filepath).not.if({
			file=File(filepath, "w");
			file.write(
				(
					"#!/bin/bash\n"
					"\n"
					"port=57110\n"
					"\n"
					"#are we on a raspberry pi\n"
					"if [ -f /etc/rpi-issue ]\n"
					"    then\n"
					"        raspberry=1\n"
					"       # do pi specific stuff\n"
					"       # we need these two lines in order to make sound\n"
					"       export SC_JACK_DEFAULT_INPUTS=\"system\"\n"
					"       export SC_JACK_DEFAULT_OUTPUTS=\"system\"\n\n"

					"    else\n"
					"        raspberry=0\n"
					"fi\n\n"
					"%"
					"\n"
					"cd %\n\n"
					"while :\n"
					"    do\n"
					"        ## Put your helper scripts here\n"
					"        ## Ex:\n"
					"        # python installation_helper.py & \n"
					"        # helper=$! #keep the helper's PID\n"
					"\n\n"
					"        ## Start your SuperCollider code\n\n"
					"        %\n\n\n"
					"        ## Now stop everything\n\n"
					"\n"
					"        %\n"
					"        killall scsynth\n"
					"\n"
					"        ## Kill your helper scripts\n"
					"        ## Ex:\n"
					"        # kill $helper || kill -9 $helper\n"
					"        ## $helper is the PID we kept above\n\n"
					"\n"
					"        if [ \"$OSTYPE\" == \"linux-gnu\" ] || [ \"$OSTYPE\" == \"freebsd\"* ] || [ $raspberry -eq 1 ]\n"
					"            then\n"
					"                killall jackd\n"
					"                sleep 5 #pause longer for jack\n"
					"        fi\n"
					"\n"
					"        sleep 5\n"
					"        port=$((port+1))\n"
					"done\n"
				).format(scpath, dir, sclang, alive)
			);
			file.close;
		});

		time = watchDogs.maxTimeToFail * 2;
		(time < 120).if({
			time = 120;
		});


		hasGui.if({
			filepath=("%/%.desktop").format(dir,name);
			File.exists(filepath).not.if({
				file=File(filepath, "w");
				file.write(
					(
						"[Desktop Entry]\n"
						"Name=%\n"
						"Exec=%/%.sh\n"
						"Type=application\n"
					).format(name, dir, name)
				);
				file.close;
			});

			}, {
				filepath=("%/%.init.d").format(dir,name);
				File.exists(filepath).not.if({
					file=File(filepath, "w");
					file.write(
						(
							"#!/bin/sh\n"
							"### BEGIN INIT INFO\n"
							"# Provides:          %\n"
							"# Required-Start:    $local_fs\n"
							"# Required-Stop:     $local_fs\n"
							"# Default-Start:     2 3 4 5\n"
							"# Default-Stop:      0 1 6\n"
							"# Short-Description: Start/stop %\n"
							"### END INIT INFO\n"
							" \n"
							"# Set the USER variable to the name of the user to start % under\n"
							"export USER='pi'\n"
							" \n"
							"case \"$1\" in\n"
							"  start)\n"
							"    su $USER -c '%/%.sh'\n"
							"    echo \"Starting %\"\n"
							"    ;;\n"
							"  stop)\n"
							"    pkill %.sh\n"
							"    pkill keepAlive_%.sh\n"
							"    pkill sclang\n"
							"    pkill scsynth\n"
							"    echo \"% stopped\"\n"
							"    ;;\n"
							"  *)\n"
							"    echo \"Usage: /etc/init.d/% {start|stop}\"\n"
							"    exit 1\n"
							"    ;;\n"
							"esac\n"
							"exit 0\n"

						).format(name, name, name, dir, name, name, name, name, name, name)
					);
					file.close;
				})
		});



		wrote_config.if ({
			"Created a configuration file %/%.config".format(dir,name).postln;
			} , {
				watchDogs.writeFile.notNil.if({
					"Please append to your configuration file:".postln;
					conf.postln;
				});
		});
		watchDogs.writeFile.isNil.if({

			"You have not specified a temporary file to create so that bash scripts can determine if your installation is still running.".postln;
			"Please modify your program. See the help file on KeepInstallationAlive.checkInWithFile.".postln;
		});


		"You should modify your code to read two arguments from the commandline and pass them to KeepInstallationAlive.new:\n".postln;
		"\tKeepInstallationAlive(thisProcess.argv[0], thisProcess.argv[1]);".postln;

		"If you intend to run this installation on a mac, you will need to update sclang so it has the full path of scalng on your computer. See the help for more.".postln;

		"You will need to make the .sh files executable. At the prompt run:".postln;
		"cd % ; chmod +x *.sh".format(dir).postln;


		"To autostart this installation on a RaspberryPi or Linux machine".postln;
		hasGui.if({
			"Copy %.desktop to ~/.config/autostart/, creating the directory if necessary".format(name).postln;
			} , {
				"At the prompt, run:".postln;
				"sudo cp %.init.d /etc/init.d/%".format(name, name).postln;
				"sudo chmod 755 /etc/init.d/%".format(name).postln;
				"sudo update-rc.d % defaults".format(name).postln;
		});

	}
}