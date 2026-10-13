# Deploys wipe running Games

miniciv keeps every Room and Game in memory, so restarting the server ends all of them. The server is redeployed every day at 04:07 UTC, whenever master has changed since the last deploy, and at once when someone starts a deploy by hand. Either way it restarts without waiting for Games to finish and without warning Players: whoever is mid-Game loses it, and their Room is gone. Security patches can also reboot the server at 04:20 UTC, with the same effect.

We accepted this rather than saving Rooms and Games across restarts or holding a deploy until no Game is running. Saving state is a game feature with its own design questions (what a restored Game looks like, how Players get back in), and is left to its own effort. Waiting for an idle server would let a busy server put off a deploy indefinitely, and a deploy started by hand is meant to happen now, for example to get a fix out. The cost is small at miniciv's size: at most about ten Players, mostly in the UK and Europe, so 04:07 UTC is the middle of their night, and the nightly deploy is skipped when master hasn't changed, so most nights nothing restarts.

If miniciv ever saves Rooms and Games across restarts, this decision should be revisited.
