# Fight logs for training

Put the exports of the mod's fight logs here: on the server, `/battlehub fights export` writes one file,
`config/battlehub/fights/export-<time>.zip`. Upload that zip into this folder (on GitHub: Add file, Upload files) and
commit it.

They are read by `train/fights.py` and used by `train/imitate.py` (see ../README.md).
