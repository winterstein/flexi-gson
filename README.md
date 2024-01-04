flexi-gson
==========

A more robust version of the Gson Java - JSON serialisation library. This can handle vague classes and circular references.


## Handling "Vague" classes 

These occur when the container doesn't specify the type, which is problematic for json.

E.g. suppose you have the Java:

	class Foo {
		Object x = new Bar();
	}
	class Bar {
		String y = ":)";
	}

This will produce the json:

	{"x":{"y":":)"}}

Note that the type information -- that x is an instance of Bar -- has been lost.

This case happens all the time in Java, either through using interfaces or Maps. So we'd like to handle it!

## The Parent GSON: Version ?? TODO rebase of the current version

This version of GSON was "hand-forked" from the old Google Code version control system (which wasn't git).

Now that gson has moved from Google Code to Git and GitHub -- it'd be good to fork the official repo, and re-do our edits to that.

Unfortunately that is likely to be painful, as they've shifted quite a few classes around.

