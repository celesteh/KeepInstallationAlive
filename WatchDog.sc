WatchDog {

	var walker, alive,task, thread, fixes, <lastCheckIn, <>tries, <>dur, <>canQuit;

	*new { |walker, dur=5, initialWait=0, tries=3, canQuit=true|
		^super.new.init(walker, dur, initialWait, tries, canQuit)
	}


	init{ |walk, d, initialWait=0, try=3, quitter=true|
		walker = walk;
		dur = d;
		canQuit = quitter;
		tries = try;
		fixes = [];

		this.buildTask(initialWait);

	}


	putFix {|index, action|

		fixes.put(index,action);

	}

	addFix {|action|

		fixes = fixes.add(action);

	}

	removeFixAt {|index|

		fixes.removeAt(index);
	}

	sizeFix{
		^fixes.size
	}

	addFirstFix {|action|

		fixes = fixes.addFirst(action)
	}

	addPenultimateFix{ |action|

		var index, size;
		index = fixes.size -1;
		size = fixes.size.max(0);

		fixes = fixes.insert(index, action);
	}



	buildTask { |initialWait=0|


		var local_alive;

		alive = tries;

		task = Task.new({
			var action;

			initialWait.wait;

			{true}.while({

				dur.wait;
				alive = alive -1;

				alive.postln;

				(alive <= 0).if({
					local_alive = alive.abs;
					"notAlive".postln;

					(local_alive >= fixes.size).if({
						canQuit.if({
							1.exit
						})
						}, { // else

							action = fixes[local_alive];
							action.value();
					});
				});
			});
		});

		thread.notNil.if({
			thread.stop;
		});
		thread = task.play;

	}

	maxTimeToFail{
		var total;

		total= dur * (tries + fixes.size);
		^total
	}


	checkIn{

		"checking in".postln;
		alive = tries;
		lastCheckIn = Date.gmtime().rawSeconds;

	}



	play {

		thread.notNil.if({
			thread.play
			}, {
				thread = task.play;
		});
	}

	stop {
		thread.notNil.if({
			thread.stop;
		})
	}

	pause {
		thread.notNil.if({
			thread.pause;
		})
	}

	resume {
		thread.notNil.if({
			thread.resume;
		})
	}

	reset {
		thread.notNil.if({
			thread.resume;
		})
	}


}

DogWalker {

	var dogs, file;

	*new{|writeFile|
		^super.new.init(writeFile);
	}

	init {|writeFile|
		dogs = Dictionary.new();
		file = writeFile;
	}

	writeFile_ {|newFile|

		//newFile.not.if({ newFile = nil }); //wtf???

		(file.isNil && newFile.notNil).if({
			file = newFile;

		} , {
			"Already set to write the file %".format(file).warn;
		});

	}

	writeFile { ^file }

	fileSet { ^file.notNil }

	addWatchDog{ |id, dur=5, initialWait=0, tries=3, canQuit=true|

		dogs[id] = WatchDog(this, dur, initialWait, tries, canQuit);
		^dogs[id];
	}

	removeWatchDog {|id|
		dogs.removeAt(id)
	}

	checkIn{|id|
		dogs[id].checkIn();
		file.notNil.if({
			"touch %".format(file).postln.unixCmd;
		});
	}

	start{|id|
		dogs[id].start()
	}
	stop{|id|
		dogs[id].stop()
	}
	pause{|id|
		dogs[id].pause()
	}
	resume{|id|
		dogs[id].resume()
	}
	reset{|id|
		dogs[id].reset()
	}


	maxTimeToFail {

		var max = 0;
		dogs.values.do({|dog|
			max = max.max(dog.maxTimeToFail)
		});

		^max;
	}

	putFix {|id, index, action|

		dogs[id].putFix(index,action);

	}

	addFix {|id, action|

		dogs[id].addFix(action);

	}

	removeFixAt {|id, index|

		dogs[id].removeFixAt(index);
	}

	at {|id|
		^dogs[id];
	}

	remove{|id|
		dogs.removeAt(id);
	}

	removeAt{|id|
		dogs.removeAt(id);
	}


}