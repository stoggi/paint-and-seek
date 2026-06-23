# Runs as each hider at round start. Edit freely.
gamemode adventure @s
clear @s
give @s paintandseek:paint_brush
# Shrink hiders to 50% size to help them blend in (restored in cleanup).
attribute @s minecraft:scale base set 0.5
title @s times 10 70 20
title @s title {"text":"HIDING","bold":true,"color":"green"}
