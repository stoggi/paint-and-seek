# Runs as each participant when a round ends. Edit freely.
clear @s paintandseek:paint_brush
clear @s minecraft:bow
clear @s minecraft:spectral_arrow
# Clear blindness (e.g. a seeker still blinded if the round ends mid-hide).
# Only blindness - the winner-glow given to survivors is intentionally kept.
effect clear @s minecraft:blindness
# Restore normal size (hiders were shrunk to 50% in setup_hider).
attribute @s minecraft:scale base set 1
