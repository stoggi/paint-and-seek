# Runs as the seeker at round start. {arrows} and {hide} (seconds) are passed in. Edit freely.
gamemode adventure @s
clear @s
give @s paintandseek:paint_brush
give @s minecraft:bow
$give @s minecraft:spectral_arrow $(arrows)
# Blind the seeker until the hide phase is over so they can't peek.
$effect give @s minecraft:darkness $(hide) 0 true
title @s times 10 70 20
title @s title {"text":"SEEKING","bold":true,"color":"red"}
