# Runs as the seeker at round start. {arrows} is passed in. Edit freely.
clear @s
give @s paintandseek:paint_brush
give @s minecraft:bow
$give @s minecraft:spectral_arrow $(arrows)
title @s times 10 70 20
title @s title {"text":"SEEKING","bold":true,"color":"red"}
